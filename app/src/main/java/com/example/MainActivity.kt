package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
                val slotsWithBookings by viewModel.slotsWithBookings.collectAsStateWithLifecycle()
                val filteredSlots by viewModel.filteredSlots.collectAsStateWithLifecycle()
                val filteredStudentsWithStats by viewModel.filteredStudentsWithStats.collectAsStateWithLifecycle()
                val isStudentMode by viewModel.isStudentMode.collectAsStateWithLifecycle()
                val savedProfile by viewModel.savedProfile.collectAsStateWithLifecycle()

                // Filter States
                val selectedDateFilter by viewModel.selectedDateFilter.collectAsStateWithLifecycle()
                val filterOnlyAvailable by viewModel.filterOnlyAvailable.collectAsStateWithLifecycle()
                val filterLessonType by viewModel.filterLessonType.collectAsStateWithLifecycle()
                val studentSearchQuery by viewModel.studentSearchQuery.collectAsStateWithLifecycle()
                val studentLevelFilter by viewModel.studentLevelFilter.collectAsStateWithLifecycle()

                // Dialog & Sheet States
                var showAddSlotDialog by remember { mutableStateOf(false) }
                var showStandardDayDialog by remember { mutableStateOf(false) }
                var dateForStandardDay by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())) }
                var initialDateForSlotDialog by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())) }
                var slotToEdit by remember { mutableStateOf<LessonSlotEntity?>(null) }
                var showAddStudentDialog by remember { mutableStateOf(false) }
                var studentToEdit by remember { mutableStateOf<StudentEntity?>(null) }
                var slotToEnrollStudent by remember { mutableStateOf<SlotWithBookings?>(null) }
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
                        onRegisterSelf = { slotId, firstName, lastName, phone, email, level, onComplete ->
                            viewModel.registerStudentSelf(slotId, firstName, lastName, phone, email, level, onComplete)
                        },
                        onUnenroll = { slotId, studentId ->
                            viewModel.unenrollStudent(slotId, studentId)
                        },
                        allStudents = allStudents,
                        onSwitchToInstructorMode = {
                            viewModel.setAppMode(false)
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
                                            Text(
                                                "Planning & Élèves",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                "Gonflage • Vol • Perf",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.75f)
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Quick Student Mode Switch Button
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
                                    }
                                )

                                2 -> StudentsScreen(
                                    studentsStats = filteredStudentsWithStats,
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
                                    }
                                )
                            }
                        }
                    }
                }

                // Modal Detail Sheet when user taps a calendar day
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
                        }
                    )
                }

                // Dialog: Standard Day Generation
                if (showStandardDayDialog) {
                    StandardDayDialog(
                        initialDateIso = dateForStandardDay,
                        onDismiss = { showStandardDayDialog = false },
                        onConfirm = { dateIso, config ->
                            viewModel.createStandardDay(dateIso, config)
                            showStandardDayDialog = false
                        }
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
            }
        }
    }
}
