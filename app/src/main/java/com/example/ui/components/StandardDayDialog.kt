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
import com.example.util.SunCalculator
import java.util.Locale

@Composable
fun StandardDayDialog(
    initialDateIso: String,
    initialConfig: StandardDayConfig = StandardDayConfig(),
    onDismiss: () -> Unit,
    onConfirm: (dateIso: String, config: StandardDayConfig, saveAsDefault: Boolean) -> Unit
) {
    var dateIso by remember { mutableStateOf(initialDateIso) }

    // Compute astronomical sunrise / sunset for the initial date at Plouharnel (56)
    val autoSunTimes = remember(dateIso) {
        SunCalculator.calculateSunTimes(dateIso)
    }

    // Sunrise / Sunset hours (initialized with astronomical calculation for Plouharnel)
    var sunriseHour by remember { mutableIntStateOf(autoSunTimes.sunriseHour) }
    var sunriseMinute by remember { mutableIntStateOf(autoSunTimes.sunriseMinute) }
    var sunsetHour by remember { mutableIntStateOf(autoSunTimes.sunsetHour) }
    var sunsetMinute by remember { mutableIntStateOf(autoSunTimes.sunsetMinute) }

    // Whenever date changes, automatically update sunrise / sunset according to Plouharnel ephemeris
    LaunchedEffect(dateIso) {
        val calculated = SunCalculator.calculateSunTimes(dateIso)
        sunriseHour = calculated.sunriseHour
        sunriseMinute = calculated.sunriseMinute
        sunsetHour = calculated.sunsetHour
        sunsetMinute = calculated.sunsetMinute
    }

    var location by remember { mutableStateOf(if (initialConfig.location == "Terrain de décollage") "Plouharnel (56)" else initialConfig.location) }

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
                    Text("Calcul éphémérides auto • Plouharnel (56)", fontSize = 11.sp, color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
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
                    label = { Text("Date de la séance (AAAA-MM-JJ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Ephemeris Auto Calculation Banner (Plouharnel 56)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PrimaryBlueContainer.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("📍", fontSize = 14.sp)
                                Text("Éphémérides Plouharnel (56)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryBlue)
                            }
                            TextButton(
                                onClick = {
                                    val calculated = SunCalculator.calculateSunTimes(dateIso)
                                    sunriseHour = calculated.sunriseHour
                                    sunriseMinute = calculated.sunriseMinute
                                    sunsetHour = calculated.sunsetHour
                                    sunsetMinute = calculated.sunsetMinute
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("🔄 Recalculer", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🌅 Lever : ${autoSunTimes.sunriseStr}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = HighDensityHeaderTitle
                            )
                            Text("•", color = SecondaryText)
                            Text(
                                "🌇 Coucher : ${autoSunTimes.sunsetStr}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = HighDensityHeaderTitle
                            )
                        }
                    }
                }

                // Adjust Sunrise / Sunset fields
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
                    label = { Text("Lieu de rendez-vous") },
                    placeholder = { Text("Plouharnel (56) / Pente école") },
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
                        title = "1. Matin Vol (Lever)",
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
                        title = "4. Soir Vol (Coucher)",
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
                        location = location.ifBlank { "Plouharnel (56)" }
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
