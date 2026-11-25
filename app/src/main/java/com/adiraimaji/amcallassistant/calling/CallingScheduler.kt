package com.adiraimaji.amcallassistant.calling

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.getSystemService

/** Exact-time scheduling for Calling Machine profiles. WorkManager's 15-minute
 *  floor cannot place a call at the time someone chose, so this uses Android's
 *  alarm directly and asks for the exact-alarm allowance when it needs it. */
internal object CallingScheduler {
    const val ACTION = "com.adiraimaji.amcallassistant.CALLING_ALARM"
    const val EXTRA_ID = "profile_id"

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching {
                context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true
            }.getOrDefault(false)

    fun ask(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun schedule(context: Context, profile: CallingProfile): Boolean {
        if (!allowed(context)) {
            ask(context)
            return false
        }
        val alarm = context.getSystemService<AlarmManager>() ?: return false
        val intent = Intent(context, CallingAlarmReceiver::class.java)
            .setAction(ACTION)
            .putExtra(EXTRA_ID, profile.id)
        val pending = android.app.PendingIntent.getBroadcast(
            context,
            profile.id.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, profile.atMillis, pending)
        return true
    }

    fun cancel(context: Context, profile: CallingProfile) {
        val alarm = context.getSystemService<AlarmManager>() ?: return
        val intent = Intent(context, CallingAlarmReceiver::class.java).setAction(ACTION)
        val pending = android.app.PendingIntent.getBroadcast(
            context,
            profile.id.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarm.cancel(pending)
    }
}
