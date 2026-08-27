package com.example.data.cloud

import android.content.Context
import android.util.Log
import com.example.data.db.PlanningDao
import com.example.data.model.BookingEntity
import com.example.data.model.LessonSlotEntity
import com.example.data.model.StudentEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SyncStatus {
    OFFLINE_LOCAL_ONLY,
    CONNECTING,
    CONNECTED_SYNCED,
    ERROR
}

class CloudSyncManager(
    private val context: Context,
    private val dao: PlanningDao,
    private val scope: CoroutineScope
) {
    private val TAG = "CloudSyncManager"

    private val _syncStatus = MutableStateFlow(SyncStatus.CONNECTING)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("Initialisation...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var firestore: FirebaseFirestore? = null
    private var slotsListener: ListenerRegistration? = null
    private var bookingsListener: ListenerRegistration? = null
    private var studentsListener: ListenerRegistration? = null

    // Avoid feedback loop during inbound sync
    private var isApplyingRemoteUpdate = false

    init {
        initFirebase()
    }

    private fun initFirebase() {
        try {
            val app = if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            } else {
                FirebaseApp.getInstance()
            }

            if (app != null) {
                val db = FirebaseFirestore.getInstance()
                firestore = db
                _syncStatus.value = SyncStatus.CONNECTED_SYNCED
                _statusMessage.value = "Cloud Sync actif (Temps réel)"
                Log.d(TAG, "Firebase Firestore initialized successfully")
                startRealtimeListeners(db)
            } else {
                _syncStatus.value = SyncStatus.OFFLINE_LOCAL_ONLY
                _statusMessage.value = "Mode local (hors-ligne)"
                Log.w(TAG, "Firebase not configured - falling back to local DB")
            }
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.OFFLINE_LOCAL_ONLY
            _statusMessage.value = "Mode local (Room SQLite)"
            Log.e(TAG, "Firebase init error (running in local mode): ${e.message}")
        }
    }

    private fun startRealtimeListeners(db: FirebaseFirestore) {
        // 1. Listen to Slots
        slotsListener = db.collection("slots").addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.w(TAG, "Slots listener error: ${error.message}")
                _syncStatus.value = SyncStatus.ERROR
                _statusMessage.value = "Erreur sync: ${error.localizedMessage}"
                return@addSnapshotListener
            }

            _syncStatus.value = SyncStatus.CONNECTED_SYNCED
            _statusMessage.value = "Synchronisé en direct"

            snapshots?.let { querySnapshot ->
                scope.launch(Dispatchers.IO) {
                    isApplyingRemoteUpdate = true
                    try {
                        for (change in querySnapshot.documentChanges) {
                            val doc = change.document
                            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                            when (change.type) {
                                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                    val slot = LessonSlotEntity(
                                        id = id,
                                        dateIso = doc.getString("dateIso") ?: "",
                                        startTime = doc.getString("startTime") ?: "",
                                        endTime = doc.getString("endTime") ?: "",
                                        title = doc.getString("title") ?: "",
                                        lessonType = doc.getString("lessonType") ?: "GONFLAGE",
                                        location = doc.getString("location") ?: "",
                                        maxCapacity = doc.getLong("maxCapacity")?.toInt() ?: 4,
                                        notes = doc.getString("notes") ?: "",
                                        isCancelled = doc.getBoolean("isCancelled") ?: false,
                                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                                    )
                                    dao.insertSlot(slot)
                                }
                                DocumentChange.Type.REMOVED -> {
                                    dao.deleteSlotById(id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying remote slot update", e)
                    } finally {
                        isApplyingRemoteUpdate = false
                    }
                }
            }
        }

        // 2. Listen to Students
        studentsListener = db.collection("students").addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.w(TAG, "Students listener error: ${error.message}")
                return@addSnapshotListener
            }

            snapshots?.let { querySnapshot ->
                scope.launch(Dispatchers.IO) {
                    isApplyingRemoteUpdate = true
                    try {
                        for (change in querySnapshot.documentChanges) {
                            val doc = change.document
                            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                            when (change.type) {
                                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                    val student = StudentEntity(
                                        id = id,
                                        firstName = doc.getString("firstName") ?: "",
                                        lastName = doc.getString("lastName") ?: "",
                                        phone = doc.getString("phone") ?: "",
                                        email = doc.getString("email") ?: "",
                                        level = doc.getString("level") ?: "Gonflage",
                                        notes = doc.getString("notes") ?: "",
                                        completedSessions = doc.getLong("completedSessions")?.toInt() ?: 0,
                                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                                    )
                                    dao.insertStudent(student)
                                }
                                DocumentChange.Type.REMOVED -> {
                                    dao.deleteStudentById(id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying remote student update", e)
                    } finally {
                        isApplyingRemoteUpdate = false
                    }
                }
            }
        }

        // 3. Listen to Bookings
        bookingsListener = db.collection("bookings").addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.w(TAG, "Bookings listener error: ${error.message}")
                return@addSnapshotListener
            }

            snapshots?.let { querySnapshot ->
                scope.launch(Dispatchers.IO) {
                    isApplyingRemoteUpdate = true
                    try {
                        for (change in querySnapshot.documentChanges) {
                            val doc = change.document
                            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                            when (change.type) {
                                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                    val booking = BookingEntity(
                                        id = id,
                                        slotId = doc.getLong("slotId") ?: 0L,
                                        studentId = doc.getLong("studentId") ?: 0L,
                                        registeredAt = doc.getLong("registeredAt") ?: System.currentTimeMillis(),
                                        isWaitingList = doc.getBoolean("isWaitingList") ?: false,
                                        attended = doc.getBoolean("attended") ?: false
                                    )
                                    dao.insertBooking(booking)
                                }
                                DocumentChange.Type.REMOVED -> {
                                    dao.deleteBookingById(id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying remote booking update", e)
                    } finally {
                        isApplyingRemoteUpdate = false
                    }
                }
            }
        }
    }

    // Outbound Cloud Sync operations
    fun pushSlot(slot: LessonSlotEntity) {
        if (isApplyingRemoteUpdate) return
        val db = firestore ?: return
        val map = hashMapOf(
            "id" to slot.id,
            "dateIso" to slot.dateIso,
            "startTime" to slot.startTime,
            "endTime" to slot.endTime,
            "title" to slot.title,
            "lessonType" to slot.lessonType,
            "location" to slot.location,
            "maxCapacity" to slot.maxCapacity,
            "notes" to slot.notes,
            "isCancelled" to slot.isCancelled,
            "createdAt" to slot.createdAt
        )
        db.collection("slots").document(slot.id.toString()).set(map)
            .addOnFailureListener { e -> Log.w(TAG, "Failed to push slot: ${e.message}") }
    }

    fun deleteSlot(slotId: Long) {
        val db = firestore ?: return
        db.collection("slots").document(slotId.toString()).delete()
            .addOnFailureListener { e -> Log.w(TAG, "Failed to delete remote slot: ${e.message}") }
    }

    fun pushStudent(student: StudentEntity) {
        if (isApplyingRemoteUpdate) return
        val db = firestore ?: return
        val map = hashMapOf(
            "id" to student.id,
            "firstName" to student.firstName,
            "lastName" to student.lastName,
            "phone" to student.phone,
            "email" to student.email,
            "level" to student.level,
            "notes" to student.notes,
            "completedSessions" to student.completedSessions,
            "createdAt" to student.createdAt
        )
        db.collection("students").document(student.id.toString()).set(map)
            .addOnFailureListener { e -> Log.w(TAG, "Failed to push student: ${e.message}") }
    }

    fun deleteStudent(studentId: Long) {
        val db = firestore ?: return
        db.collection("students").document(studentId.toString()).delete()
            .addOnFailureListener { e -> Log.w(TAG, "Failed to delete remote student: ${e.message}") }
    }

    fun pushBooking(booking: BookingEntity) {
        if (isApplyingRemoteUpdate) return
        val db = firestore ?: return
        val map = hashMapOf(
            "id" to booking.id,
            "slotId" to booking.slotId,
            "studentId" to booking.studentId,
            "registeredAt" to booking.registeredAt,
            "isWaitingList" to booking.isWaitingList,
            "attended" to booking.attended
        )
        db.collection("bookings").document(booking.id.toString()).set(map)
            .addOnFailureListener { e -> Log.w(TAG, "Failed to push booking: ${e.message}") }
    }

    fun deleteBooking(bookingId: Long) {
        val db = firestore ?: return
        db.collection("bookings").document(bookingId.toString()).delete()
            .addOnFailureListener { e -> Log.w(TAG, "Failed to delete remote booking: ${e.message}") }
    }

    fun cleanup() {
        slotsListener?.remove()
        studentsListener?.remove()
        bookingsListener?.remove()
    }
}
