package tk.glucodata.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsing of the GitHub Releases payload and the optional `update-manifest.json` asset. */
class GithubReleaseParserTests {

    private fun release(
        tag: String = "1.2.0-Alpha",
        prerelease: Boolean = false,
        draft: Boolean = false,
        assets: String = DEFAULT_ASSETS
    ) = """
        {
          "tag_name": "$tag",
          "name": "$tag",
          "body": "Fixed the thing.",
          "draft": $draft,
          "prerelease": $prerelease,
          "published_at": "2026-08-05T18:00:00Z",
          "assets": [$assets]
        }
    """.trimIndent()

    @Test
    fun `parses a release list and drops drafts`() {
        val json = "[${release()},${release(tag = "1.3.0-Alpha", draft = true)}]"
        val releases = GithubReleaseParser.parseReleases(json)
        assertEquals(1, releases.size)
        assertEquals("1.2.0-Alpha", releases[0].tagName)
        assertEquals(3, releases[0].assets.size)
        assertFalse(releases[0].prerelease)
    }

    @Test
    fun `falls back to asset-name matching when the release has no manifest`() {
        val release = GithubReleaseParser.parseReleases("[${release()}]").single()
        val update = GithubReleaseParser.toAvailableUpdate(
            release = release,
            manifest = null,
            applicationId = "tk.glucodata.ng",
            deviceSdk = 34
        )
        assertNotNull(update)
        assertEquals("JugglucoNG-1.2.0-Alpha.apk", update!!.artifact.fileName)
        assertEquals("1.2.0-Alpha", update.versionName)
        // No manifest means no version code and no digest to compare against.
        assertNull(update.versionCode)
        assertNull(update.artifact.sha256)
    }

    @Test
    fun `manifest supplies version code and checksum`() {
        val release = GithubReleaseParser.parseReleases("[${release()}]").single()
        val manifest = GithubReleaseParser.parseManifest(MANIFEST)
        assertNotNull(manifest)
        val update = GithubReleaseParser.toAvailableUpdate(
            release = release,
            manifest = manifest,
            applicationId = "tk.glucodata.ng",
            deviceSdk = 34
        )
        assertNotNull(update)
        assertEquals(1011, update!!.versionCode)
        assertEquals(SHA, update.artifact.sha256)
        // The URL always comes from the API's asset listing, never from the manifest.
        assertEquals(
            "https://github.com/ctqvva/JugglucoNG/releases/download/1.2.0-Alpha/JugglucoNG-1.2.0-Alpha.apk",
            update.artifact.downloadUrl
        )
    }

