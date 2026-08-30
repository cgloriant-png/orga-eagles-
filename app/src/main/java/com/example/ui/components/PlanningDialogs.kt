package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlanningLessonType
import com.example.data.model.SlotWithBookings
import com.example.data.model.StudentEntity
import com.example.ui.theme.*

@Composable
fun AddOrEditSlotDialog(
    initialDate: String = "",
    selectedDates: List<String> = emptyList(),
    initialSlot: com.example.data.model.LessonSlotEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        dateIso: String,
        startTime: String,
        endTime: String,
        title: String,
        lessonType: String,
        location: String,
        maxCapacity: Int,
        notes: String
    ) -> Unit,
    onConfirmForDates: ((
        dates: List<String>,
        startTime: String,
        endTime: String,
        title: String,
        lessonType: String,
        location: String,
        maxCapacity: Int,
        notes: String
    ) -> Unit)? = null
) {
    val datesList = remember(initialDate, selectedDates) {
        if (selectedDates.isNotEmpty()) selectedDates else if (initialDate.isNotBlank()) listOf(initialDate) else listOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.FRANCE).format(java.util.Date()))
    }
    val isMultiDate = datesList.size > 1

    var dateIso by remember { mutableStateOf(initialSlot?.dateIso ?: datesList.firstOrNull() ?: initialDate) }
    var startTime by remember { mutableStateOf(initialSlot?.startTime ?: "08:00") }
    var endTime by remember { mutableStateOf(initialSlot?.endTime ?: "11:30") }
    var title by remember { mutableStateOf(initialSlot?.title ?: "") }
    var selectedType by remember {
        mutableStateOf(
            if (initialSlot != null) PlanningLessonType.fromCode(initialSlot.lessonType)
            else PlanningLessonType.GONFLAGE
        )
    }
    var location by remember { mutableStateOf(initialSlot?.location ?: "Plouharnel (56)") }
    var maxCapacity by remember { mutableIntStateOf(initialSlot?.maxCapacity ?: selectedType.defaultCapacity) }
    var notes by remember { mutableStateOf(initialSlot?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    if (isMultiDate) "Créer un créneau sur ${datesList.size} jours" else if (initialSlot == null) "Créer un créneau" else "Modifier le créneau",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = HighDensityHeaderTitle
                )
                if (isMultiDate) {
                    Text(
                        "${datesList.size} dates sélectionnées",
                        fontSize = 12.sp,
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
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isMultiDate) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = HighDensityNavBar,
                        border = BorderStroke(0.5.dp, PrimaryBlue.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("📅 Dates concernées :", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                            Text(datesList.joinToString(", "), fontSize = 10.sp, color = PrimaryBlue)
                        }
                    }
                }

                // Type Selector: Gonflage, Vol, Perf
                Text("Type de créneau :", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlanningLessonType.entries.forEach { type ->
                        val isSelected = selectedType == type
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) PrimaryBlue else HighDensityNavBar,
                            border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else BorderOutline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedType = type
                                    if (initialSlot == null) {
                                        maxCapacity = type.defaultCapacity
                                        title = "Créneau ${type.label}"
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(type.emoji, fontSize = 18.sp)
                                Text(
                                    type.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else HighDensityHeaderTitle
                                )
                            }
                        }
                    }
                }

                if (!isMultiDate) {
                    // Date & Time Inputs
                    OutlinedTextField(
                        value = dateIso,
                        onValueChange = { dateIso = it },
                        label = { Text("Date (AAAA-MM-JJ)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Début (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("Fin (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Capacity counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Capacité max (places) :", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { if (maxCapacity > 1) maxCapacity-- },
                            modifier = Modifier.size(32.dp).background(HighDensityNavBar, CircleShape)
                        ) {
                            Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text("$maxCapacity", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(
                            onClick = { maxCapacity++ },
                            modifier = Modifier.size(32.dp).background(HighDensityNavBar, CircleShape)
                        ) {
                            Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Lieu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Informations pour les élèves") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.ifBlank { "Créneau ${selectedType.label}" }
                    if (isMultiDate && onConfirmForDates != null) {
                        onConfirmForDates(
                            datesList,
                            startTime,
                            endTime,
                            finalTitle,
                            selectedType.code,
                            location,
                            maxCapacity,
                            notes
                        )
                    } else {
                        onConfirm(
                            dateIso,
                            startTime,
                            endTime,
                            finalTitle,
                            selectedType.code,
                            location,
                            maxCapacity,
                            notes
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(
                    if (isMultiDate) "Créer sur ${datesList.size} jours" else if (initialSlot == null) "Créer le créneau" else "Enregistrer"
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun EnrollStudentDialog(
    slotItem: SlotWithBookings,
    allStudents: List<StudentEntity>,
    onDismiss: () -> Unit,
    onEnroll: (slotId: Long, studentId: Long, isWaitingList: Boolean) -> Unit
) {
    val enrolledIds = slotItem.enrolledStudentIds
    val availableStudents = allStudents.filter { !enrolledIds.contains(it.id) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredStudents = availableStudents.filter {
        searchQuery.isBlank() || it.fullName.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery)
    }

    val isFull = slotItem.isFull

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Inscrire un participant",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = HighDensityHeaderTitle
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                if (isFull) {
                    Surface(
                        color = AmberAccent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            "⚠️ Créneau complet ! L'élève sera placé en liste d'attente.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberAccent,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher un participant...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredStudents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Aucun élève disponible à inscrire.", fontSize = 12.sp, color = SecondaryText)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredStudents, key = { it.id }) { student ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = HighDensityNavBar,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onEnroll(slotItem.slot.id, student.id, isFull)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(shape = CircleShape, color = PrimaryBlue, modifier = Modifier.size(24.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(student.initials, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                        Column {
                                            Text(student.fullName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(student.level, fontSize = 10.sp, color = SecondaryText)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            onEnroll(slotItem.slot.id, student.id, isFull)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFull) AmberAccent else PrimaryBlue
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(if (isFull) "+ Attente" else "+ Inscrire", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

@Composable
fun AddOrEditStudentDialog(
    initialStudent: StudentEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        id: Long,
        firstName: String,
        lastName: String,
        phone: String,
        email: String,
        level: String,
        notes: String
    ) -> Unit
) {
    var firstName by remember { mutableStateOf(initialStudent?.firstName ?: "") }
    var lastName by remember { mutableStateOf(initialStudent?.lastName ?: "") }
    var phone by remember { mutableStateOf(initialStudent?.phone ?: "") }
    var email by remember { mutableStateOf(initialStudent?.email ?: "") }
    var selectedLevel by remember { mutableStateOf(initialStudent?.level ?: "Gonflage") }
    var notes by remember { mutableStateOf(initialStudent?.notes ?: "") }

    val levelOptions = listOf("Gonflage", "Vol", "Perf")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialStudent == null) "Ajouter un participant" else "Modifier le profil",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = HighDensityHeaderTitle
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("Prénom") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Nom") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Niveau / Activité :", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    levelOptions.forEach { lvl ->
                        val isSelected = selectedLevel == lvl
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) PrimaryBlue else HighDensityNavBar,
                            border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else BorderOutline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedLevel = lvl }
                        ) {
                            Text(
                                text = lvl,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else HighDensityHeaderTitle,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Téléphone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes & Remarques") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (firstName.isNotBlank() || lastName.isNotBlank()) {
                        onConfirm(
                            initialStudent?.id ?: 0L,
                            firstName,
                            lastName,
                            phone,
                            email,
                            selectedLevel,
                            notes
                        )
                    }
                },
                enabled = firstName.isNotBlank() || lastName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(if (initialStudent == null) "Ajouter" else "Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
