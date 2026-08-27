package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.PlanningRepository
import com.example.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class PlanningViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PlanningRepository

    // Base data flows
    val allStudents: StateFlow<List<StudentEntity>>
    val slotsWithBookings: StateFlow<List<SlotWithBookings>>
    val studentsWithStats: StateFlow<List<StudentWithStats>>

    // App Mode: false = Mode Moniteur (Instructeur), true = Version Élève
    private val prefs = application.getSharedPreferences("paramoteur_planning_prefs", android.content.Context.MODE_PRIVATE)

    private val _isStudentMode = MutableStateFlow(prefs.getBoolean("is_student_mode", false))
    val isStudentMode: StateFlow<Boolean> = _isStudentMode.asStateFlow()

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
        repository = PlanningRepository(db.planningDao())

        allStudents = repository.allStudents
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        slotsWithBookings = repository.slotsWithBookings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        studentsWithStats = repository.studentsWithStats
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        viewModelScope.launch {
            repository.initializeSampleDataIfNeeded()
        }
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

    fun saveStudentProfile(firstName: String, lastName: String, phone: String, level: String) {
        val profile = StudentProfile(firstName.trim(), lastName.trim(), phone.trim(), level)
        _savedProfile.value = profile
        prefs.edit()
            .putString("student_first_name", profile.firstName)
            .putString("student_last_name", profile.lastName)
            .putString("student_phone", profile.phone)
            .putString("student_level", profile.level)
            .apply()
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
        onComplete: (student: StudentEntity, slot: LessonSlotEntity, shareText: String) -> Unit
    ) {
        viewModelScope.launch {
            // Save profile locally for future 1-click use
            saveStudentProfile(firstName, lastName, phone, level)

            val (student, _) = repository.registerStudentSelf(
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

            val shareText = repository.generateStudentRegistrationWhatsAppText(student, slot, isWaitingList = false)
            _feedbackMessage.emit("Inscription enregistrée ! Ouverture de WhatsApp...")
            onComplete(student, slot, shareText)
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
}
