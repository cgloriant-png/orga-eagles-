package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.PlanningViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: PlanningViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        setContent {
            ParamoteurTheme {
                val context = LocalContext.current

                // State from ViewModel
                val allStudents by viewModel.allStudents.collectAsStateWithLifecycle()
                val allProgress by viewModel.allProgress.collectAsStateWithLifecycle()
                val slotsWithBookings by viewModel.slotsWithBookings.collectAsStateWithLifecycle()
                val filteredSlots by viewModel.filteredSlots.collectAsStateWithLifecycle()
                val filteredStudentsWithStats by viewModel.filteredStudentsWithStats.collectAsStateWithLifecycle()
                val isStudentMode by viewModel.isStudentMode.collectAsStateWithLifecycle()
                val savedProfile by viewModel.savedProfile.collectAsStateWithLifecycle()
                val instructorPin by viewModel.instructorPin.collectAsStateWithLifecycle()
                val savedStandardDayConfig by viewModel.savedStandardDayConfig.collectAsStateWithLifecycle()
                val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
                val syncStatusMsg by viewModel.syncStatusMessage.collectAsStateWithLifecycle()
                val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
                val schoolCode by viewModel.schoolCode.collectAsStateWithLifecycle()
                val syncedSlotsCount by viewModel.syncedSlotsCount.collectAsStateWithLifecycle()
                val syncedStudentsCount by viewModel.syncedStudentsCount.collectAsStateWithLifecycle()
                val syncedBookingsCount by viewModel.syncedBookingsCount.collectAsStateWithLifecycle()

                // Filter States
                val selectedDateFilter by viewModel.selectedDateFilter.collectAsStateWithLifecycle()
                val filterOnlyAvailable by viewModel.filterOnlyAvailable.collectAsStateWithLifecycle()
                val filterLessonType by viewModel.filterLessonType.collectAsStateWithLifecycle()
                val studentSearchQuery by viewModel.studentSearchQuery.collectAsStateWithLifecycle()
                val studentLevelFilter by viewModel.studentLevelFilter.collectAsStateWithLifecycle()

                // Dialog & Sheet States
                var showAddSlotDialog by remember { mutableStateOf(false) }
                var showStandardDayDialog by remember { mutableStateOf(false) }
                var showPinSettingsDialog by remember { mutableStateOf(false) }
                var showSyncSettingsDialog by remember { mutableStateOf(false) }
                var dateForStandardDay by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())) }
                var initialDateForSlotDialog by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())) }
                var slotToEdit by remember { mutableStateOf<LessonSlotEntity?>(null) }
                var showAddStudentDialog by remember { mutableStateOf(false) }
                var studentToEdit by remember { mutableStateOf<StudentEntity?>(null) }
                var slotToEnrollStudent by remember { mutableStateOf<SlotWithBookings?>(null) }
                var slotForWeatherAlert by remember { mutableStateOf<SlotWithBookings?>(null) }
                var studentForProgress by remember { mutableStateOf<StudentEntity?>(null) }
                var selectedDayForDetail by remember { mutableStateOf<String?>(null) }

                // Navigation Bar Tab: 0 = Calendrier Visuel, 1 = Liste Créneaux, 2 = Élèves & Stats
                var currentTab by remember { mutableIntStateOf(0) }

                // Snackbar Host State
                val snackbarHostState = remember { SnackbarHostState() }

                // Collect Feedback Messages
                LaunchedEffect(Unit) {
                    viewModel.feedbackMessage.collect { message ->
                        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                    }
                }

                fun openWhatsAppDirect(text: String) {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, text)
                        type = "text/plain"
                        `package` = "com.whatsapp"
                    }
                    try {
                        context.startActivity(sendIntent)
                    } catch (e: Exception) {
                        val chooser = Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            },
                            "Partager via"
                        )
                        try {
                            context.startActivity(chooser)
                        } catch (ex: Exception) {
                            Toast.makeText(context, "Impossible d'ouvrir le partage", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // If in Student Mode (Version Élève)
                if (isStudentMode) {
                    StudentPortalScreen(
                        slots = slotsWithBookings,
                        savedProfile = savedProfile,
                        onSaveProfile = { first, last, phone, level ->
                            viewModel.saveStudentProfile(first, last, phone, level)
                        },
                        onRegisterSelf = { slotId, firstName, lastName, phone, email, level ->
                            viewModel.registerStudentSelf(slotId, firstName, lastName, phone, email, level)
                        },
                        onUnenroll = { slotId, studentId ->
                            viewModel.unenrollStudent(slotId, studentId)
                        },
                        allStudents = allStudents,
                        allProgress = allProgress,
                        onExportStudentBookletPdf = { student ->
                            viewModel.exportStudentBookletPdf(student)
                        },
                        onExportStudentBookletIcs = { student ->
                            viewModel.exportStudentBookletIcs(student)
                        },
                        onVerifyPin = { pin ->
                            viewModel.verifyInstructorPin(pin)
                        },
                        onSwitchToInstructorMode = {
                            viewModel.setAppMode(false)
                        },
                        syncStatus = syncStatus,
                        syncStatusMsg = syncStatusMsg,
                        lastSyncTime = lastSyncTime,
                        schoolCode = schoolCode,
                        onSaveSchoolCode = { viewModel.setSchoolCode(it) },
                        syncedSlotsCount = syncedSlotsCount,
                        syncedStudentsCount = syncedStudentsCount,
                        syncedBookingsCount = syncedBookingsCount,
                        onForceSync = {
                            viewModel.forceSync()
                        },
                        onShareSchoolCode = {
                            openWhatsAppDirect(viewModel.generateSchoolShareText())
                        }
                    )
                } else {
                    // Instructor Mode
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        topBar = {
                            Surface(
                                color = HighDensityHeaderTitle,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("🪂", fontSize = 20.sp)
                                        Column {
                                            val statusDotColor = when (syncStatus) {
                                                com.example.data.cloud.SyncStatus.CONNECTED_SYNCED -> Color(0xFF34D399)
                                                com.example.data.cloud.SyncStatus.SYNCING -> Color(0xFFFBBF24)
                                                com.example.data.cloud.SyncStatus.CONNECTING -> Color(0xFF60A5FA)
                                                com.example.data.cloud.SyncStatus.OFFLINE, com.example.data.cloud.SyncStatus.ERROR -> Color(0xFFEF4444)
                                            }
                                            val statusBgColor = when (syncStatus) {
                                                com.example.data.cloud.SyncStatus.CONNECTED_SYNCED -> Color(0xFF10B981).copy(alpha = 0.25f)
                                                com.example.data.cloud.SyncStatus.SYNCING -> Color(0xFFF59E0B).copy(alpha = 0.25f)
                                                com.example.data.cloud.SyncStatus.CONNECTING -> Color(0xFF3B82F6).copy(alpha = 0.25f)
                                                com.example.data.cloud.SyncStatus.OFFLINE, com.example.data.cloud.SyncStatus.ERROR -> Color(0xFFDC2626).copy(alpha = 0.25f)
                                            }
                                            val statusBorderColor = when (syncStatus) {
                                                com.example.data.cloud.SyncStatus.CONNECTED_SYNCED -> Color(0xFF10B981)
                                                com.example.data.cloud.SyncStatus.SYNCING -> Color(0xFFF59E0B)
                                                com.example.data.cloud.SyncStatus.CONNECTING -> Color(0xFF3B82F6)
                                                com.example.data.cloud.SyncStatus.OFFLINE, com.example.data.cloud.SyncStatus.ERROR -> Color(0xFFDC2626)
                                            }
                                            val statusText = when (syncStatus) {
                                                com.example.data.cloud.SyncStatus.CONNECTED_SYNCED -> if (lastSyncTime.isNotBlank()) "Direct $lastSyncTime" else "En direct"
                                                com.example.data.cloud.SyncStatus.SYNCING -> "Sync..."
                                                com.example.data.cloud.SyncStatus.CONNECTING -> "Connexion..."
                                                com.example.data.cloud.SyncStatus.OFFLINE, com.example.data.cloud.SyncStatus.ERROR -> "Hors-ligne"
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    "Planning & Élèves",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = Color.White
                                                )
                                                // Sync Live Indicator
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = statusBgColor,
                                                    border = BorderStroke(1.dp, statusBorderColor),
                                                    modifier = Modifier.clickable { showSyncSettingsDialog = true }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .background(statusDotColor, CircleShape)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(statusText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                }
                                            }
                                            Text(
                                                "École: $schoolCode • Gonflage & Vol",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.85f)
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // Sync settings dialog button
                                        IconButton(
                                            onClick = { showSyncSettingsDialog = true },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.CloudSync, contentDescription = "Paramètres de synchronisation", tint = Color.White)
                                        }

                                        // Refresh / Force Sync button
                                        IconButton(
                                            onClick = { viewModel.forceSync() },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Actualiser Cloud", tint = Color.White)
                                        }

                                        // Security / PIN Settings button
                                        IconButton(
                                            onClick = { showPinSettingsDialog = true },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Security, contentDescription = "Sécurité & PIN", tint = Color.White)
                                        }

                                        // Switch to Student Mode button (Lock for students)
                                        OutlinedButton(
                                            onClick = { viewModel.setAppMode(true) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("👁️ Version Élève", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // WhatsApp share planning button
                                        Button(
                                            onClick = { openWhatsAppDirect(viewModel.getWhatsAppText()) },
                                            colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("💬", fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                    selected = currentTab == 0,
                                    onClick = { currentTab = 0 },
                                    icon = {
                                        Icon(
                                            if (currentTab == 0) Icons.Default.CalendarMonth else Icons.Outlined.CalendarMonth,
                                            contentDescription = "Planning Visuel"
                                        )
                                    },
                                    label = { Text("Visuel", fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryBlue,
                                        selectedTextColor = PrimaryBlue,
                                        indicatorColor = PrimaryBlueContainer
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentTab == 1,
                                    onClick = { currentTab = 1 },
                                    icon = {
                                        Icon(
                                            if (currentTab == 1) Icons.Default.FormatListBulleted else Icons.Outlined.FormatListBulleted,
                                            contentDescription = "Liste Créneaux"
                                        )
                                    },
                                    label = { Text("Créneaux", fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryBlue,
                                        selectedTextColor = PrimaryBlue,
                                        indicatorColor = PrimaryBlueContainer
                                    )
                                )

                                NavigationBarItem(
                                    selected = currentTab == 2,
                                    onClick = { currentTab = 2 },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (allStudents.isNotEmpty()) {
                                                    Badge { Text("${allStudents.size}") }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                if (currentTab == 2) Icons.Default.People else Icons.Outlined.People,
                                                contentDescription = "Élèves & Stats"
                                            )
                                        }
                                    },
                                    label = { Text("Élèves & Stats", fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryBlue,
                                        selectedTextColor = PrimaryBlue,
                                        indicatorColor = PrimaryBlueContainer
                                    )
                                )
                            }
                        },
                        floatingActionButton = {
                            when (currentTab) {
                                0, 1 -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // ⚡ Quick Standard Day FAB
                                        FloatingActionButton(
                                            onClick = {
                                                dateForStandardDay = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
                                                showStandardDayDialog = true
                                            },
                                            containerColor = Color(0xFFD97706),
                                            contentColor = Color.White
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Journée Type", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // New Single Slot FAB
                                        FloatingActionButton(
                                            onClick = {
                                                slotToEdit = null
                                                initialDateForSlotDialog = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
                                                showAddSlotDialog = true
                                            },
                                            containerColor = PrimaryBlue,
                                            contentColor = Color.White
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Nouveau Créneau")
                                        }
                                    }
                                }
                                2 -> {
                                    FloatingActionButton(
                                        onClick = {
                                            studentToEdit = null
                                            showAddStudentDialog = true
                                        },
                                        containerColor = PrimaryBlue,
                                        contentColor = Color.White
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = "Ajouter un élève")
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (currentTab) {
                                0 -> VisualCalendarPlanningScreen(
                                    slots = slotsWithBookings,
                                    onSelectDay = { dateIso ->
                                        selectedDayForDetail = dateIso
                                    },
                                    onOpenAddSlotForDate = { dateIso ->
                                        slotToEdit = null
                                        initialDateForSlotDialog = dateIso
                                        showAddSlotDialog = true
                                    },
                                    onOpenWhatsAppShare = {
                                        openWhatsAppDirect(viewModel.getWhatsAppText())
                                    },
                                    onOpenEnrollStudent = { slotItem ->
                                        slotToEnrollStudent = slotItem
                                    },
                                    onUnenrollStudent = { slotId, studentId ->
                                        viewModel.unenrollStudent(slotId, studentId)
                                    },
                                    onToggleAttendance = { bookingId, studentId, attended ->
                                        viewModel.toggleAttendance(bookingId, studentId, attended)
                                    },
                                    onEditSlot = { slot ->
                                        slotToEdit = slot
                                        initialDateForSlotDialog = slot.dateIso
                                        showAddSlotDialog = true
                                    },
                                    onDeleteSlot = { slotId ->
                                        viewModel.deleteSlot(slotId)
                                    },
                                    onOpenStandardDayForDate = { dateIso ->
                                        dateForStandardDay = dateIso
                                        showStandardDayDialog = true
                                    },
                                    onOpenWeatherAlert = { slotItem ->
                                        slotForWeatherAlert = slotItem
                                    },
                                    onAddToCalendar = { slotItem ->
                                        viewModel.addSlotToCalendar(slotItem.slot)
                                    }
                                )

                                1 -> PlanningScreen(
                                    slots = filteredSlots,
                                    selectedDateFilter = selectedDateFilter,
                                    onDateFilterChange = { df -> viewModel.setDateFilter(df) },
                                    selectedTypeFilter = filterLessonType,
                                    onTypeFilterChange = { tf -> viewModel.setLessonTypeFilter(tf) },
                                    onlyAvailable = filterOnlyAvailable,
                                    onToggleOnlyAvailable = { viewModel.toggleFilterOnlyAvailable() },
                                    onOpenAddSlot = {
                                        slotToEdit = null
                                        initialDateForSlotDialog = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
                                        showAddSlotDialog = true
                                    },
                                    onOpenEnrollStudent = { slotItem ->
                                        slotToEnrollStudent = slotItem
                                    },
                                    onUnenrollStudent = { slotId, studentId ->
                                        viewModel.unenrollStudent(slotId, studentId)
                                    },
                                    onToggleAttendance = { bookingId, studentId, attended ->
                                        viewModel.toggleAttendance(bookingId, studentId, attended)
                                    },
                                    onEditSlot = { slot ->
                                        slotToEdit = slot
                                        initialDateForSlotDialog = slot.dateIso
                                        showAddSlotDialog = true
                                    },
                                    onDeleteSlot = { slotId ->
                                        viewModel.deleteSlot(slotId)
                                    },
                                    onOpenWeatherAlert = { slotItem ->
                                        slotForWeatherAlert = slotItem
                                    },
                                    onAddToCalendar = { slot ->
                                        viewModel.addSlotToCalendar(slot)
                                    }
                                )

                                2 -> StudentsScreen(
                                    studentsStats = filteredStudentsWithStats,
                                    progressList = allProgress,
                                    searchQuery = studentSearchQuery,
                                    onSearchQueryChange = { q -> viewModel.setStudentSearchQuery(q) },
                                    selectedLevelFilter = studentLevelFilter,
                                    onLevelFilterChange = { l -> viewModel.setStudentLevelFilter(l) },
                                    onOpenAddStudent = {
                                        studentToEdit = null
                                        showAddStudentDialog = true
                                    },
                                    onEditStudent = { s ->
                                        studentToEdit = s
                                        showAddStudentDialog = true
                                    },
                                    onDeleteStudent = { s ->
                                        viewModel.deleteStudent(s)
                                    },
                                    onOpenProgress = { student ->
                                        studentForProgress = student
                                    }
                                )
                            }
                        }
                    }
                }

                // Modal Detail Sheet when user taps a calendar day (Instructor view)
                selectedDayForDetail?.let { dateIso ->
                    val daySlots = slotsWithBookings.filter { it.slot.dateIso == dateIso }
                    DayDetailSheet(
                        dateIso = dateIso,
                        slots = daySlots,
                        onDismiss = { selectedDayForDetail = null },
                        onOpenAddSlot = {
                            slotToEdit = null
                            initialDateForSlotDialog = dateIso
                            showAddSlotDialog = true
                        },
                        onOpenCreateStandardDay = {
                            dateForStandardDay = dateIso
                            showStandardDayDialog = true
                        },
                        onOpenEnrollStudent = { slotItem ->
                            slotToEnrollStudent = slotItem
                        },
                        onUnenrollStudent = { slotId, studentId ->
                            viewModel.unenrollStudent(slotId, studentId)
                        },
                        onToggleAttendance = { bookingId, studentId, attended ->
                            viewModel.toggleAttendance(bookingId, studentId, attended)
                        },
                        onEditSlot = { slot ->
                            slotToEdit = slot
                            initialDateForSlotDialog = slot.dateIso
                            showAddSlotDialog = true
                        },
                        onDeleteSlot = { slotId ->
                            viewModel.deleteSlot(slotId)
                        },
                        onOpenWeatherAlert = { slotItem ->
                            slotForWeatherAlert = slotItem
                        },
                        onAddToCalendar = { slot ->
                            viewModel.addSlotToCalendar(slot)
                        }
                    )
                }

                // Dialog: Standard Day Generation & Capacity Configuration
                if (showStandardDayDialog) {
                    StandardDayDialog(
                        initialDateIso = dateForStandardDay,
                        initialConfig = savedStandardDayConfig,
                        onDismiss = { showStandardDayDialog = false },
                        onConfirm = { dateIso, config, saveAsDefault ->
                            viewModel.createStandardDay(dateIso, config)
                            if (saveAsDefault) {
                                viewModel.saveDefaultStandardDayConfig(config)
                            }
                            showStandardDayDialog = false
                        }
                    )
                }

                // Dialog: Instructor PIN & Student Sharing Settings
                if (showPinSettingsDialog) {
                    var newPin by remember { mutableStateOf(instructorPin) }

                    AlertDialog(
                        onDismissRequest = { showPinSettingsDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🔒", fontSize = 22.sp)
                                Text("Protection Mode Moniteur", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    "Pour envoyer l'application à vos élèves en toute sécurité sans qu'ils puissent modifier vos créneaux ou voir les coordonnées des autres élèves, l'accès Moniteur est verrouillé par un code PIN.",
                                    fontSize = 12.sp,
                                    color = SecondaryText
                                )

                                OutlinedTextField(
                                    value = newPin,
                                    onValueChange = { if (it.length <= 6) newPin = it },
                                    label = { Text("Code PIN Moniteur (4 à 6 chiffres)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = GreenSuccessBg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("✅ Mode Élève Sécurisé :", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GreenSuccess)
                                        Text("• Visuel Mois / Trimestre / Année interactif\n• Inscription en 1 clic\n• Données personnelles protégées", fontSize = 10.sp, color = HighDensityHeaderTitle)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (newPin.length >= 4) {
                                        viewModel.setInstructorPin(newPin)
                                        showPinSettingsDialog = false
                                    } else {
                                        Toast.makeText(context, "Le code PIN doit comporter au moins 4 chiffres", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Enregistrer le PIN", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPinSettingsDialog = false }) {
                                Text("Fermer")
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = HighDensitySurface
                    )
                }

                // Dialog: Add / Edit Single Slot
                if (showAddSlotDialog) {
                    AddOrEditSlotDialog(
                        initialDate = initialDateForSlotDialog,
                        initialSlot = slotToEdit,
                        onDismiss = {
                            showAddSlotDialog = false
                            slotToEdit = null
                        },
                        onConfirm = { dateIso, startTime, endTime, title, lessonType, location, maxCapacity, notes ->
                            if (slotToEdit == null) {
                                viewModel.createSlot(
                                    dateIso = dateIso,
                                    startTime = startTime,
                                    endTime = endTime,
                                    title = title,
                                    lessonType = lessonType,
                                    location = location,
                                    maxCapacity = maxCapacity,
                                    notes = notes
                                )
                            } else {
                                val updated = slotToEdit!!.copy(
                                    dateIso = dateIso,
                                    startTime = startTime,
                                    endTime = endTime,
                                    title = title,
                                    lessonType = lessonType,
                                    location = location,
                                    maxCapacity = maxCapacity,
                                    notes = notes
                                )
                                viewModel.updateSlot(updated)
                            }
                            showAddSlotDialog = false
                            slotToEdit = null
                        }
                    )
                }

                // Dialog: Add / Edit Student
                if (showAddStudentDialog) {
                    AddOrEditStudentDialog(
                        initialStudent = studentToEdit,
                        onDismiss = {
                            showAddStudentDialog = false
                            studentToEdit = null
                        },
                        onConfirm = { id, firstName, lastName, phone, email, level, notes ->
                            viewModel.saveStudent(
                                id = id,
                                firstName = firstName,
                                lastName = lastName,
                                phone = phone,
                                email = email,
                                level = level,
                                notes = notes
                            )
                            showAddStudentDialog = false
                            studentToEdit = null
                        }
                    )
                }

                // Dialog: Enroll Student in Slot
                slotToEnrollStudent?.let { slotItem ->
                    EnrollStudentDialog(
                        slotItem = slotItem,
                        allStudents = allStudents,
                        onDismiss = { slotToEnrollStudent = null },
                        onEnroll = { slotId, studentId, isWaitingList ->
                            viewModel.enrollStudent(slotId, studentId, isWaitingList)
                            slotToEnrollStudent = null
                        }
                    )
                }

                // Dialog: Weather Alert & Slot Cancellation
                slotForWeatherAlert?.let { slotItem ->
                    WeatherAlertDialog(
                        slot = slotItem.slot,
                        enrolledStudents = slotItem.bookings.map { it.student },
                        onDismiss = { slotForWeatherAlert = null },
                        onConfirm = { isCancelled, weatherAlert, cancelReason, postponedTo, broadcastWhatsApp ->
                            viewModel.updateSlotWeather(
                                slot = slotItem.slot,
                                enrolledStudents = slotItem.bookings.map { it.student },
                                isCancelled = isCancelled,
                                weatherAlert = weatherAlert,
                                cancelReason = cancelReason,
                                postponedTo = postponedTo,
                                broadcastNotification = true
                            )
                            if (broadcastWhatsApp) {
                                val text = viewModel.generateWeatherAlertWhatsApp(
                                    slot = slotItem.slot.copy(
                                        isCancelled = isCancelled,
                                        weatherAlert = weatherAlert,
                                        cancelReason = cancelReason,
                                        postponedTo = postponedTo
                                    ),
                                    enrolledStudents = slotItem.bookings.map { it.student }
                                )
                                openWhatsAppDirect(text)
                            }
                            slotForWeatherAlert = null
                        }
                    )
                }

                // Dialog: Student FFPLUM Progress & Booklet
                studentForProgress?.let { student ->
                    val prog = allProgress.find { it.studentId == student.id }
                        ?: StudentProgressEntity(studentId = student.id)
                    StudentProgressDialog(
                        student = student,
                        initialProgress = prog,
                        onDismiss = { studentForProgress = null },
                        onSaveProgress = { updatedProgress ->
                            viewModel.saveStudentProgress(updatedProgress)
                            studentForProgress = null
                        },
                        onExportPdf = { s, _ ->
                            viewModel.exportStudentBookletPdf(s)
                        },
                        onShareWhatsApp = { text ->
                            openWhatsAppDirect(text)
                        }
                    )
                }

                // Dialog: Cloud Sync & Multi-device Settings
                SyncSettingsDialog(
                    isOpen = showSyncSettingsDialog,
                    onDismiss = { showSyncSettingsDialog = false },
                    currentSchoolCode = schoolCode,
                    onSaveSchoolCode = { viewModel.setSchoolCode(it) },
                    syncStatus = syncStatus,
                    syncStatusMsg = syncStatusMsg,
                    lastSyncTime = lastSyncTime,
                    syncedSlotsCount = syncedSlotsCount,
                    syncedStudentsCount = syncedStudentsCount,
                    syncedBookingsCount = syncedBookingsCount,
                    onForceSync = { viewModel.forceSync() },
                    onShareSchoolCode = { openWhatsAppDirect(viewModel.generateSchoolShareText()) }
                )

                // Dialog: PIN Code Security Settings
                PinSettingsDialog(
                    isOpen = showPinSettingsDialog,
                    onDismiss = { showPinSettingsDialog = false },
                    currentPin = instructorPin,
                    onSavePin = { viewModel.setInstructorPin(it) },
                    onResetPin = { viewModel.resetInstructorPin() }
                )
            }
        }
    }
}
