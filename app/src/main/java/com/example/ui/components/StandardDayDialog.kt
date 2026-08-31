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
    initialDateIso: String = "",
    selectedDates: List<String> = emptyList(),
    initialConfig: StandardDayConfig = StandardDayConfig(),
    onDismiss: () -> Unit,
    onConfirm: (dateIso: String, config: StandardDayConfig, saveAsDefault: Boolean) -> Unit,
    onConfirmMultiple: ((dates: List<String>, config: StandardDayConfig, saveAsDefault: Boolean) -> Unit)? = null
) {
    val datesList = remember(initialDateIso, selectedDates) {
        if (selectedDates.isNotEmpty()) selectedDates.sorted() else if (initialDateIso.isNotBlank()) listOf(initialDateIso) else listOf(java.text.SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(java.util.Date()))
    }
    val isMultiDate = datesList.size > 1

    var dateIso by remember { mutableStateOf(datesList.firstOrNull() ?: initialDateIso) }

    // Multi-date first and last solar ephemeris calculation
    val firstDate = datesList.firstOrNull() ?: dateIso
    val lastDate = datesList.lastOrNull() ?: dateIso

    val firstSunTimes = remember(firstDate) { SunCalculator.calculateSunTimes(firstDate) }
    val lastSunTimes = remember(lastDate) { SunCalculator.calculateSunTimes(lastDate) }

    // Astronomical calculation toggle (True by default to calculate exact sunrise/sunset per day)
    var useAstronomicalSunTimes by remember { mutableStateOf(initialConfig.useAstronomicalSunTimes) }

    // Sunrise / Sunset hours (used when astronomical calculation is disabled for manual override)
    var sunriseHour by remember { mutableIntStateOf(firstSunTimes.sunriseHour) }
    var sunriseMinute by remember { mutableIntStateOf(firstSunTimes.sunriseMinute) }
    var sunsetHour by remember { mutableIntStateOf(firstSunTimes.sunsetHour) }
    var sunsetMinute by remember { mutableIntStateOf(firstSunTimes.sunsetMinute) }

    // Whenever date changes in single date mode, automatically update sunrise / sunset
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

    // Calculated slot preview strings for display
    val previewSunTimes = if (isMultiDate) firstSunTimes else SunCalculator.calculateSunTimes(dateIso)
    val sunriseStartStr = if (useAstronomicalSunTimes) previewSunTimes.sunriseStr else String.format(Locale.US, "%02d:%02d", sunriseHour, sunriseMinute)
    val sunrisePlus1Str = if (useAstronomicalSunTimes) previewSunTimes.morningGonflageStart else String.format(Locale.US, "%02d:%02d", (sunriseHour + 1).coerceAtMost(23), sunriseMinute)
    val sunrisePlus2Str = if (useAstronomicalSunTimes) previewSunTimes.morningVolEnd else String.format(Locale.US, "%02d:%02d", (sunriseHour + 2).coerceAtMost(23), sunriseMinute)
    val sunrisePlus3Str = if (useAstronomicalSunTimes) previewSunTimes.morningGonflageEnd else String.format(Locale.US, "%02d:%02d", (sunriseHour + 3).coerceAtMost(23), sunriseMinute)

    val sunsetMinus3Str = if (useAstronomicalSunTimes) previewSunTimes.eveningGonflageStart else String.format(Locale.US, "%02d:%02d", (sunsetHour - 3).coerceAtLeast(0), sunsetMinute)
    val sunsetMinus2Str = if (useAstronomicalSunTimes) previewSunTimes.eveningVolStart else String.format(Locale.US, "%02d:%02d", (sunsetHour - 2).coerceAtLeast(0), sunsetMinute)
    val sunsetMinus1Str = if (useAstronomicalSunTimes) previewSunTimes.eveningGonflageEnd else String.format(Locale.US, "%02d:%02d", (sunsetHour - 1).coerceAtLeast(0), sunsetMinute)
    val sunsetEndStr = if (useAstronomicalSunTimes) previewSunTimes.sunsetStr else String.format(Locale.US, "%02d:%02d", sunsetHour.coerceAtMost(23), sunsetMinute)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⚡", fontSize = 22.sp)
                Column {
                    Text(
                        if (isMultiDate) "Journée Type (${datesList.size} jours)" else "Créer une Journée Type",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        if (useAstronomicalSunTimes) "Éphémérides auto par jour • Plouharnel (56)" else "Horaires fixes manuels",
                        fontSize = 11.sp,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.SemiBold
                    )
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
                if (isMultiDate) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = HighDensityNavBar,
                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("📅", fontSize = 14.sp)
                                Text(
                                    "${datesList.size} dates sélectionnées :",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = HighDensityHeaderTitle
                                )
                            }
                            Text(
                                datesList.joinToString(", "),
                                fontSize = 11.sp,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 14.sp
                            )
                        }
                    }
                } else {
                    // Date input
                    OutlinedTextField(
                        value = dateIso,
                        onValueChange = { dateIso = it },
                        label = { Text("Date de la séance (AAAA-MM-JJ)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Solar Ephemeris Mode Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (useAstronomicalSunTimes) PrimaryBlueContainer.copy(alpha = 0.6f) else HighDensityBg,
                    border = BorderStroke(1.dp, if (useAstronomicalSunTimes) PrimaryBlue.copy(alpha = 0.5f) else BorderOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { useAstronomicalSunTimes = !useAstronomicalSunTimes },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (useAstronomicalSunTimes) "☀️" else "🕒", fontSize = 16.sp)
                                Column {
                                    Text(
                                        "Calcul solaire automatique (Plouharnel)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (useAstronomicalSunTimes) PrimaryBlueDark else HighDensityHeaderTitle
                                    )
                                    Text(
                                        if (useAstronomicalSunTimes) "Adapte automatiquement les créneaux au lever/coucher de chaque date" else "Horaires fixes identiques pour toutes les dates",
                                        fontSize = 10.sp,
                                        color = SecondaryText,
                                        lineHeight = 12.sp
                                    )
                                }
                            }
                            Switch(
                                checked = useAstronomicalSunTimes,
                                onCheckedChange = { useAstronomicalSunTimes = it }
                            )
                        }

                        if (useAstronomicalSunTimes) {
                            Divider(color = PrimaryBlue.copy(alpha = 0.2f))

                            if (isMultiDate) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Évolution solaire sur la période :", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlueDark)
                                    
                                    // First date preview
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("• $firstDate (Début) :", fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = HighDensityHeaderTitle)
                                        Text("🌅 ${firstSunTimes.sunriseStr}  🌇 ${firstSunTimes.sunsetStr}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                    }

                                    // Last date preview
                                    if (firstDate != lastDate) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("• $lastDate (Fin) :", fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = HighDensityHeaderTitle)
                                            Text("🌅 ${lastSunTimes.sunriseStr}  🌇 ${lastSunTimes.sunsetStr}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                        }
                                    }

                                    Text(
                                        "✨ Chaque jour aura ses créneaux calés sur ses propres heures de lever et coucher.",
                                        fontSize = 9.5.sp,
                                        color = Color(0xFF0D9488),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🌅 Lever : ${previewSunTimes.sunriseStr}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HighDensityHeaderTitle
                                    )
                                    Text("•", color = SecondaryText)
                                    Text(
                                        "🌇 Coucher : ${previewSunTimes.sunsetStr}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HighDensityHeaderTitle
                                    )
                                }
                            }
                        }
                    }
                }

                // If manual mode: show manual time inputs
                if (!useAstronomicalSunTimes) {
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
                            label = { Text("🌅 Heure Lever") },
                            placeholder = { Text("07:00") },
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
                            label = { Text("🌇 Heure Coucher") },
                            placeholder = { Text("20:30") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
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

                // Custom Capacities Section with Rules
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Règles des 4 créneaux & Capacités :",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighDensityHeaderTitle
                    )
                    if (useAstronomicalSunTimes) {
                        Text(
                            text = "Les horaires affichés ci-dessous sont à titre indicatif pour le 1er jour ($firstDate). Chaque date suivante appliquera ces mêmes règles solaires relatives.",
                            fontSize = 10.sp,
                            color = SecondaryText,
                            lineHeight = 12.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HighDensityBg, RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CapacityStepperRow(
                        emoji = "✈️",
                        title = "1. Matin Vol (Lever -> +2h)",
                        hours = "$sunriseStartStr - $sunrisePlus2Str",
                        value = morningVolCap,
                        onValueChange = { morningVolCap = it.coerceIn(1, 20) }
                    )

                    CapacityStepperRow(
                        emoji = "🪂",
                        title = "2. Matin Gonflage (+1h à +3h)",
                        hours = "$sunrisePlus1Str - $sunrisePlus3Str",
                        value = morningGonflageCap,
                        onValueChange = { morningGonflageCap = it.coerceIn(1, 20) }
                    )

                    CapacityStepperRow(
                        emoji = "🪂",
                        title = "3. Soir Gonflage (-3h à -1h)",
                        hours = "$sunsetMinus3Str - $sunsetMinus1Str",
                        value = eveningGonflageCap,
                        onValueChange = { eveningGonflageCap = it.coerceIn(1, 20) }
                    )

                    CapacityStepperRow(
                        emoji = "✈️",
                        title = "4. Soir Vol (-2h -> Coucher)",
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
                        useAstronomicalSunTimes = useAstronomicalSunTimes,
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
                    if (isMultiDate && onConfirmMultiple != null) {
                        onConfirmMultiple(datesList, config, saveAsDefault)
                    } else {
                        onConfirm(dateIso, config, saveAsDefault)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (isMultiDate) "Appliquer sur les ${datesList.size} jours" else "Générer la Journée Type (4 créneaux)",
                    fontWeight = FontWeight.Bold
                )
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
