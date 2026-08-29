package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.PlanningLessonType
import com.example.data.model.SlotWithBookings
import com.example.data.model.StudentEntity
import com.example.data.model.StudentProgressEntity
import com.example.data.model.formatTimeRangeFrench
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfExportUtils {

    /**
     * Generate and share a clean PDF document for the school planning
     */
    fun exportPlanningPdf(
        context: Context,
        slotsWithBookings: List<SlotWithBookings>,
        periodTitle: String = "Planning Paramoteur"
    ) {
        try {
            val pdfDoc = PdfDocument()
            val pageWidth = 595 // A4 standard point width
            val pageHeight = 842 // A4 standard point height

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDoc.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 16f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = Color.rgb(2, 132, 199)
                textSize = 10f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val headerPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 9f
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 9f
                isAntiAlias = true
            }

            val boldPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 9f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val cancelledPaint = Paint().apply {
                color = Color.rgb(220, 38, 38)
                textSize = 8.5f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val boxPaint = Paint().apply {
                color = Color.rgb(241, 245, 249)
                style = Paint.Style.FILL
            }

            val borderPaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }

            var currentY = 40f
            val marginX = 35f

            // --- Header ---
            canvas.drawText("ÉCOLE DE PARAMOTEUR — PLANNING OFFICIEL", marginX, currentY, titlePaint)
            currentY += 16f
            canvas.drawText("Spot principal : Plouharnel (56) • Horaires calés sur l'aérologie solaire", marginX, currentY, subtitlePaint)
            currentY += 14f
            val todayStr = SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(Date())
            canvas.drawText("Document généré le $todayStr • Total créneaux : ${slotsWithBookings.size}", marginX, currentY, headerPaint)
            currentY += 16f

            // Separator line
            canvas.drawLine(marginX, currentY, pageWidth - marginX, currentY, borderPaint)
            currentY += 15f

            val groupedByDate = slotsWithBookings.groupBy { it.slot.dateIso }.toSortedMap()
            val dateIn = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
            val dateOut = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE)

            for ((dateIso, daySlots) in groupedByDate) {
                val formattedDate = try {
                    dateIn.parse(dateIso)?.let { dateOut.format(it).replaceFirstChar { c -> c.uppercase() } } ?: dateIso
                } catch (e: Exception) {
                    dateIso
                }

                // Check page bounds
                if (currentY > pageHeight - 120f) {
                    pdfDoc.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDoc.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = 40f
                }

                // Day section banner
                val dayRect = RectF(marginX, currentY, pageWidth - marginX, currentY + 20f)
                val dayBgPaint = Paint().apply { color = Color.rgb(224, 242, 254) }
                canvas.drawRoundRect(dayRect, 4f, 4f, dayBgPaint)
                canvas.drawText("📅  $formattedDate", marginX + 8f, currentY + 14f, subtitlePaint)
                currentY += 26f

                for (item in daySlots) {
                    val slot = item.slot
                    val type = PlanningLessonType.fromCode(slot.lessonType)
                    val timeRange = formatTimeRangeFrench(slot.startTime, slot.endTime)
                    val enrolled = item.confirmedBookings.joinToString(", ") { "${it.student.firstName} ${it.student.lastName.take(1)}." }

                    val itemHeight = if (slot.hasWeatherAlert || slot.notes.isNotBlank()) 42f else 32f

                    if (currentY + itemHeight > pageHeight - 40f) {
                        pdfDoc.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = pdfDoc.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = 40f
                    }

                    val slotRect = RectF(marginX + 5f, currentY, pageWidth - marginX, currentY + itemHeight)
                    canvas.drawRoundRect(slotRect, 4f, 4f, boxPaint)
                    canvas.drawRoundRect(slotRect, 4f, 4f, borderPaint)

                    // Line 1: Type emoji, Time & Title
                    canvas.drawText("${type.emoji}  $timeRange  •  ${slot.title}", marginX + 12f, currentY + 13f, boldPaint)

                    // Line 1 Right: Capacity / Status
                    val statusText = if (slot.isCancelled) {
                        "ANNULÉ"
                    } else if (item.isFull) {
                        "COMPLET (${item.confirmedCount}/${slot.maxCapacity})"
                    } else {
                        "${item.availablePlaces} place(s) dispo / ${slot.maxCapacity}"
                    }
                    val statusPaint = if (slot.isCancelled) cancelledPaint else boldPaint
                    val textWidth = statusPaint.measureText(statusText)
                    canvas.drawText(statusText, pageWidth - marginX - textWidth - 10f, currentY + 13f, statusPaint)

                    // Line 2: Inscrits & Location
                    val enrolledText = if (enrolled.isNotBlank()) "Inscrits : $enrolled" else "Aucun inscrit"
                    val locText = if (slot.location.isNotBlank()) "  |  Lieu : ${slot.location}" else ""
                    canvas.drawText("$enrolledText$locText", marginX + 12f, currentY + 25f, textPaint)

                    // Line 3: Weather Alert or Notes if present
                    if (slot.hasWeatherAlert) {
                        val alertMsg = if (slot.cancelReason.isNotBlank()) "⚠️ Annulation : ${slot.cancelReason}" else "⚠️ Alerte : ${slot.weatherAlert}"
                        canvas.drawText(alertMsg, marginX + 12f, currentY + 36f, cancelledPaint)
                    } else if (slot.notes.isNotBlank()) {
                        canvas.drawText("Note : ${slot.notes}", marginX + 12f, currentY + 36f, headerPaint)
                    }

                    currentY += itemHeight + 6f
                }
                currentY += 8f
            }

            // Footer
            val footerPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                textSize = 8f
                isAntiAlias = true
            }
            canvas.drawText("Paramoteur Planning & École • Document conforme FFPLUM • Page $pageNumber", marginX, pageHeight - 20f, footerPaint)

            pdfDoc.finishPage(page)

            // Save PDF to cache and share
            val pdfFile = File(context.cacheDir, "Planning_Paramoteur_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(pdfFile)
            pdfDoc.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDoc.close()

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Planning Paramoteur PDF")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Partager / Imprimer le Planning PDF"))

        } catch (e: Exception) {
            Toast.makeText(context, "Erreur export PDF : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Generate an official digital progress booklet PDF for a student
     */
    fun exportStudentBookletPdf(
        context: Context,
        student: StudentEntity,
        progress: StudentProgressEntity
    ) {
        try {
            val pdfDoc = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 17f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = Color.rgb(2, 132, 199)
                textSize = 11f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val boldPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 10f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = 9.5f
                isAntiAlias = true
            }

            val checkPaint = Paint().apply {
                color = Color.rgb(16, 185, 129)
                textSize = 11f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val uncheckPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                textSize = 11f
                isAntiAlias = true
            }

            val cardBgPaint = Paint().apply {
                color = Color.rgb(248, 250, 252)
                style = Paint.Style.FILL
            }

            val borderPaint = Paint().apply {
                color = Color.rgb(226, 232, 240)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            var currentY = 45f
            val marginX = 40f

            // Title & Banner
            canvas.drawText("LIVRET DE PROGRESSION PARAMOTEUR (FFPLUM)", marginX, currentY, titlePaint)
            currentY += 18f
            canvas.drawText("Fiche individuelle de formation & suivi des compétences", marginX, currentY, subtitlePaint)
            currentY += 20f

            // Student identity card box
            val idBox = RectF(marginX, currentY, pageWidth - marginX, currentY + 65f)
            canvas.drawRoundRect(idBox, 8f, 8f, cardBgPaint)
            canvas.drawRoundRect(idBox, 8f, 8f, borderPaint)

            canvas.drawText("Pilote / Élève : ${student.fullName}", marginX + 15f, currentY + 20f, boldPaint)
            canvas.drawText("Niveau actuel : ${student.level} • Tél : ${student.phone.ifBlank { "Non renseigné" }}", marginX + 15f, currentY + 36f, textPaint)
            canvas.drawText("Séances complétées : ${student.completedSessions} • Progression globale : ${progress.completionPercent}%", marginX + 15f, currentY + 52f, boldPaint)
            currentY += 80f

            // Flight metrics box
            val statsBox = RectF(marginX, currentY, pageWidth - marginX, currentY + 45f)
            val statsBgPaint = Paint().apply { color = Color.rgb(238, 242, 255) }
            canvas.drawRoundRect(statsBox, 8f, 8f, statsBgPaint)

            canvas.drawText("⏱️ Total Heures de Vol : ${progress.totalFlightHoursFormatted} (${progress.totalFlightsCount} vols)", marginX + 15f, currentY + 18f, boldPaint)
            canvas.drawText("🪁 Total Gonflage au Sol : ${progress.totalGonflageHoursFormatted}", marginX + 15f, currentY + 34f, boldPaint)
            currentY += 60f

            // Autonomy scale overview
            canvas.drawText("NIVEAUX D'AUTONOMIE (Échelle 1 à 5) :", marginX, currentY, subtitlePaint)
            currentY += 16f
            val autoLabels = listOf(
                "Décollage" to progress.autonomyDecollage,
                "Pilotage en vol" to progress.autonomyEnVol,
                "Atterrissage" to progress.autonomyAtterrissage,
                "Gonflage & Maîtrise voile" to progress.autonomyGonflage
            )
            autoLabels.forEach { (label, level) ->
                val stars = "★".repeat(level) + "☆".repeat(5 - level)
                val levelDesc = when (level) {
                    1 -> "Débutant guidé"
                    2 -> "Guidage radio partiel"
                    3 -> "Perfectionnement"
                    4 -> "Autonome sous surveillance"
                    else -> "Maîtrise complète"
                }
                canvas.drawText("• $label : $stars ($level/5 - $levelDesc)", marginX + 10f, currentY, textPaint)
                currentY += 15f
            }
            currentY += 12f

            // FFPLUM Skills Checklist
            canvas.drawText("MODULES & COMPÉTENCES DU SYLLABUS FFPLUM :", marginX, currentY, subtitlePaint)
            currentY += 16f

            val skills = listOf(
                "Prévol, vérification mécanique & suspentage" to progress.skillPrevol,
                "Gonflage face à la voile (vent établi)" to progress.skillGonflageFace,
                "Gonflage dos voile & recentrage" to progress.skillGonflageDos,
                "Contrôle moteur au sol, poussée & sécurité hélice" to progress.skillMoteurSol,
                "Décollage autonome & maintien de l'axe" to progress.skillDecoAutonome,
                "Virages coordonnés & gestion du palier en altitude" to progress.skillViragesAltitude,
                "Simulation panne moteur & trajectoires PTU / PTS" to progress.skillPanneMoteur,
                "Approche finale & posé de précision dans la cible" to progress.skillAtterroPrecision,
                "Navigation, lecture de carte & analyse aérologique" to progress.skillNavigationAerologie,
                "Brevet de Pilote Paramoteur validé" to progress.skillBrevetPilote,
                "Qualification Emport Passager validée" to progress.skillEmportPassager
            )

            skills.forEach { (skillName, isValidated) ->
                val checkSymbol = if (isValidated) "✅" else "⬜"
                val paintToUse = if (isValidated) checkPaint else uncheckPaint
                canvas.drawText("$checkSymbol  $skillName", marginX + 10f, currentY, if (isValidated) boldPaint else textPaint)
                currentY += 15f
            }

            currentY += 15f
            if (progress.instructorNotes.isNotBlank()) {
                canvas.drawText("APPRÉCIATION PÉDAGOGIQUE DU MONITEUR :", marginX, currentY, subtitlePaint)
                currentY += 15f
                canvas.drawText(progress.instructorNotes, marginX + 10f, currentY, textPaint)
                currentY += 25f
            }

            // Signature footer
            canvas.drawLine(marginX, pageHeight - 70f, pageWidth - marginX, pageHeight - 70f, borderPaint)
            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(progress.lastUpdated))
            canvas.drawText("Mis à jour le $dateStr • Visa de l'école & Signature du moniteur", marginX, pageHeight - 50f, textPaint)

            pdfDoc.finishPage(page)

            val file = File(context.cacheDir, "Livret_FFPLUM_${student.fullName.replace(" ", "_")}.pdf")
            val fos = FileOutputStream(file)
            pdfDoc.writeTo(fos)
            fos.flush()
            fos.close()
            pdfDoc.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Livret de Progression FFPLUM - ${student.fullName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Exporter le Livret de Progression PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur export Livret PDF : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
