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

    // Multi-tier cloud endpoints for maximum reliability
    private val NTFY_TOPIC = "paramoteur_planning_data_v2"
    private val NTFY_BASE_URL = "https://ntfy.sh/$NTFY_TOPIC"
    private val REST_BACKUP_URL = "https://api.restful-api.dev/objects/ff8081819ff5b11001a043d6d4743921"
    private val REST_CREATE_URL = "https://api.restful-api.dev/objects"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Long-lived client for SSE streaming
    private val streamClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val _syncStatus = MutableStateFlow(SyncStatus.CONNECTING)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("Connexion au Cloud...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<String>("")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private val isSyncing = AtomicBoolean(false)
    private var syncJob: Job? = null
    private var sseJob: Job? = null

    // Track deleted IDs to prevent reviving deleted items across devices
    private val prefs = context.getSharedPreferences("paramoteur_deleted_tombstones_v2", Context.MODE_PRIVATE)

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

    private fun saveRemoteDeletedIds(slotIds: Set<Long>, bookingIds: Set<Long>) {
        if (slotIds.isNotEmpty()) {
            val currentSlots = prefs.getStringSet("deleted_slot", emptySet())?.toMutableSet() ?: mutableSetOf()
            currentSlots.addAll(slotIds.map { it.toString() })
            prefs.edit().putStringSet("deleted_slot", currentSlots).apply()
        }
        if (bookingIds.isNotEmpty()) {
            val currentBookings = prefs.getStringSet("deleted_booking", emptySet())?.toMutableSet() ?: mutableSetOf()
            currentBookings.addAll(bookingIds.map { it.toString() })
            prefs.edit().putStringSet("deleted_booking", currentBookings).apply()
        }
    }

    init {
        startSyncLoops()
    }

    private fun startSyncLoops() {
        // 1. Initial immediate sync + periodic poll loop every 5 seconds
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            delay(400)
            syncFromCloud()

            while (isActive) {
                delay(5000)
                try {
                    syncFromCloud()
                } catch (e: Exception) {
                    Log.w(TAG, "Sync loop tick error: ${e.message}")
                }
            }
        }

        // 2. Realtime SSE stream for instant sub-second synchronization
        sseJob?.cancel()
        sseJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val streamRequest = Request.Builder()
                        .url("$NTFY_BASE_URL/sse")
                        .build()

                    val response = streamClient.newCall(streamRequest).execute()
                    val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
                    var line: String?
                    while (reader.readLine().also { line = it } != null && isActive) {
                        val currentLine = line ?: continue
                        if (currentLine.startsWith("data:")) {
                            val dataContent = currentLine.removePrefix("data:").trim()
                            if (dataContent.isNotBlank()) {
                                try {
                                    val ntfyObj = JSONObject(dataContent)
                                    val messageString = ntfyObj.optString("message", "")
                                    if (messageString.startsWith("{") && messageString.contains("slots")) {
                                        // Directly process the full JSON payload broadcasted
                                        val payloadObj = JSONObject(messageString)
                                        processRemoteJsonPayload(payloadObj)
                                    } else {
                                        // Refresh from cloud
                                        syncFromCloud()
                                    }
                                } catch (e: Exception) {
                                    syncFromCloud()
                                }
                            }
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    delay(4000)
                }
            }
        }
    }

    fun forceSyncNow() {
        scope.launch(Dispatchers.IO) {
            _syncStatus.value = SyncStatus.SYNCING
            _statusMessage.value = "Synchronisation..."
            syncFromCloud()
            pushFullSyncInternal()
        }
    }

    /**
     * Pull remote state from NTFY cache or REST backup and apply to Room
     */
    suspend fun syncFromCloud() {
        if (!isSyncing.compareAndSet(false, true)) {
            return
        }

        try {
            var jsonString: String? = null

            // Step A: Try reading latest broadcast from NTFY poll
            try {
                val ntfyPollReq = Request.Builder()
                    .url("$NTFY_BASE_URL/json?poll=1")
                    .get()
                    .build()
                val ntfyResp = client.newCall(ntfyPollReq).execute()
                if (ntfyResp.isSuccessful) {
                    val respText = ntfyResp.body?.string() ?: ""
                    ntfyResp.close()
                    // NTFY /json returns lines of messages. Read the last message
                    val lines = respText.lines().filter { it.isNotBlank() }
                    for (i in lines.indices.reversed()) {
                        try {
                            val msgObj = JSONObject(lines[i])
                            val msgBody = msgObj.optString("message", "")
                            if (msgBody.startsWith("{") && (msgBody.contains("slots") || msgBody.contains("students"))) {
                                jsonString = msgBody
                                break
                            }
                        } catch (_: Exception) {}
                    }
                } else {
                    ntfyResp.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "NTFY poll fallback: ${e.message}")
            }

            // Step B: If NTFY didn't have data, try REST backup
            if (jsonString == null) {
                try {
                    val restReq = Request.Builder()
                        .url(REST_BACKUP_URL)
                        .get()
                        .build()
                    val restResp = client.newCall(restReq).execute()
                    if (restResp.isSuccessful) {
                        val bodyText = restResp.body?.string() ?: ""
                        restResp.close()
                        if (bodyText.isNotBlank()) {
                            val rootObj = JSONObject(bodyText)
                            val dataObj = rootObj.optJSONObject("data")
                            if (dataObj != null) {
                                jsonString = dataObj.toString()
                            }
                        }
                    } else {
                        restResp.close()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "REST backup fetch error: ${e.message}")
                }
            }

            if (jsonString != null) {
                val payloadObj = JSONObject(jsonString)
                processRemoteJsonPayload(payloadObj)
            } else {
                // If cloud has no state yet, push local database to cloud
                pushFullSyncInternal()
            }

        } catch (e: Exception) {
            Log.e(TAG, "syncFromCloud error: ${e.message}", e)
            _syncStatus.value = SyncStatus.OFFLINE
            _statusMessage.value = "Hors-ligne"
        } finally {
            isSyncing.set(false)
        }
    }

    private suspend fun processRemoteJsonPayload(dataJson: JSONObject) {
        try {
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

            saveRemoteDeletedIds(remoteDeletedSlotIds, remoteDeletedBookingIds)

            val allDeletedSlots = remoteDeletedSlotIds + getDeletedIds("slot")
            val allDeletedBookings = remoteDeletedBookingIds + getDeletedIds("booking")

            val remoteSlots = mutableListOf<LessonSlotEntity>()
            for (i in 0 until remoteSlotsArray.length()) {
                val s = remoteSlotsArray.getJSONObject(i)
                val id = s.optLong("id")
                if (id !in allDeletedSlots) {
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
                if (id !in allDeletedBookings) {
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
            val localBookings = dao.getAllBookingsList()

            // 1. Delete slots that were deleted
            for (delId in allDeletedSlots) {
                dao.deleteSlotById(delId)
            }

            // 2. Delete bookings that were deleted
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

            // 6. Check if local had new items not yet in remote, and push merge if needed
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
            Log.e(TAG, "Error applying remote payload: ${e.message}", e)
        }
    }

    /**
     * Push all local data to NTFY channel and REST backup
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

            val jsonString = dataJson.toString()

            // 1. Instant broadcast to NTFY channel
            try {
                val ntfyBody = jsonString.toRequestBody("application/json".toMediaType())
                val ntfyReq = Request.Builder()
                    .url(NTFY_BASE_URL)
                    .addHeader("Title", "CloudSync")
                    .addHeader("Tags", "cloud,sync")
                    .post(ntfyBody)
                    .build()
                val ntfyResp = client.newCall(ntfyReq).execute()
                ntfyResp.close()
            } catch (e: Exception) {
                Log.w(TAG, "NTFY broadcast error: ${e.message}")
            }

            // 2. Persistent storage to REST backup
            try {
                val restPayload = JSONObject()
                restPayload.put("name", "Planning Paramoteur")
                restPayload.put("data", dataJson)

                val restBody = restPayload.toString().toRequestBody(JSON_MEDIA_TYPE)
                val putReq = Request.Builder()
                    .url(REST_BACKUP_URL)
                    .put(restBody)
                    .build()
                var restResp = client.newCall(putReq).execute()
                if (restResp.code == 404) {
                    restResp.close()
                    val postReq = Request.Builder()
                        .url(REST_CREATE_URL)
                        .post(restBody)
                        .build()
                    restResp = client.newCall(postReq).execute()
                }
                restResp.close()
            } catch (e: Exception) {
                Log.w(TAG, "REST backup push error: ${e.message}")
            }

            _syncStatus.value = SyncStatus.CONNECTED_SYNCED
            _statusMessage.value = "Synchronisé en direct"
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
            _lastSyncTime.value = timeFormat.format(Date())

        } catch (e: Exception) {
            Log.e(TAG, "pushFullSyncInternal error: ${e.message}", e)
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
