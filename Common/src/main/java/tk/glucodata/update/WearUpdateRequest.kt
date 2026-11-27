package tk.glucodata.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import tk.glucodata.Log
import tk.glucodata.MainActivity
import tk.glucodata.R
import tk.glucodata.WearMessagePath
import tk.glucodata.ui.PendingNavigation

object WearUpdateRequest {
    private const val CAPABILITY = "juggluco_watch_updates"
    private const val NOTIFICATION_CHANNEL = "wear_app_updates"
    private const val NOTIFICATION_ID = 0x574155
    const val ROUTE = "wear-app-updates"

    /** The message carries no APK or URL. The watch uses its own release source and verifier. */
    suspend fun send(context: Context): Int = withTimeoutOrNull(10_000L) {
        val nodes = Wearable.getCapabilityClient(context)
            .getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
        val client = Wearable.getMessageClient(context)
        var sent = 0
        for (node in nodes) {
            try {
                client.sendMessage(node.id, WearMessagePath.APP_UPDATE_CHECK.wire, ByteArray(0)).await()
                sent++
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.stack("WearAppUpdate", error)
            }
        }
        sent
    } ?: 0

    fun checkOnWatch(context: Context) {
        AppUpdateController.checkNow(context) { result ->
            val description = when (result) {
                is UpdateCheckResult.Available -> context.getString(
                    R.string.wear_app_update_available, result.update.versionName
                )
                UpdateCheckResult.UpToDate -> context.getString(R.string.app_updates_up_to_date_title)
                is UpdateCheckResult.Failed -> context.getString(R.string.app_updates_error_title)
            }
            notifyOnWatch(context, description)
        }
    }

    private fun notifyOnWatch(context: Context, description: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val title = context.getString(R.string.app_updates_title)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(PendingNavigation.EXTRA_ROUTE, ROUTE)
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            manager.createNotificationChannel(NotificationChannel(
                NOTIFICATION_CHANNEL, title, NotificationManager.IMPORTANCE_DEFAULT
            ))
            manager.notify(NOTIFICATION_ID, Notification.Builder(context, NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(description)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build())
        }.onFailure { Log.stack("WearAppUpdate", it) }
    }
}
