package com.twv.viewers.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.twv.viewers.MainActivity
import com.twv.viewers.Viewer

/**
 * Уведомления об избранных зрителях. Работают, пока жив экран зрителей (процесс приложения):
 * фонового сервиса нет, сбор данных идёт только при открытом канале.
 */
class FavoriteNotifier(private val context: Context) {
    private val channelId = "favorites"

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Избранные зрители", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun notify(v: Viewer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Избранный зритель")
            .setContentText("${v.displayName.ifEmpty { v.login }} зашёл на ${v.channel}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(v.login.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }
}
