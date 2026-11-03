package tk.glucodata

import tk.glucodata.glucosecomplication.GlucoseComplicationData
import java.util.concurrent.atomic.AtomicBoolean
import android.app.NotificationManager
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
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
    @Volatile private var currentNotificationId: Int? = null
    private val expiryHandler = Handler(Looper.getMainLooper())
    private val expiryLock = Any()
    private var expiryTask: Runnable? = null
    private val refreshQueued = AtomicBoolean(false)

    /** Every data refresh reaches this path even while the watch app is closed. */
    @JvmStatic
    fun requestRefresh() {
        if (!refreshQueued.compareAndSet(false, true)) return
        expiryHandler.post {
            refreshQueued.set(false)
            val context = Applic.app ?: return@post
            val id = currentNotificationId ?: runCatching {
                OngoingActivity.recoverOngoingActivity(context)?.notificationId
            }.getOrNull()
            id?.let { updateStatus(context, it) }
            Notify.refreshWearNotificationIcon()
        }
    }

    fun iconMode(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt("mode", MODE_NORMAL).takeIf { it in MODE_NORMAL..MODE_TREND } ?: MODE_NORMAL

    fun setIconMode(context: Context, mode: Int) {
        require(mode in MODE_NORMAL..MODE_TREND)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("mode", mode).apply()
        // The foreground notification may outlive this object after process recreation.
        val notificationId = currentNotificationId ?: runCatching {
            OngoingActivity.recoverOngoingActivity(context)?.notificationId
        }.getOrNull()
        Log.i(LOG_ID, "icon selection mode=$mode active=${notificationId != null}")
        notificationId?.let { updateStatus(context.applicationContext, it) }
        // Reading notifications use a different ID from the foreground service.
        Notify.refreshWearNotificationIcon()
    }

    override fun applySmallIcon(context: Context, notification: Notification): Notification {
        return try {
            val mode = iconMode(context)
            val reading = GlucoseComplicationData.currentReading()
            Notification.Builder.recoverBuilder(context, notification)
                .apply {
                    if (mode == MODE_NORMAL) setSmallIcon(R.drawable.novalue)
                    else setSmallIcon(icon(context, reading, mode))
                }
                .build()
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "applySmallIcon", th)
            notification
        }
    }

    override fun attach(context: Context, notification: Notification, notificationId: Int): Notification {
        return try {
            currentNotificationId = notificationId
            val reading = GlucoseComplicationData.currentReading()
            val mode = iconMode(context)
            val displayIcon = icon(context, reading, mode)
            // Keep the active, ambient and notification-fallback icon paths consistent.
            // recoverBuilder preserves the channel, foreground metadata, views and actions.
            // It requires API 24; the Wear variant's minimum is API 26.
            val baseNotification = Notification.Builder.recoverBuilder(context, notification)
                .apply {
                    if (mode == MODE_NORMAL) setSmallIcon(R.drawable.novalue)
                    else setSmallIcon(displayIcon)
                }
                .build()
            val builder = NotificationCompat.Builder(context, baseNotification).setOnlyAlertOnce(true)
            OngoingActivity.Builder(context, notificationId, builder)
                .setAnimatedIcon(displayIcon)
                .setStaticIcon(displayIcon)
                .setStatus(status(context, reading))
                .setTouchIntent(mainPendingIntent(context))
                .setOngoingActivityId(notificationId)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setTitle(context.getString(R.string.app_name))
                .build()
                .apply(context)
            scheduleExpiry(context.applicationContext, notificationId, reading)
            builder.build().also {
                it.flags = it.flags or Notification.FLAG_ONGOING_EVENT
                Log.i(LOG_ID, "attached mode=$mode iconType=${it.smallIcon?.type} readingPresent=${reading != null}")
            }
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

    private fun status(context: Context, reading: GlucoseComplicationData.Reading?): Status {
        val value = reading?.text
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.novalue)
        return Status.forPart(Status.TextPart(value))
    }

    private fun icon(context: Context, reading: GlucoseComplicationData.Reading?, mode: Int): Icon {
        val resource = when {
            mode == MODE_NORMAL -> R.drawable.novalue
            reading == null -> R.drawable.wear_ongoing_unknown
            mode == MODE_VALUE -> WearOngoingIconResources.value(reading.text)
            else -> WearOngoingIconResources.trend(reading.rate)
        }
        return Icon.createWithResource(context, resource)
    }

    private fun scheduleExpiry(context: Context, notificationId: Int, reading: GlucoseComplicationData.Reading?) {
        synchronized(expiryLock) {
            expiryTask?.let { expiryHandler.removeCallbacks(it) }
            expiryTask = null
            if (reading == null) return
            val delay = (reading.timeMillis + Notify.glucosetimeout - System.currentTimeMillis() + 1L)
                .coerceAtLeast(1L)
            val task = Runnable {
                updateStatus(context, notificationId)
                Notify.refreshWearNotificationIcon()
            }
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
