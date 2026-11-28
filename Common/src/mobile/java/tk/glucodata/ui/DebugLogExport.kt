package tk.glucodata.ui

import tk.glucodata.LogPrivacy
import java.io.File
import java.io.OutputStream

/**
 * Export payloads stay split by storage type: trace and logcat are streamed from disk, while the
 * deliberately bounded BLE history can remain an in-memory string.
 */
internal sealed class DebugLogExportSource {
    abstract fun isEmpty(): Boolean
    abstract fun copyTo(output: OutputStream)

    class FileContent(private val file: File) : DebugLogExportSource() {
        override fun isEmpty(): Boolean = !file.exists() || file.length() == 0L

        override fun copyTo(output: OutputStream) {
            file.bufferedReader().useLines { lines -> writeSanitizedLines(output, lines) }
        }
    }

    class TextContent(private val text: String) : DebugLogExportSource() {
        override fun isEmpty(): Boolean = text.isEmpty()

        override fun copyTo(output: OutputStream) {
            text.reader().buffered().useLines { lines -> writeSanitizedLines(output, lines) }
        }
    }
}

internal fun writeDebugLogReport(
    output: OutputStream,
    header: String,
    source: DebugLogExportSource,
) {
    output.write(header.toByteArray(Charsets.UTF_8))
    source.copyTo(output)
}

private fun writeSanitizedLines(output: OutputStream, lines: Sequence<String>) {
    val writer = output.bufferedWriter()
    lines.forEach { line ->
        writer.write(LogPrivacy.sanitizeExportLine(line))
        writer.newLine()
    }
    writer.flush()
}
