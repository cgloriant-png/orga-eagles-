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
import com.example.data.model.StudentEntity
import com.example.ui.theme.*

@Composable
fun StudentsScreen(
    students: List<StudentEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedLevelFilter: String?,
    onLevelFilterChange: (String?) -> Unit,
    onOpenAddStudent: () -> Unit,
    onEditStudent: (StudentEntity) -> Unit,
    onDeleteStudent: (StudentEntity) -> Unit
) {
    val levels = listOf("Gonflage", "Vol", "Perf")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HighDensityBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search bar & Add button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Rechercher un participant...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = onOpenAddStudent,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ajouter", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Level Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedLevelFilter == null,
                onClick = { onLevelFilterChange(null) },
                label = { Text("Tous (${students.size})", fontSize = 11.sp) },
                shape = RoundedCornerShape(8.dp)
            )

            levels.forEach { lvl ->
                val count = students.count { it.level.equals(lvl, ignoreCase = true) }
                FilterChip(
                    selected = selectedLevelFilter == lvl,
                    onClick = { onLevelFilterChange(if (selectedLevelFilter == lvl) null else lvl) },
                    label = { Text("$lvl ($count)", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Student List
        if (students.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("👥", fontSize = 32.sp)
                    Text("Aucun participant trouvé", fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                    Text("Ajoutez des élèves pour pouvoir les inscrire aux créneaux.", fontSize = 12.sp, color = SecondaryText)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                items(students, key = { it.id }) { student ->
                    StudentCard(
                        student = student,
                        onEdit = { onEditStudent(student) },
                        onDelete = { onDeleteStudent(student) }
                    )
                }
            }
        }
    }
}

@Composable
fun StudentCard(
    student: StudentEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
        border = BorderStroke(1.dp, BorderOutline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                    color = PrimaryBlue,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(student.initials, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = student.fullName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = HighDensityHeaderTitle
                        )

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (student.level) {
                                "Gonflage" -> Color(0xFFE0F2FE)
                                "Vol" -> Color(0xFFDCFCE7)
                                "Perf" -> Color(0xFFFFEDD5)
                                else -> HighDensityNavBar
                            }
                        ) {
                            Text(
                                text = student.level,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = HighDensityHeaderTitle,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (student.phone.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(12.dp), tint = SecondaryText)
                            Text(student.phone, fontSize = 11.sp, color = SecondaryText)
                        }
                    }

                    if (student.notes.isNotBlank()) {
                        Text("📝 ${student.notes}", fontSize = 10.sp, color = SecondaryText, maxLines = 1)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = PrimaryBlueDark, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = RedAlertText, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
