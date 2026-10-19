package tk.glucodata

import android.app.Application
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** The host is API 34; the policy receives 36/37 without claiming OEM-device coverage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class OplusStatusIconRequestTests {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun builder() = Notification.Builder(app, "glucoseNotification")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setWhen(123456L)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .also { it.extras.putString("existing_metadata", "preserve") }

    @Test fun explicitOptInMergesRequestWithoutChangingNotificationContentOrPolicy() {
        for (sdk in listOf(36, 37)) {
            val builder = builder()
            OplusStatusIconRequest.apply(builder, true, sdk, false)
            val notification = builder.build()
            assertTrue(notification.extras.containsKey(OplusStatusIconRequest.USE_APP_ICON))
            assertFalse(notification.extras.getBoolean(OplusStatusIconRequest.USE_APP_ICON))
            assertEquals("preserve", notification.extras.getString("existing_metadata"))
            assertEquals(android.R.drawable.ic_dialog_info, notification.smallIcon.resId)
            assertEquals("glucoseNotification", notification.channelId)
            assertNull(notification.group)
            assertEquals(123456L, notification.`when`)
            assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        }
    }

    @Test fun optOutOlderAndroidAndWearDoNotAddVendorMetadata() {
        for ((enabled, sdk, wearable) in listOf(
            Triple(false, 36, false), Triple(false, 37, false),
            Triple(true, 35, false), Triple(true, 36, true), Triple(true, 37, true)
        )) {
            val builder = builder()
            OplusStatusIconRequest.apply(builder, enabled, sdk, wearable)
            assertFalse(builder.build().extras.containsKey(OplusStatusIconRequest.USE_APP_ICON))
        }
    }

    @Test fun optOutDoesNotOverwriteExistingExtras() {
        val builder = builder()
        builder.extras.putBoolean(OplusStatusIconRequest.USE_APP_ICON, true)
        OplusStatusIconRequest.apply(builder, false, 36, false)
        assertTrue(builder.build().extras.getBoolean(OplusStatusIconRequest.USE_APP_ICON))
    }
}
