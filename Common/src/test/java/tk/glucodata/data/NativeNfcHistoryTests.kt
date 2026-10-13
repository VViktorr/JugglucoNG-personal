package tk.glucodata.data

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replays NFC backfill with the production C++ save, store and export code. */
class NativeNfcHistoryTests {
    private fun repositoryFile(path: String): File {
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        while (directory != null) {
            val file = File(directory, path)
            if (file.isFile) return file
            directory = directory.parentFile
        }
        error("Cannot find $path")
    }

    @Test
    fun nfcFillsMissingRawHistoryWithoutChangingTheBleCursorOrAlgorithmValues() {
        val saveSource = repositoryFile("Common/src/main/cpp/share/savehistory.cpp").readText()
        val saveStart = saveSource.indexOf("\nint saveScanTrend(")
        val saveEnd = saveSource.indexOf("\nextern bool addStreamHistory(", saveStart)
        assertTrue("Locate the production NFC trend save", saveStart >= 0 && saveEnd > saveStart)
        val nativeSource = repositoryFile("Common/src/main/cpp/g.cpp").readText()
        val exportStart = nativeSource.indexOf("static bool rawOnlyPollValid(")
        val exportEnd = nativeSource.indexOf("static jlongArray historySamplesToJava(", exportStart)
        assertTrue("Locate the production poll/NFC exporters", exportStart >= 0 && exportEnd > exportStart)
        val fixture = repositoryFile("Common/src/test/resources/native/nfc_raw_history.cpp").readText()
        val includeDirectory = repositoryFile("Common/src/main/cpp/NfcRawHistory.hpp").parentFile
        val directory = Files.createTempDirectory("nfc-history-test").toFile()
        try {
            val source = File(directory, "test.cpp")
            source.writeText(
                fixture.replace("// PRODUCTION_NFC_SAVE", saveSource.substring(saveStart, saveEnd))
                    .replace("// PRODUCTION_EXPORTERS", nativeSource.substring(exportStart, exportEnd))
            )
            val binary = File(directory, "test")
            run(directory, listOf(System.getenv("CXX") ?: "c++", "-std=c++17", "-pthread",
                "-I${includeDirectory.absolutePath}", source.path, "-o", binary.path))
            run(directory, listOf(binary.path))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun run(directory: File, command: List<String>) {
        val log = File(directory, "output.log")
        val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
        val completed = process.waitFor(60, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        assertTrue("Timed out: $command", completed)
        assertEquals(log.readText(), 0, process.exitValue())
    }
}
