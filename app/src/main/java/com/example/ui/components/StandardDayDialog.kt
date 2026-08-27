package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StandardDayConfig
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun StandardDayDialog(
    initialDateIso: String,
    initialConfig: StandardDayConfig = StandardDayConfig(),
    onDismiss: () -> Unit,
    onConfirm: (dateIso: String, config: StandardDayConfig, saveAsDefault: Boolean) -> Unit
) {
    var dateIso by remember { mutableStateOf(initialDateIso) }

    // Sunrise / Sunset hours
    var sunriseHour by remember { mutableIntStateOf(initialConfig.sunriseHour) }
    var sunriseMinute by remember { mutableIntStateOf(initialConfig.sunriseMinute) }
    var sunsetHour by remember { mutableIntStateOf(initialConfig.sunsetHour) }
    var sunsetMinute by remember { mutableIntStateOf(initialConfig.sunsetMinute) }

    var location by remember { mutableStateOf(initialConfig.location) }

    // Customizable capacities (User can modify Vol & Gonflage slots)
    var morningVolCap by remember { mutableIntStateOf(initialConfig.morningVolCapacity) }
    var morningGonflageCap by remember { mutableIntStateOf(initialConfig.morningGonflageCapacity) }
    var eveningGonflageCap by remember { mutableIntStateOf(initialConfig.eveningGonflageCapacity) }
    var eveningVolCap by remember { mutableIntStateOf(initialConfig.eveningVolCapacity) }

    var saveAsDefault by remember { mutableStateOf(false) }

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
                    Text("Génération automatique & Paramètres des 4 créneaux", fontSize = 11.sp, color = SecondaryText)
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

                // Custom Capacities Section
                Text(
                    text = "Capacité des créneaux (Nombre de places) :",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighDensityHeaderTitle
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HighDensityBg, RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CapacityStepperRow(
                        emoji = "✈️",
                        title = "1. Matin Vol",
                        hours = "$sunriseStartStr - $sunrisePlus2Str",
                        value = morningVolCap,
                        onValueChange = { morningVolCap = it.coerceIn(1, 20) }
                    )

                    CapacityStepperRow(
                        emoji = "🪂",
                        title = "2. Matin Gonflage",
                        hours = "$sunrisePlus2Str - $sunrisePlus4Str",
                        value = morningGonflageCap,
                        onValueChange = { morningGonflageCap = it.coerceIn(1, 20) }
                    )

                    CapacityStepperRow(
                        emoji = "🪂",
                        title = "3. Soir Gonflage",
                        hours = "$sunsetMinus4Str - $sunsetMinus2Str",
                        value = eveningGonflageCap,
                        onValueChange = { eveningGonflageCap = it.coerceIn(1, 20) }
                    )

                    CapacityStepperRow(
                        emoji = "✈️",
                        title = "4. Soir Vol",
                        hours = "$sunsetMinus2Str - $sunsetEndStr",
                        value = eveningVolCap,
                        onValueChange = { eveningVolCap = it.coerceIn(1, 20) }
                    )
                }

                // Option to save these settings as default
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { saveAsDefault = !saveAsDefault },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = saveAsDefault,
                        onCheckedChange = { saveAsDefault = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Enregistrer ces capacités comme configuration par défaut",
                        fontSize = 11.sp,
                        color = HighDensityHeaderTitle
                    )
                }
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
                    onConfirm(dateIso, config, saveAsDefault)
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
private fun CapacityStepperRow(
    emoji: String,
    title: String,
    hours: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HighDensitySurface, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(emoji, fontSize = 14.sp)
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = HighDensityHeaderTitle)
                Text(hours, fontSize = 10.sp, color = SecondaryText)
            }
        }

        // Stepper: [-] [ 4 places ] [+]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilledIconButton(
                onClick = { onValueChange(value - 1) },
                enabled = value > 1,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = HighDensityNavBar)
            ) {
                Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = HighDensityBg,
                border = BorderStroke(1.dp, BorderOutline.copy(alpha = 0.5f)),
                modifier = Modifier.widthIn(min = 60.dp)
            ) {
                Text(
                    text = "$value place${if (value > 1) "s" else ""}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = PrimaryBlue,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            FilledIconButton(
                onClick = { onValueChange(value + 1) },
                enabled = value < 20,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = HighDensityNavBar)
            ) {
                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
            }
        }
    }
}
