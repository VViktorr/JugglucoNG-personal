package tk.glucodata

import java.io.File
import java.net.URLClassLoader
import javax.tools.ToolProvider
import org.junit.Assert.assertEquals
import org.junit.Test

/** The real legacy timer must delegate phone status to freshness reconciliation. */
class LegacyOngoingNotificationTests {
    @Test
    fun oldTimerCannotDirectlyOverwriteFreshPhoneNotification() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "Common/src/main/java/tk/glucodata/Notify.java").isFile }
        val source = File(root, "Common/src/main/java/tk/glucodata/Notify.java").readText()
        val start = source.indexOf("    void oldnotification(long time) {")
        val end = source.indexOf("\n    @SuppressWarnings", start)
        check(start >= 0 && end > start)
        val deadlineStart = source.indexOf("    private final Runnable freshnessDeadlineRefreshRunnable")
        val deadlineEnd = source.indexOf("    private void armFreshnessDeadlineLocked", deadlineStart)
        check(deadlineStart >= 0 && deadlineEnd > deadlineStart)
        val fixture = File(root, "Common/src/test/resources/java/OngoingTimerHarness.java").readText()
            .replace("// PRODUCTION_OLD_NOTIFICATION", source.substring(start, end))
            .replace("// PRODUCTION_FRESHNESS_DEADLINE", source.substring(deadlineStart, deadlineEnd))
        val directory = java.nio.file.Files.createTempDirectory("ongoing-notification-timer").toFile()
        try {
            val javaFile = File(directory, "OngoingTimerHarness.java").apply { writeText(fixture) }
            val output = java.io.ByteArrayOutputStream()
            val compiler = checkNotNull(ToolProvider.getSystemJavaCompiler())
            val result = compiler.run(null, output, output, "-d", directory.path, javaFile.path,
                File(root, "Common/src/main/java/tk/glucodata/NotificationRefreshPolicy.java").path)
            assertEquals(output.toString(), 0, result)
            URLClassLoader(arrayOf(directory.toURI().toURL()), null).use { loader ->
                loader.loadClass("tk.glucodata.OngoingTimerHarness").getMethod("run").invoke(null)
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
