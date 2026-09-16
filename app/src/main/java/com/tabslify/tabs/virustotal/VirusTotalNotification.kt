package com.tabslify.tabs.virustotal

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tabslify.R
import com.tabslify.core.activities.MainActivity
import com.tabslify.core.objects.tNotify
import com.tabslify.services.QuietHoursNotificationService.Companion.VIRUSTOTAL_CHANNEL_ID

fun sendVirusTotalScanNotification(context: Context, job: VirusTotalScanJob) {
    val state = job.state
    val (title, message) = when (state) {
        is VirusTotalState.Result -> {
            val stats = state.stats
            context.getString(R.string.virustotal_scan_abgeschlossen) to
                context.getString(R.string.virustotal_scan_ergebnis, job.label, stats.malicious, stats.total)
        }

        is VirusTotalState.Error ->
            context.getString(R.string.virustotal_scan_abgeschlossen) to state.message

        else -> return
    }

    val intentBase = Intent(context, MainActivity::class.java)
    intentBase.action = Intent.ACTION_VIEW
    intentBase.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    intentBase.putExtra("target", "virustotal")
    intentBase.putExtra("vt_report_id", job.id)
    intentBase.setPackage(context.packageName)

    val pendingIntent = PendingIntent.getActivity(
        context,
        job.id.hashCode(),
        intentBase,
        PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, VIRUSTOTAL_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    tNotify(context, job.id.hashCode(), notification, "virustotal_scan")
}
