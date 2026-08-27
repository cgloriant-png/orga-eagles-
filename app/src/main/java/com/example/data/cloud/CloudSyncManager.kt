package com.example.data.cloud

import android.content.Context
import android.util.Log
import com.example.data.db.PlanningDao
import com.example.data.model.BookingEntity
import com.example.data.model.LessonSlotEntity
import com.example.data.model.StudentEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class SyncStatus {
    OFFLINE,
    CONNECTING,
    SYNCING,
    CONNECTED_SYNCED,
    ERROR
}

class CloudSyncManager(
    private val context: Context,
    private val dao: PlanningDao,
    private val scope: CoroutineScope
) {
    private val TAG = "CloudSyncManager"

    // Primary cloud snapshot endpoint
    private val CLOUD_OBJECT_ID = "ff8081819ff5b11001a043d6d4743921"
    private val API_BASE_URL = "https://api.restful-api.dev/objects"
    private val NTFY_CHANNEL = "https://ntfy.sh/paramoteur_planning_sync_v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Separate long-lived client for SSE streaming
    private val streamClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val _syncStatus = MutableStateFlow(SyncStatus.CONNECTING)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("Initialisation cloud...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<String>("")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private val isSyncing = AtomicBoolean(false)
    private var syncJob: Job? = null
    private var sseJob: Job? = null

    // Track locally deleted item IDs to propagate deletions cleanly
    private val prefs = context.getSharedPreferences("paramoteur_deleted_tombstones", Context.MODE_PRIVATE)

    fun recordDeletedId(type: String, id: Long) {
        val currentSet = prefs.getStringSet("deleted_$type", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(id.toString())
        prefs.edit().putStringSet("deleted_$type", currentSet).apply()
        pushFullSync()
    }

    private fun getDeletedIds(type: String): Set<Long> {
        return prefs.getStringSet("deleted_$type", emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
    }

    init {
        startSyncLoops()
    }

    private fun startSyncLoops() {
        // 1. Polling loop every 3 seconds
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            // Initial sync on startup
            delay(300)
            syncFromCloud()

            while (isActive) {
                delay(3000)
                try {
                    syncFromCloud()
                } catch (e: Exception) {
                    Log.w(TAG, "Background sync tick error: ${e.message}")
                }
            }
        }

        // 2. Realtime SSE Stream listener for instant updates
        sseJob?.cancel()
        sseJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val streamRequest = Request.Builder()
                        .url("$NTFY_CHANNEL/sse")
                        .build()
                    val response = streamClient.newCall(streamRequest).execute()
                    val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
                    var line: String?
                    while (reader.readLine().also { line = it } != null && isActive) {
                        if (line?.startsWith("data:") == true) {
                            // Instant notification received from another device!
                            syncFromCloud()
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    // Retry streaming after 5 seconds if interrupted
                    delay(5000)
                }
            }
        }
    }

    fun forceSyncNow() {
        scope.launch(Dispatchers.IO) {
            _syncStatus.value = SyncStatus.SYNCING
            _statusMessage.value = "Synchronisation..."
            syncFromCloud()
            pushFullSync()
        }
    }

    /**
     * Pull remote slots, students, and bookings and update local database
     */
    suspend fun syncFromCloud() {
        if (!isSyncing.compareAndSet(false, true)) {
            return
        }

        try {
            val request = Request.Builder()
                .url("$API_BASE_URL/$CLOUD_OBJECT_ID")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    // If object doesn't exist yet on server, create it with local state
                    pushFullSyncInternal()
                    return
                }
                _syncStatus.value = SyncStatus.OFFLINE
                _statusMessage.value = "Hors-ligne"
                response.close()
                return
            }

            val responseBody = response.body?.string() ?: ""
            response.close()

            if (responseBody.isBlank()) return

            val rootJson = JSONObject(responseBody)
            val dataJson = rootJson.optJSONObject("data") ?: JSONObject()

            val remoteSlotsArray = dataJson.optJSONArray("slots") ?: JSONArray()
            val remoteStudentsArray = dataJson.optJSONArray("students") ?: JSONArray()
            val remoteBookingsArray = dataJson.optJSONArray("bookings") ?: JSONArray()
            val deletedSlotsArray = dataJson.optJSONArray("deletedSlotIds") ?: JSONArray()
            val deletedBookingsArray = dataJson.optJSONArray("deletedBookingIds") ?: JSONArray()

            val remoteDeletedSlotIds = mutableSetOf<Long>()
            for (i in 0 until deletedSlotsArray.length()) {
                remoteDeletedSlotIds.add(deletedSlotsArray.getLong(i))
            }

            val remoteDeletedBookingIds = mutableSetOf<Long>()
            for (i in 0 until deletedBookingsArray.length()) {
                remoteDeletedBookingIds.add(deletedBookingsArray.getLong(i))
            }

            val remoteSlots = mutableListOf<LessonSlotEntity>()
            for (i in 0 until remoteSlotsArray.length()) {
                val s = remoteSlotsArray.getJSONObject(i)
                val id = s.optLong("id")
                if (id !in remoteDeletedSlotIds) {
                    remoteSlots.add(
                        LessonSlotEntity(
                            id = id,
                            dateIso = s.optString("dateIso"),
                            startTime = s.optString("startTime"),
                            endTime = s.optString("endTime"),
                            title = s.optString("title"),
                            lessonType = s.optString("lessonType", "GONFLAGE"),
                            location = s.optString("location"),
                            maxCapacity = s.optInt("maxCapacity", 4),
                            notes = s.optString("notes"),
                            isCancelled = s.optBoolean("isCancelled", false),
                            createdAt = s.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            val remoteStudents = mutableListOf<StudentEntity>()
            for (i in 0 until remoteStudentsArray.length()) {
                val st = remoteStudentsArray.getJSONObject(i)
                remoteStudents.add(
                    StudentEntity(
                        id = st.optLong("id"),
                        firstName = st.optString("firstName"),
                        lastName = st.optString("lastName"),
                        phone = st.optString("phone"),
                        email = st.optString("email"),
                        level = st.optString("level", "Gonflage"),
                        notes = st.optString("notes"),
                        completedSessions = st.optInt("completedSessions", 0),
                        createdAt = st.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            val remoteBookings = mutableListOf<BookingEntity>()
            for (i in 0 until remoteBookingsArray.length()) {
                val b = remoteBookingsArray.getJSONObject(i)
                val id = b.optLong("id")
                if (id !in remoteDeletedBookingIds) {
                    remoteBookings.add(
                        BookingEntity(
                            id = id,
                            slotId = b.optLong("slotId"),
                            studentId = b.optLong("studentId"),
                            registeredAt = b.optLong("registeredAt", System.currentTimeMillis()),
                            isWaitingList = b.optBoolean("isWaitingList", false),
                            attended = b.optBoolean("attended", false)
                        )
                    )
                }
            }

            val localSlots = dao.getAllSlotsList()
            val localStudents = dao.getAllStudentsList()
            val localBookings = dao.getAllBookingsList()

            // 1. Delete slots that were deleted remotely or locally recorded
            val allDeletedSlots = remoteDeletedSlotIds + getDeletedIds("slot")
            for (delId in allDeletedSlots) {
                dao.deleteSlotById(delId)
            }

            // 2. Delete bookings that were deleted remotely or locally recorded
            val allDeletedBookings = remoteDeletedBookingIds + getDeletedIds("booking")
            for (delId in allDeletedBookings) {
                dao.deleteBookingById(delId)
            }

            // 3. Upsert remote slots
            if (remoteSlots.isNotEmpty()) {
                dao.insertSlots(remoteSlots)
            }

            // 4. Upsert remote students
            if (remoteStudents.isNotEmpty()) {
                dao.insertStudents(remoteStudents)
            }

            // 5. Upsert remote bookings
            if (remoteBookings.isNotEmpty()) {
                dao.insertBookings(remoteBookings)
            }

            // 6. If local had items not yet in remote, push merged state to server
            val remoteSlotIds = remoteSlots.map { it.id }.toSet()
            val remoteBookingIds = remoteBookings.map { it.id }.toSet()
            val hasNewLocalSlots = localSlots.any { it.id !in remoteSlotIds && it.id !in allDeletedSlots }
            val hasNewLocalBookings = localBookings.any { it.id !in remoteBookingIds && it.id !in allDeletedBookings }

            if (hasNewLocalSlots || hasNewLocalBookings) {
                pushFullSyncInternal()
            }

            _syncStatus.value = SyncStatus.CONNECTED_SYNCED
            _statusMessage.value = "Synchronisé en direct"
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
            _lastSyncTime.value = timeFormat.format(Date())

        } catch (e: Exception) {
            Log.w(TAG, "Sync from cloud error: ${e.message}")
            _syncStatus.value = SyncStatus.OFFLINE
            _statusMessage.value = "Hors-ligne"
        } finally {
            isSyncing.set(false)
        }
    }

    /**
     * Push all local data (slots, students, bookings) to the cloud
     */
    fun pushFullSync() {
        scope.launch(Dispatchers.IO) {
            pushFullSyncInternal()
        }
    }

    private suspend fun pushFullSyncInternal() {
        try {
            val slots = dao.getAllSlotsList()
            val students = dao.getAllStudentsList()
            val bookings = dao.getAllBookingsList()
            val deletedSlotIds = getDeletedIds("slot")
            val deletedBookingIds = getDeletedIds("booking")

            val slotsArray = JSONArray()
            for (s in slots) {
                if (s.id !in deletedSlotIds) {
                    val obj = JSONObject()
                    obj.put("id", s.id)
                    obj.put("dateIso", s.dateIso)
                    obj.put("startTime", s.startTime)
                    obj.put("endTime", s.endTime)
                    obj.put("title", s.title)
                    obj.put("lessonType", s.lessonType)
                    obj.put("location", s.location)
                    obj.put("maxCapacity", s.maxCapacity)
                    obj.put("notes", s.notes)
                    obj.put("isCancelled", s.isCancelled)
                    obj.put("createdAt", s.createdAt)
                    slotsArray.put(obj)
                }
            }

            val studentsArray = JSONArray()
            for (st in students) {
                val obj = JSONObject()
                obj.put("id", st.id)
                obj.put("firstName", st.firstName)
                obj.put("lastName", st.lastName)
                obj.put("phone", st.phone)
                obj.put("email", st.email)
                obj.put("level", st.level)
                obj.put("notes", st.notes)
                obj.put("completedSessions", st.completedSessions)
                obj.put("createdAt", st.createdAt)
                studentsArray.put(obj)
            }

            val bookingsArray = JSONArray()
            for (b in bookings) {
                if (b.id !in deletedBookingIds) {
                    val obj = JSONObject()
                    obj.put("id", b.id)
                    obj.put("slotId", b.slotId)
                    obj.put("studentId", b.studentId)
                    obj.put("registeredAt", b.registeredAt)
                    obj.put("isWaitingList", b.isWaitingList)
                    obj.put("attended", b.attended)
                    bookingsArray.put(obj)
                }
            }

            val deletedSlotsJson = JSONArray()
            deletedSlotIds.forEach { deletedSlotsJson.put(it) }

            val deletedBookingsJson = JSONArray()
            deletedBookingIds.forEach { deletedBookingsJson.put(it) }

            val dataJson = JSONObject()
            dataJson.put("version", 2)
            dataJson.put("lastUpdated", System.currentTimeMillis())
            dataJson.put("slots", slotsArray)
            dataJson.put("students", studentsArray)
            dataJson.put("bookings", bookingsArray)
            dataJson.put("deletedSlotIds", deletedSlotsJson)
            dataJson.put("deletedBookingIds", deletedBookingsJson)

            val payload = JSONObject()
            payload.put("name", "Planning Paramoteur")
            payload.put("data", dataJson)

            val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

            // 1. PUT to REST snapshot storage
            val putRequest = Request.Builder()
                .url("$API_BASE_URL/$CLOUD_OBJECT_ID")
                .put(body)
                .build()

            var response = client.newCall(putRequest).execute()
            if (response.code == 404) {
                response.close()
                val postRequest = Request.Builder()
                    .url(API_BASE_URL)
                    .post(body)
                    .build()
                response = client.newCall(postRequest).execute()
            }

            if (response.isSuccessful) {
                _syncStatus.value = SyncStatus.CONNECTED_SYNCED
                _statusMessage.value = "Synchronisé en direct"
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
                _lastSyncTime.value = timeFormat.format(Date())
            }
            response.close()

            // 2. Broadcast instant ping via NTFY so all other active phones update immediately
            try {
                val pingBody = "sync".toRequestBody("text/plain".toMediaType())
                val pingReq = Request.Builder().url(NTFY_CHANNEL).post(pingBody).build()
                client.newCall(pingReq).execute().close()
            } catch (e: Exception) {
                // Ping broadcast is best-effort, background 3s loop guarantees eventual consistency
            }

        } catch (e: Exception) {
            Log.e(TAG, "Push error: ${e.message}", e)
        }
    }

    fun pushSlot(slot: LessonSlotEntity) {
        pushFullSync()
    }

    fun deleteSlot(slotId: Long) {
        recordDeletedId("slot", slotId)
    }

    fun pushStudent(student: StudentEntity) {
        pushFullSync()
    }

    fun deleteStudent(studentId: Long) {
        pushFullSync()
    }

    fun pushBooking(booking: BookingEntity) {
        pushFullSync()
    }

    fun deleteBooking(bookingId: Long) {
        recordDeletedId("booking", bookingId)
    }

    fun cleanup() {
        syncJob?.cancel()
        sseJob?.cancel()
    }
}
