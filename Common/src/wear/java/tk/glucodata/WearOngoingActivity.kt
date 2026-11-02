package tk.glucodata

import android.app.NotificationManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import tk.glucodata.glucosecomplication.ComplicationRenderer
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

object WearOngoingActivity : OngoingNotification {
    private const val LOG_ID = "WearOngoingActivity"

    const val MODE_NORMAL = 0
    const val MODE_VALUE = 1
    const val MODE_TREND = 2
    private const val PREFS = "wear_ongoing_icon"
    private const val ICON_SIZE = 96
    @Volatile private var currentNotificationId: Int? = null
    private val expiryHandler = Handler(Looper.getMainLooper())
    private val expiryLock = Any()
    private var expiryTask: Runnable? = null

    fun iconMode(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt("mode", MODE_NORMAL).takeIf { it in MODE_NORMAL..MODE_TREND } ?: MODE_NORMAL

    fun setIconMode(context: Context, mode: Int) {
        require(mode in MODE_NORMAL..MODE_TREND)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("mode", mode).apply()
        currentNotificationId?.let { updateStatus(context.applicationContext, it) }
    }

    override fun attach(context: Context, notification: Notification, notificationId: Int): Notification {
        return try {
            currentNotificationId = notificationId
            val reading = CurrentDisplaySource.resolveCurrent(Notify.glucosetimeout)
            val builder = NotificationCompat.Builder(context, notification).setOnlyAlertOnce(true)
            OngoingActivity.Builder(context, notificationId, builder)
                .setStaticIcon(icon(context, reading))
                .setStatus(status(context, reading))
                .setTouchIntent(mainPendingIntent(context))
                .setOngoingActivityId(notificationId)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setTitle(context.getString(R.string.app_name))
                .build()
                .apply(context)
            scheduleExpiry(context.applicationContext, notificationId, reading)
            builder.build().also { it.flags = it.flags or Notification.FLAG_ONGOING_EVENT }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "attach", th)
            notification
        }
    }

    override fun updateStatus(context: Context?, notificationId: Int) {
        context ?: return
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // An icon cannot be changed with OngoingActivity.update(), which updates only Status.
            // Reattach to the existing notification, preserving its ID, channel and tap action.
            val active = manager.activeNotifications.firstOrNull {
                it.id == notificationId && it.tag == null
            } ?: return // Never resurrect a stopped foreground notification.
            manager.notify(notificationId, attach(context, active.notification, notificationId))
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "updateStatus", th)
        }
    }

    private fun status(context: Context, reading: CurrentDisplaySource.Snapshot?): Status {
        val value = reading?.primaryStr
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.novalue)
        return Status.forPart(Status.TextPart(value))
    }

    private fun icon(context: Context, reading: CurrentDisplaySource.Snapshot?): Icon {
        if (iconMode(context) == MODE_NORMAL) return Icon.createWithResource(context, R.drawable.novalue)
        val bitmap = when {
            reading == null -> ComplicationRenderer.valueBitmap(ICON_SIZE, "—", 0f, false, Color.WHITE)
            iconMode(context) == MODE_VALUE -> ComplicationRenderer.valueBitmap(
                ICON_SIZE, reading.primaryStr, reading.primaryValue, reading.isMmol, Color.WHITE,
            )
            reading.rate.isFinite() -> ComplicationRenderer.arrowBitmap(ICON_SIZE, reading.rate, Color.WHITE)
            else -> ComplicationRenderer.valueBitmap(ICON_SIZE, "—", 0f, false, Color.WHITE)
        }
        return Icon.createWithBitmap(bitmap)
    }

    private fun scheduleExpiry(context: Context, notificationId: Int, reading: CurrentDisplaySource.Snapshot?) {
        synchronized(expiryLock) {
            expiryTask?.let { expiryHandler.removeCallbacks(it) }
            expiryTask = null
            if (reading == null) return
            val delay = (reading.timeMillis + Notify.glucosetimeout - System.currentTimeMillis() + 1L)
                .coerceAtLeast(1L)
            val task = Runnable { updateStatus(context, notificationId) }
            expiryTask = task
            expiryHandler.postDelayed(task, delay)
        }
    }

    private fun mainPendingIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, flags)
    }
}
