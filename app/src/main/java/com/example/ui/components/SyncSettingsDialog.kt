package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.cloud.SyncStatus
import com.example.ui.theme.*

@Composable
fun SyncSettingsDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    currentSchoolCode: String,
    onSaveSchoolCode: (String) -> Unit,
    syncStatus: SyncStatus,
    syncStatusMsg: String,
    lastSyncTime: String,
    syncedSlotsCount: Int,
    syncedStudentsCount: Int,
    syncedBookingsCount: Int,
    onForceSync: () -> Unit,
    onShareSchoolCode: () -> Unit
) {
    if (!isOpen) return

    var codeInput by remember(currentSchoolCode) { mutableStateOf(currentSchoolCode) }

    val statusDotColor = when (syncStatus) {
        SyncStatus.CONNECTED_SYNCED -> Color(0xFF34D399)
        SyncStatus.SYNCING -> Color(0xFFFBBF24)
        SyncStatus.CONNECTING -> Color(0xFF60A5FA)
        SyncStatus.OFFLINE, SyncStatus.ERROR -> Color(0xFFEF4444)
    }

    val statusLabel = when (syncStatus) {
        SyncStatus.CONNECTED_SYNCED -> "Connecté en direct"
        SyncStatus.SYNCING -> "Synchronisation en cours..."
        SyncStatus.CONNECTING -> "Connexion au Cloud..."
        SyncStatus.OFFLINE, SyncStatus.ERROR -> "Hors-ligne / Vérifiez la connexion"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = PrimaryBlue)
                Text("Synchronisation Multi-Appareils", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live Status Box
                Surface(
                    color = HighDensitySurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CardBorder)
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(statusDotColor, CircleShape)
                            )
                            Column {
                                Text(statusLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (lastSyncTime.isNotBlank()) {
                                    Text("Dernier échange : $lastSyncTime", fontSize = 11.sp, color = SecondaryText)
                                }
                            }
                        }

                        IconButton(
                            onClick = onForceSync,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualiser", tint = PrimaryBlue)
                        }
                    }
                }

                // Stats Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = HighDensitySurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$syncedSlotsCount", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrimaryBlue)
                            Text("Créneaux", fontSize = 10.sp, color = SecondaryText)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = HighDensitySurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$syncedBookingsCount", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF10B981))
                            Text("Inscriptions", fontSize = 10.sp, color = SecondaryText)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = HighDensitySurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$syncedStudentsCount", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF8B5CF6))
                            Text("Élèves", fontSize = 10.sp, color = SecondaryText)
                        }
                    }
                }

                // School Code field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Code École / Club partagé :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = SecondaryText
                    )
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase().replace(" ", "") },
                        placeholder = { Text("Ex: PLOUHARNEL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (codeInput != currentSchoolCode && codeInput.isNotBlank()) {
                                IconButton(onClick = { onSaveSchoolCode(codeInput) }) {
                                    Icon(Icons.Default.Check, contentDescription = "Valider", tint = PrimaryBlue)
                                }
                            }
                        }
                    )
                    Text(
                        "Indiquez ce même code sur le téléphone du moniteur et des élèves pour synchroniser automatiquement les réservations.",
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }

                // Share School Code Button
                Button(
                    onClick = onShareSchoolCode,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Partager le Code École (${currentSchoolCode})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (codeInput != currentSchoolCode && codeInput.isNotBlank()) {
                        onSaveSchoolCode(codeInput)
                    }
                    onDismiss()
                }
            ) {
                Text("Fermer", fontWeight = FontWeight.Bold)
            }
        }
    )
}
