package tk.glucodata

import java.io.File
import java.net.URLClassLoader
import javax.tools.ToolProvider
import org.junit.Assert.assertEquals
import org.junit.Test

/** Compiles the actual phone notification selector against recording history/live sources. */
class OngoingNotificationHistoryTests {
    @Test
    fun ongoingNotificationUsesNewerNfcHistoryWithoutChangingTheAlarmResolver() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "Common/src/main/java/tk/glucodata/Notify.java").isFile }
        val source = File(root, "Common/src/main/java/tk/glucodata/Notify.java").readText()
        val start = source.indexOf("    // ONGOING-HISTORY-BEGIN:")
        val end = source.indexOf("    // ONGOING-HISTORY-END", start)
        check(start >= 0 && end > start)
        val fixture = File(root, "Common/src/test/resources/java/OngoingHistoryHarness.java").readText()
            .replace("// PRODUCTION_ONGOING_HISTORY", source.substring(start, end)
                .replace("tk.glucodata.ui.DisplayValueResolver", "DisplayValueResolver"))
        val directory = java.nio.file.Files.createTempDirectory("ongoing-notification-history").toFile()
        try {
            val javaFile = File(directory, "OngoingHistoryHarness.java").apply { writeText(fixture) }
            val output = java.io.ByteArrayOutputStream()
            val compiler = checkNotNull(ToolProvider.getSystemJavaCompiler())
            val result = compiler.run(null, output, output, "-d", directory.path, javaFile.path)
            assertEquals(output.toString(), 0, result)
            URLClassLoader(arrayOf(directory.toURI().toURL()), null).use { loader ->
                loader.loadClass("tk.glucodata.OngoingHistoryHarness").getMethod("run").invoke(null)
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
