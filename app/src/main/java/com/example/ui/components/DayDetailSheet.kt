package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.model.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    dateIso: String,
    slots: List<SlotWithBookings>,
    onDismiss: () -> Unit,
    onOpenAddSlot: () -> Unit,
    onOpenCreateStandardDay: () -> Unit,
    onOpenEnrollStudent: (SlotWithBookings) -> Unit,
    onUnenrollStudent: (Long, Long) -> Unit,
    onToggleAttendance: (Long, Long, Boolean) -> Unit,
    onEditSlot: (LessonSlotEntity) -> Unit,
    onDeleteSlot: (Long) -> Unit
) {
    val dateIn = remember { SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE) }
    val dateOut = remember { SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE) }
    val formattedDate = remember(dateIso) {
        val d = try { dateIn.parse(dateIso) } catch (e: Exception) { null }
        d?.let { dateOut.format(it).replaceFirstChar { c -> c.uppercase() } } ?: dateIso
    }

    val status = getDayStatus(slots)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HighDensityBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxHeight(0.85f)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = when (status) {
                                DayColorStatus.GREEN_AVAILABLE -> GreenSuccess
                                DayColorStatus.RED_FULL -> RedAlertText
                                DayColorStatus.WHITE_NO_SLOT -> Color.White
                            },
                            border = if (status == DayColorStatus.WHITE_NO_SLOT) BorderStroke(1.dp, BorderOutline) else null,
                            modifier = Modifier.size(12.dp)
                        ) {}

                        Text(
                            text = formattedDate,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = HighDensityHeaderTitle
                        )
                    }

                    Text(
                        text = when (status) {
                            DayColorStatus.GREEN_AVAILABLE -> "🟢 Des créneaux sont disponibles"
                            DayColorStatus.RED_FULL -> "🔴 Tous les créneaux sont complets"
                            DayColorStatus.WHITE_NO_SLOT -> "⚪ Aucun créneau proposé pour cette date"
                        },
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onOpenCreateStandardDay,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("⚡ Journée Type", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onOpenAddSlot,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Créneau", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = BorderOutline.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            if (slots.isEmpty()) {
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
                        Text("📅", fontSize = 32.sp)
                        Text(
                            "Pas de créneau ce jour-là",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = HighDensityHeaderTitle
                        )
                        Text(
                            "Cliquez sur 'Ajouter' pour créer une séance (Gonflage, Vol ou Perf).",
                            fontSize = 12.sp,
                            color = SecondaryText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = onOpenAddSlot,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Proposer un créneau")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(slots, key = { it.slot.id }) { slotItem ->
                        SlotCard(
                            slotItem = slotItem,
                            onOpenEnrollStudent = onOpenEnrollStudent,
                            onUnenrollStudent = onUnenrollStudent,
                            onToggleAttendance = onToggleAttendance,
                            onEditSlot = onEditSlot,
                            onDeleteSlot = onDeleteSlot
                        )
                    }
                }
            }
        }
    }
}
