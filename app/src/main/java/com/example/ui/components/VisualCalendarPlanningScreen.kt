package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.example.data.model.PlanningLessonType
import com.example.data.model.SlotWithBookings
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class PlanningViewMode {
    ANNUEL,
    TRIMESTRE,
    MOIS
}

enum class DayColorStatus {
    WHITE_NO_SLOT, // Blanc / Gris neutre très clair : Pas de créneau proposé
    GREEN_AVAILABLE, // Vert : Au moins un créneau avec des places disponibles
    RED_FULL // Rouge : Créneau(x) présent(s) mais TOUS complets
}

@Composable
fun VisualCalendarPlanningScreen(
    slots: List<SlotWithBookings>,
    onSelectDay: (String) -> Unit, // YYYY-MM-DD
    onOpenAddSlotForDate: (String) -> Unit,
    onOpenWhatsAppShare: () -> Unit
) {
    var viewMode by remember { mutableStateOf(PlanningViewMode.MOIS) }
    var selectedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedQuarter by remember { mutableIntStateOf((Calendar.getInstance().get(Calendar.MONTH) / 3) + 1) } // 1..4
    var selectedMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) } // 0..11

    // Precompute map of dateIso -> list of slots
    val slotsByDate = remember(slots) {
        slots.groupBy { it.slot.dateIso }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HighDensityBg)
    ) {
        // Top Toolbar: View Switcher (Annuel / Trimestre / Mois) & WhatsApp quick share
        Surface(
            color = HighDensitySurface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // View Mode Segmented Controls
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = HighDensityNavBar,
                        border = BorderStroke(1.dp, BorderOutline.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            PlanningViewMode.entries.forEach { mode ->
                                val isSelected = viewMode == mode
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (isSelected) PrimaryBlue else Color.Transparent,
                                    modifier = Modifier.clickable { viewMode = mode }
                                ) {
                                    Text(
                                        text = when (mode) {
                                            PlanningViewMode.ANNUEL -> "Annuel"
                                            PlanningViewMode.TRIMESTRE -> "Trimestre"
                                            PlanningViewMode.MOIS -> "Mois"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else SecondaryText,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // WhatsApp Action Button
                    Button(
                        onClick = onOpenWhatsAppShare,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("💬", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Controls (Prev/Next buttons for Month, Quarter or Year)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            when (viewMode) {
                                PlanningViewMode.ANNUEL -> selectedYear--
                                PlanningViewMode.TRIMESTRE -> {
                                    if (selectedQuarter > 1) selectedQuarter--
                                    else {
                                        selectedQuarter = 4
                                        selectedYear--
                                    }
                                }
                                PlanningViewMode.MOIS -> {
                                    if (selectedMonth > 0) selectedMonth--
                                    else {
                                        selectedMonth = 11
                                        selectedYear--
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Précédent")
                    }

                    val titleText = when (viewMode) {
                        PlanningViewMode.ANNUEL -> "Année $selectedYear"
                        PlanningViewMode.TRIMESTRE -> "Trimestre T$selectedQuarter $selectedYear (${getQuarterMonthsLabel(selectedQuarter)})"
                        PlanningViewMode.MOIS -> "${getMonthName(selectedMonth).replaceFirstChar { it.uppercase() }} $selectedYear"
                    }

                    Text(
                        text = titleText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighDensityHeaderTitle
                    )

                    IconButton(
                        onClick = {
                            when (viewMode) {
                                PlanningViewMode.ANNUEL -> selectedYear++
                                PlanningViewMode.TRIMESTRE -> {
                                    if (selectedQuarter < 4) selectedQuarter++
                                    else {
                                        selectedQuarter = 1
                                        selectedYear++
                                    }
                                }
                                PlanningViewMode.MOIS -> {
                                    if (selectedMonth < 11) selectedMonth++
                                    else {
                                        selectedMonth = 0
                                        selectedYear++
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Suivant")
                    }
                }

                // Legend Indicator (Code Couleur)
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
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendItem(color = GreenSuccess, label = "Vert : Disponible")
                        LegendItem(color = RedAlertText, label = "Rouge : Complet")
                        LegendItem(color = BorderOutline, label = "Blanc : Pas de créneau", isBorderOnly = true)
                    }
                }
            }
        }

        Divider(color = BorderOutline.copy(alpha = 0.4f))

        // Main Content based on View Mode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            when (viewMode) {
                PlanningViewMode.MOIS -> MonthView(
                    year = selectedYear,
                    month = selectedMonth,
                    slotsByDate = slotsByDate,
                    onSelectDay = onSelectDay,
                    onOpenAddSlotForDate = onOpenAddSlotForDate
                )
                PlanningViewMode.TRIMESTRE -> QuarterView(
                    year = selectedYear,
                    quarter = selectedQuarter,
                    slotsByDate = slotsByDate,
                    onSelectMonth = { m ->
                        selectedMonth = m
                        viewMode = PlanningViewMode.MOIS
                    },
                    onSelectDay = onSelectDay
                )
                PlanningViewMode.ANNUEL -> AnnualView(
                    year = selectedYear,
                    slotsByDate = slotsByDate,
                    onSelectMonth = { m ->
                        selectedMonth = m
                        viewMode = PlanningViewMode.MOIS
                    }
                )
            }
        }
    }
}

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
            modifier = Modifier.size(10.dp)
        ) {}
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = SecondaryText)
    }
}

@Composable
fun MonthView(
    year: Int,
    month: Int,
    slotsByDate: Map<String, List<SlotWithBookings>>,
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
    // Convert to French week (Monday = 0 ... Sunday = 6)
    val startOffset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2

    val weekDays = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
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
                        val status = getDayStatus(daySlots)

                        MonthDayCell(
                            dayNumber = dayNum,
                            dateIso = dateIso,
                            slots = daySlots,
                            status = status,
                            isToday = isToday,
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onAddSlot: () -> Unit
) {
    val bgColor = when (status) {
        DayColorStatus.WHITE_NO_SLOT -> Color.White
        DayColorStatus.GREEN_AVAILABLE -> Color(0xFFDCFCE7) // Vert clair
        DayColorStatus.RED_FULL -> Color(0xFFFFDAD6) // Rouge clair
    }

    val borderColor = when (status) {
        DayColorStatus.WHITE_NO_SLOT -> if (isToday) PrimaryBlue else BorderOutline.copy(alpha = 0.6f)
        DayColorStatus.GREEN_AVAILABLE -> GreenSuccess
        DayColorStatus.RED_FULL -> RedAlertText
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (isToday) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .aspectRatio(0.85f)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Day number + status dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$dayNumber",
                    fontSize = 12.sp,
                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (isToday) PrimaryBlue else HighDensityHeaderTitle
                )

                when (status) {
                    DayColorStatus.GREEN_AVAILABLE -> {
                        Surface(
                            shape = CircleShape,
                            color = GreenSuccess,
                            modifier = Modifier.size(7.dp)
                        ) {}
                    }
                    DayColorStatus.RED_FULL -> {
                        Surface(
                            shape = CircleShape,
                            color = RedAlertText,
                            modifier = Modifier.size(7.dp)
                        ) {}
                    }
                    DayColorStatus.WHITE_NO_SLOT -> {}
                }
            }

            // Slot badges summary (Gonflage, Vol, Perf)
            if (slots.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    slots.take(2).forEach { item ->
                        val type = PlanningLessonType.fromCode(item.slot.lessonType)
                        val total = item.slot.maxCapacity
                        val dispo = item.availablePlaces

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (item.isFull) RedAlertText.copy(alpha = 0.15f) else GreenSuccess.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${type.emoji} ${type.label.take(4)}",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (item.isFull) "0" else "$dispo",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (item.isFull) RedAlertText else GreenSuccess
                                )
                            }
                        }
                    }
                    if (slots.size > 2) {
                        Text(
                            text = "+${slots.size - 2}",
                            fontSize = 8.sp,
                            color = SecondaryText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                // Empty / add prompt
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("-", fontSize = 11.sp, color = BorderOutline)
                }
            }
        }
    }
}

