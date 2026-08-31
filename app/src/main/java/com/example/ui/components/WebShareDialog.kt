package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*

@Composable
fun WebShareDialog(
    schoolCode: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("paramoteur_web_share_prefs", Context.MODE_PRIVATE) }
    val scrollState = rememberScrollState()

    val cleanSchool = schoolCode.trim().uppercase()
    val defaultUrl = "https://cgloriant-png.github.io/orga-eagles-/"
    var webPortalUrl by remember {
        mutableStateOf(prefs.getString("custom_student_web_url", defaultUrl) ?: defaultUrl)
    }

    fun getStudentShareMessage(): String {
        val targetUrl = webPortalUrl.trim().ifEmpty { defaultUrl }
        val fullUrl = if (targetUrl.contains("#")) targetUrl else "$targetUrl#$cleanSchool"

        return """
🦅 *EAGLES ACADEMY - ESPACE ÉLÈVES & PLANNING*
École de Paramoteur de Plouharnel

Voici le lien pour consulter le planning et réserver vos séances de vol et de gonflage sur votre iPhone, Android ou ordinateur :

📲 *Accès Web & iPhone :*
$fullUrl

💡 *Installation sur iPhone (recommandé) :*
1. Ouvrez le lien dans Safari
2. Appuyez sur le bouton *Partager* (carré avec flèche vers le haut)
3. Sélectionnez *« Sur l'écran d'accueil »* pour l'installer comme une vraie appli !

🔐 *Sécurité & Activation :*
À la première ouverture, l'appli affichera votre identifiant unique (ex: PM-XXXX-XXXX). Cliquez sur « Envoyer mon ID au Moniteur » pour recevoir votre clé d'activation personnelle.

Bon vol à tous ! 🪂
        """.trimIndent()
    }

    fun saveUrl(newUrl: String) {
        webPortalUrl = newUrl
        prefs.edit().putString("custom_student_web_url", newUrl).apply()
    }

    fun copyText(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copié dans le presse-papiers !", Toast.LENGTH_SHORT).show()
    }

    fun testUrlInBrowser() {
        val targetUrl = webPortalUrl.trim().ifEmpty { defaultUrl }
        val fullUrl = if (targetUrl.contains("#")) targetUrl else "$targetUrl#$cleanSchool"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible d'ouvrir le lien : $fullUrl", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareWhatsApp(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            `package` = "com.whatsapp"
        }
        try {
            context.startActivity(sendIntent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                },
                "Partager le lien Élèves via"
            )
            try {
                context.startActivity(chooser)
            } catch (ex: Exception) {
                Toast.makeText(context, "Impossible de partager", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = HighDensitySurface,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(scrollState)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryBlueContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🍎", fontSize = 18.sp)
                            }
                        }
                        Column {
                            Text(
                                "Accès iPhone & Web Élèves",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                "Compatible iOS, Android & PC",
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // URL Configuration Card
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
                    border = BorderStroke(1.dp, PrimaryBlue)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "🌐 URL du portail Web / iPhone :",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF38BDF8)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = webPortalUrl,
                            onValueChange = { saveUrl(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0B1329),
                                unfocusedContainerColor = Color(0xFF0B1329)
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Preset GitHub Pages
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistChip(
                                onClick = { saveUrl("https://cgloriant-png.github.io/orga-eagles-/") },
                                label = { Text("🐙 GitHub Pages (Actif & Vérifié)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { testUrlInBrowser() },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tester le lien", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { saveUrl(defaultUrl) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Réinit.", fontSize = 11.5.sp, color = Color.LightGray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Explicative Card: How to activate on GitHub
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🐙", fontSize = 14.sp)
                            Text("Activation GitHub Pages (10 secondes)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "1. Poussez vos modifications sur GitHub.\n2. Sur GitHub, allez dans **Settings > Pages**.\n3. Sous 'Build and deployment', sélectionnez la branche **main** et le dossier **/docs** ou **/** puis cliquez sur **Save**.\n4. Votre portail élèves est instantanément en ligne !",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Security explanation
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                            Text("Sécurité & Révocation des Clés", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFFFD54F))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "• L'élève vous envoie son identifiant (ex: PM-A4F2-89C1).\n• Vous lui attribuez une clé (7j, 30j, 1 an, etc.) dans « Gestion Licences ».\n• Dès qu'un élève quitte l'école, cliquez sur l'icône rouge 🚫 dans « Gestion Licences » : son accès Web/iPhone est immédiatement verrouillé en temps réel !",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Button(
                    onClick = { shareWhatsApp(getStudentShareMessage()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                ) {
                    Text("💬", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Envoyer les accès par WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { copyText(getStudentShareMessage(), "Message d'accès Élèves") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copier le texte explicatif", fontSize = 12.5.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fermer", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}
