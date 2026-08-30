package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.util.SunCalculator
import java.text.SimpleDateFormat
import java.util.*

enum class PlanningViewMode {
    JOURNEE,
    SEMAINE,
    MOIS,
    TRIMESTRE,
    ANNUEL
}

enum class DayColorStatus {
    WHITE_NO_SLOT,
    GREEN_AVAILABLE,
    RED_FULL
}

@Composable
fun VisualCalendarPlanningScreen(
    slots: List<SlotWithBookings>,
    onSelectDay: (String) -> Unit, // YYYY-MM-DD
    onOpenAddSlotForDate: (String) -> Unit,
    onOpenWhatsAppShare: () -> Unit,
    onOpenEnrollStudent: ((SlotWithBookings) -> Unit)? = null,
    onUnenrollStudent: ((Long, Long) -> Unit)? = null,
    onToggleAttendance: ((Long, Long, Boolean) -> Unit)? = null,
    onEditSlot: ((LessonSlotEntity) -> Unit)? = null,
    onDeleteSlot: ((Long) -> Unit)? = null,
    onOpenStandardDayForDate: ((String) -> Unit)? = null,
    onOpenMultiDayAddSlot: ((List<String>) -> Unit)? = null,
    onOpenMultiDayStandardDay: ((List<String>) -> Unit)? = null,
    onOpenWeatherAlert: ((SlotWithBookings) -> Unit)? = null,
    onAddToCalendar: ((SlotWithBookings) -> Unit)? = null
) {
    var viewMode by remember { mutableStateOf(PlanningViewMode.MOIS) }
    var selectedDates by remember { mutableStateOf(setOf<String>()) }
    var isMultiSelectMode by remember { mutableStateOf(false) }

    val todayCal = Calendar.getInstance()
    var selectedYear by remember { mutableIntStateOf(todayCal.get(Calendar.YEAR)) }
    var selectedQuarter by remember { mutableIntStateOf((todayCal.get(Calendar.MONTH) / 3) + 1) } // 1..4
    var selectedMonth by remember { mutableIntStateOf(todayCal.get(Calendar.MONTH)) } // 0..11

    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
    var selectedDayIso by remember { mutableStateOf(todayIso) }

    // Start date of selected week (Monday)
    var selectedWeekStartCal by remember {
        val c = Calendar.getInstance(Locale.FRANCE).apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        mutableStateOf(c)
    }

    // Precompute map of dateIso -> list of slots
    val slotsByDate = remember(slots) {
        slots.groupBy { it.slot.dateIso }
    }

    val toggleDateSelection: (String) -> Unit = { date ->
        selectedDates = if (selectedDates.contains(date)) {
            selectedDates - date
        } else {
            selectedDates + date
        }
    }

    val selectAllDaysInMonth: (Int, Int) -> Unit = { y, m ->
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthDates = (1..maxDays).map { d ->
            String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
        }
        selectedDates = selectedDates + monthDates
        isMultiSelectMode = true
    }

    val selectWeekendsInMonth: (Int, Int) -> Unit = { y, m ->
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val weekendDates = mutableListOf<String>()
        for (d in 1..maxDays) {
            cal.set(Calendar.DAY_OF_MONTH, d)
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
                weekendDates.add(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
            }
        }
        selectedDates = selectedDates + weekendDates
        isMultiSelectMode = true
    }

    val selectWeekdaysInMonth: (Int, Int) -> Unit = { y, m ->
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val weekdayDates = mutableListOf<String>()
        for (d in 1..maxDays) {
            cal.set(Calendar.DAY_OF_MONTH, d)
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            if (dow != Calendar.SATURDAY && dow != Calendar.SUNDAY) {
                weekdayDates.add(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
            }
        }
        selectedDates = selectedDates + weekdayDates
        isMultiSelectMode = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HighDensityBg)
    ) {
        // Top Toolbar: View Switcher (Journée / Semaine / Mois / Trimestre / Année), Multi-select & WhatsApp
        Surface(
            color = HighDensitySurface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // View Mode Switcher + Multi-Select + WhatsApp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = HighDensityNavBar,
                        border = BorderStroke(1.dp, BorderOutline.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(2.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            PlanningViewMode.entries.forEach { mode ->
                                val isSelected = viewMode == mode
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (isSelected) PrimaryBlue else Color.Transparent,
                                    modifier = Modifier.clickable { viewMode = mode }
                                ) {
                                    Text(
                                        text = when (mode) {
                                            PlanningViewMode.JOURNEE -> "Jour"
                                            PlanningViewMode.SEMAINE -> "Semaine"
                                            PlanningViewMode.MOIS -> "Mois"
                                            PlanningViewMode.TRIMESTRE -> "Trimestre"
                                            PlanningViewMode.ANNUEL -> "Année"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else SecondaryText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Multi-selection Toggle Button
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMultiSelectMode) PrimaryBlue else HighDensityNavBar,
                        border = BorderStroke(1.dp, if (isMultiSelectMode) PrimaryBlue else BorderOutline.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(32.dp)
                            .clickable {
                                isMultiSelectMode = !isMultiSelectMode
                                if (!isMultiSelectMode) selectedDates = emptySet()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (isMultiSelectMode) Icons.Default.Checklist else Icons.Default.ChecklistRtl,
                                contentDescription = "Sélection multiple",
                                tint = if (isMultiSelectMode) Color.White else PrimaryBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                if (selectedDates.isNotEmpty()) "${selectedDates.size}j" else "Sélection",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMultiSelectMode) Color.White else HighDensityHeaderTitle
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // WhatsApp Action Button
                    Button(
                        onClick = onOpenWhatsAppShare,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("💬", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Navigation Controls (Prev/Next buttons depending on mode)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            when (viewMode) {
                                PlanningViewMode.JOURNEE -> {
                                    val cal = parseDateToCalendar(selectedDayIso)
                                    cal.add(Calendar.DAY_OF_YEAR, -1)
                                    selectedDayIso = formatDateFromCalendar(cal)
                                }
                                PlanningViewMode.SEMAINE -> {
                                    val newCal = (selectedWeekStartCal.clone() as Calendar).apply {
                                        add(Calendar.WEEK_OF_YEAR, -1)
                                    }
                                    selectedWeekStartCal = newCal
                                }
                                PlanningViewMode.MOIS -> {
                                    if (selectedMonth > 0) selectedMonth--
                                    else {
                                        selectedMonth = 11
                                        selectedYear--
                                    }
                                }
                                PlanningViewMode.TRIMESTRE -> {
                                    if (selectedQuarter > 1) selectedQuarter--
                                    else {
                                        selectedQuarter = 4
                                        selectedYear--
                                    }
                                }
                                PlanningViewMode.ANNUEL -> selectedYear--
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Précédent")
                    }

                    val titleText = when (viewMode) {
                        PlanningViewMode.JOURNEE -> formatFrenchDayTitle(selectedDayIso)
                        PlanningViewMode.SEMAINE -> formatWeekTitle(selectedWeekStartCal)
                        PlanningViewMode.MOIS -> "${getMonthName(selectedMonth).replaceFirstChar { it.uppercase() }} $selectedYear"
                        PlanningViewMode.TRIMESTRE -> "Trimestre T$selectedQuarter $selectedYear (${getQuarterMonthsLabel(selectedQuarter)})"
                        PlanningViewMode.ANNUEL -> "Année $selectedYear"
                    }

                    Text(
                        text = titleText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighDensityHeaderTitle,
                        textAlign = TextAlign.Center
                    )

                    IconButton(
                        onClick = {
                            when (viewMode) {
                                PlanningViewMode.JOURNEE -> {
                                    val cal = parseDateToCalendar(selectedDayIso)
                                    cal.add(Calendar.DAY_OF_YEAR, 1)
                                    selectedDayIso = formatDateFromCalendar(cal)
                                }
                                PlanningViewMode.SEMAINE -> {
                                    val newCal = (selectedWeekStartCal.clone() as Calendar).apply {
                                        add(Calendar.WEEK_OF_YEAR, 1)
                                    }
                                    selectedWeekStartCal = newCal
                                }
                                PlanningViewMode.MOIS -> {
                                    if (selectedMonth < 11) selectedMonth++
                                    else {
                                        selectedMonth = 0
                                        selectedYear++
                                    }
                                }
                                PlanningViewMode.TRIMESTRE -> {
                                    if (selectedQuarter < 4) selectedQuarter++
                                    else {
                                        selectedQuarter = 1
                                        selectedYear++
                                    }
                                }
                                PlanningViewMode.ANNUEL -> selectedYear++
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Suivant")
                    }
                }

                // Legend / Quick indicator bar
                Surface(
                    color = HighDensityNavBar,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendItem(color = GreenSuccess, label = "🟢 Dispo")
                        LegendItem(color = RedAlertText, label = "🔴 Complet")
                        LegendItem(color = Color(0xFF0284C7), label = "✈️ Vol")
                        LegendItem(color = Color(0xFFD97706), label = "🪁 Gonflage")
                        LegendItem(color = Color(0xFF7C3AED), label = "🎯 Perf")
                    }
                }
            }
        }

        Divider(color = BorderOutline.copy(alpha = 0.4f))

        // Main Content based on View Mode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            when (viewMode) {
                PlanningViewMode.JOURNEE -> {
                    DayOrganizerView(
                        dateIso = selectedDayIso,
                        slots = slotsByDate[selectedDayIso].orEmpty(),
                        onSelectOtherDay = { d -> selectedDayIso = d },
                        onOpenAddSlot = { onOpenAddSlotForDate(selectedDayIso) },
                        onOpenStandardDay = { onOpenStandardDayForDate?.invoke(selectedDayIso) },
                        onOpenEnrollStudent = onOpenEnrollStudent,
                        onUnenrollStudent = onUnenrollStudent,
                        onToggleAttendance = onToggleAttendance,
                        onEditSlot = onEditSlot,
                        onDeleteSlot = onDeleteSlot,
                        onOpenWeatherAlert = onOpenWeatherAlert,
                        onAddToCalendar = onAddToCalendar
                    )
                }

                PlanningViewMode.SEMAINE -> {
                    WeekOrganizerView(
                        weekStartCal = selectedWeekStartCal,
                        slotsByDate = slotsByDate,
                        onSelectDay = { dateIso ->
                            selectedDayIso = dateIso
                            viewMode = PlanningViewMode.JOURNEE
                        },
                        onOpenDayDetail = onSelectDay,
                        onOpenAddSlotForDate = onOpenAddSlotForDate
                    )
                }

                PlanningViewMode.MOIS -> {
                    MonthView(
                        year = selectedYear,
                        month = selectedMonth,
                        slotsByDate = slotsByDate,
                        selectedDates = selectedDates,
                        isMultiSelectMode = isMultiSelectMode,
                        onToggleSelectDay = toggleDateSelection,
                        onSelectAllInMonth = { selectAllDaysInMonth(selectedYear, selectedMonth) },
                        onSelectWeekendsInMonth = { selectWeekendsInMonth(selectedYear, selectedMonth) },
                        onSelectWeekdaysInMonth = { selectWeekdaysInMonth(selectedYear, selectedMonth) },
                        onClearSelection = { selectedDates = emptySet() },
                        onSelectDay = { dateIso ->
                            if (isMultiSelectMode) {
                                toggleDateSelection(dateIso)
                            } else {
                                selectedDayIso = dateIso
                                onSelectDay(dateIso)
                            }
                        },
                        onOpenAddSlotForDate = onOpenAddSlotForDate
                    )
                }

                PlanningViewMode.TRIMESTRE -> {
                    QuarterView(
                        year = selectedYear,
                        quarter = selectedQuarter,
                        slotsByDate = slotsByDate,
                        selectedDates = selectedDates,
                        isMultiSelectMode = isMultiSelectMode,
                        onToggleSelectDay = toggleDateSelection,
                        onSelectAllInMonth = selectAllDaysInMonth,
                        onSelectMonth = { m ->
                            selectedMonth = m
                            viewMode = PlanningViewMode.MOIS
                        },
                        onSelectDay = { dateIso ->
                            if (isMultiSelectMode) {
                                toggleDateSelection(dateIso)
                            } else {
                                onSelectDay(dateIso)
                            }
                        }
                    )
                }

                PlanningViewMode.ANNUEL -> {
                    AnnualView(
                        year = selectedYear,
                        slotsByDate = slotsByDate,
                        selectedDates = selectedDates,
                        isMultiSelectMode = isMultiSelectMode,
                        onToggleSelectDay = toggleDateSelection,
                        onSelectAllInMonth = selectAllDaysInMonth,
                        onSelectMonth = { m ->
                            selectedMonth = m
                            viewMode = PlanningViewMode.MOIS
                        }
                    )
                }
            }

            // Floating Multi-Day Action Bar (Visible when days are selected)
            if (selectedDates.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = HighDensitySurface,
                    shadowElevation = 10.dp,
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.5.dp, PrimaryBlue),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 60.dp, start = 8.dp, end = 8.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                "📅 ${selectedDates.size} j sélectionné${if (selectedDates.size > 1) "s" else ""}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = HighDensityHeaderTitle
                            )
                            Text(
                                "Appliquer en masse :",
                                fontSize = 10.sp,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Journée Type Bulk Action
                            Button(
                                onClick = {
                                    val sortedDates = selectedDates.toList().sorted()
                                    onOpenMultiDayStandardDay?.invoke(sortedDates)
                                        ?: onOpenStandardDayForDate?.invoke(sortedDates.first())
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Journée Type", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Single Slot Bulk Action
                            Button(
                                onClick = {
                                    val sortedDates = selectedDates.toList().sorted()
                                    onOpenMultiDayAddSlot?.invoke(sortedDates)
                                        ?: onOpenAddSlotForDate(sortedDates.first())
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Créneau", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Clear Selection Button
                            IconButton(
                                onClick = {
                                    selectedDates = emptySet()
                                    isMultiSelectMode = false
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Désélectionner", tint = SecondaryText)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 1. ORGANIZER DAY VIEW ("Visuel Journée")
// ----------------------------------------------------
@Composable
fun DayOrganizerView(
    dateIso: String,
    slots: List<SlotWithBookings>,
    onSelectOtherDay: (String) -> Unit,
    onOpenAddSlot: () -> Unit,
    onOpenStandardDay: (() -> Unit)?,
    onOpenEnrollStudent: ((SlotWithBookings) -> Unit)?,
    onUnenrollStudent: ((Long, Long) -> Unit)?,
    onToggleAttendance: ((Long, Long, Boolean) -> Unit)?,
    onEditSlot: ((LessonSlotEntity) -> Unit)?,
    onDeleteSlot: ((Long) -> Unit)?,
    onOpenWeatherAlert: ((SlotWithBookings) -> Unit)? = null,
    onAddToCalendar: ((SlotWithBookings) -> Unit)? = null
) {
    val sunTimes = remember(dateIso) {
        SunCalculator.calculateSunTimes(dateIso)
    }

    val totalEnrolled = slots.sumOf { it.confirmedCount }
    val totalCapacity = slots.sumOf { it.slot.maxCapacity }
    val totalAvailable = slots.sumOf { it.availablePlaces }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp)
    ) {
        // Solar banner & Quick day stats
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = HighDensitySurface,
            border = BorderStroke(1.dp, BorderOutline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📍", fontSize = 12.sp)
                        Text("Plouharnel (56) :", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                        Text("🌅 ${sunTimes.sunriseStr} • 🌇 ${sunTimes.sunsetStr}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                    }

                    // Stats summary
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (totalAvailable > 0) GreenSuccessBg else RedAlertBg
                    ) {
                        Text(
                            text = "$totalEnrolled / $totalCapacity inscrits ($totalAvailable dispo)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalAvailable > 0) GreenSuccess else RedAlertText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onOpenStandardDay != null) {
                        OutlinedButton(
                            onClick = onOpenStandardDay,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFD97706))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Journée Type", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onOpenAddSlot,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nouveau Créneau", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (slots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("☀️", fontSize = 36.sp)
                    Text("Aucun créneau ce jour", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = HighDensityHeaderTitle)
                    Text("Générez une 'Journée Type' ou ajoutez une séance.", fontSize = 12.sp, color = SecondaryText)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(slots, key = { it.slot.id }) { slotItem ->
                    SlotCard(
                        slotItem = slotItem,
                        onOpenEnrollStudent = { onOpenEnrollStudent?.invoke(slotItem) },
                        onUnenrollStudent = { sId, stId -> onUnenrollStudent?.invoke(sId, stId) },
                        onToggleAttendance = { bId, stId, att -> onToggleAttendance?.invoke(bId, stId, att) },
                        onEditSlot = { s -> onEditSlot?.invoke(s) },
                        onDeleteSlot = { sId -> onDeleteSlot?.invoke(sId) },
                        onOpenWeatherAlert = { onOpenWeatherAlert?.invoke(slotItem) },
                        onAddToCalendar = { onAddToCalendar?.invoke(slotItem) }
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// 2. ORGANIZER WEEK VIEW ("Visuel Semaine - Tous les inscrits en un clin d'œil")
// ----------------------------------------------------
@Composable
fun WeekOrganizerView(
    weekStartCal: Calendar,
    slotsByDate: Map<String, List<SlotWithBookings>>,
    onSelectDay: (String) -> Unit,
    onOpenDayDetail: (String) -> Unit,
    onOpenAddSlotForDate: (String) -> Unit
) {
    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE) }
    val dayNameFormat = remember { SimpleDateFormat("EEEE d MMMM", Locale.FRANCE) }
    val shortDayNameFormat = remember { SimpleDateFormat("EEE d", Locale.FRANCE) }
    val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date()) }

    // Generate 7 days for the week (Monday -> Sunday)
    val daysOfWeek = remember(weekStartCal) {
        val list = mutableListOf<String>()
        val cal = weekStartCal.clone() as Calendar
        for (i in 0 until 7) {
            list.add(dayFormat.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        list
    }

    // Weekly statistics
    val allWeekSlots = remember(daysOfWeek, slotsByDate) {
        daysOfWeek.flatMap { slotsByDate[it].orEmpty() }
    }
    val totalWeekEnrolled = allWeekSlots.sumOf { it.confirmedCount }
    val totalWeekSlots = allWeekSlots.size
    val totalWeekDispo = allWeekSlots.sumOf { it.availablePlaces }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp)
    ) {
        // Week Summary Banner
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = HighDensitySurface,
            border = BorderStroke(1.dp, BorderOutline.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🗓️", fontSize = 14.sp)
                    Text(
                        "$totalWeekSlots créneaux prévus",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = HighDensityHeaderTitle
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = PrimaryBlueContainer) {
                        Text(
                            "👥 $totalWeekEnrolled inscrits",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = GreenSuccessBg) {
                        Text(
                            "🟢 $totalWeekDispo dispo",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 7 Days list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(daysOfWeek) { dateIso ->
                val daySlots = slotsByDate[dateIso].orEmpty()
                val isToday = dateIso == todayIso
                val dayCal = parseDateToCalendar(dateIso)
                val dayTitle = dayNameFormat.format(dayCal.time).replaceFirstChar { it.uppercase() }
                val status = getDayStatus(daySlots)

                WeekDayCard(
                    dateIso = dateIso,
                    dayTitle = dayTitle,
                    isToday = isToday,
                    status = status,
                    slots = daySlots,
                    onClickDay = { onOpenDayDetail(dateIso) },
                    onAddSlot = { onOpenAddSlotForDate(dateIso) }
                )
            }
        }
    }
}

@Composable
fun WeekDayCard(
    dateIso: String,
    dayTitle: String,
    isToday: Boolean,
    status: DayColorStatus,
    slots: List<SlotWithBookings>,
    onClickDay: () -> Unit,
    onAddSlot: () -> Unit
) {
    val cardBg = when {
        isToday -> Color(0xFFEFF6FF)
        status == DayColorStatus.GREEN_AVAILABLE -> Color(0xFFF0FDF4)
        status == DayColorStatus.RED_FULL -> Color(0xFFFFF1F2)
        else -> HighDensitySurface
    }

    val borderColor = when {
        isToday -> PrimaryBlue
        status == DayColorStatus.GREEN_AVAILABLE -> GreenSuccess.copy(alpha = 0.5f)
        status == DayColorStatus.RED_FULL -> RedAlertText.copy(alpha = 0.5f)
        else -> BorderOutline.copy(alpha = 0.4f)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(if (isToday) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickDay() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Day Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = when (status) {
                            DayColorStatus.GREEN_AVAILABLE -> GreenSuccess
                            DayColorStatus.RED_FULL -> RedAlertText
                            DayColorStatus.WHITE_NO_SLOT -> Color.White
                        },
                        border = if (status == DayColorStatus.WHITE_NO_SLOT) BorderStroke(1.dp, BorderOutline) else null,
                        modifier = Modifier.size(10.dp)
                    ) {}

                    Text(
                        text = dayTitle,
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isToday) PrimaryBlue else HighDensityHeaderTitle
                    )

                    if (isToday) {
                        Surface(shape = RoundedCornerShape(4.dp), color = PrimaryBlue) {
                            Text("Aujourd'hui", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (slots.isNotEmpty()) {
                        val confirmedTotal = slots.sumOf { it.confirmedCount }
                        val capTotal = slots.sumOf { it.slot.maxCapacity }
                        Text(
                            text = "$confirmedTotal / $capTotal inscrit(s)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SecondaryText
                        )
                    }

                    IconButton(
                        onClick = onAddSlot,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Ajouter créneau", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Slots list with clear registered student names
            if (slots.isEmpty()) {
                Text(
                    text = "Aucun créneau programmé",
                    fontSize = 11.sp,
                    color = SecondaryText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    slots.forEach { item ->
                        val slot = item.slot
                        val type = PlanningLessonType.fromCode(slot.lessonType)
                        val timeFormatted = formatTimeRangeFrench(slot.startTime, slot.endTime)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, type.borderColor.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Slot header line: Time (de 8h à 10h) | VOL / GONFLAGE / PERF | Occupancy
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Time badge
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = PrimaryBlueDark
                                        ) {
                                            Text(
                                                text = timeFormatted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        // Activity Badge: VOL / GONFLAGE / PERF
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = type.containerColor,
                                            border = BorderStroke(1.dp, type.borderColor)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(type.emoji, fontSize = 11.sp)
                                                Text(
                                                    text = type.label.uppercase(),
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 10.sp,
                                                    color = type.primaryColor
                                                )
                                            }
                                        }
                                    }

                                    // Capacity status
                                    if (item.isFull) {
                                        Surface(shape = RoundedCornerShape(6.dp), color = RedAlertBg, border = BorderStroke(0.5.dp, RedAlertText)) {
                                            Text("🔴 COMPLET (${item.confirmedCount}/${slot.maxCapacity})", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = RedAlertText, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    } else {
                                        Surface(shape = RoundedCornerShape(6.dp), color = GreenSuccessBg, border = BorderStroke(0.5.dp, GreenSuccess)) {
                                            Text("🟢 ${item.availablePlaces} dispo (${item.confirmedCount}/${slot.maxCapacity})", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GreenSuccess, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }

                                // Visual Enrolled Student List (Toutes les personnes inscrites en un clin d'œil)
                                if (item.confirmedBookings.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("👤 Inscrits :", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            item.confirmedBookings.forEach { booking ->
                                                val student = booking.student
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = HighDensityNavBar,
                                                    border = BorderStroke(0.5.dp, BorderOutline)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Text(
                                                            text = "${student.firstName} ${student.lastName.firstOrNull()?.let { "$it." } ?: ""}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = HighDensityHeaderTitle
                                                        )
                                                        Text(
                                                            text = "(${student.level})",
                                                            fontSize = 9.sp,
                                                            color = SecondaryText
                                                        )
                                                        if (booking.booking.attended) {
                                                            Text("✓", fontSize = 9.sp, color = GreenSuccess, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        "Aucun élève inscrit pour le moment",
                                        fontSize = 10.sp,
                                        color = SecondaryText,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }

                                if (item.waitingListBookings.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("⏳ Attente :", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                        val waitNames = item.waitingListBookings.joinToString(", ") { "${it.student.firstName} ${it.student.lastName}" }
                                        Text(waitNames, fontSize = 9.sp, color = SecondaryText)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 3. MONTH VIEW ("Visuel Mois")
// ----------------------------------------------------
@Composable
fun LegendItem(color: Color, label: String, isBorderOnly: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isBorderOnly) Color.White else color,
            border = if (isBorderOnly) BorderStroke(1.5.dp, BorderOutline) else null,
            modifier = Modifier.size(8.dp)
        ) {}
        Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Medium, color = SecondaryText)
    }
}

@Composable
fun MonthView(
    year: Int,
    month: Int,
    slotsByDate: Map<String, List<SlotWithBookings>>,
    selectedDates: Set<String> = emptySet(),
    isMultiSelectMode: Boolean = false,
    onToggleSelectDay: ((String) -> Unit)? = null,
    onSelectAllInMonth: (() -> Unit)? = null,
    onSelectWeekendsInMonth: (() -> Unit)? = null,
    onSelectWeekdaysInMonth: (() -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    onSelectDay: (String) -> Unit,
    onOpenAddSlotForDate: (String) -> Unit
) {
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }

    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sunday = 1, Monday = 2
    val startOffset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2

    val weekDays = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = if (selectedDates.isNotEmpty()) 120.dp else 60.dp)
    ) {
        // Multi-Select Quick Action Shortcuts
        if (isMultiSelectMode) {
            Surface(
                color = HighDensityNavBar,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sélection rapide :", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PrimaryBlueContainer,
                        modifier = Modifier.clickable { onSelectAllInMonth?.invoke() }
                    ) {
                        Text("📅 Tout le mois", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = HighDensitySurface,
                        border = BorderStroke(0.5.dp, BorderOutline),
                        modifier = Modifier.clickable { onSelectWeekendsInMonth?.invoke() }
                    ) {
                        Text("🏖️ Week-ends", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = HighDensityHeaderTitle, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = HighDensitySurface,
                        border = BorderStroke(0.5.dp, BorderOutline),
                        modifier = Modifier.clickable { onSelectWeekdaysInMonth?.invoke() }
                    ) {
                        Text("🛫 Semaine (L-V)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = HighDensityHeaderTitle, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }

                    if (selectedDates.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = RedAlertBg,
                            modifier = Modifier.clickable { onClearSelection?.invoke() }
                        ) {
                            Text("✕ Vider (${selectedDates.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RedAlertText, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }

        // Week days header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekDays.forEach { dayName ->
                Text(
                    text = dayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = SecondaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Calendar Grid
        val totalCells = startOffset + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (col in 0 until 7) {
                    val dayNum = (row * 7 + col) - startOffset + 1
                    if (dayNum in 1..daysInMonth) {
                        val dateIso = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayNum)
                        val daySlots = slotsByDate[dateIso].orEmpty()
                        val isToday = dateIso == todayIso
                        val isSelected = selectedDates.contains(dateIso)
                        val status = getDayStatus(daySlots)

                        MonthDayCell(
                            dayNumber = dayNum,
                            dateIso = dateIso,
                            slots = daySlots,
                            status = status,
                            isToday = isToday,
                            isSelected = isSelected,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectDay(dateIso) },
                            onAddSlot = { onOpenAddSlotForDate(dateIso) }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun MonthDayCell(
    dayNumber: Int,
    dateIso: String,
    slots: List<SlotWithBookings>,
    status: DayColorStatus,
    isToday: Boolean,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onAddSlot: () -> Unit
) {
    val bgColor = when {
        isSelected -> PrimaryBlueContainer.copy(alpha = 0.55f)
        status == DayColorStatus.WHITE_NO_SLOT -> Color.White
        status == DayColorStatus.GREEN_AVAILABLE -> Color(0xFFDCFCE7)
        status == DayColorStatus.RED_FULL -> Color(0xFFFFDAD6)
        else -> Color.White
    }

    val borderColor = when {
        isSelected -> PrimaryBlue
        isToday -> PrimaryBlue
        status == DayColorStatus.WHITE_NO_SLOT -> BorderOutline.copy(alpha = 0.6f)
        status == DayColorStatus.GREEN_AVAILABLE -> GreenSuccess
        status == DayColorStatus.RED_FULL -> RedAlertText
        else -> BorderOutline
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (isSelected || isToday) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
        modifier = modifier
            .height(64.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$dayNumber",
                    fontSize = 12.sp,
                    fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (isSelected) PrimaryBlue else if (isToday) PrimaryBlue else HighDensityHeaderTitle
                )

                if (isSelected) {
                    Surface(shape = CircleShape, color = PrimaryBlue, modifier = Modifier.size(12.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✓", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    when (status) {
                        DayColorStatus.GREEN_AVAILABLE -> {
                            Surface(shape = CircleShape, color = GreenSuccess, modifier = Modifier.size(7.dp)) {}
                        }
                        DayColorStatus.RED_FULL -> {
                            Surface(shape = CircleShape, color = RedAlertText, modifier = Modifier.size(7.dp)) {}
                        }
                        DayColorStatus.WHITE_NO_SLOT -> {}
                    }
                }
            }

            // Clean number of enrolled students (replaces cut-off slot text at the bottom)
            if (slots.isNotEmpty()) {
                val totalEnrolled = slots.sumOf { it.confirmedCount }
                val totalCapacity = slots.sumOf { it.slot.maxCapacity }
                val isFull = status == DayColorStatus.RED_FULL || (totalCapacity > 0 && totalEnrolled >= totalCapacity)

                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = if (isFull) Color(0xFFFEE2E2) else if (totalEnrolled > 0) Color(0xFFEFF6FF) else Color(0xFFF0FDF4),
                    border = BorderStroke(
                        0.5.dp,
                        if (isFull) Color(0xFFEF4444) else if (totalEnrolled > 0) PrimaryBlue else GreenSuccess
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 1.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (totalCapacity > 0) "👤 $totalEnrolled/$totalCapacity" else "👤 $totalEnrolled",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isFull) RedAlertText else if (totalEnrolled > 0) PrimaryBlue else GreenSuccess,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// ----------------------------------------------------
// 4. QUARTER & ANNUAL VIEWS
// ----------------------------------------------------
@Composable
fun QuarterView(
    year: Int,
    quarter: Int,
    slotsByDate: Map<String, List<SlotWithBookings>>,
    selectedDates: Set<String> = emptySet(),
    isMultiSelectMode: Boolean = false,
    onToggleSelectDay: ((String) -> Unit)? = null,
    onSelectAllInMonth: ((Int, Int) -> Unit)? = null,
    onSelectMonth: (Int) -> Unit,
    onSelectDay: (String) -> Unit
) {
    val months = when (quarter) {
        1 -> listOf(0, 1, 2)
        2 -> listOf(3, 4, 5)
        3 -> listOf(6, 7, 8)
        else -> listOf(9, 10, 11)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = if (selectedDates.isNotEmpty()) 120.dp else 60.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        months.forEach { monthIndex ->
            MiniMonthCard(
                year = year,
                month = monthIndex,
                slotsByDate = slotsByDate,
                selectedDates = selectedDates,
                isMultiSelectMode = isMultiSelectMode,
                onToggleSelectMonth = { onSelectAllInMonth?.invoke(year, monthIndex) },
                onMonthClick = { onSelectMonth(monthIndex) },
                onDayClick = onSelectDay
            )
        }
    }
}

@Composable
fun AnnualView(
    year: Int,
    slotsByDate: Map<String, List<SlotWithBookings>>,
    selectedDates: Set<String> = emptySet(),
    isMultiSelectMode: Boolean = false,
    onToggleSelectDay: ((String) -> Unit)? = null,
    onSelectAllInMonth: ((Int, Int) -> Unit)? = null,
    onSelectMonth: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = if (selectedDates.isNotEmpty()) 120.dp else 60.dp)
    ) {
        items(12) { monthIndex ->
            MiniMonthCard(
                year = year,
                month = monthIndex,
                slotsByDate = slotsByDate,
                selectedDates = selectedDates,
                isMultiSelectMode = isMultiSelectMode,
                onToggleSelectMonth = { onSelectAllInMonth?.invoke(year, monthIndex) },
                onMonthClick = { onSelectMonth(monthIndex) },
                onDayClick = { onSelectMonth(monthIndex) }
            )
        }
    }
}

@Composable
fun MiniMonthCard(
    year: Int,
    month: Int,
    slotsByDate: Map<String, List<SlotWithBookings>>,
    selectedDates: Set<String> = emptySet(),
    isMultiSelectMode: Boolean = false,
    onToggleSelectMonth: (() -> Unit)? = null,
    onMonthClick: () -> Unit,
    onDayClick: (String) -> Unit
) {
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val startOffset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2

    var greenDays = 0
    var redDays = 0

    for (d in 1..daysInMonth) {
        val dateIso = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, d)
        val s = slotsByDate[dateIso].orEmpty()
        when (getDayStatus(s)) {
            DayColorStatus.GREEN_AVAILABLE -> greenDays++
            DayColorStatus.RED_FULL -> redDays++
            DayColorStatus.WHITE_NO_SLOT -> {}
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
        border = BorderStroke(1.dp, BorderOutline.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMonthClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = getMonthName(month).replaceFirstChar { it.uppercase() },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = HighDensityHeaderTitle
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isMultiSelectMode) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PrimaryBlueContainer,
                            modifier = Modifier.clickable { onToggleSelectMonth?.invoke() }
                        ) {
                            Text("+ Tout", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }

                    if (greenDays > 0) {
                        Surface(shape = RoundedCornerShape(4.dp), color = GreenSuccessBg) {
                            Text("$greenDays dispo", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GreenSuccess, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    if (redDays > 0) {
                        Surface(shape = RoundedCornerShape(4.dp), color = RedAlertBg) {
                            Text("$redDays complet", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = RedAlertText, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach { d ->
                    Text(
                        text = d,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            val totalCells = startOffset + daysInMonth
            val rows = (totalCells + 6) / 7

            for (row in 0 until rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (col in 0 until 7) {
                        val dayNum = (row * 7 + col) - startOffset + 1
                        if (dayNum in 1..daysInMonth) {
                            val dateIso = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayNum)
                            val daySlots = slotsByDate[dateIso].orEmpty()
                            val isSelected = selectedDates.contains(dateIso)
                            val status = getDayStatus(daySlots)

                            val cellColor = when {
                                isSelected -> PrimaryBlue
                                status == DayColorStatus.GREEN_AVAILABLE -> GreenSuccess
                                status == DayColorStatus.RED_FULL -> RedAlertText
                                else -> Color.White
                            }

                            val cellBorder = when {
                                isSelected -> BorderStroke(1.dp, PrimaryBlue)
                                status == DayColorStatus.WHITE_NO_SLOT -> BorderStroke(0.5.dp, BorderOutline.copy(alpha = 0.4f))
                                else -> null
                            }

                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = cellColor,
                                border = cellBorder,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clickable { onDayClick(dateIso) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (isSelected) "✓" else "$dayNum",
                                        fontSize = if (isSelected) 8.sp else 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else if (status == DayColorStatus.WHITE_NO_SLOT) HighDensityHeaderTitle else Color.White
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// UTILITIES
// ----------------------------------------------------
fun getDayStatus(slots: List<SlotWithBookings>): DayColorStatus {
    val activeSlots = slots.filter { !it.slot.isCancelled }
    if (activeSlots.isEmpty()) return DayColorStatus.WHITE_NO_SLOT

    val hasAvailable = activeSlots.any { !it.isFull }
    return if (hasAvailable) DayColorStatus.GREEN_AVAILABLE else DayColorStatus.RED_FULL
}

fun getMonthName(monthIndex: Int): String {
    return when (monthIndex) {
        0 -> "Janvier"
        1 -> "Février"
        2 -> "Mars"
        3 -> "Avril"
        4 -> "Mai"
        5 -> "Juin"
        6 -> "Juillet"
        7 -> "Août"
        8 -> "Septembre"
        9 -> "Octobre"
        10 -> "Novembre"
        11 -> "Décembre"
        else -> ""
    }
}

fun getQuarterMonthsLabel(quarter: Int): String {
    return when (quarter) {
        1 -> "Jan - Fév - Mar"
        2 -> "Avr - Mai - Juin"
        3 -> "Juil - Août - Sep"
        else -> "Oct - Nov - Déc"
    }
}

fun parseDateToCalendar(dateIso: String): Calendar {
    val cal = Calendar.getInstance(Locale.FRANCE)
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
    try {
        sdf.parse(dateIso)?.let { cal.time = it }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return cal
}

fun formatDateFromCalendar(cal: Calendar): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
    return sdf.format(cal.time)
}

fun formatFrenchDayTitle(dateIso: String): String {
    val cal = parseDateToCalendar(dateIso)
    val sdf = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE)
    return sdf.format(cal.time).replaceFirstChar { it.uppercase() }
}

fun formatWeekTitle(weekStartCal: Calendar): String {
    val endCal = (weekStartCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_MONTH, 6)
    }
    val weekNum = weekStartCal.get(Calendar.WEEK_OF_YEAR)
    val startDay = weekStartCal.get(Calendar.DAY_OF_MONTH)
    val endDay = endCal.get(Calendar.DAY_OF_MONTH)
    val endMonth = getMonthName(endCal.get(Calendar.MONTH))
    val year = endCal.get(Calendar.YEAR)

    return "Semaine $weekNum : du $startDay au $endDay $endMonth $year"
}
