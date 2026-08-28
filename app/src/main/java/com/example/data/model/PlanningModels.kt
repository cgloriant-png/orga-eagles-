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

data class BookingWithSlotInfo(
    val booking: BookingEntity,
    val slot: LessonSlotEntity
)

data class StudentWithStats(
    val student: StudentEntity,
    val totalBookings: Int,
    val attendedBookings: Int,
    val upcomingBookings: Int,
    val waitingListBookings: Int,
    val bookingHistory: List<BookingWithSlotInfo> = emptyList()
) {
    val attendanceRate: Float
        get() = if (totalBookings > 0) (attendedBookings.toFloat() / totalBookings) * 100f else 0f
}

data class StandardDayConfig(
    val sunriseHour: Int = 6,
    val sunriseMinute: Int = 30,
    val sunsetHour: Int = 21,
    val sunsetMinute: Int = 0,
    val morningVolCapacity: Int = 2,
    val morningGonflageCapacity: Int = 4,
    val eveningGonflageCapacity: Int = 4,
    val eveningVolCapacity: Int = 2,
    val location: String = "Terrain de décollage"
)

data class SlotWithBookings(
    val slot: LessonSlotEntity,
    val bookings: List<BookingWithStudent> = emptyList()
) {
    val confirmedBookings: List<BookingWithStudent> get() = bookings.filter { !it.booking.isWaitingList }
    val waitingListBookings: List<BookingWithStudent> get() = bookings.filter { it.booking.isWaitingList }
    val confirmedCount: Int get() = confirmedBookings.size
    val availablePlaces: Int get() = (slot.maxCapacity - confirmedBookings.size).coerceAtLeast(0)
    val isFull: Boolean get() = availablePlaces == 0
    val enrolledStudentIds: Set<Long> get() = bookings.map { it.student.id }.toSet()
}

enum class PlanningLessonType(
    val code: String,
    val label: String,
    val shortLabel: String,
    val emoji: String,
    val defaultCapacity: Int,
    val primaryColorHex: Long,
    val containerColorHex: Long,
    val borderColorHex: Long
) {
    GONFLAGE("GONFLAGE", "Gonflage au sol", "GONFLAGE", "🪁", 4, 0xFFD97706, 0xFFFEF3C7, 0xFFF59E0B),
    VOL("VOL", "Vol Paramoteur", "VOL", "✈️", 2, 0xFF0284C7, 0xFFE0F2FE, 0xFF38BDF8),
    PERF("PERF", "Perfectionnement", "PERF", "🎯", 3, 0xFF7C3AED, 0xFFEDE9FE, 0xFFA78BFA);

    val primaryColor: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color(primaryColorHex)
    val containerColor: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color(containerColorHex)
    val borderColor: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color(borderColorHex)

    companion object {
        fun fromCode(code: String): PlanningLessonType {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: GONFLAGE
        }
    }
}

fun formatTimeFrench(timeStr: String): String {
    val clean = timeStr.trim().replace(":", "h").replace("H", "h")
    val parts = clean.split("h")
    if (parts.isEmpty()) return timeStr
    val hour = parts[0].trim().toIntOrNull() ?: return timeStr
    val minute = if (parts.size > 1) parts[1].trim() else ""
    return if (minute.isEmpty() || minute == "00" || minute == "0") {
        "${hour}h"
    } else {
        val paddedMinute = if (minute.length == 1) "${minute}0" else minute
        "${hour}h$paddedMinute"
    }
}

fun formatTimeRangeFrench(startTime: String, endTime: String): String {
    val start = formatTimeFrench(startTime)
    val end = formatTimeFrench(endTime)
    return "de $start à $end"
}


