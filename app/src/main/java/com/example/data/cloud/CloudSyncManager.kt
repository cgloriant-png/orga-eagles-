package com.example.data.cloud

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.db.PlanningDao
import com.example.data.model.BookingEntity
import com.example.data.model.LessonSlotEntity
import com.example.data.model.StudentEntity
import com.example.data.model.StudentProgressEntity
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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

    private val prefs = context.getSharedPreferences("paramoteur_sync_prefs_v6", Context.MODE_PRIVATE)

    // Unique device ID to identify senders and prevent infinite loops
    private val myDeviceId: String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device_id", it).apply()
    }

    // Configurable School / Club Code
    private val _schoolCode = MutableStateFlow(
        prefs.getString("school_code", "PLOUHARNEL") ?: "PLOUHARNEL"
    )
    val schoolCode: StateFlow<String> = _schoolCode.asStateFlow()

    private fun getSanitizedTopic(code: String): String {
        val clean = code.trim().lowercase().replace("[^a-z0-9_]".toRegex(), "")
        return if (clean.isBlank()) "paramoteur_plouharnel_v6" else "paramoteur_${clean}_v6"
    }

    private var currentTopic: String = getSanitizedTopic(_schoolCode.value)
    private fun getNtfyUrl() = "https://ntfy.sh/$currentTopic"

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

    private val _syncStatus = MutableStateFlow(SyncStatus.CONNECTING)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("Connexion au Cloud (${_schoolCode.value})...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<String>("")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private val _syncedSlotsCount = MutableStateFlow(0)
    val syncedSlotsCount: StateFlow<Int> = _syncedSlotsCount.asStateFlow()

    private val _syncedStudentsCount = MutableStateFlow(0)
    val syncedStudentsCount: StateFlow<Int> = _syncedStudentsCount.asStateFlow()

    private val _syncedBookingsCount = MutableStateFlow(0)
    val syncedBookingsCount: StateFlow<Int> = _syncedBookingsCount.asStateFlow()

    private val syncMutex = Mutex()
    private var syncJob: Job? = null
    private var sseJob: Job? = null
    private var pendingPushJob: Job? = null

    init {
        startSyncLoops()
    }

    fun setSchoolCode(newCode: String) {
        val clean = newCode.trim().uppercase()
        if (clean.isNotBlank() && clean != _schoolCode.value) {
            _schoolCode.value = clean
            prefs.edit().putString("school_code", clean).apply()
            currentTopic = getSanitizedTopic(clean)
            _statusMessage.value = "Connexion à $clean..."
            restartSyncLoops()
            forceSyncNow()
        }
    }

    private fun restartSyncLoops() {
        syncJob?.cancel()
        sseJob?.cancel()
        startSyncLoops()
    }

    private fun startSyncLoops() {
        // 1. Initial immediate sync + periodic background polling (every 3 seconds)
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            delay(100)
            syncFromCloud(forcePushMerged = true)

            while (isActive) {
                delay(3500)
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
                    val streamUrl = "${getNtfyUrl()}/sse"
                    val streamRequest = Request.Builder()
                        .url(streamUrl)
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

    private fun compressPayload(jsonStr: String): String {
        return try {
            if (jsonStr.length < 60000) {
                jsonStr // Plain JSON is best for instant browser & mobile interoperability
            } else {
                val bos = ByteArrayOutputStream()
                GZIPOutputStream(bos).use { it.write(jsonStr.toByteArray(Charsets.UTF_8)) }
                "GZ:" + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            jsonStr
        }
    }

    private fun decompressPayload(payload: String): String {
        return try {
            if (payload.startsWith("GZ:")) {
                val base64Data = payload.removePrefix("GZ:")
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                val bis = ByteArrayInputStream(bytes)
                GZIPInputStream(bis).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                payload
            }
        } catch (e: Exception) {
            payload
        }
    }

    private suspend fun handleIncomingNtfyEvent(dataContent: String) {
        try {
            val ntfyObj = JSONObject(dataContent)
            val eventType = ntfyObj.optString("event", "")
            if (eventType != "message") return

            val rawMessage = ntfyObj.optString("message", "")
            if (rawMessage.isBlank()) return

            val decompressed = decompressPayload(rawMessage)
            if (decompressed.startsWith("{") && decompressed.contains("\"slots\"")) {
                val snapshotJson = JSONObject(decompressed)
                val sender = snapshotJson.optString("senderId", "")
                if (sender == myDeviceId) return // Own echo

                mergeSnapshotIntoLocalDb(snapshotJson)

                val totalSlots = dao.getSlotsCount()
                val totalStudents = dao.getStudentsCount()
                val totalBookings = dao.getAllBookingsList().size

                _syncedSlotsCount.value = totalSlots
                _syncedStudentsCount.value = totalStudents
                _syncedBookingsCount.value = totalBookings
                _syncStatus.value = SyncStatus.CONNECTED_SYNCED
                _statusMessage.value = "En direct (${_schoolCode.value})"
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
                _lastSyncTime.value = timeFormat.format(Date())
            } else {
                // Ping notification -> poll full cloud state
                syncFromCloud()
            }
        } catch (e: Exception) {
            Log.w(TAG, "handleIncomingNtfyEvent error: ${e.message}")
        }
    }

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

    fun forceSyncNow() {
        scope.launch(Dispatchers.IO) {
            _syncStatus.value = SyncStatus.SYNCING
            _statusMessage.value = "Synchronisation en cours..."
            syncFromCloud(forcePushMerged = true)
        }
    }

    /**
     * Fetch the newest snapshot from cloud, merge with local DB, and re-publish union if needed
     */
    suspend fun syncFromCloud(forcePushMerged: Boolean = false) {
        syncMutex.withLock {
            try {
                var remoteSnapshot: JSONObject? = null
                val pollUrl = "${getNtfyUrl()}/json?poll=1&since=all"

                try {
                    val pollReq = Request.Builder()
                        .url(pollUrl)
                        .get()
                        .build()
                    val pollResp = httpClient.newCall(pollReq).execute()
                    if (pollResp.isSuccessful) {
                        val bodyText = pollResp.body?.string() ?: ""
                        pollResp.close()
                        val lines = bodyText.split("\n").filter { it.isNotBlank() }

                        // Collect and merge across all historical snapshots to ensure no student or slot is ever lost
                        val aggregatedSlots = mutableMapOf<Long, JSONObject>()
                        val aggregatedStudents = mutableMapOf<Long, JSONObject>()
                        val aggregatedBookings = mutableMapOf<Long, JSONObject>()
                        val aggregatedProgress = mutableMapOf<Long, JSONObject>()
                        val aggregatedDeletedSlots = mutableSetOf<Long>()
                        val aggregatedDeletedBookings = mutableSetOf<Long>()
                        val aggregatedDeletedStudents = mutableSetOf<Long>()

                        for (line in lines) {
                            try {
                                val msgObj = JSONObject(line)
                                val rawMsg = msgObj.optString("message", "")
                                if (rawMsg.isNotBlank()) {
                                    val decompressed = decompressPayload(rawMsg)
                                    if (decompressed.startsWith("{") && decompressed.contains("\"slots\"")) {
                                        val snap = JSONObject(decompressed)
                                        val slotsArr = snap.optJSONArray("slots") ?: JSONArray()
                                        for (i in 0 until slotsArr.length()) {
                                            val s = slotsArr.getJSONObject(i)
                                            aggregatedSlots[s.optLong("id")] = s
                                        }
                                        val studsArr = snap.optJSONArray("students") ?: JSONArray()
                                        for (i in 0 until studsArr.length()) {
                                            val st = studsArr.getJSONObject(i)
                                            val fn = st.optString("firstName", "").trim()
                                            val ln = st.optString("lastName", "").trim()
                                            // Purge sample dummy students
                                            val isDummy = (fn in listOf("Jean", "Sophie", "Lucas", "Thomas", "Marie") && ln in listOf("Dupont", "Martin", "Bernard", "Petit", "Leroy"))
                                            if (!isDummy) {
                                                aggregatedStudents[st.optLong("id")] = st
                                            }
                                        }
                                        val bksArr = snap.optJSONArray("bookings") ?: JSONArray()
                                        for (i in 0 until bksArr.length()) {
                                            val b = bksArr.getJSONObject(i)
                                            aggregatedBookings[b.optLong("id")] = b
                                        }
                                        val prgArr = snap.optJSONArray("progress") ?: JSONArray()
                                        for (i in 0 until prgArr.length()) {
                                            val p = prgArr.getJSONObject(i)
                                            aggregatedProgress[p.optLong("studentId")] = p
                                        }
                                        val delS = snap.optJSONArray("deletedSlotIds") ?: JSONArray()
                                        for (i in 0 until delS.length()) aggregatedDeletedSlots.add(delS.getLong(i))
                                        val delB = snap.optJSONArray("deletedBookingIds") ?: JSONArray()
                                        for (i in 0 until delB.length()) aggregatedDeletedBookings.add(delB.getLong(i))
                                        val delSt = snap.optJSONArray("deletedStudentIds") ?: JSONArray()
                                        for (i in 0 until delSt.length()) aggregatedDeletedStudents.add(delSt.getLong(i))
                                    }
                                }
                            } catch (ignored: Exception) {}
                        }

                        if (aggregatedSlots.isNotEmpty() || aggregatedStudents.isNotEmpty()) {
                            val mergedObj = JSONObject()
                            mergedObj.put("slots", JSONArray(aggregatedSlots.values))
                            mergedObj.put("students", JSONArray(aggregatedStudents.values))
                            mergedObj.put("bookings", JSONArray(aggregatedBookings.values))
                            mergedObj.put("progress", JSONArray(aggregatedProgress.values))
                            mergedObj.put("deletedSlotIds", JSONArray(aggregatedDeletedSlots.toList()))
                            mergedObj.put("deletedBookingIds", JSONArray(aggregatedDeletedBookings.toList()))
                            mergedObj.put("deletedStudentIds", JSONArray(aggregatedDeletedStudents.toList()))
                            remoteSnapshot = mergedObj
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

                val totalSlots = dao.getSlotsCount()
                val totalStudents = dao.getStudentsCount()
                val totalBookings = dao.getAllBookingsList().size

                _syncedSlotsCount.value = totalSlots
                _syncedStudentsCount.value = totalStudents
                _syncedBookingsCount.value = totalBookings

                _syncStatus.value = SyncStatus.CONNECTED_SYNCED
                _statusMessage.value = "En direct (${_schoolCode.value})"
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
        val localProgress = dao.getAllStudentProgressList()

        if (remoteJson == null) {
            // No remote state found; local data is the initial state
            return localSlots.isNotEmpty() || localStudents.isNotEmpty() || localBookings.isNotEmpty()
        }

        val remoteSlotsArray = remoteJson.optJSONArray("slots") ?: JSONArray()
        val remoteStudentsArray = remoteJson.optJSONArray("students") ?: JSONArray()
        val remoteBookingsArray = remoteJson.optJSONArray("bookings") ?: JSONArray()
        val deletedSlotsArray = remoteJson.optJSONArray("deletedSlotIds") ?: JSONArray()
        val deletedBookingsArray = remoteJson.optJSONArray("deletedBookingIds") ?: JSONArray()
        val deletedStudentsArray = remoteJson.optJSONArray("deletedStudentIds") ?: JSONArray()
        val revokedDevicesArray = remoteJson.optJSONArray("revokedDevices") ?: JSONArray()
        val revokedKeysArray = remoteJson.optJSONArray("revokedKeys") ?: JSONArray()

        val remoteRevokedDevices = mutableSetOf<String>()
        for (i in 0 until revokedDevicesArray.length()) {
            remoteRevokedDevices.add(revokedDevicesArray.getString(i))
        }
        val remoteRevokedKeys = mutableSetOf<String>()
        for (i in 0 until revokedKeysArray.length()) {
            remoteRevokedKeys.add(revokedKeysArray.getString(i))
        }

        if (remoteRevokedDevices.isNotEmpty() || remoteRevokedKeys.isNotEmpty()) {
            com.example.util.LicenseManager.mergeRemoteRevocations(context, remoteRevokedDevices, remoteRevokedKeys)
        }

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

        val localSlotsMap = localSlots.associateBy { it.id }
        val localStudentsMap = localStudents.associateBy { it.id }
        val localBookingsMap = localBookings.associateBy { it.id }
        val localProgressMap = localProgress.associateBy { it.studentId }

        // 1. Apply deletions to local Room only if item exists locally
        for (delId in allDeletedSlots) {
            if (localSlotsMap.containsKey(delId)) {
                dao.deleteSlotById(delId)
            }
        }
        for (delId in allDeletedBookings) {
            if (localBookingsMap.containsKey(delId)) {
                dao.deleteBookingById(delId)
            }
        }
        for (delId in allDeletedStudents) {
            if (localStudentsMap.containsKey(delId)) {
                dao.deleteStudentById(delId)
            }
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
                        weatherAlert = s.optString("weatherAlert", ""),
                        cancelReason = s.optString("cancelReason", ""),
                        postponedTo = s.optString("postponedTo", ""),
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

        // 5. Parse and upsert remote student progress
        val remoteProgressArray = remoteJson.optJSONArray("progress") ?: JSONArray()
        val remoteProgressList = mutableListOf<StudentProgressEntity>()
        for (i in 0 until remoteProgressArray.length()) {
            val pr = remoteProgressArray.getJSONObject(i)
            val studentId = pr.optLong("studentId")
            if (studentId !in allDeletedStudents) {
                remoteProgressList.add(
                    StudentProgressEntity(
                        studentId = studentId,
                        totalFlightMinutes = pr.optInt("totalFlightMinutes", 0),
                        totalFlightsCount = pr.optInt("totalFlightsCount", 0),
                        totalGonflageMinutes = pr.optInt("totalGonflageMinutes", 0),
                        autonomyDecollage = pr.optInt("autonomyDecollage", 1),
                        autonomyEnVol = pr.optInt("autonomyEnVol", 1),
                        autonomyAtterrissage = pr.optInt("autonomyAtterrissage", 1),
                        autonomyGonflage = pr.optInt("autonomyGonflage", 1),
                        skillPrevol = pr.optBoolean("skillPrevol", false),
                        skillGonflageFace = pr.optBoolean("skillGonflageFace", false),
                        skillGonflageDos = pr.optBoolean("skillGonflageDos", false),
                        skillMoteurSol = pr.optBoolean("skillMoteurSol", false),
                        skillDecoAutonome = pr.optBoolean("skillDecoAutonome", false),
                        skillViragesAltitude = pr.optBoolean("skillViragesAltitude", false),
                        skillPanneMoteur = pr.optBoolean("skillPanneMoteur", false),
                        skillAtterroPrecision = pr.optBoolean("skillAtterroPrecision", false),
                        skillNavigationAerologie = pr.optBoolean("skillNavigationAerologie", false),
                        skillBrevetPilote = pr.optBoolean("skillBrevetPilote", false),
                        skillEmportPassager = pr.optBoolean("skillEmportPassager", false),
                        instructorNotes = pr.optString("instructorNotes", ""),
                        lastUpdated = pr.optLong("lastUpdated", System.currentTimeMillis())
                    )
                )
            }
        }

        // Only insert/update items that have changed compared to local DB
        val slotsToInsert = remoteSlots.filter { localSlotsMap[it.id] != it }
        val studentsToInsert = remoteStudents.filter { localStudentsMap[it.id] != it }
        val bookingsToInsert = remoteBookings.filter { localBookingsMap[it.id] != it }
        val progressToInsert = remoteProgressList.filter { localProgressMap[it.studentId] != it }

        if (slotsToInsert.isNotEmpty()) dao.insertSlots(slotsToInsert)
        if (studentsToInsert.isNotEmpty()) dao.insertStudents(studentsToInsert)
        if (bookingsToInsert.isNotEmpty()) dao.insertBookings(bookingsToInsert)
        if (progressToInsert.isNotEmpty()) dao.insertAllProgress(progressToInsert)

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
                delay(60)
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
            val progressList = dao.getAllStudentProgressList()
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
                    obj.put("weatherAlert", s.weatherAlert)
                    obj.put("cancelReason", s.cancelReason)
                    obj.put("postponedTo", s.postponedTo)
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

            val progressArray = JSONArray()
            for (pr in progressList) {
                if (pr.studentId !in deletedStudentIds) {
                    val obj = JSONObject()
                    obj.put("studentId", pr.studentId)
                    obj.put("totalFlightMinutes", pr.totalFlightMinutes)
                    obj.put("totalFlightsCount", pr.totalFlightsCount)
                    obj.put("totalGonflageMinutes", pr.totalGonflageMinutes)
                    obj.put("autonomyDecollage", pr.autonomyDecollage)
                    obj.put("autonomyEnVol", pr.autonomyEnVol)
                    obj.put("autonomyAtterrissage", pr.autonomyAtterrissage)
                    obj.put("autonomyGonflage", pr.autonomyGonflage)
                    obj.put("skillPrevol", pr.skillPrevol)
                    obj.put("skillGonflageFace", pr.skillGonflageFace)
                    obj.put("skillGonflageDos", pr.skillGonflageDos)
                    obj.put("skillMoteurSol", pr.skillMoteurSol)
                    obj.put("skillDecoAutonome", pr.skillDecoAutonome)
                    obj.put("skillViragesAltitude", pr.skillViragesAltitude)
                    obj.put("skillPanneMoteur", pr.skillPanneMoteur)
                    obj.put("skillAtterroPrecision", pr.skillAtterroPrecision)
                    obj.put("skillNavigationAerologie", pr.skillNavigationAerologie)
                    obj.put("skillBrevetPilote", pr.skillBrevetPilote)
                    obj.put("skillEmportPassager", pr.skillEmportPassager)
                    obj.put("instructorNotes", pr.instructorNotes)
                    obj.put("lastUpdated", pr.lastUpdated)
                    progressArray.put(obj)
                }
            }

            val deletedSlotsJson = JSONArray()
            deletedSlotIds.forEach { deletedSlotsJson.put(it) }

            val deletedBookingsJson = JSONArray()
            deletedBookingIds.forEach { deletedBookingsJson.put(it) }

            val deletedStudentsJson = JSONArray()
            deletedStudentIds.forEach { deletedStudentsJson.put(it) }

            val revokedDevicesJson = JSONArray()
            com.example.util.LicenseManager.getRevokedDevices(context).forEach { revokedDevicesJson.put(it) }

            val revokedKeysJson = JSONArray()
            com.example.util.LicenseManager.getRevokedKeys(context).forEach { revokedKeysJson.put(it) }

            val payload = JSONObject()
            payload.put("version", 6)
            payload.put("schoolCode", _schoolCode.value)
            payload.put("senderId", myDeviceId)
            payload.put("lastUpdated", System.currentTimeMillis())
            payload.put("slots", slotsArray)
            payload.put("students", studentsArray)
            payload.put("bookings", bookingsArray)
            payload.put("progress", progressArray)
            payload.put("deletedSlotIds", deletedSlotsJson)
            payload.put("deletedBookingIds", deletedBookingsJson)
            payload.put("deletedStudentIds", deletedStudentsJson)
            payload.put("revokedDevices", revokedDevicesJson)
            payload.put("revokedKeys", revokedKeysJson)

            val payloadString = payload.toString()
            val compressed = compressPayload(payloadString)

            val postBody = compressed.toRequestBody("text/plain; charset=utf-8".toMediaType())
            val postReq = Request.Builder()
                .url(getNtfyUrl())
                .addHeader("Title", "ParamoteurSync")
                .addHeader("Priority", "high")
                .addHeader("Tags", "cloud,sync")
                .post(postBody)
                .build()

            val postResp = httpClient.newCall(postReq).execute()
            postResp.close()

            _syncedSlotsCount.value = slots.size
            _syncedStudentsCount.value = students.size
            _syncedBookingsCount.value = bookings.size

            _syncStatus.value = SyncStatus.CONNECTED_SYNCED
            _statusMessage.value = "En direct (${_schoolCode.value})"
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
