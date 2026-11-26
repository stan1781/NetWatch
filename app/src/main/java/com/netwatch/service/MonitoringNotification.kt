package com.netwatch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.netwatch.MainActivity
import com.netwatch.R
import com.netwatch.core.util.ByteFormatter
import com.netwatch.domain.model.AlertEvent
import com.netwatch.domain.model.MonitorSettings
import com.netwatch.domain.model.SpeedSample

class MonitoringNotification(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels(settings: MonitorSettings) {
        val monitor = NotificationChannel(
            CHANNEL_MONITOR,
            context.getString(R.string.notification_channel_monitor),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "前台服务正在采样实时速度与流量。"
            setShowBadge(false)
        }
        val alerts = NotificationChannel(
            channelIdFor(settings),
            context.getString(R.string.notification_channel_alerts),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "高速下载、短时间流量过大、套餐占用预警。"
            enableVibration(settings.notificationVibrate)
            if (settings.notificationSound) {
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build(),
                )
            } else {
                setSound(null, null)
            }
        }
        manager?.createNotificationChannel(monitor)
        manager?.createNotificationChannel(alerts)
    }

    fun monitorNotification(sample: SpeedSample?, todayTotal: String): Notification {
        val pending = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val speed = if (sample == null) {
            "正在初始化采样…"
        } else {
            val label = sample.displayPathLabel
            "↓ ${ByteFormatter.formatSpeed(sample.displayDownloadBytesPerSec)}  ↑ ${ByteFormatter.formatSpeed(sample.displayUploadBytesPerSec)}　$label"
        }
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_monitor_title))
            .setContentText("$speed　今日移动 $todayTotal")
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun alertNotification(event: AlertEvent, settings: MonitorSettings): Notification {
        val pending = PendingIntent.getActivity(
            context,
            event.type.ordinal + 10,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val details = buildString {
            append(event.message)
            event.triggerSpeedBytesPerSec?.let { append("　").append(ByteFormatter.formatSpeed(it)) }
            event.triggerVolumeBytes?.let { append("　").append(ByteFormatter.formatBytes(it)) }
            event.planPercent?.let { append("　").append(ByteFormatter.formatPercent(it)) }
            event.slotIndex?.let { append("　SIM ").append(it + 1) }
        }
        return NotificationCompat.Builder(context, channelIdFor(settings))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("NetWatch 预警")
            .setContentText(details)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun notifyAlert(event: AlertEvent, settings: MonitorSettings) {
        if (!settings.notificationsEnabled) return
        manager?.notify(ALERT_BASE + event.type.ordinal, alertNotification(event, settings))
    }

    private fun channelIdFor(settings: MonitorSettings): String =
        "$CHANNEL_ALERTS_BASE-s${if (settings.notificationSound) 1 else 0}-v${if (settings.notificationVibrate) 1 else 0}"

    companion object {
        const val CHANNEL_MONITOR = "netwatch.monitor"
        const val CHANNEL_ALERTS_BASE = "netwatch.alerts"
        const val MONITOR_ID = 1001
        const val ALERT_BASE = 2000
    }
}
