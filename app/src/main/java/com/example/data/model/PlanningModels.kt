package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String,
    val lastName: String,
    val phone: String = "",
    val email: String = "",
    val level: String = "Gonflage", // Gonflage, Vol, Perf
    val notes: String = "",
    val completedSessions: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val fullName: String get() = "$firstName $lastName".trim()
    val initials: String get() = "${firstName.take(1)}${lastName.take(1)}".uppercase()
}

@Entity(tableName = "lesson_slots")
data class LessonSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateIso: String, // "YYYY-MM-DD"
    val startTime: String, // "08:00"
    val endTime: String, // "12:00"
    val title: String, // e.g. "Créneau Gonflage"
    val lessonType: String, // GONFLAGE, VOL, PERF
    val location: String = "Terrain de décollage",
    val maxCapacity: Int = 4,
    val notes: String = "",
    val isCancelled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bookings",
    foreignKeys = [
        ForeignKey(
            entity = LessonSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["slotId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("slotId"),
        Index("studentId"),
        Index(value = ["slotId", "studentId"], unique = true)
    ]
)
data class BookingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotId: Long,
    val studentId: Long,
    val registeredAt: Long = System.currentTimeMillis(),
    val isWaitingList: Boolean = false,
    val attended: Boolean = false
)

data class BookingWithStudent(
    val booking: BookingEntity,
    val student: StudentEntity
)

data class SlotWithBookings(
    val slot: LessonSlotEntity,
    val bookings: List<BookingWithStudent> = emptyList()
) {
    val confirmedBookings: List<BookingWithStudent> get() = bookings.filter { !it.booking.isWaitingList }
    val waitingListBookings: List<BookingWithStudent> get() = bookings.filter { it.booking.isWaitingList }
    val availablePlaces: Int get() = (slot.maxCapacity - confirmedBookings.size).coerceAtLeast(0)
    val isFull: Boolean get() = availablePlaces == 0
    val enrolledStudentIds: Set<Long> get() = bookings.map { it.student.id }.toSet()
}

enum class PlanningLessonType(val code: String, val label: String, val emoji: String, val defaultCapacity: Int) {
    GONFLAGE("GONFLAGE", "Gonflage", "🪂", 4),
    VOL("VOL", "Vol", "✈️", 2),
    PERF("PERF", "Perf", "🎯", 3);

    companion object {
        fun fromCode(code: String): PlanningLessonType {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: GONFLAGE
        }
    }
}

