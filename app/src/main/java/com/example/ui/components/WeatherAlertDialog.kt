package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.data.model.LessonSlotEntity
import com.example.data.model.StudentEntity
import com.example.ui.theme.*

@Composable
fun WeatherAlertDialog(
    slot: LessonSlotEntity,
    enrolledStudents: List<StudentEntity>,
    onDismiss: () -> Unit,
    onConfirm: (
        isCancelled: Boolean,
        weatherAlert: String,
        cancelReason: String,
        postponedTo: String,
        broadcastWhatsApp: Boolean
    ) -> Unit
) {
    var isCancelled by remember { mutableStateOf(slot.isCancelled) }
    var selectedPreset by remember {
        mutableStateOf(
            if (slot.cancelReason.isNotBlank()) slot.cancelReason
            else if (slot.weatherAlert.isNotBlank()) slot.weatherAlert
            else "💨 Vent fort & Rafales (> 25 km/h)"
        )
    }
    var customReason by remember { mutableStateOf("") }
    var postponedTo by remember { mutableStateOf(slot.postponedTo) }
    var broadcastWhatsApp by remember { mutableStateOf(true) }

    val presets = listOf(
        "💨 Vent fort & Rafales (> 25 km/h)",
        "⛈️ Pluie, Risque d'orages & Averses",
        "🌫️ Brouillard marin & Plafond bas",
        "🔥 Turbulences thermiques & Gradient fort",
        "🌬️ Vent de travers & Aérologie instable",
        "🛠️ Maintenance matériel / Paramoteur"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚠️", fontSize = 22.sp)
                Text(
                    "Gestion Météo & Aérologie",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = HighDensityHeaderTitle
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Créneau : ${slot.title} (${slot.dateIso} à ${slot.startTime})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue
                )

                // Decision Action: Cancel or Just Alert
                Text("Statut du créneau :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (!isCancelled) Color(0xFFFEF3C7) else HighDensityNavBar,
                        border = BorderStroke(1.dp, if (!isCancelled) AmberAccent else BorderOutline),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isCancelled = false }
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("⚠️ Alerte Météo", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AmberAccent)
                            Text("Maintenu sous réserve", fontSize = 9.sp, color = SecondaryText)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCancelled) Color(0xFFFEE2E2) else HighDensityNavBar,
                        border = BorderStroke(1.dp, if (isCancelled) Color(0xFFDC2626) else BorderOutline),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isCancelled = true }
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🚫 Annuler Séance", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFDC2626))
                            Text("Aérologie dangereuse", fontSize = 9.sp, color = SecondaryText)
                        }
                    }
                }

                // Presets list
                Text("Motif aérologique :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    presets.forEach { preset ->
                        val isSelected = selectedPreset == preset
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) PrimaryBlueContainer else HighDensityNavBar,
                            border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else BorderOutline.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPreset = preset }
                        ) {
                            Text(
                                text = preset,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) PrimaryBlue else HighDensityHeaderTitle,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customReason,
                    onValueChange = { customReason = it },
                    label = { Text("Autre motif / Précisions aérologie") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isCancelled) {
                    OutlinedTextField(
                        value = postponedTo,
                        onValueChange = { postponedTo = it },
                        label = { Text("Date de report suggérée (optionnel)") },
                        placeholder = { Text("ex. Demain matin 07h00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (enrolledStudents.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "👥 ${enrolledStudents.size} élève(s) inscrit(s) concerné(s) :",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenSuccess
                            )
                            Text(
                                enrolledStudents.joinToString(", ") { "${it.fullName} (${it.phone})" },
                                fontSize = 10.sp,
                                color = HighDensityHeaderTitle
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = broadcastWhatsApp,
                                    onCheckedChange = { broadcastWhatsApp = it },
                                    colors = CheckboxDefaults.colors(checkedColor = GreenSuccess)
                                )
                                Text("Ouvrir WhatsApp pour notifier les élèves", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalReason = if (customReason.isNotBlank()) customReason else selectedPreset
                    val weatherAlert = if (!isCancelled) finalReason else ""
                    val cancelReason = if (isCancelled) finalReason else ""
                    onConfirm(isCancelled, weatherAlert, cancelReason, postponedTo, broadcastWhatsApp)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCancelled) Color(0xFFDC2626) else AmberAccent
                )
            ) {
                Text(if (isCancelled) "Valider l'annulation" else "Activer l'alerte")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (slot.isCancelled || slot.hasWeatherAlert) {
                    TextButton(
                        onClick = {
                            // Clear alert and restore slot
                            onConfirm(false, "", "", "", false)
                        }
                    ) {
                        Text("Rétablir / Lever alerte", color = GreenSuccess)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Fermer")
                }
            }
        },
        containerColor = HighDensitySurface,
        shape = RoundedCornerShape(16.dp)
    )
}
