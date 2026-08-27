package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.data.model.StandardDayConfig
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun StandardDayDialog(
    initialDateIso: String,
    onDismiss: () -> Unit,
    onConfirm: (dateIso: String, config: StandardDayConfig) -> Unit
) {
    var dateIso by remember { mutableStateOf(initialDateIso) }

    // Sunrise / Sunset hours
    var sunriseHour by remember { mutableIntStateOf(6) }
    var sunriseMinute by remember { mutableIntStateOf(30) }
    var sunsetHour by remember { mutableIntStateOf(21) }
    var sunsetMinute by remember { mutableIntStateOf(0) }

    var location by remember { mutableStateOf("Terrain de décollage") }
    var morningVolCap by remember { mutableIntStateOf(2) }
    var morningGonflageCap by remember { mutableIntStateOf(4) }
    var eveningGonflageCap by remember { mutableIntStateOf(4) }
    var eveningVolCap by remember { mutableIntStateOf(2) }

    // Calculated slot preview strings
    val sunriseStartStr = String.format(Locale.US, "%02d:%02d", sunriseHour, sunriseMinute)
    val sunrisePlus2Str = String.format(Locale.US, "%02d:%02d", (sunriseHour + 2).coerceAtMost(23), sunriseMinute)
    val sunrisePlus4Str = String.format(Locale.US, "%02d:%02d", (sunriseHour + 4).coerceAtMost(23), sunriseMinute)

    val sunsetMinus4Str = String.format(Locale.US, "%02d:%02d", (sunsetHour - 4).coerceAtLeast(0), sunsetMinute)
    val sunsetMinus2Str = String.format(Locale.US, "%02d:%02d", (sunsetHour - 2).coerceAtLeast(0), sunsetMinute)
    val sunsetEndStr = String.format(Locale.US, "%02d:%02d", sunsetHour.coerceAtMost(23), sunsetMinute)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⚡", fontSize = 22.sp)
                Column {
                    Text("Créer une Journée Type", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Génération automatique des 4 créneaux", fontSize = 11.sp, color = SecondaryText)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date input
                OutlinedTextField(
                    value = dateIso,
                    onValueChange = { dateIso = it },
                    label = { Text("Date (AAAA-MM-JJ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Sun presets chips
                Text("Saison & Heures du Soleil :", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = {
                            sunriseHour = 6; sunriseMinute = 30
                            sunsetHour = 21; sunsetMinute = 0
                        },
                        label = { Text("☀️ Été (6h30-21h)", fontSize = 10.sp) }
                    )
                    AssistChip(
                        onClick = {
                            sunriseHour = 7; sunriseMinute = 30
                            sunsetHour = 19; sunsetMinute = 30
                        },
                        label = { Text("🍂 Mi-saison (7h30-19h30)", fontSize = 10.sp) }
                    )
                    AssistChip(
                        onClick = {
                            sunriseHour = 8; sunriseMinute = 30
                            sunsetHour = 17; sunsetMinute = 30
                        },
                        label = { Text("❄️ Hiver (8h30-17h30)", fontSize = 10.sp) }
                    )
                }

                // Adjust Sunrise / Sunset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sunriseStartStr,
                        onValueChange = { text ->
                            val parts = text.split(":")
                            if (parts.size == 2) {
                                parts[0].toIntOrNull()?.let { sunriseHour = it.coerceIn(0, 23) }
                                parts[1].toIntOrNull()?.let { sunriseMinute = it.coerceIn(0, 59) }
                            }
                        },
                        label = { Text("🌅 Lever Soleil") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = sunsetEndStr,
                        onValueChange = { text ->
                            val parts = text.split(":")
                            if (parts.size == 2) {
                                parts[0].toIntOrNull()?.let { sunsetHour = it.coerceIn(0, 23) }
                                parts[1].toIntOrNull()?.let { sunsetMinute = it.coerceIn(0, 59) }
                            }
                        },
                        label = { Text("🌇 Coucher Soleil") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Location
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Lieu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Divider(color = BorderOutline.copy(alpha = 0.5f))

                // Preview of the 4 slots
                Text("Aperçu des 4 créneaux générés :", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)

                // 1. Matin Vol
                SlotPreviewItem(
                    emoji = "✈️",
                    type = "VOL",
                    hours = "$sunriseStartStr - $sunrisePlus2Str",
                    description = "Lever du soleil à +2h",
                    capacity = morningVolCap,
                    badgeColor = Color(0xFFDCFCE7)
                )

                // 2. Matin Gonflage
                SlotPreviewItem(
                    emoji = "🪂",
                    type = "GONFLAGE",
                    hours = "$sunrisePlus2Str - $sunrisePlus4Str",
                    description = "+2h à +4h après lever",
                    capacity = morningGonflageCap,
                    badgeColor = Color(0xFFE0F2FE)
                )

                // 3. Soir Gonflage
                SlotPreviewItem(
                    emoji = "🪂",
                    type = "GONFLAGE",
                    hours = "$sunsetMinus4Str - $sunsetMinus2Str",
                    description = "-4h à -2h avant coucher",
                    capacity = eveningGonflageCap,
                    badgeColor = Color(0xFFE0F2FE)
                )

                // 4. Soir Vol
                SlotPreviewItem(
                    emoji = "✈️",
                    type = "VOL",
                    hours = "$sunsetMinus2Str - $sunsetEndStr",
                    description = "-2h jusqu'au coucher du soleil",
                    capacity = eveningVolCap,
                    badgeColor = Color(0xFFDCFCE7)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val config = StandardDayConfig(
                        sunriseHour = sunriseHour,
                        sunriseMinute = sunriseMinute,
                        sunsetHour = sunsetHour,
                        sunsetMinute = sunsetMinute,
                        morningVolCapacity = morningVolCap,
                        morningGonflageCapacity = morningGonflageCap,
                        eveningGonflageCapacity = eveningGonflageCap,
                        eveningVolCapacity = eveningVolCap,
                        location = location.ifBlank { "Terrain de décollage" }
                    )
                    onConfirm(dateIso, config)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Générer la Journée Type (4 créneaux)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = HighDensitySurface
    )
}

@Composable
private fun SlotPreviewItem(
    emoji: String,
    type: String,
    hours: String,
    description: String,
    capacity: Int,
    badgeColor: Color
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HighDensityBg),
        border = BorderStroke(1.dp, BorderOutline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(emoji, fontSize = 14.sp)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(hours, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = HighDensityHeaderTitle)
                        Surface(shape = RoundedCornerShape(4.dp), color = badgeColor) {
                            Text(type, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    Text(description, fontSize = 10.sp, color = SecondaryText)
                }
            }

            Surface(shape = RoundedCornerShape(6.dp), color = HighDensitySurface) {
                Text("$capacity places", fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}
