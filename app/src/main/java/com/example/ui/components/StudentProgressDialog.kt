package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StudentEntity
import com.example.data.model.StudentProgressEntity
import com.example.ui.theme.*

@Composable
fun StudentProgressDialog(
    student: StudentEntity,
    initialProgress: StudentProgressEntity,
    onDismiss: () -> Unit,
    onSaveProgress: (StudentProgressEntity) -> Unit,
    onExportPdf: (StudentEntity, StudentProgressEntity) -> Unit,
    onShareWhatsApp: (String) -> Unit
) {
    var flightMinutes by remember { mutableIntStateOf(initialProgress.totalFlightMinutes) }
    var flightsCount by remember { mutableIntStateOf(initialProgress.totalFlightsCount) }
    var gonflageMinutes by remember { mutableIntStateOf(initialProgress.totalGonflageMinutes) }

    var autoDeco by remember { mutableIntStateOf(initialProgress.autonomyDecollage) }
    var autoVol by remember { mutableIntStateOf(initialProgress.autonomyEnVol) }
    var autoAtterro by remember { mutableIntStateOf(initialProgress.autonomyAtterrissage) }
    var autoGonf by remember { mutableIntStateOf(initialProgress.autonomyGonflage) }

    var skillPrevol by remember { mutableStateOf(initialProgress.skillPrevol) }
    var skillGonfFace by remember { mutableStateOf(initialProgress.skillGonflageFace) }
    var skillGonfDos by remember { mutableStateOf(initialProgress.skillGonflageDos) }
    var skillMoteur by remember { mutableStateOf(initialProgress.skillMoteurSol) }
    var skillDeco by remember { mutableStateOf(initialProgress.skillDecoAutonome) }
    var skillVirages by remember { mutableStateOf(initialProgress.skillViragesAltitude) }
    var skillPanne by remember { mutableStateOf(initialProgress.skillPanneMoteur) }
    var skillAtterro by remember { mutableStateOf(initialProgress.skillAtterroPrecision) }
    var skillNav by remember { mutableStateOf(initialProgress.skillNavigationAerologie) }
    var skillBrevet by remember { mutableStateOf(initialProgress.skillBrevetPilote) }
    var skillEmport by remember { mutableStateOf(initialProgress.skillEmportPassager) }

    var instructorNotes by remember { mutableStateOf(initialProgress.instructorNotes) }

    val validatedCount = listOf(
        skillPrevol, skillGonfFace, skillGonfDos, skillMoteur, skillDeco,
        skillVirages, skillPanne, skillAtterro, skillNav, skillBrevet, skillEmport
    ).count { it }

    val percent = ((validatedCount.toFloat() / 11f) * 100).toInt()

    fun buildCurrentProgressEntity(): StudentProgressEntity {
        return StudentProgressEntity(
            studentId = student.id,
            totalFlightMinutes = flightMinutes,
            totalFlightsCount = flightsCount,
            totalGonflageMinutes = gonflageMinutes,
            autonomyDecollage = autoDeco,
            autonomyEnVol = autoVol,
            autonomyAtterrissage = autoAtterro,
            autonomyGonflage = autoGonf,
            skillPrevol = skillPrevol,
            skillGonflageFace = skillGonfFace,
            skillGonflageDos = skillGonfDos,
            skillMoteurSol = skillMoteur,
            skillDecoAutonome = skillDeco,
            skillViragesAltitude = skillVirages,
            skillPanneMoteur = skillPanne,
            skillAtterroPrecision = skillAtterro,
            skillNavigationAerologie = skillNav,
            skillBrevetPilote = skillBrevet,
            skillEmportPassager = skillEmport,
            instructorNotes = instructorNotes
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📖", fontSize = 22.sp)
                    Column {
                        Text(
                            "Livret de Progression FFPLUM",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = HighDensityHeaderTitle
                        )
                        Text(
                            "${student.fullName} • Niveau : ${student.level}",
                            fontSize = 11.sp,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Progression Bar Header
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PrimaryBlueContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Progression globale du Brevet :", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("$percent% ($validatedCount/11 modules)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = PrimaryBlue,
                            trackColor = Color.White
                        )
                    }
                }

                // Flight Log Counters (Heures de vol, nombre de vols, gonflage)
                Text("⏱️ Compteurs & Heures de Vol :", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                // Flight Time Row
                Surface(shape = RoundedCornerShape(8.dp), color = HighDensityNavBar, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val h = flightMinutes / 60
                            val m = flightMinutes % 60
                            val formatted = if (m == 0) "${h}h" else "${h}h${m.toString().padStart(2, '0')}"
                            Text("Temps de Vol Total", fontSize = 11.sp, color = SecondaryText)
                            Text(formatted, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryBlue)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(
                                onClick = { flightMinutes = (flightMinutes - 15).coerceAtLeast(0) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) { Text("-15m", fontSize = 10.sp) }

                            FilledTonalButton(
                                onClick = { flightMinutes += 15 },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) { Text("+15m", fontSize = 10.sp) }

                            FilledTonalButton(
                                onClick = { flightMinutes += 30 },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) { Text("+30m", fontSize = 10.sp) }

                            FilledTonalButton(
                                onClick = { flightMinutes += 60 },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) { Text("+1h", fontSize = 10.sp) }
                        }
                    }
                }

                // Flight Count & Gonflage Rows
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Flight Count
                    Surface(shape = RoundedCornerShape(8.dp), color = HighDensityNavBar, modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Nombre de Vols", fontSize = 10.sp, color = SecondaryText)
                                Text("$flightsCount vols", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { if (flightsCount > 0) flightsCount-- },
                                    modifier = Modifier.size(24.dp).background(HighDensitySurface, CircleShape)
                                ) { Text("-", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                IconButton(
                                    onClick = { flightsCount++ },
                                    modifier = Modifier.size(24.dp).background(HighDensitySurface, CircleShape)
                                ) { Text("+", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                            }
                        }
                    }

                    // Gonflage Time
                    Surface(shape = RoundedCornerShape(8.dp), color = HighDensityNavBar, modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val gh = gonflageMinutes / 60
                                val gm = gonflageMinutes % 60
                                val gformatted = if (gm == 0) "${gh}h" else "${gh}h${gm.toString().padStart(2, '0')}"
                                Text("Gonflage Sol", fontSize = 10.sp, color = SecondaryText)
                                Text(gformatted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { gonflageMinutes = (gonflageMinutes - 30).coerceAtLeast(0) },
                                    modifier = Modifier.size(24.dp).background(HighDensitySurface, CircleShape)
                                ) { Text("-", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                IconButton(
                                    onClick = { gonflageMinutes += 30 },
                                    modifier = Modifier.size(24.dp).background(HighDensitySurface, CircleShape)
                                ) { Text("+", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                            }
                        }
                    }
                }

                // Autonomy Rating Dials (1 to 5 stars)
                Text("🎯 Niveaux d'Autonomie (1 = Guidé, 5 = Autonome) :", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                AutonomyRatingRow("Décollage (Course & Axe)", autoDeco) { autoDeco = it }
                AutonomyRatingRow("Pilotage & Palier en vol", autoVol) { autoVol = it }
                AutonomyRatingRow("Atterrissage & Arrondi", autoAtterro) { autoAtterro = it }
                AutonomyRatingRow("Gonflage & Contrôle voile", autoGonf) { autoGonf = it }

                // FFPLUM Competency Checklist
                Text("📋 Modules Syllabus FFPLUM :", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SkillCheckboxRow("1. Prévol & suspentage", skillPrevol) { skillPrevol = it }
                    SkillCheckboxRow("2. Gonflage face voile (vent établi)", skillGonfFace) { skillGonfFace = it }
                    SkillCheckboxRow("3. Gonflage dos voile & recentrage", skillGonfDos) { skillGonfDos = it }
                    SkillCheckboxRow("4. Contrôle moteur & poussée au sol", skillMoteur) { skillMoteur = it }
                    SkillCheckboxRow("5. Décollage autonome & tenue d'axe", skillDeco) { skillDeco = it }
                    SkillCheckboxRow("6. Virages coordonnés & gestion palier", skillVirages) { skillVirages = it }
                    SkillCheckboxRow("7. Simulation panne moteur & PTU/PTS", skillPanne) { skillPanne = it }
                    SkillCheckboxRow("8. Approche & posé de précision", skillAtterro) { skillAtterro = it }
                    SkillCheckboxRow("9. Navigation & analyse aérologique", skillNav) { skillNav = it }
                    SkillCheckboxRow("10. 🎓 Brevet Pilote FFPLUM validé", skillBrevet, isHighlight = true) { skillBrevet = it }
                    SkillCheckboxRow("11. 👥 Qualification Emport Passager", skillEmport, isHighlight = true) { skillEmport = it }
                }

                // Instructor Pedagogical Notes
                OutlinedTextField(
                    value = instructorNotes,
                    onValueChange = { instructorNotes = it },
                    label = { Text("Appréciation pédagogique du moniteur") },
                    placeholder = { Text("Points forts, axes de travail, progression...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Export Buttons in Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val entity = buildCurrentProgressEntity()
                            onExportPdf(student, entity)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("📄 PDF Livret", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val entity = buildCurrentProgressEntity()
                            val summary = """
                                🪂 *LIVRET DE PROGRESSION FFPLUM*
                                Pilote : *${student.fullName}*
                                
                                ⏱️ *Temps de Vol* : ${entity.totalFlightHoursFormatted} (${entity.totalFlightsCount} vols)
                                🪁 *Gonflage* : ${entity.totalGonflageHoursFormatted}
                                📊 *Modules validés* : ${entity.validatedSkillsCount}/11 (${entity.completionPercent}%)
                                
                                🎯 *Autonomie :*
                                • Déco : ${entity.autonomyDecollage}/5
                                • Vol : ${entity.autonomyEnVol}/5
                                • Atterro : ${entity.autonomyAtterrissage}/5
                                • Gonflage : ${entity.autonomyGonflage}/5
                                
                                📝 *Commentaire moniteur :* ${if (instructorNotes.isNotBlank()) instructorNotes else "En cours de formation"}
                            """.trimIndent()
                            onShareWhatsApp(summary)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("💬 WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val progressToSave = buildCurrentProgressEntity()
                    onSaveProgress(progressToSave)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Enregistrer la progression")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = HighDensitySurface
    )
}

@Composable
private fun AutonomyRatingRow(
    label: String,
    currentRating: Int,
    onRatingChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = HighDensityHeaderTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..5).forEach { star ->
                val isFilled = star <= currentRating
                IconButton(
                    onClick = { onRatingChanged(star) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text(
                        if (isFilled) "★" else "☆",
                        fontSize = 16.sp,
                        color = if (isFilled) AmberAccent else Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillCheckboxRow(
    title: String,
    checked: Boolean,
    isHighlight: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (checked) (if (isHighlight) GreenSuccessBg else PrimaryBlueContainer.copy(alpha = 0.4f)) else HighDensityNavBar,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = if (isHighlight) GreenSuccess else PrimaryBlue
                ),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (checked || isHighlight) FontWeight.Bold else FontWeight.Normal,
                color = if (checked && isHighlight) GreenSuccess else HighDensityHeaderTitle
            )
        }
    }
}