    @Test
    fun `same package uses a different APK and version code on Wear OS`() {
        val packageId = "tk.glucodata.ng"
        val phone = "JugglucoNG-1.4.3-phone.apk"
        val watch = "JugglucoNG-1.4.3-wear.apk"
        val release = GithubReleaseParser.parseReleases(
            "[${release(tag = "main-v1.4.3", assets = """
                {"name":"$phone","browser_download_url":"https://example.test/$phone","size":10},
                {"name":"$watch","browser_download_url":"https://example.test/$watch","size":12}
            """)}]"
        ).single()
        val manifestText = """
            {"schema":1,"versionName":"1.4.3","versionCode":1403,"minSdk":26,"artifacts":[
              {"applicationId":"$packageId","file":"$phone","sha256":"$SHA"},
              {"applicationId":"$packageId","device":"wear","file":"$watch","versionCode":1001403,"minSdk":28,"sha256":"$SHA"}
            ]}
        """.trimIndent()
        val manifest = GithubReleaseParser.parseManifest(manifestText)!!
        val phoneUpdate = GithubReleaseParser.toAvailableUpdate(release, manifest, packageId, 34)!!
        val watchUpdate = GithubReleaseParser.toAvailableUpdate(release, manifest, packageId, 34, wear = true)!!
        assertEquals(phone, phoneUpdate.artifact.fileName)
        assertEquals(1403, phoneUpdate.versionCode)
        assertEquals(watch, watchUpdate.artifact.fileName)
        assertEquals(1001403, watchUpdate.versionCode)
        assertEquals(SHA, watchUpdate.artifact.sha256)
        assertTrue(GithubReleaseParser.isNewerThanInstalled(watchUpdate, 1001402, "1.4.2"))
        assertNull(GithubReleaseParser.toAvailableUpdate(release, manifest, packageId, 27, wear = true))
        assertNull(GithubReleaseParser.toAvailableUpdate(release, null, packageId, 34, wear = true))
        val phoneOnly = GithubReleaseParser.parseManifest(MANIFEST)!!
        assertNull(GithubReleaseParser.toAvailableUpdate(release, phoneOnly, packageId, 34, wear = true))
    }

    @Test
    fun `a manifest that omits this variant means no update`() {
        val release = GithubReleaseParser.parseReleases("[${release()}]").single()
        val manifest = GithubReleaseParser.parseManifest(MANIFEST)
        assertNull(
            GithubReleaseParser.toAvailableUpdate(
                release = release,
                manifest = manifest,
                applicationId = "tk.glucodata.ng.dub2",
                deviceSdk = 34
            )
        )
    }

    @Test
    fun `minSdk above the device blocks the offer`() {
        val release = GithubReleaseParser.parseReleases("[${release()}]").single()
        val manifest = GithubReleaseParser.parseManifest(MANIFEST.replace("\"minSdk\": 26", "\"minSdk\": 35"))
        assertNull(
            GithubReleaseParser.toAvailableUpdate(
                release = release,
                manifest = manifest,
                applicationId = "tk.glucodata.ng",
                deviceSdk = 34
            )
        )
    }

    @Test
    fun `unknown manifest schema is rejected`() {
        assertNull(GithubReleaseParser.parseManifest(MANIFEST.replace("\"schema\": 1", "\"schema\": 9")))
        assertNull(GithubReleaseParser.parseManifest("{ not json"))
    }

    @Test
    fun `version code decides when the manifest declares one`() {
        val release = GithubReleaseParser.parseReleases("[${release()}]").single()
        val update = GithubReleaseParser.toAvailableUpdate(
            release, GithubReleaseParser.parseManifest(MANIFEST), "tk.glucodata.ng", 34
        )!!
        assertTrue(GithubReleaseParser.isNewerThanInstalled(update, 1010, "9.9.9"))
        assertFalse(GithubReleaseParser.isNewerThanInstalled(update, 1011, "0.0.1"))
    }

    @Test
    fun `version name decides when no manifest is present`() {
        val release = GithubReleaseParser.parseReleases("[${release()}]").single()
        val update = GithubReleaseParser.toAvailableUpdate(release, null, "tk.glucodata.ng", 34)!!
        assertTrue(GithubReleaseParser.isNewerThanInstalled(update, 9999, "1.1.0-Alpha"))
        assertFalse(GithubReleaseParser.isNewerThanInstalled(update, 1, "1.2.0-Alpha"))
    }

    @Test
    fun `releases without an asset for this variant are skipped`() {
        val release = GithubReleaseParser.parseReleases(
            "[${release(assets = wearAsset())}]"
        ).single()
        assertNull(GithubReleaseParser.toAvailableUpdate(release, null, "tk.glucodata.ng", 34))
    }

    private companion object {
        const val SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        fun asset(name: String, size: Long = 76108854L) = """
            {
              "name": "$name",
              "size": $size,
              "browser_download_url": "https://github.com/ctqvva/JugglucoNG/releases/download/1.2.0-Alpha/$name"
            }
        """.trimIndent()

        val DEFAULT_ASSETS = listOf(
            asset("JugglucoNG-1.2.0-Alpha.apk"),
            asset("JugglucoNG-1.2.0-Alpha-dub.apk", 76108902L),
            asset("update-manifest.json", 512L)
        ).joinToString(",")

        fun wearAsset() = asset("JugglucoNG-1.2.0-Alpha-wear.apk")

        val MANIFEST = """
            {
              "schema": 1,
              "versionName": "1.2.0-Alpha",
              "versionCode": 1011,
              "minSdk": 26,
              "artifacts": [
                {
                  "applicationId": "tk.glucodata.ng",
                  "file": "JugglucoNG-1.2.0-Alpha.apk",
                  "size": 76108854,
                  "sha256": "$SHA"
                },
                {
                  "applicationId": "tk.glucodata.ng.dub",
                  "file": "JugglucoNG-1.2.0-Alpha-dub.apk",
                  "size": 76108902,
                  "sha256": "$SHA"
                }
              ]
            }
        """.trimIndent()
    }
}
