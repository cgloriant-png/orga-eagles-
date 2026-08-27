package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    onRegisterSelf: (slotId: Long, firstName: String, lastName: String, phone: String, email: String, level: String, onComplete: (StudentEntity, LessonSlotEntity, String) -> Unit) -> Unit,
    onUnenroll: (slotId: Long, studentId: Long) -> Unit,
    allStudents: List<StudentEntity>,
    onSwitchToInstructorMode: () -> Unit
) {
    val context = LocalContext.current
    var showProfileModal by remember { mutableStateOf(!savedProfile.isConfigured) }
    var slotToRegister by remember { mutableStateOf<SlotWithBookings?>(null) }
    var selectedDateFilter by remember { mutableStateOf<String?>("TOUS") }
    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }

    // Date filters list
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

    // Current student entity if found
    val currentStudentEntity = allStudents.find {
        it.phone.isNotBlank() && it.phone.replace(" ", "") == savedProfile.phone.replace(" ", "")
    } ?: allStudents.find {
        it.firstName.equals(savedProfile.firstName, ignoreCase = true) && it.lastName.equals(savedProfile.lastName, ignoreCase = true)
    }

    val currentStudentId = currentStudentEntity?.id

    // Filtered slots for student view
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

    fun openWhatsAppShare(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            `package` = "com.whatsapp"
        }
        try {
            context.startActivity(sendIntent)
        } catch (_: Exception) {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                },
                "Envoyer mon inscription au moniteur"
            )
            try { context.startActivity(chooser) } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HighDensityBg)
    ) {
        // Student Mode Top Banner
        Surface(
            color = PrimaryBlueDark,
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🪂", fontSize = 16.sp)
                    Text("Espace Élève", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                }

                TextButton(
                    onClick = onSwitchToInstructorMode,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.9f))
                ) {
                    Text("🧑‍🏫 Mode Moniteur", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Student Profile Header Card
        Card(
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
            border = BorderStroke(1.dp, BorderOutline.copy(alpha = 0.4f)),
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (savedProfile.isConfigured) PrimaryBlue else Color(0xFFD97706),
                        modifier = Modifier.size(36.dp)
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
                            Text("Profil non renseigné", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFD97706))
                            Text("Cliquez pour saisir vos coordonnées", fontSize = 11.sp, color = SecondaryText)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showProfileModal = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(if (savedProfile.isConfigured) "Modifier" else "Configurer", fontSize = 11.sp)
                }
            }
        }

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

        // Slots List
        if (displaySlots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📅", fontSize = 32.sp)
                    Text("Aucun créneau ouvert pour cette période", fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                    Text("Sélectionnez une autre date ou revenez bientôt.", fontSize = 12.sp, color = SecondaryText)
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
                    val slot = slotItem.slot
                    val type = PlanningLessonType.fromCode(slot.lessonType)
                    val isAlreadyEnrolled = currentStudentId != null && slotItem.enrolledStudentIds.contains(currentStudentId)
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
                                    Text("Vous êtes sur ce créneau", fontSize = 11.sp, color = GreenSuccess, fontWeight = FontWeight.Bold)
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
                                    onClick = {
                                        if (!savedProfile.isConfigured) {
                                            slotToRegister = slotItem
                                            showProfileModal = true
                                        } else {
                                            onRegisterSelf(
                                                slot.id,
                                                savedProfile.firstName,
                                                savedProfile.lastName,
                                                savedProfile.phone,
                                                "",
                                                savedProfile.level
                                            ) { _, _, shareText ->
                                                openWhatsAppShare(shareText)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFull) Color(0xFFD97706) else GreenSuccess
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isFull) {
                                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Demander liste d'attente (1 clic)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
            }
        }
    }

    // Modal Profile & First Registration
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
                    Text("Vos Coordonnées Élève", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Ces informations permettent au moniteur de vous identifier et de vous contacter pour les créneaux.",
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
                            ) { _, _, shareText ->
                                openWhatsAppShare(shareText)
                            }
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
