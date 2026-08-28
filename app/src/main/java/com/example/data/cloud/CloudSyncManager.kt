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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Multi-device synchronized cloud topic
    private val SYNC_TOPIC = "paramoteur_planning_sync_v5"
    private val NTFY_BASE_URL = "https://ntfy.sh/$SYNC_TOPIC"

    private val prefs = context.getSharedPreferences("paramoteur_sync_prefs_v5", Context.MODE_PRIVATE)

    // Unique device ID to identify senders and prevent infinite loops
    private val myDeviceId: String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device_id", it).apply()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Long-lived client for SSE streaming
    private val sseClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val _syncStatus = MutableStateFlow(SyncStatus.CONNECTING)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("Connexion au Cloud...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<String>("")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private val syncMutex = Mutex()
    private var syncJob: Job? = null
    private var sseJob: Job? = null
    private var pendingPushJob: Job? = null

    fun recordDeletedId(type: String, id: Long) {
        val currentSet = prefs.getStringSet("deleted_$type", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(id.toString())
        prefs.edit().putStringSet("deleted_$type", currentSet).apply()
        pushFullSync(immediate = true)
    }

    private fun getDeletedIds(type: String): Set<Long> {
        return prefs.getStringSet("deleted_$type", emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
    }

    private fun saveRemoteDeletedIds(slotIds: Set<Long>, bookingIds: Set<Long>, studentIds: Set<Long>) {
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
        if (studentIds.isNotEmpty()) {
            val currentStudents = prefs.getStringSet("deleted_student", emptySet())?.toMutableSet() ?: mutableSetOf()
            currentStudents.addAll(studentIds.map { it.toString() })
            prefs.edit().putStringSet("deleted_student", currentStudents).apply()
        }
    }

    init {
        startSyncLoops()
    }

    private fun startSyncLoops() {
        // 1. Initial immediate sync + periodic background polling (every 4 seconds)
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            delay(150)
            syncFromCloud()

            while (isActive) {
                delay(4000)
                try {
                    syncFromCloud()
                } catch (e: Exception) {
                    Log.w(TAG, "Periodic sync loop error: ${e.message}")
                }
            }
        }

        // 2. Real-time Server-Sent Events (SSE) stream for instant sub-second synchronization
        sseJob?.cancel()
        sseJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val streamRequest = Request.Builder()
                        .url("$NTFY_BASE_URL/sse")
                        .build()

                    val response = sseClient.newCall(streamRequest).execute()
                    val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
                    var line: String?
                    while (reader.readLine().also { line = it } != null && isActive) {
                        val currentLine = line ?: continue
                        if (currentLine.startsWith("data:")) {
                            val dataContent = currentLine.removePrefix("data:").trim()
                            if (dataContent.isNotBlank()) {
                                handleIncomingNtfyEvent(dataContent)
                            }
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    delay(3000)
                }
            }
        }
    }

    private suspend fun handleIncomingNtfyEvent(dataContent: String) {
        try {
            val ntfyObj = JSONObject(dataContent)
            val eventType = ntfyObj.optString("event", "")
            if (eventType != "message") return

            val messageString = ntfyObj.optString("message", "")
            val attachmentObj = ntfyObj.optJSONObject("attachment")
            val attachmentUrl = attachmentObj?.optString("url", "")

            var snapshotJson: JSONObject? = null

            // 1. Check direct message payload
            if (messageString.startsWith("{") && messageString.contains("\"slots\"")) {
                val directObj = JSONObject(messageString)
                val sender = directObj.optString("senderId", "")
                if (sender == myDeviceId) return // Own echo
                snapshotJson = directObj
            }
            // 2. Or download attachment payload if provided
            else if (!attachmentUrl.isNullOrBlank()) {
                try {
                    val getReq = Request.Builder().url(attachmentUrl).get().build()
                    val resp = httpClient.newCall(getReq).execute()
                    if (resp.isSuccessful) {
                        val bodyText = resp.body?.string() ?: ""
                        resp.close()
                        if (bodyText.isNotBlank()) {
                            val dlObj = JSONObject(bodyText)
                            val sender = dlObj.optString("senderId", "")
                            if (sender == myDeviceId) return // Own echo
                            snapshotJson = dlObj
                        }
                    } else {
                        resp.close()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error downloading attachment: ${e.message}")
                }
            }

            if (snapshotJson != null) {
                mergeSnapshotIntoLocalDb(snapshotJson)
            } else {
                // Generic notification ping -> poll full cloud state
                syncFromCloud()
            }
        } catch (e: Exception) {
            Log.w(TAG, "handleIncomingNtfyEvent error: ${e.message}")
        }
    }

    fun forceSyncNow() {
        scope.launch(Dispatchers.IO) {
            _syncStatus.value = SyncStatus.SYNCING
            _statusMessage.value = "Synchronisation..."
            syncFromCloud(forcePushMerged = true)
        }
    }

    /**
     * Fetch the newest snapshot from cloud, merge with local DB, and re-publish union if needed
     */
    suspend fun syncFromCloud(forcePushMerged: Boolean = false) {
        syncMutex.withLock {
            try {
                // Poll recent topic messages
                var remoteSnapshot: JSONObject? = null
                try {
                    val pollReq = Request.Builder()
                        .url("$NTFY_BASE_URL/json?poll=1")
                        .get()
                        .build()
                    val pollResp = httpClient.newCall(pollReq).execute()
                    if (pollResp.isSuccessful) {
                        val bodyText = pollResp.body?.string() ?: ""
                        pollResp.close()
                        val lines = bodyText.split("\n").filter { it.isNotBlank() }

                        // Iterate in reverse (newest first) to find the most recent valid snapshot
                        for (line in lines.reversed()) {
                            try {
                                val msgObj = JSONObject(line)
                                val msgStr = msgObj.optString("message", "")
                                val attObj = msgObj.optJSONObject("attachment")
                                val attUrl = attObj?.optString("url", "")

                                if (msgStr.startsWith("{") && msgStr.contains("\"slots\"")) {
                                    remoteSnapshot = JSONObject(msgStr)
                                    break
                                } else if (!attUrl.isNullOrBlank()) {
                                    val getReq = Request.Builder().url(attUrl).get().build()
                                    val attResp = httpClient.newCall(getReq).execute()
                                    if (attResp.isSuccessful) {
                                        val attBody = attResp.body?.string() ?: ""
                                        attResp.close()
                                        if (attBody.isNotBlank()) {
                                            remoteSnapshot = JSONObject(attBody)
                                            break
                                        }
                                    } else {
                                        attResp.close()
                                    }
                                }
                            } catch (ignored: Exception) {}
                        }
                    } else {
                        pollResp.close()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Poll error: ${e.message}")
                }

                val hasNewLocalData = mergeSnapshotIntoLocalDb(remoteSnapshot)

                if (hasNewLocalData || forcePushMerged) {
                    pushSnapshotToCloudInternal()
                }

                _syncStatus.value = SyncStatus.CONNECTED_SYNCED
                _statusMessage.value = "Synchronisé en direct"
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
                _lastSyncTime.value = timeFormat.format(Date())

            } catch (e: Exception) {
                Log.e(TAG, "syncFromCloud error: ${e.message}", e)
                _syncStatus.value = SyncStatus.OFFLINE
                _statusMessage.value = "Hors-ligne"
            }
        }
    }

    private suspend fun mergeSnapshotIntoLocalDb(remoteJson: JSONObject?): Boolean {
        val localSlots = dao.getAllSlotsList()
        val localStudents = dao.getAllStudentsList()
        val localBookings = dao.getAllBookingsList()

        if (remoteJson == null) {
            // No remote state found; local data is brand new
            return localSlots.isNotEmpty() || localStudents.isNotEmpty() || localBookings.isNotEmpty()
        }

        val remoteSlotsArray = remoteJson.optJSONArray("slots") ?: JSONArray()
        val remoteStudentsArray = remoteJson.optJSONArray("students") ?: JSONArray()
        val remoteBookingsArray = remoteJson.optJSONArray("bookings") ?: JSONArray()
        val deletedSlotsArray = remoteJson.optJSONArray("deletedSlotIds") ?: JSONArray()
        val deletedBookingsArray = remoteJson.optJSONArray("deletedBookingIds") ?: JSONArray()
        val deletedStudentsArray = remoteJson.optJSONArray("deletedStudentIds") ?: JSONArray()

        val remoteDeletedSlotIds = mutableSetOf<Long>()
        for (i in 0 until deletedSlotsArray.length()) {
            remoteDeletedSlotIds.add(deletedSlotsArray.getLong(i))
        }

        val remoteDeletedBookingIds = mutableSetOf<Long>()
        for (i in 0 until deletedBookingsArray.length()) {
            remoteDeletedBookingIds.add(deletedBookingsArray.getLong(i))
        }

        val remoteDeletedStudentIds = mutableSetOf<Long>()
        for (i in 0 until deletedStudentsArray.length()) {
            remoteDeletedStudentIds.add(deletedStudentsArray.getLong(i))
        }

        saveRemoteDeletedIds(remoteDeletedSlotIds, remoteDeletedBookingIds, remoteDeletedStudentIds)

        val allDeletedSlots = remoteDeletedSlotIds + getDeletedIds("slot")
        val allDeletedBookings = remoteDeletedBookingIds + getDeletedIds("booking")
        val allDeletedStudents = remoteDeletedStudentIds + getDeletedIds("student")

        // 1. Apply deletions to local Room
        for (delId in allDeletedSlots) {
            dao.deleteSlotById(delId)
        }
        for (delId in allDeletedBookings) {
            dao.deleteBookingById(delId)
        }
        for (delId in allDeletedStudents) {
            dao.deleteStudentById(delId)
        }

        // 2. Parse and upsert remote slots
        val remoteSlots = mutableListOf<LessonSlotEntity>()
        val remoteSlotIds = mutableSetOf<Long>()
        for (i in 0 until remoteSlotsArray.length()) {
            val s = remoteSlotsArray.getJSONObject(i)
            val id = s.optLong("id")
            remoteSlotIds.add(id)
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

        // 3. Parse and upsert remote students
        val remoteStudents = mutableListOf<StudentEntity>()
        val remoteStudentIds = mutableSetOf<Long>()
        for (i in 0 until remoteStudentsArray.length()) {
            val st = remoteStudentsArray.getJSONObject(i)
            val id = st.optLong("id")
            remoteStudentIds.add(id)
            if (id !in allDeletedStudents) {
                remoteStudents.add(
                    StudentEntity(
                        id = id,
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
        }

        // 4. Parse and upsert remote bookings
        val remoteBookings = mutableListOf<BookingEntity>()
        val remoteBookingIds = mutableSetOf<Long>()
        for (i in 0 until remoteBookingsArray.length()) {
            val b = remoteBookingsArray.getJSONObject(i)
            val id = b.optLong("id")
            remoteBookingIds.add(id)
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

        if (remoteSlots.isNotEmpty()) dao.insertSlots(remoteSlots)
        if (remoteStudents.isNotEmpty()) dao.insertStudents(remoteStudents)
        if (remoteBookings.isNotEmpty()) dao.insertBookings(remoteBookings)

        // Check if local database has items that were not yet in the remote snapshot
        val localUnsyncedSlots = localSlots.filter { it.id !in remoteSlotIds && it.id !in allDeletedSlots }
        val localUnsyncedStudents = localStudents.filter { it.id !in remoteStudentIds && it.id !in allDeletedStudents }
        val localUnsyncedBookings = localBookings.filter { it.id !in remoteBookingIds && it.id !in allDeletedBookings }

        return localUnsyncedSlots.isNotEmpty() || localUnsyncedStudents.isNotEmpty() || localUnsyncedBookings.isNotEmpty()
    }

    /**
     * Push full snapshot to Cloud immediately or debounced
     */
    fun pushFullSync(immediate: Boolean = false) {
        if (immediate) {
            scope.launch(Dispatchers.IO) {
                syncMutex.withLock {
                    pushSnapshotToCloudInternal()
                }
            }
        } else {
            pendingPushJob?.cancel()
            pendingPushJob = scope.launch(Dispatchers.IO) {
                delay(80)
                syncMutex.withLock {
                    pushSnapshotToCloudInternal()
                }
            }
        }
    }

    private suspend fun pushSnapshotToCloudInternal() {
        try {
            val slots = dao.getAllSlotsList()
            val students = dao.getAllStudentsList()
            val bookings = dao.getAllBookingsList()
            val deletedSlotIds = getDeletedIds("slot")
            val deletedBookingIds = getDeletedIds("booking")
            val deletedStudentIds = getDeletedIds("student")

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
                if (st.id !in deletedStudentIds) {
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

            val deletedStudentsJson = JSONArray()
            deletedStudentIds.forEach { deletedStudentsJson.put(it) }

            val payload = JSONObject()
            payload.put("version", 5)
            payload.put("senderId", myDeviceId)
            payload.put("lastUpdated", System.currentTimeMillis())
            payload.put("slots", slotsArray)
            payload.put("students", studentsArray)
            payload.put("bookings", bookingsArray)
            payload.put("deletedSlotIds", deletedSlotsJson)
            payload.put("deletedBookingIds", deletedBookingsJson)
            payload.put("deletedStudentIds", deletedStudentsJson)

            val payloadString = payload.toString()

            // 1. Upload persistent attachment snapshot (cached on NTFY server for 12+ hours)
            val putBody = payloadString.toRequestBody("application/json; charset=utf-8".toMediaType())
            val putReq = Request.Builder()
                .url(NTFY_BASE_URL)
                .addHeader("Filename", "sync.json")
                .addHeader("Title", "ParamoteurSync")
                .addHeader("Tags", "cloud,sync")
                .put(putBody)
                .build()

            val putResp = httpClient.newCall(putReq).execute()
            putResp.close()

            // 2. If payload fits directly in inline message (< 3800 bytes), also POST inline message for instant SSE parsing
            if (payloadString.length < 3800) {
                val postBody = payloadString.toRequestBody("text/plain; charset=utf-8".toMediaType())
                val postReq = Request.Builder()
                    .url(NTFY_BASE_URL)
                    .addHeader("Title", "ParamoteurSyncDirect")
                    .addHeader("Tags", "sync")
                    .post(postBody)
                    .build()
                val postResp = httpClient.newCall(postReq).execute()
                postResp.close()
            }

            _syncStatus.value = SyncStatus.CONNECTED_SYNCED
            _statusMessage.value = "Synchronisé en direct"
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
            _lastSyncTime.value = timeFormat.format(Date())

        } catch (e: Exception) {
            Log.e(TAG, "pushSnapshotToCloudInternal error: ${e.message}", e)
        }
    }

    fun pushSlot(slot: LessonSlotEntity) {
        pushFullSync(immediate = true)
    }

    fun deleteSlot(slotId: Long) {
        recordDeletedId("slot", slotId)
    }

    fun pushStudent(student: StudentEntity) {
        pushFullSync(immediate = true)
    }

    fun deleteStudent(studentId: Long) {
        recordDeletedId("student", studentId)
    }

    fun pushBooking(booking: BookingEntity) {
        pushFullSync(immediate = true)
    }

    fun deleteBooking(bookingId: Long) {
        recordDeletedId("booking", bookingId)
    }

    fun cleanup() {
        syncJob?.cancel()
        sseJob?.cancel()
        pendingPushJob?.cancel()
    }
}
