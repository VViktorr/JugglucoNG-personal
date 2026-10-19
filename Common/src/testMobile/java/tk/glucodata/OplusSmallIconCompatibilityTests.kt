package tk.glucodata

import android.app.Application
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class OplusSmallIconCompatibilityTests {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun extraIsAnIntegerAndPreservesTheOriginalNotification() {
        val builder = Notification.Builder(app, "glucose")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("105").setWhen(123L).setOngoing(true)
        builder.extras.putString("existing", "preserve")
        OplusSmallIconCompatibility.apply(builder, true,
            "com.oplus.juggluco.ngtest", 36, false, true)
        val notification = builder.build()
        assertEquals(1, notification.extras.getInt(OplusSmallIconCompatibility.EXTRA))
        @Suppress("DEPRECATION")
        assertTrue(notification.extras.get(OplusSmallIconCompatibility.EXTRA) is Int)
        assertFalse(notification.extras.containsKey("oplus_smallicon_use_app_icon"))
        assertEquals("preserve", notification.extras.getString("existing"))
        assertEquals(android.R.drawable.ic_dialog_info, notification.smallIcon.resId)
        assertEquals("glucose", notification.channelId)
        assertEquals(123L, notification.`when`)
        assertNull(notification.group)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test fun onlyExplicitPhoneTestBuildsWithAcceptedPrefixesQualify() {
        for (pkg in listOf("com.oplus.juggluco.ngtest", "com.oppo.test", "com.coloros.test")) {
            for (sdk in listOf(36, 37)) {
                assertTrue(OplusSmallIconCompatibility.shouldRequest(true, pkg, sdk, false, true))
            }
        }
        assertFalse(OplusSmallIconCompatibility.shouldRequest(false,
            "com.oplus.juggluco.ngtest", 36, false, true))
        assertFalse(OplusSmallIconCompatibility.shouldRequest(true, "tk.glucodata.ng", 36, false, true))
        assertFalse(OplusSmallIconCompatibility.shouldRequest(true, "com.oplusfake.test", 36, false, true))
        assertFalse(OplusSmallIconCompatibility.shouldRequest(true, null, 36, false, true))
        assertFalse(OplusSmallIconCompatibility.shouldRequest(true, "com.oplus.test", 35, false, true))
        assertFalse(OplusSmallIconCompatibility.shouldRequest(true, "com.oplus.test", 36, true, true))
        assertFalse(OplusSmallIconCompatibility.shouldRequest(true, "com.oplus.test", 36, false, false))
    }

    @Test fun normalBuildDoesNotAddVendorExtrasEvenWithMatchingPrefix() {
        val builder = Notification.Builder(app, "glucose")
        OplusSmallIconCompatibility.apply(builder, false, "com.oplus.test", 37, false, true)
        assertFalse(builder.extras.containsKey(OplusSmallIconCompatibility.EXTRA))
    }
}
