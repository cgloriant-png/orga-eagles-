package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.data.model.PlanningLessonType
import com.example.ui.theme.*

@Composable
fun PlanningHeader(
    selectedDateFilter: String?,
    onDateFilterChange: (String?) -> Unit,
    selectedTypeFilter: String?,
    onTypeFilterChange: (String?) -> Unit,
    onlyAvailable: Boolean,
    onToggleOnlyAvailable: () -> Unit,
    onOpenAddSlot: () -> Unit
) {
    val dateFilters = listOf(
        "TOUS" to "Tous",
        "TODAY" to "Aujourd'hui",
        "TOMORROW" to "Demain",
        "WEEK" to "Cette Semaine"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HighDensitySurface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quick Date Chips + Add button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(dateFilters) { (key, label) ->
                    val isSelected = (selectedDateFilter == key) || (selectedDateFilter == null && key == "TOUS")
                    FilterChip(
                        selected = isSelected,
                        onClick = { onDateFilterChange(key) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
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

        // Type Filters (Gonflage, Vol, Perf) & Dispo switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PlanningLessonType.entries.forEach { type ->
                    val isSelected = selectedTypeFilter == type.code
                    FilterChip(
                        selected = isSelected,
                        onClick = { onTypeFilterChange(if (isSelected) null else type.code) },
                        label = { Text("${type.emoji} ${type.label}", fontSize = 11.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            FilterChip(
                selected = onlyAvailable,
                onClick = onToggleOnlyAvailable,
                label = { Text("🟢 Libres", fontSize = 11.sp) },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}
