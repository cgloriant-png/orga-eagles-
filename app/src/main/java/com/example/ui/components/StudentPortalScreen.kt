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
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.PlanningViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StudentPortalScreen(
    slots: List<SlotWithBookings>,
    savedProfile: PlanningViewModel.StudentProfile,
    onSaveProfile: (firstName: String, lastName: String, phone: String, level: String) -> Unit,
    onRegisterSelf: (slotId: Long, firstName: String, lastName: String, phone: String, email: String, level: String) -> Unit,
    onUnenroll: (slotId: Long, studentId: Long) -> Unit,
    allStudents: List<StudentEntity>,
    onVerifyPin: (String) -> Boolean,
    onSwitchToInstructorMode: () -> Unit
) {
    val context = LocalContext.current

    // Navigation Tab in Student Mode: 0 = Calendrier Visuel (Mois, Trimestre, Année), 1 = Mes Inscriptions & Liste
    var studentTab by remember { mutableIntStateOf(0) }

    // Dialog & Sheet states
    var showProfileModal by remember { mutableStateOf(!savedProfile.isConfigured) }
    var slotToRegister by remember { mutableStateOf<SlotWithBookings?>(null) }
    var selectedDayForDetail by remember { mutableStateOf<String?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }

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

    // Filtered slots for student list view
    val displaySlots = slots.filter { item ->
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
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Espace Élèves", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.25f),
                                        border = BorderStroke(1.dp, Color(0xFF10B981))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(Color(0xFF34D399), CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("En direct", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6EE7B7))
                                        }
                                    }
                                }
                                Text("Inscriptions & Planning", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                            }
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
                    label = { Text("Mes Créneaux & Liste", fontWeight = if (studentTab == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp) },
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
                                            PlanningViewMode.entries.forEach { mode ->
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
                                                PlanningViewMode.MOIS -> {
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
                                        PlanningViewMode.MOIS -> "${getMonthName(selectedMonth).replaceFirstChar { it.uppercase() }} $selectedYear"
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
                                                PlanningViewMode.MOIS -> {
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
                                PlanningViewMode.MOIS -> MonthView(
                                    year = selectedYear,
                                    month = selectedMonth,
                                    slotsByDate = slotsByDate,
                                    onSelectDay = { dateIso -> selectedDayForDetail = dateIso },
                                    onOpenAddSlotForDate = { dateIso -> selectedDayForDetail = dateIso }
                                )
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
                        // Quick Filters Row
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HighDensitySurface)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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
                                modifier = Modifier.fillMaxWidth(),
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

                        Divider(color = BorderOutline.copy(alpha = 0.4f))

                        if (displaySlots.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("📅", fontSize = 32.sp)
                                    Text("Aucun créneau ouvert pour cette sélection", fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                                    Text("Consultez le Planning Visuel pour voir les autres mois.", fontSize = 12.sp, color = SecondaryText)
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
                                    StudentSlotCard(
                                        slotItem = slotItem,
                                        isAlreadyEnrolled = currentStudentId != null && slotItem.enrolledStudentIds.contains(currentStudentId),
                                        currentStudentId = currentStudentId,
                                        onUnenroll = { sId, stId -> onUnenroll(sId, stId) },
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
}

@Composable
fun StudentSlotCard(
    slotItem: SlotWithBookings,
    isAlreadyEnrolled: Boolean,
    currentStudentId: Long?,
    onUnenroll: (slotId: Long, studentId: Long) -> Unit,
    onRegisterClick: () -> Unit
) {
    val slot = slotItem.slot
    val type = PlanningLessonType.fromCode(slot.lessonType)
    val isFull = slotItem.isFull

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
        border = BorderStroke(
            1.5.dp,
            if (isAlreadyEnrolled) PrimaryBlue else if (isFull) RedAlertText.copy(alpha = 0.5f) else GreenSuccess.copy(alpha = 0.5f)
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
            // Date & Type Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(type.emoji, fontSize = 18.sp)
                    Column {
                        Text(
                            text = "${slot.dateIso} • ${slot.startTime} - ${slot.endTime}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = HighDensityHeaderTitle
                        )
                        Text(
                            text = "${type.label} (${slot.title})",
                            fontSize = 11.sp,
                            color = SecondaryText
                        )
                    }
                }

                // Status Badge
                if (isAlreadyEnrolled) {
                    Surface(shape = RoundedCornerShape(6.dp), color = PrimaryBlueContainer) {
                        Text("✅ Déjà inscrit", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else if (isFull) {
                    Surface(shape = RoundedCornerShape(6.dp), color = RedAlertBg) {
                        Text("🔴 COMPLET", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RedAlertText, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else {
                    Surface(shape = RoundedCornerShape(6.dp), color = GreenSuccessBg) {
                        Text("🟢 ${slotItem.availablePlaces} place(s) dispo", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenSuccess, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            if (slot.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📍", fontSize = 10.sp)
                    Text(slot.location, fontSize = 11.sp, color = SecondaryText)
                }
            }

            if (slot.notes.isNotBlank()) {
                Text("💬 ${slot.notes}", fontSize = 10.sp, color = SecondaryText)
            }

            // 1-Click Action Button
            if (isAlreadyEnrolled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vous êtes inscrit sur ce créneau", fontSize = 11.sp, color = GreenSuccess, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            if (currentStudentId != null) {
                                onUnenroll(slot.id, currentStudentId)
                            }
                        }
                    ) {
                        Text("Se désinscrire", fontSize = 11.sp, color = RedAlertText)
                    }
                }
            } else {
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
                            text = "Séances du $dateIso",
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
                        onRegisterClick = { onRegisterSlot(slotItem) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
