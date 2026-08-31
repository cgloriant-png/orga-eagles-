package com.example.data.db

import com.example.data.cloud.CloudSyncManager
import com.example.data.model.*
import com.example.util.SunCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.*

class PlanningRepository(
    private val planningDao: PlanningDao,
    var cloudSyncManager: CloudSyncManager? = null
) {

    val allStudents: Flow<List<StudentEntity>> = planningDao.getAllStudents()
    val allSlots: Flow<List<LessonSlotEntity>> = planningDao.getAllSlots()
    val allBookings: Flow<List<BookingEntity>> = planningDao.getAllBookings()
    val allProgress: Flow<List<StudentProgressEntity>> = planningDao.getAllStudentProgress()

    // Combined Flow: Slots with enrolled students
    val slotsWithBookings: Flow<List<SlotWithBookings>> = combine(
        allSlots,
        allBookings,
        allStudents
    ) { slots, bookings, students ->
        val studentMap = students.associateBy { it.id }
        val bookingBySlotId = bookings.groupBy { it.slotId }

        slots.map { slot ->
            val slotBookings = bookingBySlotId[slot.id].orEmpty().map { booking ->
                val student = studentMap[booking.studentId] ?: StudentEntity(
                    id = booking.studentId,
                    firstName = "Élève",
                    lastName = "",
                    phone = "",
                    email = "",
                    level = "Gonflage"
                )
                BookingWithStudent(booking, student)
            }
            SlotWithBookings(slot, slotBookings)
        }
    }

    // Combined Flow: Students with detailed stats & booking history
    val studentsWithStats: Flow<List<StudentWithStats>> = combine(
        allStudents,
        allBookings,
        allSlots
    ) { students, bookings, slots ->
        val slotMap = slots.associateBy { it.id }
        val bookingByStudent = bookings.groupBy { it.studentId }

        students.map { student ->
            val sBookings = bookingByStudent[student.id].orEmpty()
            val total = sBookings.size
            val attended = sBookings.count { it.attended }
            val waiting = sBookings.count { it.isWaitingList }
            val history = sBookings.mapNotNull { b ->
                slotMap[b.slotId]?.let { s -> BookingWithSlotInfo(b, s) }
            }.sortedByDescending { it.slot.dateIso + " " + it.slot.startTime }
            val upcoming = history.count { !it.booking.attended && !it.slot.isCancelled }

            StudentWithStats(
                student = student,
                totalBookings = total,
                attendedBookings = attended,
                upcomingBookings = upcoming,
                waitingListBookings = waiting,
                bookingHistory = history
            )
        }
    }

    // --- Student Progress & Livret FFPLUM ---
    suspend fun getStudentProgress(studentId: Long): StudentProgressEntity {
        return planningDao.getStudentProgressSync(studentId) ?: StudentProgressEntity(studentId = studentId)
    }

    suspend fun saveStudentProgress(progress: StudentProgressEntity) {
        planningDao.insertOrUpdateProgress(progress.copy(lastUpdated = System.currentTimeMillis()))
        cloudSyncManager?.pushFullSync(immediate = true)
    }

    suspend fun updateSlotWeatherAlert(
        slotId: Long,
        isCancelled: Boolean,
        weatherAlert: String,
        cancelReason: String = "",
        postponedTo: String = ""
    ) {
        val slot = planningDao.getSlotById(slotId) ?: return
        val updated = slot.copy(
            isCancelled = isCancelled,
            weatherAlert = weatherAlert,
            cancelReason = cancelReason,
            postponedTo = postponedTo
        )
        planningDao.updateSlot(updated)
        cloudSyncManager?.pushSlot(updated)
    }

    private fun generateUniqueId(): Long {
        val ts = System.currentTimeMillis() and 0x1FFFFFFFFFFL
        val rand = (1000..9999).random().toLong()
        return ts * 10000L + rand
    }

    // --- Slot Actions ---
    suspend fun createSlot(slot: LessonSlotEntity): Long {
        val slotWithId = if (slot.id <= 0L) slot.copy(id = generateUniqueId()) else slot
        planningDao.insertSlot(slotWithId)
        cloudSyncManager?.pushSlot(slotWithId)
        return slotWithId.id
    }

    suspend fun updateSlot(slot: LessonSlotEntity) {
        planningDao.updateSlot(slot)
        cloudSyncManager?.pushSlot(slot)
    }

    suspend fun deleteSlot(slotId: Long) {
        val bookings = planningDao.getBookingsForSlotSync(slotId)
        for (b in bookings) {
            planningDao.deleteBookingById(b.id)
            cloudSyncManager?.deleteBooking(b.id)
        }
        planningDao.deleteSlotById(slotId)
        cloudSyncManager?.deleteSlot(slotId)
    }

    // --- Student Actions ---
    suspend fun createStudent(student: StudentEntity): Long {
        val studentWithId = if (student.id <= 0L) student.copy(id = generateUniqueId()) else student
        planningDao.insertStudent(studentWithId)
        cloudSyncManager?.pushStudent(studentWithId)
        return studentWithId.id
    }

    suspend fun updateStudent(student: StudentEntity) {
        planningDao.updateStudent(student)
        cloudSyncManager?.pushStudent(student)
    }

    suspend fun deleteStudent(student: StudentEntity) {
        val bookings = planningDao.getAllBookingsList().filter { it.studentId == student.id }
        for (b in bookings) {
            planningDao.deleteBookingById(b.id)
            cloudSyncManager?.deleteBooking(b.id)
        }
        planningDao.deleteStudent(student)
        cloudSyncManager?.deleteStudent(student.id)
    }

    // --- Booking Actions ---
    suspend fun enrollStudent(slotId: Long, studentId: Long, isWaitingList: Boolean = false): Boolean {
        try {
            val existing = planningDao.getBookingsForSlotSync(slotId).find { it.studentId == studentId }
            if (existing != null) {
                return false
            }
            val booking = BookingEntity(
                id = generateUniqueId(),
                slotId = slotId,
                studentId = studentId,
                isWaitingList = isWaitingList
            )
            planningDao.insertBooking(booking)
            cloudSyncManager?.pushBooking(booking)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    suspend fun unenrollStudent(slotId: Long, studentId: Long) {
        // Find existing booking to delete from cloud
        val bookings = planningDao.getBookingsForSlotSync(slotId)
        bookings.find { it.studentId == studentId }?.let { b ->
            cloudSyncManager?.deleteBooking(b.id)
        }
        planningDao.deleteBookingBySlotAndStudent(slotId, studentId)
        cloudSyncManager?.pushFullSync(immediate = true)
    }

    fun normalizePhoneNumber(phone: String): String {
        return phone.replace("[^0-9+]".toRegex(), "")
            .let { if (it.startsWith("+33")) "0" + it.substring(3) else it }
    }

    suspend fun getOrCreateStudentProfile(
        firstName: String,
        lastName: String,
        phone: String,
        email: String = "",
        level: String = "Gonflage"
    ): StudentEntity {
        val cleanFirst = firstName.trim()
        val cleanLast = lastName.trim()
        val cleanPhone = phone.trim()
        val normPhone = normalizePhoneNumber(cleanPhone)

        val allStudents = planningDao.getAllStudentsList()

        var student = allStudents.find {
            normPhone.isNotBlank() && normalizePhoneNumber(it.phone) == normPhone
        }

        if (student == null && cleanFirst.isNotBlank() && cleanLast.isNotBlank()) {
            student = allStudents.find {
                it.firstName.equals(cleanFirst, ignoreCase = true) &&
                it.lastName.equals(cleanLast, ignoreCase = true)
            }
        }

        if (student == null && cleanFirst.isNotBlank() && normPhone.isNotBlank()) {
            student = allStudents.find {
                it.firstName.equals(cleanFirst, ignoreCase = true) &&
                normalizePhoneNumber(it.phone) == normPhone
            }
        }

        if (student == null) {
            val newStudent = StudentEntity(
                id = generateUniqueId(),
                firstName = cleanFirst.ifBlank { "Élève" },
                lastName = cleanLast,
                phone = cleanPhone,
                email = email.trim(),
                level = level.ifBlank { "Gonflage" },
                notes = "Inscrit via Espace Élève"
            )
            planningDao.insertStudent(newStudent)
            cloudSyncManager?.pushStudent(newStudent)
            return newStudent
        } else {
            val updated = student.copy(
                firstName = cleanFirst.ifBlank { student.firstName },
                lastName = cleanLast.ifBlank { student.lastName },
                phone = cleanPhone.ifBlank { student.phone },
                email = email.trim().ifBlank { student.email },
                level = level.ifBlank { student.level }
            )
            if (updated != student) {
                planningDao.updateStudent(updated)
                cloudSyncManager?.pushStudent(updated)
            }
            return updated
        }
    }

    suspend fun saveOrUpdateStudentProfile(
        firstName: String,
        lastName: String,
        phone: String,
        level: String
    ): StudentEntity {
        val student = getOrCreateStudentProfile(firstName, lastName, phone, "", level)
        cloudSyncManager?.pushFullSync(immediate = true)
        return student
    }

    suspend fun toggleAttendance(bookingId: Long, studentId: Long, attended: Boolean) {
        planningDao.updateAttendance(bookingId, attended)
        if (attended) {
            planningDao.incrementCompletedSessions(studentId)
        }
        planningDao.getBookingById(bookingId)?.let { b ->
            cloudSyncManager?.pushBooking(b.copy(attended = attended))
        }
    }

    // --- Standard Day Generation ("Journée Type") ---
    suspend fun createStandardDaySlots(
        dateIso: String,
        config: StandardDayConfig = StandardDayConfig()
    ): List<Long> {
        // Calculate exact astronomical solar times for this specific date (Plouharnel 56)
        val sunTimes = SunCalculator.calculateSunTimes(dateIso)
        val sunriseH = if (config.useAstronomicalSunTimes) sunTimes.sunriseHour else config.sunriseHour
        val sunriseM = if (config.useAstronomicalSunTimes) sunTimes.sunriseMinute else config.sunriseMinute
        val sunsetH = if (config.useAstronomicalSunTimes) sunTimes.sunsetHour else config.sunsetHour
        val sunsetM = if (config.useAstronomicalSunTimes) sunTimes.sunsetMinute else config.sunsetMinute

        val sunriseStart = String.format(Locale.US, "%02d:%02d", sunriseH, sunriseM)
        val sunrisePlus1 = String.format(Locale.US, "%02d:%02d", (sunriseH + 1).coerceAtMost(23), sunriseM)
        val sunrisePlus2 = String.format(Locale.US, "%02d:%02d", (sunriseH + 2).coerceAtMost(23), sunriseM)
        val sunrisePlus3 = String.format(Locale.US, "%02d:%02d", (sunriseH + 3).coerceAtMost(23), sunriseM)

        val sunsetMinus3 = String.format(Locale.US, "%02d:%02d", (sunsetH - 3).coerceAtLeast(0), sunsetM)
        val sunsetMinus2 = String.format(Locale.US, "%02d:%02d", (sunsetH - 2).coerceAtLeast(0), sunsetM)
        val sunsetMinus1 = String.format(Locale.US, "%02d:%02d", (sunsetH - 1).coerceAtLeast(0), sunsetM)
        val sunsetEnd = String.format(Locale.US, "%02d:%02d", sunsetH.coerceAtMost(23), sunsetM)

        val location = if (config.location == "Terrain de décollage") "Plouharnel (56)" else config.location

        val slotsToCreate = listOf(
            LessonSlotEntity(
                dateIso = dateIso,
                startTime = sunriseStart,
                endTime = sunrisePlus2,
                title = "Matin Vol (Lever -> +2h)",
                lessonType = "VOL",
                location = location,
                maxCapacity = config.morningVolCapacity,
                notes = "Aérologie calme du lever du soleil ($sunriseStart à $sunrisePlus2)"
            ),
            LessonSlotEntity(
                dateIso = dateIso,
                startTime = sunrisePlus1,
                endTime = sunrisePlus3,
                title = "Matin Gonflage (+1h à +3h)",
                lessonType = "GONFLAGE",
                location = location,
                maxCapacity = config.morningGonflageCapacity,
                notes = "Brise matinale de 1h à 3h après le lever ($sunrisePlus1 à $sunrisePlus3)"
            ),
            LessonSlotEntity(
                dateIso = dateIso,
                startTime = sunsetMinus3,
                endTime = sunsetMinus1,
                title = "Soir Gonflage (-3h à -1h)",
                lessonType = "GONFLAGE",
                location = location,
                maxCapacity = config.eveningGonflageCapacity,
                notes = "Gonflage fin d'après-midi de 3h à 1h avant le coucher ($sunsetMinus3 à $sunsetMinus1)"
            ),
            LessonSlotEntity(
                dateIso = dateIso,
                startTime = sunsetMinus2,
                endTime = sunsetEnd,
                title = "Soir Vol (Coucher du soleil)",
                lessonType = "VOL",
                location = location,
                maxCapacity = config.eveningVolCapacity,
                notes = "Restitution & vol calme de 2h avant jusqu'au coucher ($sunsetMinus2 à $sunsetEnd)"
            )
        )

        val createdSlots = slotsToCreate.map { it.copy(id = generateUniqueId()) }
        planningDao.insertSlots(createdSlots)
        cloudSyncManager?.pushFullSync(immediate = true)
        return createdSlots.map { it.id }
    }

    // --- Student Self-Registration (used in Version Élève) ---
    suspend fun registerStudentSelf(
        slotId: Long,
        firstName: String,
        lastName: String,
        phone: String,
        email: String = "",
        level: String = "Gonflage"
    ): Pair<StudentEntity, Boolean> {
        val student = getOrCreateStudentProfile(firstName, lastName, phone, email, level)

        // Enroll into slot
        val slot = planningDao.getSlotById(slotId)
        val bookings = planningDao.getBookingsForSlotSync(slotId)
        val isSlotFull = bookings.filter { !it.isWaitingList }.size >= (slot?.maxCapacity ?: 4)

        val enrolled = enrollStudent(slotId, student.id, isWaitingList = isSlotFull)
        cloudSyncManager?.pushFullSync(immediate = true)
        return Pair(student, enrolled)
    }

    // Pre-formatted message for student sending their registration to instructor
    fun generateStudentRegistrationWhatsAppText(
        student: StudentEntity,
        slot: LessonSlotEntity,
        isWaitingList: Boolean = false
    ): String {
        val type = PlanningLessonType.fromCode(slot.lessonType)
        val dateIn = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
        val dateOut = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE)
        val formattedDate = try {
            dateIn.parse(slot.dateIso)?.let { dateOut.format(it).replaceFirstChar { c -> c.uppercase() } } ?: slot.dateIso
        } catch (e: Exception) {
            slot.dateIso
        }

        val timeRange = formatTimeRangeFrench(slot.startTime, slot.endTime)
        return if (!isWaitingList) {
            """
            🪂 *INSCRIPTION CRÉNEAU PARAMOTEUR* 🪂
            Bonjour ! Je m'inscris au créneau suivant :
            
            📅 *Date* : $formattedDate
            ⏰ *Horaire* : $timeRange
            ${type.emoji} *Activité* : ${type.label} (${slot.title})
            📍 *Lieu* : ${slot.location}
            
            👤 *Mes Coordonnées :*
            • Nom : ${student.fullName}
            • Tél : ${student.phone}
            • Niveau : ${student.level}
            
            Merci de confirmer mon inscription !
            """.trimIndent()
        } else {
            """
            ⏳ *DEMANDE LISTE D'ATTENTE* ⏳
            Bonjour ! Le créneau étant complet, je souhaite me placer en liste d'attente :
            
            📅 *Date* : $formattedDate ($timeRange)
            ${type.emoji} *Activité* : ${type.label}
            👤 *Élève* : ${student.fullName} (${student.phone})
            """.trimIndent()
        }
    }

    // WhatsApp Export formatted for Gonflage / Vol / Perf
    fun generateWhatsAppPlanningText(slotsWithBookings: List<SlotWithBookings>): String {
        val sb = StringBuilder()
        sb.append("🪂 *PLANNING DES CRÉNEAUX* 🪂\n")
        sb.append("Bonjour ! Voici les prochains créneaux disponibles :\n\n")

        val groupedByDate = slotsWithBookings.groupBy { it.slot.dateIso }
        val dateIn = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
        val dateOut = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)

        groupedByDate.forEach { (dateIso, daySlots) ->
            val formattedDate = try {
                dateIn.parse(dateIso)?.let { dateOut.format(it).replaceFirstChar { c -> c.uppercase() } } ?: dateIso
            } catch (e: Exception) {
                dateIso
            }

            sb.append("📅 *").append(formattedDate).append("*\n")

            daySlots.forEach { item ->
                val slot = item.slot
                val type = PlanningLessonType.fromCode(slot.lessonType)
                val timeRange = formatTimeRangeFrench(slot.startTime, slot.endTime)

                sb.append("  • *").append(timeRange).append("* | ")
                sb.append(type.emoji).append(" *").append(type.label).append("*\n")
                if (slot.location.isNotBlank()) {
                    sb.append("    📍 ").append(slot.location).append("\n")
                }

                val enrolled = item.confirmedBookings.map { it.student.firstName }
                val dispo = item.availablePlaces

                if (slot.isCancelled) {
                    sb.append("    ⚠️ *CRÉNEAU ANNULÉ*\n")
                } else if (item.isFull) {
                    sb.append("    🔴 *COMPLET* (").append(enrolled.joinToString(", ")).append(")\n")
                    if (item.waitingListBookings.isNotEmpty()) {
                        val waitList = item.waitingListBookings.map { it.student.firstName }
                        sb.append("    ⏳ Liste d'attente : ").append(waitList.joinToString(", ")).append("\n")
                    }
                } else {
                    val names = if (enrolled.isNotEmpty()) " (Inscrits : ${enrolled.joinToString(", ")})" else ""
                    sb.append("    🟢 *").append(dispo).append(" place(s) dispo* / ").append(slot.maxCapacity).append(" max").append(names).append("\n")
                }

                if (slot.notes.isNotBlank()) {
                    sb.append("    💬 _").append(slot.notes).append("_\n")
                }
                sb.append("\n")
            }
        }

        sb.append("👉 Contactez-moi pour réserver votre créneau !")
        return sb.toString()
    }

    fun generateWeatherAlertWhatsAppText(
        slot: LessonSlotEntity,
        enrolledStudents: List<StudentEntity>
    ): String {
        val dateIn = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
        val dateOut = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE)
        val formattedDate = try {
            dateIn.parse(slot.dateIso)?.let { dateOut.format(it).replaceFirstChar { c -> c.uppercase() } } ?: slot.dateIso
        } catch (e: Exception) {
            slot.dateIso
        }
        val type = PlanningLessonType.fromCode(slot.lessonType)
        val timeRange = formatTimeRangeFrench(slot.startTime, slot.endTime)

        val statusHeader = if (slot.isCancelled) {
            "🚫 *ANNULATION DE CRÉNEAU - AÉROLOGIE DÉFAVORABLE* 🚫"
        } else {
            "⚠️ *ALERTE MÉTÉO / AÉROLOGIE SOUS RÉSERVE* ⚠️"
        }

        val reasonText = if (slot.cancelReason.isNotBlank()) slot.cancelReason else if (slot.weatherAlert.isNotBlank()) slot.weatherAlert else "Conditions aérologiques non sécurisées"

        val namesList = if (enrolledStudents.isNotEmpty()) {
            "👥 *Élèves concernés :* " + enrolledStudents.joinToString(", ") { it.firstName }
        } else ""

        val postponedText = if (slot.postponedTo.isNotBlank()) {
            "\n🔁 *Report proposé :* ${slot.postponedTo}"
        } else ""

        return """
            $statusHeader
            
            Bonjour à tous !
            
            📅 *Date* : $formattedDate
            ⏰ *Horaire* : $timeRange
            ${type.emoji} *Séance* : ${type.label} (${slot.title})
            📍 *Lieu* : ${slot.location}
            
            🌪️ *Motif aérologique :*
            $reasonText
            $postponedText
            
            $namesList
            
            ${if (slot.isCancelled) "Pour votre sécurité, la séance est annulée. Nous vous tiendrons informés du prochain créneau praticable !" else "Nous surveillons l'évolution de la brise et du gradient avant le départ."}
        """.trimIndent()
    }

    suspend fun seedDefaultDataIfEmpty(force: Boolean = false) {
        val studentCount = planningDao.getStudentsCount()
        val slotCount = planningDao.getSlotsCount()
        if (!force && (studentCount > 0 || slotCount > 0)) {
            return
        }

        val sampleStudents = listOf(
            StudentEntity(id = generateUniqueId(), firstName = "Jean", lastName = "Dupont", phone = "0612345678", email = "jean.dupont@email.fr", level = "Gonflage"),
            StudentEntity(id = generateUniqueId(), firstName = "Sophie", lastName = "Martin", phone = "0698765432", email = "sophie.martin@email.fr", level = "Vol"),
            StudentEntity(id = generateUniqueId(), firstName = "Lucas", lastName = "Bernard", phone = "0711223344", email = "lucas.b@email.fr", level = "Perfectionnement"),
            StudentEntity(id = generateUniqueId(), firstName = "Thomas", lastName = "Petit", phone = "0655443322", email = "thomas.p@email.fr", level = "Breveté"),
            StudentEntity(id = generateUniqueId(), firstName = "Marie", lastName = "Leroy", phone = "0677889900", email = "marie.leroy@email.fr", level = "Gonflage")
        )

        planningDao.insertStudents(sampleStudents)

        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
        val defaultDayConfig = StandardDayConfig()

        for (i in 0..6) {
            val dateStr = sdf.format(cal.time)
            createStandardDaySlots(dateStr, defaultDayConfig)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        cloudSyncManager?.pushFullSync(immediate = true)
    }
}
