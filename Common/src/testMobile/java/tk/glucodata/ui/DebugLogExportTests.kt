package tk.glucodata.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class DebugLogExportTests {

    @Test
    fun `file backed log is copied after the report header`() {
        val sourceFile = File.createTempFile("juggluco-debug-export", ".log")
        try {
            val payload = "I/Libre2GattCallback stage=gatt-state status=0\n".repeat(6_000).toByteArray()
            sourceFile.outputStream().use { it.write(payload) }
            val output = ByteArrayOutputStream()

            writeDebugLogReport(
                output = output,
                header = "# test build\n\n",
                source = DebugLogExportSource.FileContent(sourceFile),
            )

            assertArrayEquals("# test build\n\n".toByteArray() + payload, output.toByteArray())
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun `bounded text log is copied after the report header`() {
        val output = ByteArrayOutputStream()

        writeDebugLogReport(
            output = output,
            header = "# test build\n\n",
            source = DebugLogExportSource.TextContent("BLE error\n"),
        )

        assertArrayEquals("# test build\n\nBLE error\n".toByteArray(), output.toByteArray())
    }

    @Test
    fun `old authentication response is removed from a saved trace`() {
        val output = ByteArrayOutputStream()
        val oldTrace = """NightPost: readJSONObject len=59 {"UserToken":"test-only","Email":"example@example.test"}
I/Libre2GattCallback stage=gatt-state status=0
"""
        writeDebugLogReport(output, "", DebugLogExportSource.TextContent(oldTrace))
        val text = output.toString(Charsets.UTF_8.name())
        assertFalse(text.contains("test-only"))
        assertFalse(text.contains("example@example.test"))
        assertTrue(text.contains("stage=gatt-state status=0"))
    }

    @Test
    fun `missing and empty file sources are rejected before export`() {
        val missing = File("/path/that/does/not/exist/juggluco-trace.log")
        val empty = File.createTempFile("juggluco-empty-export", ".log")
        try {
            assertTrue(DebugLogExportSource.FileContent(missing).isEmpty())
            assertTrue(DebugLogExportSource.FileContent(empty).isEmpty())
            assertFalse(DebugLogExportSource.TextContent("one line\n").isEmpty())
        } finally {
            empty.delete()
        }
    }
}
