package tk.glucodata.glucosecomplication

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplicationProviderWiringTests {
    private val moduleRoot: File = run {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "src/wear/AndroidManifest.xml").isFile) break
            File(dir, "Common/src/wear/AndroidManifest.xml").takeIf { it.isFile }?.let {
                dir = File(dir, "Common")
                break
            }
            dir = dir.parentFile
        }
        dir ?: throw AssertionError("Common module root not found from ${System.getProperty("user.dir")}")
    }

    private fun source(relative: String): String = File(moduleRoot, relative).readText()

    private fun serviceBlock(className: String): String {
        val manifest = source("src/wear/AndroidManifest.xml")
        val at = manifest.indexOf("android:name=\".glucosecomplication.$className\"")
        assertTrue("missing service $className", at >= 0)
        val start = manifest.lastIndexOf("<service", at)
        val end = manifest.indexOf("</service>", at)
        assertTrue("unterminated service $className", start >= 0 && end > at)
        return manifest.substring(start, end)
    }

    private fun supportedTypes(className: String): Set<String> {
        val block = serviceBlock(className)
        val marker = "android.support.wearable.complications.SUPPORTED_TYPES"
        val at = block.indexOf(marker)
        assertTrue("$className has no SUPPORTED_TYPES", at >= 0)
        val tail = block.substring(at)
        val value = Regex("""android:value="([^"]+)"""").find(tail)?.groupValues?.get(1)
            ?: throw AssertionError("$className has no supported type value")
        return value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    @Test
    fun trendProviderIsOfferableInImageAndTextSlots() {
        assertEquals(
            setOf("ICON", "SMALL_IMAGE", "SHORT_TEXT", "LONG_TEXT"),
            supportedTypes("ArrowDataSourceService"),
        )
    }

    @Test
    fun valueAndTrendProviderCoversEveryCommonSlotShape() {
        assertEquals(
            setOf("LARGE_IMAGE", "SMALL_IMAGE", "ICON", "SHORT_TEXT", "LONG_TEXT", "RANGED_VALUE"),
            supportedTypes("ArrowValueDataSourceService"),
        )
    }

    @Test
    fun valueAndTrendTextDoesNotDependOnOptionalImage() {
        val source = source("src/wear/java/tk/glucodata/glucosecomplication/ArrowValueDataSourceService.kt")
        assertTrue(source.contains("ComplicationType.SHORT_TEXT -> GlucoseComplicationData.shortValueData("))
        assertTrue(source.contains("ComplicationType.LONG_TEXT -> GlucoseComplicationData.longTextData("))
        assertTrue(source.contains("ComplicationType.RANGED_VALUE -> GlucoseComplicationData.rangedValueData("))
        assertTrue(
            "value + trend must request text arrows for text/ranged forms",
            Regex("""withArrow\s*=\s*true""").findAll(source).count() >= 3,
        )
    }

    @Test
    fun trendPreviewReturnsTheRequestedTypeInsteadOfAlwaysAnIcon() {
        val source = source("src/wear/java/tk/glucodata/glucosecomplication/ArrowDataSourceService.kt")
        assertTrue(source.contains("override fun getPreviewData(type: ComplicationType): ComplicationData? ="))
        assertTrue(source.contains("build(type, GlucoseComplicationData.previewReading())"))
    }
}
