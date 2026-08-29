package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.LessonSlotEntity
import com.example.data.model.formatTimeRangeFrench

object WeatherNotificationHelper {

    private const val CHANNEL_ID = "paramoteur_weather_alerts"
    private const val CHANNEL_NAME = "Alertes Aérologie & Annulations Paramoteur"
    private const val CHANNEL_DESC = "Notifications en cas de vent fort, pluie ou annulation météo d'un créneau de vol/gonflage"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showWeatherAlertNotification(
        context: Context,
        slot: LessonSlotEntity,
        isCancellation: Boolean,
        reason: String
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            slot.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val timeRange = formatTimeRangeFrench(slot.startTime, slot.endTime)
        val title = if (isCancellation) {
            "🚫 Annulation Météo : ${slot.title}"
        } else {
            "⚠️ Alerte Aérologie : ${slot.title}"
        }

        val content = "${slot.dateIso} ($timeRange) - $reason"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$content\nLieu : ${slot.location}\n${if (slot.postponedTo.isNotBlank()) "Report proposé : " + slot.postponedTo else ""}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(slot.id.toInt(), builder.build())
            }
        } catch (e: SecurityException) {
            // Notification permission might not be granted yet
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
