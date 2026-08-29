package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.CloudSyncManager
import com.example.data.cloud.SyncStatus
import com.example.data.db.AppDatabase
import com.example.data.db.PlanningRepository
import com.example.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class PlanningViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PlanningRepository
    val cloudSyncManager: CloudSyncManager

    // Cloud Sync status
    val syncStatus: StateFlow<SyncStatus>
    val syncStatusMessage: StateFlow<String>
    val lastSyncTime: StateFlow<String>

    // Base data flows
    val allStudents: StateFlow<List<StudentEntity>>
    val slotsWithBookings: StateFlow<List<SlotWithBookings>>
    val studentsWithStats: StateFlow<List<StudentWithStats>>
    val allProgress: StateFlow<List<StudentProgressEntity>>

    // App Mode: true = Version Élève (par défaut pour les nouveaux utilisateurs), false = Mode Moniteur
    private val prefs = application.getSharedPreferences("paramoteur_planning_prefs", android.content.Context.MODE_PRIVATE)

    private val _isStudentMode = MutableStateFlow(prefs.getBoolean("is_student_mode", true))
    val isStudentMode: StateFlow<Boolean> = _isStudentMode.asStateFlow()

    // Instructor PIN (defaults to "1234")
    private val _instructorPin = MutableStateFlow(prefs.getString("instructor_pin", "1234") ?: "1234")
    val instructorPin: StateFlow<String> = _instructorPin.asStateFlow()

    // Saved Default Standard Day Config
    private val _savedStandardDayConfig = MutableStateFlow(
        StandardDayConfig(
            sunriseHour = prefs.getInt("std_sun_rise_h", 6),
            sunriseMinute = prefs.getInt("std_sun_rise_m", 30),
            sunsetHour = prefs.getInt("std_sun_set_h", 21),
            sunsetMinute = prefs.getInt("std_sun_set_m", 0),
            morningVolCapacity = prefs.getInt("std_morn_vol", 2),
            morningGonflageCapacity = prefs.getInt("std_morn_gonf", 4),
            eveningGonflageCapacity = prefs.getInt("std_eve_gonf", 4),
            eveningVolCapacity = prefs.getInt("std_eve_vol", 2),
            location = prefs.getString("std_location", "Terrain de décollage") ?: "Terrain de décollage"
        )
    )
    val savedStandardDayConfig: StateFlow<StandardDayConfig> = _savedStandardDayConfig.asStateFlow()

    // Saved Student Identity for 1-click registration
    data class StudentProfile(
        val firstName: String = "",
        val lastName: String = "",
        val phone: String = "",
        val level: String = "Gonflage"
    ) {
        val isConfigured: Boolean get() = firstName.isNotBlank() && phone.isNotBlank()
        val fullName: String get() = "$firstName $lastName".trim()
    }

    private val _savedProfile = MutableStateFlow(
        StudentProfile(
            firstName = prefs.getString("student_first_name", "") ?: "",
            lastName = prefs.getString("student_last_name", "") ?: "",
            phone = prefs.getString("student_phone", "") ?: "",
            level = prefs.getString("student_level", "Gonflage") ?: "Gonflage"
        )
    )
    val savedProfile: StateFlow<StudentProfile> = _savedProfile.asStateFlow()

    // Filters for list view
    private val _selectedDateFilter = MutableStateFlow<String?>("TOUS") // "TOUS", "TODAY", "TOMORROW", "WEEK", or "YYYY-MM-DD"
    val selectedDateFilter: StateFlow<String?> = _selectedDateFilter.asStateFlow()

    private val _filterOnlyAvailable = MutableStateFlow(false)
    val filterOnlyAvailable: StateFlow<Boolean> = _filterOnlyAvailable.asStateFlow()

    private val _filterLessonType = MutableStateFlow<String?>(null)
    val filterLessonType: StateFlow<String?> = _filterLessonType.asStateFlow()

    private val _studentSearchQuery = MutableStateFlow("")
    val studentSearchQuery: StateFlow<String> = _studentSearchQuery.asStateFlow()

    private val _studentLevelFilter = MutableStateFlow<String?>(null)
    val studentLevelFilter: StateFlow<String?> = _studentLevelFilter.asStateFlow()

    // Toast / Feedback message
    private val _feedbackMessage = MutableSharedFlow<String>()
    val feedbackMessage: SharedFlow<String> = _feedbackMessage.asSharedFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        cloudSyncManager = CloudSyncManager(application, db.planningDao(), viewModelScope)
        repository = PlanningRepository(db.planningDao(), cloudSyncManager)

        syncStatus = cloudSyncManager.syncStatus
        syncStatusMessage = cloudSyncManager.statusMessage
        lastSyncTime = cloudSyncManager.lastSyncTime

        allStudents = repository.allStudents
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        slotsWithBookings = repository.slotsWithBookings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        studentsWithStats = repository.studentsWithStats
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allProgress = repository.allProgress
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun toggleAppMode() {
        val newMode = !_isStudentMode.value
        _isStudentMode.value = newMode
        prefs.edit().putBoolean("is_student_mode", newMode).apply()
    }

    fun setAppMode(studentMode: Boolean) {
        _isStudentMode.value = studentMode
        prefs.edit().putBoolean("is_student_mode", studentMode).apply()
    }

    fun setInstructorPin(newPin: String) {
        val pin = newPin.trim()
        if (pin.length >= 4) {
            _instructorPin.value = pin
            prefs.edit().putString("instructor_pin", pin).apply()
            viewModelScope.launch {
                _feedbackMessage.emit("Code PIN Moniteur mis à jour ($pin)")
            }
        }
    }

    fun verifyInstructorPin(enteredPin: String): Boolean {
        return enteredPin.trim() == _instructorPin.value.trim()
    }

    fun forceSync() {
        viewModelScope.launch {
            cloudSyncManager.forceSyncNow()
            _feedbackMessage.emit("🔄 Synchronisation Cloud effectuée")
        }
    }

    fun saveDefaultStandardDayConfig(config: StandardDayConfig) {
        _savedStandardDayConfig.value = config
        prefs.edit()
            .putInt("std_sun_rise_h", config.sunriseHour)
            .putInt("std_sun_rise_m", config.sunriseMinute)
            .putInt("std_sun_set_h", config.sunsetHour)
            .putInt("std_sun_set_m", config.sunsetMinute)
            .putInt("std_morn_vol", config.morningVolCapacity)
            .putInt("std_morn_gonf", config.morningGonflageCapacity)
            .putInt("std_eve_gonf", config.eveningGonflageCapacity)
            .putInt("std_eve_vol", config.eveningVolCapacity)
            .putString("std_location", config.location)
            .apply()
    }

    fun saveStudentProfile(firstName: String, lastName: String, phone: String, level: String) {
        val profile = StudentProfile(firstName.trim(), lastName.trim(), phone.trim(), level)
        _savedProfile.value = profile
        prefs.edit()
            .putString("student_first_name", profile.firstName)
            .putString("student_last_name", profile.lastName)
            .putString("student_phone", profile.phone)
            .putString("student_level", profile.level)
            .apply()

        viewModelScope.launch {
            if (profile.firstName.isNotBlank() || profile.phone.isNotBlank()) {
                repository.saveOrUpdateStudentProfile(profile.firstName, profile.lastName, profile.phone, profile.level)
                _feedbackMessage.emit("Profil synchronisé avec l'école")
            }
        }
    }

    fun setDateFilter(filter: String?) {
        _selectedDateFilter.value = filter
    }

    fun toggleFilterOnlyAvailable() {
        _filterOnlyAvailable.value = !_filterOnlyAvailable.value
    }

    fun setLessonTypeFilter(typeCode: String?) {
        _filterLessonType.value = typeCode
    }

    fun setStudentSearchQuery(query: String) {
        _studentSearchQuery.value = query
    }

    fun setStudentLevelFilter(level: String?) {
        _studentLevelFilter.value = level
    }

    private data class SlotFilters(
        val dateFilter: String?,
        val onlyAvailable: Boolean,
        val typeFilter: String?
    )

    private val _slotFilters: Flow<SlotFilters> = combine(
        _selectedDateFilter,
        _filterOnlyAvailable,
        _filterLessonType
    ) { dateFilter, onlyAvailable, typeFilter ->
        SlotFilters(dateFilter, onlyAvailable, typeFilter)
    }

    // Filtered slots calculation
    val filteredSlots: StateFlow<List<SlotWithBookings>> = combine(
        slotsWithBookings,
        _slotFilters
    ) { slots, filters ->
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowIso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 6)
        val endOfWeekIso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(cal.time)

        slots.filter { item ->
            // Date filter
            val dateMatches = when (filters.dateFilter) {
                null, "TOUS" -> true
                "TODAY" -> item.slot.dateIso == todayIso
                "TOMORROW" -> item.slot.dateIso == tomorrowIso
                "WEEK" -> item.slot.dateIso in todayIso..endOfWeekIso
                else -> item.slot.dateIso == filters.dateFilter
            }

            // Availability filter
            val availableMatches = if (filters.onlyAvailable) !item.isFull && !item.slot.isCancelled else true

            // Type filter (GONFLAGE, VOL, PERF)
            val typeMatches = if (filters.typeFilter != null) item.slot.lessonType.equals(filters.typeFilter, ignoreCase = true) else true

            dateMatches && availableMatches && typeMatches
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered students list
    val filteredStudents: StateFlow<List<StudentEntity>> = combine(
        allStudents,
        _studentSearchQuery,
        _studentLevelFilter
    ) { students, query, level ->
        students.filter { student ->
            val matchesQuery = query.isBlank() ||
                    student.fullName.contains(query, ignoreCase = true) ||
                    student.phone.contains(query)

            val matchesLevel = level == null || student.level.contains(level, ignoreCase = true)

            matchesQuery && matchesLevel
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered students with detailed stats
    val filteredStudentsWithStats: StateFlow<List<StudentWithStats>> = combine(
        studentsWithStats,
        _studentSearchQuery,
        _studentLevelFilter
    ) { studentsStats, query, level ->
        studentsStats.filter { item ->
            val student = item.student
            val matchesQuery = query.isBlank() ||
                    student.fullName.contains(query, ignoreCase = true) ||
                    student.phone.contains(query)

            val matchesLevel = level == null || student.level.contains(level, ignoreCase = true)

            matchesQuery && matchesLevel
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Standard Day ("Journée Type") Generation ---
    fun createStandardDay(
        dateIso: String,
        config: StandardDayConfig = StandardDayConfig()
    ) {
        viewModelScope.launch {
            val ids = repository.createStandardDaySlots(dateIso, config)
            _feedbackMessage.emit("Journée Type créée (${ids.size} créneaux Vol & Gonflage) !")
        }
    }

    // --- Student Self-Registration (Version Élève) ---
    fun registerStudentSelf(
        slotId: Long,
        firstName: String,
        lastName: String,
        phone: String,
        email: String = "",
        level: String = "Gonflage",
        onComplete: ((student: StudentEntity, slot: LessonSlotEntity) -> Unit)? = null
    ) {
        viewModelScope.launch {
            // Save profile locally for future 1-click use
            saveStudentProfile(firstName, lastName, phone, level)

            val (student, isSuccess) = repository.registerStudentSelf(
                slotId = slotId,
                firstName = firstName,
                lastName = lastName,
                phone = phone,
                email = email,
                level = level
            )

            // Find slot details
            val slot = slotsWithBookings.value.find { it.slot.id == slotId }?.slot
                ?: LessonSlotEntity(id = slotId, dateIso = "", startTime = "", endTime = "", title = "Créneau", lessonType = "GONFLAGE")

            if (isSuccess) {
                _feedbackMessage.emit("✅ Inscription validée ! 1 place réservée pour ${student.fullName}.")
            } else {
                _feedbackMessage.emit("ℹ️ Vous êtes déjà inscrit à ce créneau.")
            }
            onComplete?.invoke(student, slot)
        }
    }

    // --- Slot Operations ---
    fun createSlot(
        dateIso: String,
        startTime: String,
        endTime: String,
        title: String,
        lessonType: String,
        location: String,
        maxCapacity: Int,
        notes: String
    ) {
        viewModelScope.launch {
            val slot = LessonSlotEntity(
                dateIso = dateIso,
                startTime = startTime,
                endTime = endTime,
                title = title.ifBlank { "Créneau ${PlanningLessonType.fromCode(lessonType).label}" },
                lessonType = lessonType,
                location = location.ifBlank { "Terrain de décollage" },
                maxCapacity = maxCapacity.coerceAtLeast(1),
                notes = notes
            )
            repository.createSlot(slot)
            _feedbackMessage.emit("Nouveau créneau créé avec succès !")
        }
    }

    fun updateSlot(slot: LessonSlotEntity) {
        viewModelScope.launch {
            repository.updateSlot(slot)
            _feedbackMessage.emit("Créneau mis à jour !")
        }
    }

    fun deleteSlot(slotId: Long) {
        viewModelScope.launch {
            repository.deleteSlot(slotId)
            _feedbackMessage.emit("Créneau supprimé")
        }
    }

    // --- Booking Operations ---
    fun enrollStudent(slotId: Long, studentId: Long, isWaitingList: Boolean = false) {
        viewModelScope.launch {
            val success = repository.enrollStudent(slotId, studentId, isWaitingList)
            if (success) {
                _feedbackMessage.emit(if (isWaitingList) "Ajouté en liste d'attente !" else "Participant inscrit !")
            } else {
                _feedbackMessage.emit("Ce participant est déjà inscrit.")
            }
        }
    }

    fun unenrollStudent(slotId: Long, studentId: Long) {
        viewModelScope.launch {
            repository.unenrollStudent(slotId, studentId)
            _feedbackMessage.emit("Inscription annulée.")
        }
    }

    fun toggleAttendance(bookingId: Long, studentId: Long, currentAttended: Boolean) {
        viewModelScope.launch {
            repository.toggleAttendance(bookingId, studentId, !currentAttended)
            if (!currentAttended) {
                _feedbackMessage.emit("Présence validée !")
            }
        }
    }

    // --- Student Operations ---
    fun saveStudent(
        id: Long = 0,
        firstName: String,
        lastName: String,
        phone: String,
        email: String,
        level: String,
        notes: String
    ) {
        viewModelScope.launch {
            val student = StudentEntity(
                id = id,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                phone = phone.trim(),
                email = email.trim(),
                level = level,
                notes = notes.trim()
            )
            if (id == 0L) {
                repository.createStudent(student)
                _feedbackMessage.emit("Nouveau participant ${student.fullName} enregistré !")
            } else {
                repository.updateStudent(student)
                _feedbackMessage.emit("Profil ${student.fullName} mis à jour !")
            }
        }
    }

    fun deleteStudent(student: StudentEntity) {
        viewModelScope.launch {
            repository.deleteStudent(student)
            _feedbackMessage.emit("Participant supprimé.")
        }
    }

    fun getWhatsAppText(): String {
        return repository.generateWhatsAppPlanningText(slotsWithBookings.value)
    }

    // --- Weather Alerts & Slot Cancellations ---
    fun updateSlotWeather(
        slot: LessonSlotEntity,
        enrolledStudents: List<StudentEntity>,
        isCancelled: Boolean,
        weatherAlert: String,
        cancelReason: String,
        postponedTo: String,
        broadcastNotification: Boolean = true
    ) {
        viewModelScope.launch {
            repository.updateSlotWeatherAlert(
                slotId = slot.id,
                isCancelled = isCancelled,
                weatherAlert = weatherAlert,
                cancelReason = cancelReason,
                postponedTo = postponedTo
            )

            if (broadcastNotification && (isCancelled || weatherAlert.isNotBlank())) {
                val reason = if (cancelReason.isNotBlank()) cancelReason else weatherAlert
                com.example.util.WeatherNotificationHelper.showWeatherAlertNotification(
                    context = getApplication(),
                    slot = slot.copy(
                        isCancelled = isCancelled,
                        weatherAlert = weatherAlert,
                        cancelReason = cancelReason,
                        postponedTo = postponedTo
                    ),
                    isCancellation = isCancelled,
                    reason = reason
                )
            }

            val msg = if (isCancelled) "🚫 Séance annulée pour cause météo" else if (weatherAlert.isNotBlank()) "⚠️ Alerte météo enregistrée" else "Créneau rétabli"
            _feedbackMessage.emit(msg)
        }
    }

    fun generateWeatherAlertWhatsApp(slot: LessonSlotEntity, enrolledStudents: List<StudentEntity>): String {
        return repository.generateWeatherAlertWhatsAppText(slot, enrolledStudents)
    }

    // --- Student Progress Operations ---
    fun saveStudentProgress(progress: StudentProgressEntity) {
        viewModelScope.launch {
            repository.saveStudentProgress(progress)
            _feedbackMessage.emit("Progression et livret FFPLUM mis à jour !")
        }
    }

    fun getStudentProgressFor(studentId: Long): StudentProgressEntity {
        return allProgress.value.find { it.studentId == studentId } ?: StudentProgressEntity(studentId = studentId)
    }

    // --- Calendar & PDF Exports ---
    fun addSlotToCalendar(slot: LessonSlotEntity) {
        com.example.util.CalendarExportUtils.addSlotToGoogleCalendar(getApplication(), slot)
    }

    fun exportPlanningIcs() {
        com.example.util.CalendarExportUtils.exportSlotsToIcs(
            context = getApplication(),
            slotsWithBookings = slotsWithBookings.value,
            exportTitle = "Planning_Paramoteur"
        )
    }

    fun exportPlanningPdf() {
        com.example.util.PdfExportUtils.exportPlanningPdf(
            context = getApplication(),
            slotsWithBookings = slotsWithBookings.value,
            periodTitle = "Planning École Paramoteur"
        )
    }

    fun exportStudentBookletPdf(student: StudentEntity) {
        val progress = getStudentProgressFor(student.id)
        com.example.util.PdfExportUtils.exportStudentBookletPdf(
            context = getApplication(),
            student = student,
            progress = progress
        )
    }

    fun exportStudentBookletIcs(student: StudentEntity) {
        val studentSlots = slotsWithBookings.value.filter { it.enrolledStudentIds.contains(student.id) }
        com.example.util.CalendarExportUtils.exportSlotsToIcs(
            context = getApplication(),
            slotsWithBookings = studentSlots,
            exportTitle = "Seances_${student.firstName}_${student.lastName}"
        )
    }
}
