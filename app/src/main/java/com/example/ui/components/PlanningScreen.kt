package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LessonSlotEntity
import com.example.data.model.SlotWithBookings
import com.example.ui.theme.*

@Composable
fun PlanningScreen(
    slots: List<SlotWithBookings>,
    selectedDateFilter: String?,
    onDateFilterChange: (String?) -> Unit,
    selectedTypeFilter: String?,
    onTypeFilterChange: (String?) -> Unit,
    onlyAvailable: Boolean,
    onToggleOnlyAvailable: () -> Unit,
    onOpenAddSlot: () -> Unit,
    onOpenEnrollStudent: (SlotWithBookings) -> Unit,
    onUnenrollStudent: (Long, Long) -> Unit,
    onToggleAttendance: (Long, Long, Boolean) -> Unit,
    onEditSlot: (LessonSlotEntity) -> Unit,
    onDeleteSlot: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HighDensityBg)
    ) {
        PlanningHeader(
            selectedDateFilter = selectedDateFilter,
            onDateFilterChange = onDateFilterChange,
            selectedTypeFilter = selectedTypeFilter,
            onTypeFilterChange = onTypeFilterChange,
            onlyAvailable = onlyAvailable,
            onToggleOnlyAvailable = onToggleOnlyAvailable,
            onOpenAddSlot = onOpenAddSlot
        )

        Divider(color = BorderOutline.copy(alpha = 0.4f))

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
                    Text("Aucun créneau correspondant", fontWeight = FontWeight.Bold, color = HighDensityHeaderTitle)
                    Text("Créez un nouveau créneau ou ajustez vos filtres.", fontSize = 12.sp, color = SecondaryText)
                    Button(
                        onClick = onOpenAddSlot,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Créer un créneau")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 60.dp)
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
