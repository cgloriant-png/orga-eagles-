package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlanningLessonType
import com.example.data.model.StudentEntity
import com.example.data.model.StudentWithStats
import com.example.ui.theme.*

@Composable
fun StudentsScreen(
    studentsStats: List<StudentWithStats>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedLevelFilter: String?,
    onLevelFilterChange: (String?) -> Unit,
    onOpenAddStudent: () -> Unit,
    onEditStudent: (StudentEntity) -> Unit,
    onDeleteStudent: (StudentEntity) -> Unit
) {
    val context = LocalContext.current
    val levels = listOf("Gonflage", "Vol", "Perf")

    // Global stats summary
    val totalStudents = studentsStats.size
    val totalBookingsAll = studentsStats.sumOf { it.totalBookings }
    val totalAttendedAll = studentsStats.sumOf { it.attendedBookings }
    val globalAttendanceRate = if (totalBookingsAll > 0) (totalAttendedAll.toFloat() / totalBookingsAll * 100).toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HighDensityBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Global stats overview header
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
            border = BorderStroke(1.dp, BorderOutline.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatColumn(label = "Élèves", value = "$totalStudents", icon = "👥", color = PrimaryBlue)
                Divider(modifier = Modifier.height(28.dp).width(1.dp), color = BorderOutline.copy(alpha = 0.5f))
                StatColumn(label = "Inscriptions", value = "$totalBookingsAll", icon = "📌", color = Color(0xFFD97706))
                Divider(modifier = Modifier.height(28.dp).width(1.dp), color = BorderOutline.copy(alpha = 0.5f))
                StatColumn(label = "Présences", value = "$totalAttendedAll", icon = "✅", color = GreenSuccess)
                Divider(modifier = Modifier.height(28.dp).width(1.dp), color = BorderOutline.copy(alpha = 0.5f))
                StatColumn(label = "Assiduité", value = "$globalAttendanceRate%", icon = "📊", color = PrimaryBlueDark)
            }
        }

        // Search bar & Add button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Rechercher un élève...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
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
                label = { Text("Tous (${studentsStats.size})", fontSize = 11.sp) },
                shape = RoundedCornerShape(8.dp)
            )

            levels.forEach { lvl ->
                val count = studentsStats.count { it.student.level.equals(lvl, ignoreCase = true) }
                FilterChip(
                    selected = selectedLevelFilter == lvl,
                    onClick = { onLevelFilterChange(if (selectedLevelFilter == lvl) null else lvl) },
                    label = { Text("$lvl ($count)", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Student List with Stats
        if (studentsStats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("👥", fontSize = 32.sp)
                    Text("Aucun élève trouvé", fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                    Text("Ajoutez vos élèves ou partagez la version élève pour qu'ils s'inscrivent.", fontSize = 12.sp, color = SecondaryText)
                    Button(
                        onClick = onOpenAddStudent,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ajouter un élève")
                    }
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
                items(studentsStats, key = { it.student.id }) { item ->
                    StudentStatCard(
                        item = item,
                        onEdit = { onEditStudent(item.student) },
                        onDelete = { onDeleteStudent(item.student) },
                        onCallPhone = { phone ->
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        onOpenWhatsApp = { phone ->
                            val clean = phone.replace(" ", "").replace("-", "").replace(".", "")
                            val url = "https://wa.me/$clean"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    icon: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(icon, fontSize = 11.sp)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Text(label, fontSize = 10.sp, color = SecondaryText)
    }
}

@Composable
fun StudentStatCard(
    item: StudentWithStats,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCallPhone: (String) -> Unit,
    onOpenWhatsApp: (String) -> Unit
) {
    val student = item.student
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
        border = BorderStroke(1.dp, BorderOutline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.size(38.dp)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(11.dp), tint = SecondaryText)
                                Text(student.phone, fontSize = 11.sp, color = SecondaryText)
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (student.phone.isNotBlank()) {
                        IconButton(onClick = { onOpenWhatsApp(student.phone) }, modifier = Modifier.size(28.dp)) {
                            Text("💬", fontSize = 14.sp)
                        }
                        IconButton(onClick = { onCallPhone(student.phone) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Call, contentDescription = "Appeler", tint = GreenSuccess, modifier = Modifier.size(15.dp))
                        }
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = PrimaryBlueDark, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = RedAlertText, modifier = Modifier.size(15.dp))
                    }
                }
            }

            // Student Key Statistics Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HighDensityBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "📌 ${item.totalBookings} inscrit(s)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = HighDensityHeaderTitle
                    )
                    Text(
                        text = "✅ ${item.attendedBookings} présent(s)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = GreenSuccess
                    )
                    if (item.upcomingBookings > 0) {
                        Text(
                            text = "⏳ ${item.upcomingBookings} à venir",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryBlueDark
                        )
                    }
                }

                Text(
                    text = "Taux : ${item.attendanceRate.toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.attendanceRate >= 80f) GreenSuccess else if (item.attendanceRate >= 50f) Color(0xFFD97706) else RedAlertText
                )
            }

            if (student.notes.isNotBlank()) {
                Text("📝 ${student.notes}", fontSize = 10.sp, color = SecondaryText, maxLines = if (isExpanded) 10 else 1)
            }

            // Expandable session history
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Divider(color = BorderOutline.copy(alpha = 0.4f))
                    Text("Historique des créneaux :", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)

                    if (item.bookingHistory.isEmpty()) {
                        Text("Aucun créneau enregistré pour le moment.", fontSize = 10.sp, color = SecondaryText)
                    } else {
                        item.bookingHistory.take(6).forEach { historyItem ->
                            val slot = historyItem.slot
                            val booking = historyItem.booking
                            val type = PlanningLessonType.fromCode(slot.lessonType)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(HighDensitySurface, RoundedCornerShape(4.dp))
                                    .padding(vertical = 2.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(type.emoji, fontSize = 11.sp)
                                    Text(
                                        text = "${slot.dateIso} (${slot.startTime}-${slot.endTime})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (booking.attended) {
                                    Text("✅ Présent", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                                } else if (booking.isWaitingList) {
                                    Text("⏳ Liste d'attente", fontSize = 10.sp, color = Color(0xFFD97706))
                                } else {
                                    Text("🔵 Inscrit", fontSize = 10.sp, color = PrimaryBlue)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
