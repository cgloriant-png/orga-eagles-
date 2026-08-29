package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.LessonSlotEntity
import com.example.data.model.PlanningLessonType
import com.example.data.model.SlotWithBookings
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CalendarExportUtils {

    /**
     * Add a single slot directly into Google Calendar / Android Calendar via native intent
     */
    fun addSlotToGoogleCalendar(context: Context, slot: LessonSlotEntity) {
        try {
            val dateIn = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE)
            val startTimeCal = Calendar.getInstance().apply {
                val parsed = dateIn.parse("${slot.dateIso} ${slot.startTime}")
                if (parsed != null) time = parsed
            }
            val endTimeCal = Calendar.getInstance().apply {
                val parsed = dateIn.parse("${slot.dateIso} ${slot.endTime}")
                if (parsed != null) time = parsed
            }

            val type = PlanningLessonType.fromCode(slot.lessonType)
            val eventTitle = "Paramoteur - ${type.label} (${slot.title})"
            val description = buildString {
                append("Séance de paramoteur : ${type.label}\n")
                if (slot.location.isNotBlank()) append("Lieu : ${slot.location}\n")
                if (slot.notes.isNotBlank()) append("Notes : ${slot.notes}\n")
                if (slot.weatherAlert.isNotBlank()) append("⚠️ Alerte Météo : ${slot.weatherAlert}\n")
            }

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeCal.timeInMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeCal.timeInMillis)
                putExtra(CalendarContract.Events.TITLE, eventTitle)
                putExtra(CalendarContract.Events.DESCRIPTION, description)
                putExtra(CalendarContract.Events.EVENT_LOCATION, slot.location)
                putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible d'ouvrir l'agenda : ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Generate standard RFC 5545 .ics iCalendar file for multiple slots and share it
     */
    fun exportSlotsToIcs(
        context: Context,
        slotsWithBookings: List<SlotWithBookings>,
        exportTitle: String = "Planning_Paramoteur"
    ) {
        try {
            val icsFile = File(context.cacheDir, "${exportTitle.replace(" ", "_")}.ics")
            val writer = FileWriter(icsFile)

            val dfIcs = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parseSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE)

            writer.write("BEGIN:VCALENDAR\r\n")
            writer.write("VERSION:2.0\r\n")
            writer.write("PRODID:-//Ecole Paramoteur//Planning v5.0//FR\r\n")
            writer.write("CALSCALE:GREGORIAN\r\n")
            writer.write("METHOD:PUBLISH\r\n")
            writer.write("X-WR-CALNAME:${exportTitle}\r\n")
            writer.write("X-WR-TIMEZONE:Europe/Paris\r\n")

            for (item in slotsWithBookings) {
                val slot = item.slot
                val type = PlanningLessonType.fromCode(slot.lessonType)

                val startParsed = try { parseSdf.parse("${slot.dateIso} ${slot.startTime}") } catch (e: Exception) { null }
                val endParsed = try { parseSdf.parse("${slot.dateIso} ${slot.endTime}") } catch (e: Exception) { null }

                if (startParsed != null && endParsed != null) {
                    val enrolledNames = item.confirmedBookings.joinToString(", ") { it.student.fullName }

                    writer.write("BEGIN:VEVENT\r\n")
                    writer.write("UID:slot_${slot.id}_${slot.dateIso}@paramoteur.school\r\n")
                    writer.write("DTSTAMP:${dfIcs.format(Date())}\r\n")
                    writer.write("DTSTART:${dfIcs.format(startParsed)}\r\n")
                    writer.write("DTEND:${dfIcs.format(endParsed)}\r\n")
                    writer.write("SUMMARY:Paramoteur ${type.emoji} ${type.label} - ${slot.title}\r\n")
                    writer.write("LOCATION:${slot.location.replace(",", "\\,")}\r\n")

                    val desc = "Type: ${type.label}\\nInscrits: ${enrolledNames.ifBlank { "Aucun" }}\\nNotes: ${slot.notes}" +
                            if (slot.isCancelled) "\\n[ANNULE POUR CAUSE METEO]" else ""
                    writer.write("DESCRIPTION:$desc\r\n")

                    if (slot.isCancelled) {
                        writer.write("STATUS:CANCELLED\r\n")
                    } else {
                        writer.write("STATUS:CONFIRMED\r\n")
                    }
                    writer.write("END:VEVENT\r\n")
                }
            }

            writer.write("END:VCALENDAR\r\n")
            writer.flush()
            writer.close()

            // Share ICS file with Calendar app or send via email/WhatsApp
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                icsFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Export Calendrier Paramoteur")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Synchroniser avec l'agenda"))

        } catch (e: Exception) {
            Toast.makeText(context, "Erreur export calendrier : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
