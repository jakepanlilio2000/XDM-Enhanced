package xdm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import xdm.app.ui.components.BrowserExtensionPanel
import xdm.app.utils.Browser
import xdm.integration.ExtensionVersionCheck
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BrowserExtensionUpdateSystemTest {

    private val repoRoot: File = run {
        val here = File(".").canonicalFile
        if (File(here, "browser-extension").exists()) {
            here
        } else if (File(here.parentFile, "browser-extension").exists()) {
            here.parentFile
        } else {
            here
        }
    }

    @Test
    fun firefoxManifest_geckoSettingsConfiguredWithUpdateUrl() {
        val manifestFile = File(repoRoot, "browser-extension/firefox-extension/manifest.json")
        assertTrue(manifestFile.exists(), "Firefox manifest.json must exist at: ${manifestFile.absolutePath}")

        val json = Json.parseToJsonElement(manifestFile.readText()).jsonObject
        assertEquals(2, json["manifest_version"]?.toString()?.toInt(), "Firefox extension must be MV2")

        val gecko = json["browser_specific_settings"]?.jsonObject?.get("gecko")?.jsonObject
        assertNotNull(gecko, "Firefox manifest must declare browser_specific_settings.gecko")

        val geckoId = gecko?.get("id")?.toString()?.replace("\"", "")
        assertEquals("xdm-enhanced-browser-monitor@jakepanlilio2000.github.io", geckoId)

        val updateUrl = gecko?.get("update_url")?.toString()?.replace("\"", "")
        assertNotNull(updateUrl, "Firefox manifest must declare gecko.update_url")
        assertTrue(updateUrl!!.startsWith("https://raw.githubusercontent.com/jakepanlilio2000/XDM-Enhanced/"), "Gecko update_url must point to user fork: $updateUrl")
        assertTrue(updateUrl.endsWith("updates.json"), "Gecko update_url must end with updates.json: $updateUrl")
    }

    @Test
    fun firefoxUpdatesJson_validManifestAndPointsToForkReleases() {
        val updatesFile = File(repoRoot, "browser-extension/updates.json")
        assertTrue(updatesFile.exists(), "browser-extension/updates.json must exist")

        val json = Json.parseToJsonElement(updatesFile.readText()).jsonObject
        val addons = json["addons"]?.jsonObject
        assertNotNull(addons, "updates.json must declare addons object")

        val xdmAddon = addons?.get("xdm-enhanced-browser-monitor@jakepanlilio2000.github.io")?.jsonObject
        assertNotNull(xdmAddon, "updates.json must declare entry for gecko ID")

        val updates = xdmAddon?.get("updates")
        assertNotNull(updates, "updates array must be present")
        assertTrue(updates.toString().contains("https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/"), "Update link must point to fork release assets: $updates")
        assertTrue(updates.toString().contains(".xpi"), "Update link must point to .xpi package: $updates")
    }

    @Test
    fun chromeUpdatesXml_validXmlAndPointsToForkReleases() {
        val xmlFile = File(repoRoot, "browser-extension/chrome-updates.xml")
        assertTrue(xmlFile.exists(), "browser-extension/chrome-updates.xml must exist")

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc: Document = dBuilder.parse(xmlFile)
        doc.documentElement.normalize()

        assertEquals("gupdate", doc.documentElement.nodeName)
        val appNodes = doc.getElementsByTagName("app")
        assertTrue(appNodes.length > 0, "chrome-updates.xml must contain <app> elements")

        val appElem = appNodes.item(0) as org.w3c.dom.Element
        assertEquals("xdmenhancedchromebrowserintegration0", appElem.getAttribute("appid"))

        val updatecheckNodes = appElem.getElementsByTagName("updatecheck")
        assertTrue(updatecheckNodes.length > 0, "Must contain <updatecheck> element")
        val updatecheck = updatecheckNodes.item(0) as org.w3c.dom.Element
        val codebase = updatecheck.getAttribute("codebase")
        assertTrue(codebase.startsWith("https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/"), "Codebase must point to fork releases: $codebase")
        assertTrue(codebase.endsWith(".crx"), "Codebase must point to .crx package: $codebase")
    }

    @Test
    fun chromiumManifest_validMv3AndForkHomepage() {
        val manifestFile = File(repoRoot, "browser-extension/chrome-extension/manifest.json")
        assertTrue(manifestFile.exists(), "Chromium manifest.json must exist")

        val json = Json.parseToJsonElement(manifestFile.readText()).jsonObject
        assertEquals(3, json["manifest_version"]?.toString()?.toInt(), "Chromium extension must be MV3")

        val homepage = json["homepage_url"]?.toString()?.replace("\"", "")
        assertEquals("https://github.com/jakepanlilio2000/XDM-Enhanced", homepage)
    }

    @Test
    fun extensionVersionCheck_handlesAllBrowserFamiliesAccurately() {
        val chromeUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/141.0.0.0 Safari/537.36"
        val edgeUa = "$chromeUa Edg/141.0.0.0"
        val braveUa = "$chromeUa Brave/141.0.0.0"
        val firefoxUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"

        // Detection
        assertEquals(Browser.CHROME, ExtensionVersionCheck.detectBrowser("chrome/3.5.2", null, chromeUa))
        assertEquals(Browser.EDGE, ExtensionVersionCheck.detectBrowser("chrome/3.5.2", null, edgeUa))
        assertEquals(Browser.BRAVE, ExtensionVersionCheck.detectBrowser("chrome/3.5.2", null, braveUa))
        assertEquals(Browser.FIREFOX, ExtensionVersionCheck.detectBrowser("firefox/1.1.2", null, firefoxUa))

        // Outdated check
        assertNull(ExtensionVersionCheck.outdatedBrowser("chrome/3.5.2", null, chromeUa), "3.5.2 is not older than 3.5")
        assertNull(ExtensionVersionCheck.outdatedBrowser("firefox/1.1.2", null, firefoxUa), "1.1.2 is not older than 1.1")

        assertEquals(Browser.CHROME, ExtensionVersionCheck.outdatedBrowser("chrome/3.4", null, chromeUa))
        assertEquals(Browser.EDGE, ExtensionVersionCheck.outdatedBrowser("chrome/3.4", null, edgeUa))
        assertEquals(Browser.FIREFOX, ExtensionVersionCheck.outdatedBrowser("firefox/1.0", null, firefoxUa))
    }

    @Test
    fun extensionPageUrl_neverReferencesUpstream() {
        val urls = listOf(
            BrowserExtensionPanel.extensionPageUrl("chrome"),
            BrowserExtensionPanel.extensionPageUrl("firefox"),
            BrowserExtensionPanel.extensionPageUrl("edge"),
            BrowserExtensionPanel.extensionPageUrl("brave"),
            BrowserExtensionPanel.extensionPageUrl("opera"),
            BrowserExtensionPanel.extensionPageUrl("other")
        )

        for (u in urls) {
            assertFalse(u.contains("xtremedownloadmanager.com"), "URL must not point to upstream: $u")
            assertTrue(u.startsWith("https://github.com/jakepanlilio2000/XDM-Enhanced"), "URL must point to fork: $u")
        }
    }
}