@Composable
fun QuarterView(
    year: Int,
    quarter: Int,
    slotsByDate: Map<String, List<SlotWithBookings>>,
    onSelectMonth: (Int) -> Unit,
    onSelectDay: (String) -> Unit
) {
    val months = when (quarter) {
        1 -> listOf(0, 1, 2) // Janvier, Février, Mars
        2 -> listOf(3, 4, 5) // Avril, Mai, Juin
        3 -> listOf(6, 7, 8) // Juillet, Août, Septembre
        else -> listOf(9, 10, 11) // Octobre, Novembre, Décembre
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        months.forEach { monthIndex ->
            MiniMonthCard(
                year = year,
                month = monthIndex,
                slotsByDate = slotsByDate,
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
    onSelectMonth: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        items(12) { monthIndex ->
            MiniMonthCard(
                year = year,
                month = monthIndex,
                slotsByDate = slotsByDate,
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

    // Stats for this month
    var greenDays = 0
    var redDays = 0
    var whiteDays = 0

    for (d in 1..daysInMonth) {
        val dateIso = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, d)
        val s = slotsByDate[dateIso].orEmpty()
        when (getDayStatus(s)) {
            DayColorStatus.GREEN_AVAILABLE -> greenDays++
            DayColorStatus.RED_FULL -> redDays++
            DayColorStatus.WHITE_NO_SLOT -> whiteDays++
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
            // Header
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

                // Mini indicators
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
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

            // Mini Week header
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

            // Mini Calendar matrix
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
                            val status = getDayStatus(daySlots)

                            val cellColor = when (status) {
                                DayColorStatus.GREEN_AVAILABLE -> GreenSuccess
                                DayColorStatus.RED_FULL -> RedAlertText
                                DayColorStatus.WHITE_NO_SLOT -> Color.White
                            }

                            val cellBorder = when (status) {
                                DayColorStatus.WHITE_NO_SLOT -> BorderStroke(0.5.dp, BorderOutline.copy(alpha = 0.4f))
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
                                        text = "$dayNum",
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (status == DayColorStatus.WHITE_NO_SLOT) HighDensityHeaderTitle else Color.White
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

// Utility: Determine color state for a given day
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
