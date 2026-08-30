package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import com.example.util.LicenseManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(
    onActivated: () -> Unit
) {
    val context = LocalContext.current
    val deviceId = remember { LicenseManager.getDeviceId(context) }
    var enteredCode by remember { mutableStateOf("") }
    var pilotName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    var showMasterPinDialog by remember { mutableStateOf(false) }
    var masterPinInput by remember { mutableStateOf("") }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun copyToClipboard(text: String, label: String) {
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copié dans le presse-papier !", Toast.LENGTH_SHORT).show()
    }

    fun sendViaWhatsApp() {
        val message = "Bonjour !\nVoici mon Identifiant Appareil pour activer mon application Eagles Academy :\n\n👉 $deviceId\n\nMerci de m'envoyer ma clé d'activation !"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message)
            type = "text/plain"
            `package` = "com.whatsapp"
        }
        try {
            context.startActivity(sendIntent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, message)
                    type = "text/plain"
                },
                "Envoyer mon ID au moniteur"
            )
            try {
                context.startActivity(chooser)
            } catch (ex: Exception) {
                Toast.makeText(context, "Impossible d'ouvrir le partage", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareDeviceId() {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                "Bonjour, voici mon identifiant d'appareil pour activer l'application Eagles Academy : $deviceId"
            )
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Envoyer l'identifiant au concepteur")
        context.startActivity(shareIntent)
    }

    fun tryActivation() {
        errorMessage = null
        successMessage = null
        val result = LicenseManager.activate(context, enteredCode, pilotName)
        if (result.first) {
            successMessage = result.second
            Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
            onActivated()
        } else {
            errorMessage = result.second
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HighDensityBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Eagles Academy Logo
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_eagles_logo_1787304896446),
                    contentDescription = "Logo Eagles Academy",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "EAGLES ACADEMY",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = HighDensityHeaderTitle,
                letterSpacing = 1.sp
            )

            Text(
                text = "Paramoteur & ULM - Activation Requise",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SecondaryText
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Step 1: Device ID & WhatsApp Send
            Card(
                colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlueDark,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("1", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Text(
                            text = "VOTRE IDENTIFIANT APPAREIL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HighDensityHeaderTitle
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = deviceId,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Direct Green WhatsApp button
                    Button(
                        onClick = { sendViaWhatsApp() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) {
                        Text("💬", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Envoyer mon ID par WhatsApp au Moniteur",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { copyToClipboard(deviceId, "Identifiant Appareil") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copier l'ID", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { shareDeviceId() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Autre partage", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Le moniteur générera votre clé d'activation personnalisée dès réception de cet identifiant.",
                        fontSize = 11.sp,
                        color = SecondaryText,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Step 2: Activation Key Form
            Card(
                colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlueDark,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("2", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Text(
                            text = "SAISIE DE LA CLÉ D'ACTIVATION REÇUE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HighDensityHeaderTitle
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pilotName,
                        onValueChange = { pilotName = it },
                        label = { Text("Votre Nom / Prénom (facultatif)") },
                        placeholder = { Text("Ex: Thomas V.") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryBlueDark) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = enteredCode,
                        onValueChange = { enteredCode = it.trim().uppercase() },
                        label = { Text("Clé d'activation") },
                        placeholder = { Text("ACT-30D-XXXX-YYYY") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = PrimaryBlueDark) },
                        trailingIcon = {
                            if (enteredCode.isNotEmpty()) {
                                IconButton(onClick = { enteredCode = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Effacer")
                                }
                            } else {
                                IconButton(onClick = {
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        enteredCode = clip.getItemAt(0).text.toString().trim().uppercase()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Coller")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { tryActivation() }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = Color(0xFFDC2626),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (successMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                            Text(
                                text = successMessage ?: "",
                                color = Color(0xFF16A34A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { tryActivation() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueDark)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Activer mon application", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Developer / Creator Master Unlock Button
            TextButton(
                onClick = { showMasterPinDialog = true }
            ) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Accès Moniteur / Développeur",
                    color = SecondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    if (showMasterPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showMasterPinDialog = false
                masterPinInput = ""
            },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryBlueDark) },
            title = { Text("Code Moniteur / Développeur", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Saisissez votre code pour déverrouiller l'accès complet sans restriction :",
                        fontSize = 13.sp,
                        color = SecondaryText
                    )
                    OutlinedTextField(
                        value = masterPinInput,
                        onValueChange = { masterPinInput = it },
                        label = { Text("Code Développeur Master") },
                        placeholder = { Text("PARAMASTER2026") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val res = LicenseManager.activate(context, masterPinInput, "Concepteur Master")
                            if (res.first) {
                                showMasterPinDialog = false
                                Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                                onActivated()
                            } else {
                                Toast.makeText(context, "Code incorrect !", Toast.LENGTH_SHORT).show()
                            }
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val res = LicenseManager.activate(context, masterPinInput, "Concepteur Master")
                        if (res.first) {
                            showMasterPinDialog = false
                            Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                            onActivated()
                        } else {
                            Toast.makeText(context, "Code incorrect !", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Valider")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMasterPinDialog = false
                    masterPinInput = ""
                }) {
                    Text("Annuler")
                }
            }
        )
    }
}
