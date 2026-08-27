package com.example.data.db

import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.*

class PlanningRepository(private val planningDao: PlanningDao) {

    val allStudents: Flow<List<StudentEntity>> = planningDao.getAllStudents()
    val allSlots: Flow<List<LessonSlotEntity>> = planningDao.getAllSlots()
    val allBookings: Flow<List<BookingEntity>> = planningDao.getAllBookings()

    // Combined Flow: Slots with enrolled students
    val slotsWithBookings: Flow<List<SlotWithBookings>> = combine(
        allSlots,
        allBookings,
        allStudents
    ) { slots, bookings, students ->
        val studentMap = students.associateBy { it.id }
        val bookingBySlotId = bookings.groupBy { it.slotId }

        slots.map { slot ->
            val slotBookings = bookingBySlotId[slot.id].orEmpty().mapNotNull { booking ->
                studentMap[booking.studentId]?.let { student ->
                    BookingWithStudent(booking, student)
                }
            }
            SlotWithBookings(slot, slotBookings)
        }
    }

    suspend fun initializeSampleDataIfNeeded() {
        val count = planningDao.getStudentsCount()
        if (count == 0) {
            val sampleStudents = listOf(
                StudentEntity(id = 1, firstName = "Julien", lastName = "Mercier", phone = "06 12 34 56 78", level = "Gonflage", notes = "Très assidu"),
                StudentEntity(id = 2, firstName = "Sophie", lastName = "Bernard", phone = "06 23 45 67 89", level = "Vol", notes = "Prête pour autonomie"),
                StudentEntity(id = 3, firstName = "Thomas", lastName = "Laurent", phone = "06 34 56 78 90", level = "Perf", notes = "Travail en thermique"),
                StudentEntity(id = 4, firstName = "Lucas", lastName = "Dubois", phone = "06 45 67 89 01", level = "Vol"),
                StudentEntity(id = 5, firstName = "Émilie", lastName = "Moreau", phone = "06 56 78 90 12", level = "Gonflage"),
                StudentEntity(id = 6, firstName = "Maxime", lastName = "Petit", phone = "06 67 89 01 23", level = "Perf")
            )
            planningDao.insertStudents(sampleStudents)

            // Seed a few calendar slots
            val cal = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)

            val slots = mutableListOf<LessonSlotEntity>()
            val bookings = mutableListOf<BookingEntity>()

            // Aujourd'hui
            val d0 = dateFormat.format(cal.time)
            slots.add(
                LessonSlotEntity(
                    id = 1,
                    dateIso = d0,
                    startTime = "08:00",
                    endTime = "11:30",
                    title = "Créneau Gonflage Matin",
                    lessonType = "GONFLAGE",
                    location = "Pente École",
                    maxCapacity = 4,
                    notes = "Vent régulier prévu"
                )
            )

            // Demain (Complet)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val d1 = dateFormat.format(cal.time)
            slots.add(
                LessonSlotEntity(
                    id = 2,
                    dateIso = d1,
                    startTime = "07:00",
                    endTime = "10:00",
                    title = "Créneau Vol du Matin",
                    lessonType = "VOL",
                    location = "Décollage Sud",
                    maxCapacity = 2,
                    notes = "Conditions calmes idéales"
                )
            )

            // Après-demain (Disponible)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val d2 = dateFormat.format(cal.time)
            slots.add(
                LessonSlotEntity(
                    id = 3,
                    dateIso = d2,
                    startTime = "09:00",
                    endTime = "12:00",
                    title = "Créneau Perf & Pilotage",
                    lessonType = "PERF",
                    location = "Terrain Principal",
                    maxCapacity = 3,
                    notes = "Travail sur les trajectoires"
                )
            )

            planningDao.insertSlots(slots)

            // Bookings: Slot 1 has 2/4 (Disponible - VERT), Slot 2 has 2/2 (Complet - ROUGE)
            bookings.add(BookingEntity(slotId = 1, studentId = 1, isWaitingList = false))
            bookings.add(BookingEntity(slotId = 1, studentId = 5, isWaitingList = false))
            bookings.add(BookingEntity(slotId = 2, studentId = 2, isWaitingList = false))
            bookings.add(BookingEntity(slotId = 2, studentId = 4, isWaitingList = false))

            planningDao.insertBookings(bookings)
        }
    }

    // --- Slot Actions ---
    suspend fun createSlot(slot: LessonSlotEntity): Long = planningDao.insertSlot(slot)

    suspend fun updateSlot(slot: LessonSlotEntity) = planningDao.updateSlot(slot)

    suspend fun deleteSlot(slotId: Long) = planningDao.deleteSlotById(slotId)

    // --- Student Actions ---
    suspend fun createStudent(student: StudentEntity): Long = planningDao.insertStudent(student)

    suspend fun updateStudent(student: StudentEntity) = planningDao.updateStudent(student)

    suspend fun deleteStudent(student: StudentEntity) = planningDao.deleteStudent(student)

    // --- Booking Actions ---
    suspend fun enrollStudent(slotId: Long, studentId: Long, isWaitingList: Boolean = false): Boolean {
        try {
            planningDao.insertBooking(
                BookingEntity(
                    slotId = slotId,
                    studentId = studentId,
                    isWaitingList = isWaitingList
                )
            )
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    suspend fun unenrollStudent(slotId: Long, studentId: Long) {
        planningDao.deleteBookingBySlotAndStudent(slotId, studentId)
    }

    suspend fun toggleAttendance(bookingId: Long, studentId: Long, attended: Boolean) {
        planningDao.updateAttendance(bookingId, attended)
        if (attended) {
            planningDao.incrementCompletedSessions(studentId)
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

                sb.append("  • *").append(slot.startTime).append(" - ").append(slot.endTime).append("* | ")
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
}
