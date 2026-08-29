package com.example.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.cloud.SyncStatus
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.PlanningViewModel
import com.example.util.CalendarExportUtils
import com.example.util.PdfExportUtils
import java.text.SimpleDateFormat
import java.util.*

private data class SyncPillStyle(
    val dotColor: Color,
    val bgColor: Color,
    val borderColor: Color,
    val text: String
)

// Date formatting helper for French display
private fun formatDateFrench(dateIso: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
        val formatter = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE)
        val d = parser.parse(dateIso)
        if (d != null) formatter.format(d).replaceFirstChar { it.uppercase() } else dateIso
    } catch (e: Exception) {
        dateIso
    }
}

@Composable
fun StudentPortalScreen(
    slots: List<SlotWithBookings>,
    savedProfile: PlanningViewModel.StudentProfile,
    onSaveProfile: (firstName: String, lastName: String, phone: String, level: String) -> Unit,
    onRegisterSelf: (slotId: Long, firstName: String, lastName: String, phone: String, email: String, level: String) -> Unit,
    onUnenroll: (slotId: Long, studentId: Long) -> Unit,
    allStudents: List<StudentEntity>,
    onVerifyPin: (String) -> Boolean,
    onSwitchToInstructorMode: () -> Unit,
    syncStatus: SyncStatus = SyncStatus.CONNECTED_SYNCED,
    syncStatusMsg: String = "En direct",
    lastSyncTime: String = "",
    schoolCode: String = "PLOUHARNEL",
    onSaveSchoolCode: (String) -> Unit = {},
    syncedSlotsCount: Int = 0,
    syncedStudentsCount: Int = 0,
    syncedBookingsCount: Int = 0,
    onForceSync: () -> Unit = {},
    onShareSchoolCode: () -> Unit = {},
    allProgress: List<StudentProgressEntity> = emptyList(),
    onExportStudentBookletPdf: ((StudentEntity) -> Unit)? = null,
    onExportStudentBookletIcs: ((StudentEntity) -> Unit)? = null
) {
    val context = LocalContext.current

    // Navigation Tab in Student Mode: 0 = Calendrier Visuel (Mois, Trimestre, Année), 1 = Mes Inscriptions & Liste
    var studentTab by remember { mutableIntStateOf(0) }

    // Dialog & Sheet states
    var showProfileModal by remember { mutableStateOf(!savedProfile.isConfigured) }
    var slotToRegister by remember { mutableStateOf<SlotWithBookings?>(null) }
    var selectedDayForDetail by remember { mutableStateOf<String?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showSyncSettingsDialog by remember { mutableStateOf(false) }

    // Visual Calendar sub-mode (Mois, Trimestre, Année)
    var calendarViewMode by remember { mutableStateOf(PlanningViewMode.MOIS) }
    var selectedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedQuarter by remember { mutableIntStateOf((Calendar.getInstance().get(Calendar.MONTH) / 3) + 1) }
    var selectedMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }

    // List filters
    var selectedDateFilter by remember { mutableStateOf<String?>("TOUS") }
    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }

    val dateFilters = listOf(
        "TOUS" to "Tous",
        "TODAY" to "Aujourd'hui",
        "TOMORROW" to "Demain",
        "WEEK" to "Cette Semaine"
    )

    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, 1)
    val tomorrowIso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(cal.time)
    cal.add(Calendar.DAY_OF_YEAR, 6)
    val endOfWeekIso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(cal.time)

    // Match current student entity
    val currentStudentEntity = allStudents.find {
        it.phone.isNotBlank() && it.phone.replace(" ", "") == savedProfile.phone.replace(" ", "")
    } ?: allStudents.find {
        it.firstName.equals(savedProfile.firstName, ignoreCase = true) && it.lastName.equals(savedProfile.lastName, ignoreCase = true)
    }
    val currentStudentId = currentStudentEntity?.id

    // Precompute slots map
    val slotsByDate = remember(slots) {
        slots.groupBy { it.slot.dateIso }
    }

    // Helper to check if student is enrolled in a slot
    fun isStudentEnrolledInSlot(slotItem: SlotWithBookings): Boolean {
        if (currentStudentId != null && slotItem.enrolledStudentIds.contains(currentStudentId)) return true
        val cleanSavedPhone = savedProfile.phone.replace(" ", "")
        val cleanSavedFirst = savedProfile.firstName.trim()
        val cleanSavedLast = savedProfile.lastName.trim()

        return slotItem.confirmedBookings.any { b ->
            (cleanSavedPhone.isNotBlank() && b.student.phone.replace(" ", "") == cleanSavedPhone) ||
            (cleanSavedFirst.isNotBlank() && b.student.firstName.equals(cleanSavedFirst, ignoreCase = true) && b.student.lastName.equals(cleanSavedLast, ignoreCase = true))
        } || slotItem.waitingListBookings.any { b ->
            (cleanSavedPhone.isNotBlank() && b.student.phone.replace(" ", "") == cleanSavedPhone) ||
            (cleanSavedFirst.isNotBlank() && b.student.firstName.equals(cleanSavedFirst, ignoreCase = true) && b.student.lastName.equals(cleanSavedLast, ignoreCase = true))
        }
    }

    // Helper to open WhatsApp to notify instructor of registration
    fun sendWhatsAppRegistrationConfirmation(slotItem: SlotWithBookings) {
        val slot = slotItem.slot
        val type = PlanningLessonType.fromCode(slot.lessonType).label
        val timeFr = formatTimeRangeFrench(slot.startTime, slot.endTime)
        val dateFr = formatDateFrench(slot.dateIso)
        val msg = "Bonjour ! Je vous confirme mon inscription pour la séance *$type* du *$dateFr* ($timeFr).\nÉlève : ${savedProfile.fullName} (${savedProfile.level})\nTél : ${savedProfile.phone}"
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://api.whatsapp.com/send?text=" + java.net.URLEncoder.encode(msg, "UTF-8"))
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, msg)
            context.startActivity(Intent.createChooser(shareIntent, "Notifier le moniteur"))
        }
    }

    // Sub-view in Tab 1: "ALL" vs "MY_BOOKINGS"
    var studentListSubTab by remember { mutableStateOf("ALL") }

    val myBookingsCount = remember(slots, currentStudentId, savedProfile) {
        slots.count { isStudentEnrolledInSlot(it) }
    }

    // Filtered slots for student list view
    val displaySlots = slots.filter { item ->
        if (studentListSubTab == "MY_BOOKINGS") {
            isStudentEnrolledInSlot(item) && !item.slot.isCancelled
        } else {
            val dateMatches = when (selectedDateFilter) {
                null, "TOUS" -> true
                "TODAY" -> item.slot.dateIso == todayIso
                "TOMORROW" -> item.slot.dateIso == tomorrowIso
                "WEEK" -> item.slot.dateIso in todayIso..endOfWeekIso
                else -> item.slot.dateIso == selectedDateFilter
            }
            val typeMatches = if (selectedTypeFilter != null) item.slot.lessonType.equals(selectedTypeFilter, ignoreCase = true) else true
            dateMatches && typeMatches && !item.slot.isCancelled
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(
                color = HighDensityHeaderTitle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🪂", fontSize = 20.sp)
                            Column {
                                val pillStyle = when (syncStatus) {
                                    SyncStatus.CONNECTED_SYNCED -> SyncPillStyle(Color(0xFF34D399), Color(0xFF10B981).copy(alpha = 0.25f), Color(0xFF10B981), if (lastSyncTime.isNotBlank()) "En direct $lastSyncTime" else "En direct")
                                    SyncStatus.SYNCING -> SyncPillStyle(Color(0xFFFBBF24), Color(0xFFF59E0B).copy(alpha = 0.25f), Color(0xFFF59E0B), "Synchronisation...")
                                    SyncStatus.CONNECTING -> SyncPillStyle(Color(0xFF60A5FA), Color(0xFF3B82F6).copy(alpha = 0.25f), Color(0xFF3B82F6), "Connexion...")
                                    SyncStatus.OFFLINE, SyncStatus.ERROR -> SyncPillStyle(Color(0xFFEF4444), Color(0xFFDC2626).copy(alpha = 0.25f), Color(0xFFDC2626), "Hors-ligne")
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Espace Élèves", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = pillStyle.bgColor,
                                        border = BorderStroke(1.dp, pillStyle.borderColor),
                                        modifier = Modifier.clickable { showSyncSettingsDialog = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(pillStyle.dotColor, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(pillStyle.text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                                Text("École: $schoolCode • Inscriptions en direct", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Sync settings button
                            IconButton(
                                onClick = { showSyncSettingsDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = "Paramètres de synchronisation", tint = Color.White)
                            }

                            // Moniteur Access button with PIN Lock
                            OutlinedButton(
                                onClick = { showPinDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Accès Moniteur", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Student Profile banner
                    Surface(
                        color = HighDensitySurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (savedProfile.isConfigured) PrimaryBlue else Color(0xFFD97706),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            if (savedProfile.isConfigured) savedProfile.firstName.take(1).uppercase() else "?",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Column {
                                    if (savedProfile.isConfigured) {
                                        Text(
                                            text = savedProfile.fullName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = HighDensityHeaderTitle
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(savedProfile.phone, fontSize = 11.sp, color = SecondaryText)
                                            Surface(shape = RoundedCornerShape(4.dp), color = PrimaryBlueContainer) {
                                                Text(savedProfile.level, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    } else {
                                        Text("Profil non renseigné", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFD97706))
                                        Text("Touchez ici pour saisir vos coordonnées", fontSize = 10.sp, color = SecondaryText)
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { showProfileModal = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(if (savedProfile.isConfigured) "Modifier" else "Configurer", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = HighDensitySurface,
                tonalElevation = 6.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = studentTab == 0,
                    onClick = { studentTab = 0 },
                    icon = {
                        Icon(
                            if (studentTab == 0) Icons.Default.CalendarMonth else Icons.Outlined.CalendarMonth,
                            contentDescription = "Planning Visuel"
                        )
                    },
                    label = { Text("Planning Visuel (Mois/Trim/An)", fontWeight = if (studentTab == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        indicatorColor = PrimaryBlueContainer
                    )
                )

                NavigationBarItem(
                    selected = studentTab == 1,
                    onClick = { studentTab = 1 },
                    icon = {
                        Icon(
                            if (studentTab == 1) Icons.Default.FormatListBulleted else Icons.Outlined.FormatListBulleted,
                            contentDescription = "Mes Créneaux & Liste"
                        )
                    },
                    label = { Text("Mes Créneaux", fontWeight = if (studentTab == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        indicatorColor = PrimaryBlueContainer
                    )
                )

                NavigationBarItem(
                    selected = studentTab == 2,
                    onClick = { studentTab = 2 },
                    icon = {
                        Icon(
                            if (studentTab == 2) Icons.Default.MenuBook else Icons.Outlined.MenuBook,
                            contentDescription = "Mon Livret FFPLUM"
                        )
                    },
                    label = { Text("Mon Livret FFPLUM", fontWeight = if (studentTab == 2) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        indicatorColor = PrimaryBlueContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(HighDensityBg)
        ) {
            when (studentTab) {
                0 -> {
                    // Visual Calendar View for Student (Mois, Trimestre, Année)
                    Column(modifier = Modifier.fillMaxSize()) {
                        // View Mode Switcher (Annuel / Trimestre / Mois)
                        Surface(
                            color = HighDensitySurface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // View Mode Segmented Controls
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = HighDensityNavBar,
                                        border = BorderStroke(1.dp, BorderOutline.copy(alpha = 0.5f))
                                    ) {
                                        Row(modifier = Modifier.padding(3.dp)) {
                                            listOf(PlanningViewMode.MOIS, PlanningViewMode.TRIMESTRE, PlanningViewMode.ANNUEL).forEach { mode ->
                                                val isSelected = calendarViewMode == mode
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) PrimaryBlue else Color.Transparent,
                                                    modifier = Modifier.clickable { calendarViewMode = mode }
                                                ) {
                                                    Text(
                                                        text = when (mode) {
                                                            PlanningViewMode.ANNUEL -> "Annuel"
                                                            PlanningViewMode.TRIMESTRE -> "Trimestre"
                                                            PlanningViewMode.MOIS -> "Mois"
                                                            else -> "Mois"
                                                        },
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) Color.White else SecondaryText,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Quick inscription guide
                                    Surface(shape = RoundedCornerShape(6.dp), color = GreenSuccessBg) {
                                        Text("👆 Touchez un jour pour vous inscrire", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenSuccess, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Navigation Arrows
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = {
                                            when (calendarViewMode) {
                                                PlanningViewMode.ANNUEL -> selectedYear--
                                                PlanningViewMode.TRIMESTRE -> {
                                                    if (selectedQuarter > 1) selectedQuarter--
                                                    else { selectedQuarter = 4; selectedYear-- }
                                                }
                                                else -> {
                                                    if (selectedMonth > 0) selectedMonth--
                                                    else { selectedMonth = 11; selectedYear-- }
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Précédent")
                                    }

                                    val titleText = when (calendarViewMode) {
                                        PlanningViewMode.ANNUEL -> "Année $selectedYear"
                                        PlanningViewMode.TRIMESTRE -> "Trimestre T$selectedQuarter $selectedYear (${getQuarterMonthsLabel(selectedQuarter)})"
                                        else -> "${getMonthName(selectedMonth).replaceFirstChar { it.uppercase() }} $selectedYear"
                                    }

                                    Text(
                                        text = titleText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HighDensityHeaderTitle
                                    )

                                    IconButton(
                                        onClick = {
                                            when (calendarViewMode) {
                                                PlanningViewMode.ANNUEL -> selectedYear++
                                                PlanningViewMode.TRIMESTRE -> {
                                                    if (selectedQuarter < 4) selectedQuarter++
                                                    else { selectedQuarter = 1; selectedYear++ }
                                                }
                                                else -> {
                                                    if (selectedMonth < 11) selectedMonth++
                                                    else { selectedMonth = 0; selectedYear++ }
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Suivant")
                                    }
                                }

                                // Legend
                                Surface(
                                    color = HighDensityNavBar,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LegendItem(color = GreenSuccess, label = "Vert : Places libres")
                                        LegendItem(color = RedAlertText, label = "Rouge : Complet")
                                        LegendItem(color = BorderOutline, label = "Blanc : Pas de séance", isBorderOnly = true)
                                    }
                                }
                            }
                        }

                        Divider(color = BorderOutline.copy(alpha = 0.4f))

                        // Calendar Content
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            when (calendarViewMode) {
                                PlanningViewMode.TRIMESTRE -> QuarterView(
                                    year = selectedYear,
                                    quarter = selectedQuarter,
                                    slotsByDate = slotsByDate,
                                    onSelectMonth = { m ->
                                        selectedMonth = m
                                        calendarViewMode = PlanningViewMode.MOIS
                                    },
                                    onSelectDay = { dateIso -> selectedDayForDetail = dateIso }
                                )
                                PlanningViewMode.ANNUEL -> AnnualView(
                                    year = selectedYear,
                                    slotsByDate = slotsByDate,
                                    onSelectMonth = { m ->
                                        selectedMonth = m
                                        calendarViewMode = PlanningViewMode.MOIS
                                    }
                                )
                                else -> MonthView(
                                    year = selectedYear,
                                    month = selectedMonth,
                                    slotsByDate = slotsByDate,
                                    onSelectDay = { dateIso -> selectedDayForDetail = dateIso },
                                    onOpenAddSlotForDate = { dateIso -> selectedDayForDetail = dateIso }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // List View of Slots & My Registrations
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(HighDensityBg)
                    ) {
                        // Sub-navigation bar: All Slots vs My Registrations
                        Surface(
                            color = HighDensitySurface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Button: All Open Slots
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (studentListSubTab == "ALL") PrimaryBlue else HighDensityNavBar,
                                        border = BorderStroke(1.dp, if (studentListSubTab == "ALL") PrimaryBlue else BorderOutline),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { studentListSubTab = "ALL" }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "📋 Toutes les séances",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (studentListSubTab == "ALL") Color.White else HighDensityHeaderTitle
                                            )
                                        }
                                    }

                                    // Button: My Registrations with Badge
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (studentListSubTab == "MY_BOOKINGS") GreenSuccess else HighDensityNavBar,
                                        border = BorderStroke(1.dp, if (studentListSubTab == "MY_BOOKINGS") GreenSuccess else BorderOutline),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { studentListSubTab = "MY_BOOKINGS" }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                "🎯 Mes Inscriptions",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (studentListSubTab == "MY_BOOKINGS") Color.White else HighDensityHeaderTitle
                                            )
                                            if (myBookingsCount > 0) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (studentListSubTab == "MY_BOOKINGS") Color.White else GreenSuccess
                                                ) {
                                                    Text(
                                                        "$myBookingsCount",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (studentListSubTab == "MY_BOOKINGS") GreenSuccess else Color.White,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Quick Filters Row (only if in ALL view)
                                if (studentListSubTab == "ALL") {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(dateFilters) { (key, label) ->
                                            val isSelected = (selectedDateFilter == key) || (selectedDateFilter == null && key == "TOUS")
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { selectedDateFilter = key },
                                                label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        PlanningLessonType.entries.forEach { type ->
                                            val isSelected = selectedTypeFilter == type.code
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { selectedTypeFilter = if (isSelected) null else type.code },
                                                label = { Text("${type.emoji} ${type.label}", fontSize = 11.sp) },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Divider(color = BorderOutline.copy(alpha = 0.4f))

                        if (displaySlots.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(if (studentListSubTab == "MY_BOOKINGS") "🪂" else "📅", fontSize = 36.sp)
                                    Text(
                                        if (studentListSubTab == "MY_BOOKINGS") "Vous n'avez aucune inscription active" else "Aucun créneau ouvert pour cette sélection",
                                        fontWeight = FontWeight.Bold,
                                        color = HighDensityHeaderTitle
                                    )
                                    Text(
                                        if (studentListSubTab == "MY_BOOKINGS") "Inscrivez-vous en 1 clic dans l'onglet 'Toutes les séances' ou sur le Planning Visuel." else "Consultez le Planning Visuel pour voir les autres mois.",
                                        fontSize = 12.sp,
                                        color = SecondaryText,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 30.dp)
                            ) {
                                items(displaySlots, key = { it.slot.id }) { slotItem ->
                                    val isEnrolled = isStudentEnrolledInSlot(slotItem)
                                    StudentSlotCard(
                                        slotItem = slotItem,
                                        isAlreadyEnrolled = isEnrolled,
                                        currentStudentId = currentStudentId,
                                        onUnenroll = { sId, stId -> onUnenroll(sId, stId) },
                                        onNotifyWhatsApp = { sendWhatsAppRegistrationConfirmation(slotItem) },
                                        onRegisterClick = {
                                            if (!savedProfile.isConfigured) {
                                                slotToRegister = slotItem
                                                showProfileModal = true
                                            } else {
                                                onRegisterSelf(
                                                    slotItem.slot.id,
                                                    savedProfile.firstName,
                                                    savedProfile.lastName,
                                                    savedProfile.phone,
                                                    "",
                                                    savedProfile.level
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Student FFPLUM Booklet & Progression Tab
                    val matchedStudent = remember(allStudents, savedProfile) {
                        if (!savedProfile.isConfigured) null
                        else allStudents.find { it.phone == savedProfile.phone || (it.firstName.equals(savedProfile.firstName, ignoreCase = true) && it.lastName.equals(savedProfile.lastName, ignoreCase = true)) }
                    }
                    val studentProgress = remember(allProgress, matchedStudent) {
                        matchedStudent?.let { st -> allProgress.find { it.studentId == st.id } }
                    }

                    if (!savedProfile.isConfigured || matchedStudent == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(HighDensityBg)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("📖", fontSize = 48.sp)
                                Text(
                                    "Livret Numérique FFPLUM",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = HighDensityHeaderTitle
                                )
                                Text(
                                    "Pour consulter votre suivi pédagogique, vos exercices validés et télécharger votre livret officiel FFPLUM, veuillez configurer votre profil ou vous inscrire à un premier créneau.",
                                    fontSize = 13.sp,
                                    color = SecondaryText,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { showProfileModal = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Renseigner mon profil")
                                }
                            }
                        }
                    } else {
                        val prog = studentProgress ?: StudentProgressEntity(studentId = matchedStudent.id)
                        val totalExercises = prog.totalSkillsCount
                        val completedExercises = prog.validatedSkillsCount
                        val progressPct = prog.completionPercent

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(HighDensityBg)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 40.dp)
                        ) {
                            // Header Card: Student Info & Overall Progress
                            item {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = PrimaryBlue,
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("🪂", fontSize = 22.sp)
                                                    }
                                                }
                                                Column {
                                                    Text(
                                                        text = matchedStudent.fullName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp,
                                                        color = HighDensityHeaderTitle
                                                    )
                                                    Text(
                                                        text = "Niveau : ${matchedStudent.level} • Paramoteur FFPLUM",
                                                        fontSize = 12.sp,
                                                        color = SecondaryText
                                                    )
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (prog.skillBrevetPilote) GreenSuccessBg else PrimaryBlueContainer
                                            ) {
                                                Text(
                                                    text = if (prog.skillBrevetPilote) "🏆 Brevet Validé" else "En formation",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = if (prog.skillBrevetPilote) GreenSuccess else PrimaryBlue,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Divider(color = BorderOutline.copy(alpha = 0.4f))

                                        // Progress Bar
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Progression globale cursus", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                                            Text("$completedExercises / $totalExercises modules ($progressPct%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                        }

                                        LinearProgressIndicator(
                                            progress = { progressPct / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp),
                                            color = if (progressPct >= 80) GreenSuccess else PrimaryBlue,
                                            trackColor = BorderOutline.copy(alpha = 0.3f)
                                        )

                                        // Flight Stats Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = HighDensityNavBar,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text("⏱️ Heures de vol", fontSize = 10.sp, color = SecondaryText)
                                                    Text(
                                                        prog.totalFlightHoursFormatted,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = HighDensityHeaderTitle
                                                    )
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = HighDensityNavBar,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text("🛫 Décollages", fontSize = 10.sp, color = SecondaryText)
                                                    Text(
                                                        "${prog.totalFlightsCount}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = HighDensityHeaderTitle
                                                    )
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = HighDensityNavBar,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text("🪂 Gonflage", fontSize = 10.sp, color = SecondaryText)
                                                    Text(
                                                        prog.totalGonflageHoursFormatted,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = HighDensityHeaderTitle
                                                    )
                                                }
                                            }
                                        }

                                        // Export Buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { onExportStudentBookletPdf?.invoke(matchedStudent) },
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Livret PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = { onExportStudentBookletIcs?.invoke(matchedStudent) },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Agenda (.ics)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Autonomy Radar / Rating
                            item {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("⭐ Indices d'Autonomie Évalués par le Moniteur", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                                        AutonomyRatingRow("Décollage (Dos / Face)", prog.autonomyDecollage)
                                        AutonomyRatingRow("Pilotage & Conduite de Vol", prog.autonomyEnVol)
                                        AutonomyRatingRow("Approche & Atterrissage", prog.autonomyAtterrissage)
                                        AutonomyRatingRow("Gonflage & Maîtrise Voile", prog.autonomyGonflage)
                                    }
                                }
                            }

                            // Pedagogical Checklist
                            item {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("📋 Progression FFPLUM Validée", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                                        Text("Phase 1 : Gonflage & Pré-vol", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                        ChecklistItemDisplay("Contrôle pré-vol et disposition de l'aile", prog.skillPrevol)
                                        ChecklistItemDisplay("Gonflage dos au vent et montée d'aile", prog.skillGonflageDos)
                                        ChecklistItemDisplay("Gonflage face à l'aile et affalement", prog.skillGonflageFace)
                                        ChecklistItemDisplay("Démarrage et sécurité moteur au sol", prog.skillMoteurSol)

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Phase 2 : Grands Vols & Manœuvres", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                        ChecklistItemDisplay("Décollage autonome en sécurité", prog.skillDecoAutonome)
                                        ChecklistItemDisplay("Virages 360° et maintien d'altitude", prog.skillViragesAltitude)
                                        ChecklistItemDisplay("Simulation panne moteur et PTU/PTS", prog.skillPanneMoteur)
                                        ChecklistItemDisplay("Précision d'atterrissage sur cible", prog.skillAtterroPrecision)

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Phase 3 : Brevet & Navigation", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                        ChecklistItemDisplay("Navigation et analyse aérologie", prog.skillNavigationAerologie)
                                        ChecklistItemDisplay("Brevet de pilote paramoteur validé", prog.skillBrevetPilote)
                                        ChecklistItemDisplay("Qualification emport passager", prog.skillEmportPassager)
                                    }
                                }
                            }

                            if (prog.instructorNotes.isNotBlank()) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("📝 Commentaires et Conseils du Moniteur", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = HighDensityHeaderTitle)
                                            Text(prog.instructorNotes, fontSize = 12.sp, color = SecondaryText)
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

    // Modal Sheet: Day Detail when student clicks a calendar day
    selectedDayForDetail?.let { dateIso ->
        val daySlots = slotsByDate[dateIso].orEmpty()
        StudentDayDetailSheet(
            dateIso = dateIso,
            slots = daySlots,
            currentStudentId = currentStudentId,
            savedProfile = savedProfile,
            onDismiss = { selectedDayForDetail = null },
            onUnenroll = { slotId, studentId -> onUnenroll(slotId, studentId) },
            onNotifyWhatsApp = { sendWhatsAppRegistrationConfirmation(it) },
            onRegisterSlot = { slotItem ->
                if (!savedProfile.isConfigured) {
                    slotToRegister = slotItem
                    showProfileModal = true
                } else {
                    onRegisterSelf(
                        slotItem.slot.id,
                        savedProfile.firstName,
                        savedProfile.lastName,
                        savedProfile.phone,
                        "",
                        savedProfile.level
                    )
                }
            }
        )
    }

    // PIN Protection Dialog for Instructor Access
    if (showPinDialog) {
        var enteredPin by remember { mutableStateOf("") }
        var isPinError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔒", fontSize = 20.sp)
                    Text("Accès Mode Moniteur", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Cet espace est réservé au moniteur (gestion des élèves, validation des présences et création de créneaux).\n\nVeuillez saisir votre Code PIN à 4 chiffres :",
                        fontSize = 12.sp,
                        color = SecondaryText
                    )

                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = {
                            if (it.length <= 8) {
                                enteredPin = it
                                isPinError = false
                            }
                        },
                        label = { Text("Code PIN Moniteur") },
                        placeholder = { Text("1234") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = isPinError,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (isPinError) {
                        Text(
                            "❌ Code PIN incorrect (Code par défaut : 1234)",
                            fontSize = 11.sp,
                            color = RedAlertText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onVerifyPin(enteredPin)) {
                            showPinDialog = false
                            onSwitchToInstructorMode()
                        } else {
                            isPinError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Déverrouiller", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Annuler")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = HighDensitySurface
        )
    }

    // Modal Profile Setup / Edit
    if (showProfileModal) {
        var firstName by remember { mutableStateOf(savedProfile.firstName) }
        var lastName by remember { mutableStateOf(savedProfile.lastName) }
        var phone by remember { mutableStateOf(savedProfile.phone) }
        var level by remember { mutableStateOf(savedProfile.level) }

        AlertDialog(
            onDismissRequest = {
                if (savedProfile.isConfigured) showProfileModal = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("👤", fontSize = 20.sp)
                    Text("Mes Coordonnées Élève", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Ces informations permettent au moniteur de vous identifier et de vous contacter facilement pour les créneaux.",
                        fontSize = 11.sp,
                        color = SecondaryText
                    )

                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("Prénom *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Nom *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Numéro de Téléphone *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text("Niveau de pratique :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Gonflage", "Vol", "Perf").forEach { lvl ->
                            FilterChip(
                                selected = level == lvl,
                                onClick = { level = lvl },
                                label = { Text(lvl, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (firstName.isBlank() || phone.isBlank()) {
                            Toast.makeText(context, "Veuillez renseigner au moins le prénom et le téléphone", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onSaveProfile(firstName, lastName, phone, level)
                        showProfileModal = false

                        // If opened from a slot button, complete the registration right away
                        slotToRegister?.let { slotItem ->
                            onRegisterSelf(
                                slotItem.slot.id,
                                firstName,
                                lastName,
                                phone,
                                "",
                                level
                            )
                            slotToRegister = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Valider mes coordonnées", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (savedProfile.isConfigured) {
                    TextButton(onClick = { showProfileModal = false }) {
                        Text("Annuler")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = HighDensitySurface
        )
    }

    // Sync Settings Dialog
    SyncSettingsDialog(
        isOpen = showSyncSettingsDialog,
        onDismiss = { showSyncSettingsDialog = false },
        currentSchoolCode = schoolCode,
        onSaveSchoolCode = onSaveSchoolCode,
        syncStatus = syncStatus,
        syncStatusMsg = syncStatusMsg,
        lastSyncTime = lastSyncTime,
        syncedSlotsCount = syncedSlotsCount,
        syncedStudentsCount = syncedStudentsCount,
        syncedBookingsCount = syncedBookingsCount,
        onForceSync = onForceSync,
        onShareSchoolCode = onShareSchoolCode
    )
}

@Composable
private fun AutonomyRatingRow(label: String, rating: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = HighDensityHeaderTitle, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..5).forEach { i ->
                Text(
                    text = if (i <= rating) "★" else "☆",
                    fontSize = 14.sp,
                    color = if (i <= rating) Color(0xFFEAB308) else BorderOutline
                )
            }
        }
    }
}

@Composable
private fun ChecklistItemDisplay(label: String, done: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(if (done) "✅" else "⚪", fontSize = 12.sp)
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (done) HighDensityHeaderTitle else SecondaryText,
            fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun StudentSlotCard(
    slotItem: SlotWithBookings,
    isAlreadyEnrolled: Boolean,
    currentStudentId: Long?,
    onUnenroll: (slotId: Long, studentId: Long) -> Unit,
    onNotifyWhatsApp: (() -> Unit)? = null,
    onRegisterClick: () -> Unit
) {
    val context = LocalContext.current
    val slot = slotItem.slot
    val type = PlanningLessonType.fromCode(slot.lessonType)
    val isFull = slotItem.isFull
    val timeRangeText = formatTimeRangeFrench(slot.startTime, slot.endTime)

    val myBooking = slotItem.confirmedBookings.find { it.booking.studentId == currentStudentId }
        ?: slotItem.waitingListBookings.find { it.booking.studentId == currentStudentId }
    val isWaiting = myBooking?.booking?.isWaitingList ?: false

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (slot.isCancelled) RedAlertBg.copy(alpha = 0.4f) else if (isAlreadyEnrolled) Color(0xFFF0FDF4) else HighDensitySurface
        ),
        border = BorderStroke(
            1.5.dp,
            if (slot.isCancelled) RedAlertText else if (isAlreadyEnrolled) GreenSuccess else if (isFull) RedAlertText.copy(alpha = 0.5f) else type.borderColor.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Heure & Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Heure format "de 8h à 10h"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryBlueDark,
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                        Text(
                            text = timeRangeText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }

                // Type de leçon
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = type.containerColor,
                    border = BorderStroke(1.5.dp, type.borderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(type.emoji, fontSize = 13.sp)
                        Text(
                            text = type.label.uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = type.primaryColor
                        )
                    }
                }

                // Status Badge
                if (slot.isCancelled) {
                    Surface(shape = RoundedCornerShape(8.dp), color = RedAlertBg, border = BorderStroke(1.dp, RedAlertText)) {
                        Text("🔴 ANNULÉ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RedAlertText, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                } else if (isAlreadyEnrolled) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isWaiting) Color(0xFFFEF3C7) else GreenSuccessBg,
                        border = BorderStroke(1.dp, if (isWaiting) Color(0xFFD97706) else GreenSuccess)
                    ) {
                        Text(
                            if (isWaiting) "⏳ En attente" else "✅ Inscrit",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWaiting) Color(0xFFD97706) else GreenSuccess,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                } else if (isFull) {
                    Surface(shape = RoundedCornerShape(8.dp), color = RedAlertBg, border = BorderStroke(1.dp, RedAlertText)) {
                        Text("🔴 COMPLET", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RedAlertText, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                } else {
                    Surface(shape = RoundedCornerShape(8.dp), color = GreenSuccessBg, border = BorderStroke(1.dp, GreenSuccess)) {
                        Text("🟢 ${slotItem.availablePlaces} dispo", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenSuccess, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                }
            }

            // Weather alert or cancellation banner
            if (slot.isCancelled) {
                Surface(
                    color = RedAlertBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, RedAlertText.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⚠️", fontSize = 14.sp)
                        Column {
                            Text("Séance annulée par le moniteur", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedAlertText)
                            if (slot.cancelReason.isNotBlank()) {
                                Text(slot.cancelReason, fontSize = 10.sp, color = RedAlertText)
                            }
                            if (slot.postponedTo.isNotBlank()) {
                                Text("Reporté au : ${slot.postponedTo}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                            }
                        }
                    }
                }
            } else if (slot.weatherAlert.isNotBlank()) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🌤️", fontSize = 14.sp)
                        Text(slot.weatherAlert, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF92400E))
                    }
                }
            }

            // Title & Date/Location
            Column {
                Text(
                    text = "${slot.title} • ${formatDateFrench(slot.dateIso)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = HighDensityHeaderTitle
                )
                if (slot.location.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📍", fontSize = 10.sp)
                        Text(slot.location, fontSize = 11.sp, color = SecondaryText)
                    }
                }
            }

            if (slot.notes.isNotBlank()) {
                Text("💬 ${slot.notes}", fontSize = 10.sp, color = SecondaryText)
            }

            // Action Buttons
            if (isAlreadyEnrolled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (isWaiting) "⏳" else "✅", fontSize = 12.sp)
                            Text(
                                if (isWaiting) "Place en liste d'attente" else "Place confirmée pour cette séance",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isWaiting) Color(0xFFD97706) else GreenSuccess
                            )
                        }

                        TextButton(
                            onClick = {
                                if (currentStudentId != null) {
                                    onUnenroll(slot.id, currentStudentId)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Se désinscrire", fontSize = 11.sp, color = RedAlertText, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Direct WhatsApp Confirmation Button to Instructor
                    onNotifyWhatsApp?.let { sendWA ->
                        Button(
                            onClick = sendWA,
                            colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("💬", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Confirmer au moniteur sur WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Google Calendar Export Button
                    OutlinedButton(
                        onClick = { CalendarExportUtils.addSlotToGoogleCalendar(context, slot) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth().height(34.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp), tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ajouter à mon agenda Google", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                    }
                }
            } else if (!slot.isCancelled) {
                Button(
                    onClick = onRegisterClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFull) Color(0xFFD97706) else GreenSuccess
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isFull) {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Demander liste d'attente", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("M'inscrire à ce créneau (1 clic)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDayDetailSheet(
    dateIso: String,
    slots: List<SlotWithBookings>,
    currentStudentId: Long?,
    savedProfile: PlanningViewModel.StudentProfile,
    onDismiss: () -> Unit,
    onUnenroll: (Long, Long) -> Unit,
    onNotifyWhatsApp: ((SlotWithBookings) -> Unit)? = null,
    onRegisterSlot: (SlotWithBookings) -> Unit
) {
    val sunTimes = remember(dateIso) {
        com.example.util.SunCalculator.calculateSunTimes(dateIso)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HighDensitySurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📅", fontSize = 22.sp)
                    Column {
                        Text(
                            text = "Séances du ${formatDateFrench(dateIso)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = HighDensityHeaderTitle
                        )
                        Text(
                            text = "${slots.size} créneau(x) proposé(s)",
                            fontSize = 11.sp,
                            color = SecondaryText
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            // Plouharnel Solar Banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = HighDensityBg,
                border = BorderStroke(1.dp, BorderOutline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📍", fontSize = 11.sp)
                        Text("Plouharnel (56) :", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                    }
                    Text(
                        "🌅 Lever ${sunTimes.sunriseStr}  •  🌇 Coucher ${sunTimes.sunsetStr}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue
                    )
                }
            }

            Divider(color = BorderOutline.copy(alpha = 0.4f))

            if (slots.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("☀️", fontSize = 32.sp)
                        Text("Aucune séance prévue pour ce jour", fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                        Text("Consultez les autres jours verts du calendrier.", fontSize = 11.sp, color = SecondaryText)
                    }
                }
            } else {
                slots.forEach { slotItem ->
                    val isAlreadyEnrolled = currentStudentId != null && slotItem.enrolledStudentIds.contains(currentStudentId)
                    StudentSlotCard(
                        slotItem = slotItem,
                        isAlreadyEnrolled = isAlreadyEnrolled,
                        currentStudentId = currentStudentId,
                        onUnenroll = onUnenroll,
                        onNotifyWhatsApp = { onNotifyWhatsApp?.invoke(slotItem) },
                        onRegisterClick = { onRegisterSlot(slotItem) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
