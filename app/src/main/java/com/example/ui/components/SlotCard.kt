package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*

@Composable
fun SlotCard(
    slotItem: SlotWithBookings,
    onOpenEnrollStudent: (SlotWithBookings) -> Unit,
    onUnenrollStudent: (Long, Long) -> Unit, // slotId, studentId
    onToggleAttendance: (Long, Long, Boolean) -> Unit, // bookingId, studentId, currentAttended
    onEditSlot: (LessonSlotEntity) -> Unit,
    onDeleteSlot: (Long) -> Unit
) {
    val slot = slotItem.slot
    val lessonType = PlanningLessonType.fromCode(slot.lessonType)
    val isFull = slotItem.isFull
    val confirmedCount = slotItem.confirmedBookings.size
    val maxCap = slot.maxCapacity
    val ratio = if (maxCap > 0) (confirmedCount.toFloat() / maxCap).coerceIn(0f, 1f) else 0f

    val cardBg = when {
        isFull -> Color(0xFFFFFAF9)
        else -> HighDensitySurface
    }

    val cardBorderColor = when {
        isFull -> RedAlertText.copy(alpha = 0.5f)
        else -> GreenSuccess.copy(alpha = 0.5f)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: Heure, Type (Gonflage/Vol/Perf) & Statut Dispo (Vert/Rouge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Heure
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryBlueDark,
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                        Text(
                            text = "${slot.startTime} - ${slot.endTime}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }

                // Type de leçon (Gonflage, Vol, Perf)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (lessonType) {
                        PlanningLessonType.GONFLAGE -> Color(0xFFE0F2FE)
                        PlanningLessonType.VOL -> Color(0xFFDCFCE7)
                        PlanningLessonType.PERF -> Color(0xFFFFEDD5)
                    },
                    border = BorderStroke(0.5.dp, PrimaryBlue.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(lessonType.emoji, fontSize = 12.sp)
                        Text(
                            text = lessonType.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = HighDensityHeaderTitle
                        )
                    }
                }

                // Status Badge (Complet / X Dispo)
                if (isFull) {
                    Surface(
                        color = RedAlertBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, RedAlertText)
                    ) {
                        Text(
                            "🔴 COMPLET",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedAlertText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Surface(
                        color = GreenSuccessBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, GreenSuccess)
                    ) {
                        Text(
                            "🟢 ${slotItem.availablePlaces} DISPO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Titre & Lieu
            Text(
                text = slot.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = HighDensityHeaderTitle
            )

            if (slot.location.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(13.dp), tint = SecondaryText)
                    Text(slot.location, fontSize = 11.sp, color = SecondaryText)
                }
            }

            if (slot.notes.isNotBlank()) {
                Surface(
                    color = HighDensityNavBar,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Text(
                        text = "📝 ${slot.notes}",
                        fontSize = 11.sp,
                        color = SecondaryText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Gauge de remplissage
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$confirmedCount / $maxCap places inscrites",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isFull) RedAlertText else PrimaryBlueDark
                )

                if (slotItem.waitingListBookings.isNotEmpty()) {
                    Text(
                        text = "⏳ ${slotItem.waitingListBookings.size} en attente",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberAccent
                    )
                }
            }

            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isFull) RedAlertText else GreenSuccess,
                trackColor = BorderOutline.copy(alpha = 0.3f)
            )

            // Élèves inscrits list
            if (slotItem.confirmedBookings.isNotEmpty() || slotItem.waitingListBookings.isNotEmpty()) {
                Text(
                    text = "Personnes inscrites :",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SecondaryText,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    slotItem.confirmedBookings.forEach { bws ->
                        val s = bws.student
                        val b = bws.booking
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (b.attended) GreenSuccessBg else HighDensityNavBar,
                            border = BorderStroke(0.5.dp, if (b.attended) GreenSuccess else BorderOutline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = PrimaryBlue,
                                        shape = CircleShape,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(s.initials, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    Text(
                                        s.fullName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = HighDensityHeaderTitle
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.White.copy(alpha = 0.8f)
                                    ) {
                                        Text(
                                            s.level,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SecondaryText,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onToggleAttendance(b.id, s.id, b.attended) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (b.attended) Icons.Default.CheckCircle else Icons.Outlined.CheckCircleOutline,
                                            contentDescription = "Présent",
                                            tint = if (b.attended) GreenSuccess else SecondaryText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onUnenrollStudent(slot.id, s.id) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Désinscrire",
                                            tint = RedAlertText,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Liste d'attente
                    if (slotItem.waitingListBookings.isNotEmpty()) {
                        Text("Liste d'attente :", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
                        slotItem.waitingListBookings.forEach { bws ->
                            val s = bws.student
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("⏳ ${s.fullName}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    IconButton(
                                        onClick = { onUnenrollStudent(slot.id, s.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = RedAlertText, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions: Inscrire quelqu'un, Modifier, Supprimer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onOpenEnrollStudent(slotItem) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Inscrire", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = { onEditSlot(slot) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(HighDensityNavBar, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = PrimaryBlueDark, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { onDeleteSlot(slot.id) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(RedAlertBg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = RedAlertText, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
