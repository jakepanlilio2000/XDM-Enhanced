package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xdm.app.skins.SkinPackageValidator
import xdm.app.skins.SkinPackager
import xdm.app.skins.SkinValidatorCli
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class SampleSkinGeneratorTest {

    @Test
    fun generateAndValidateCyberNeonSkin() {
        // Root of the workspace
        val repoRoot = File("..").canonicalFile.let {
            if (File(it, "pom.xml").exists()) it else File(".").canonicalFile
        }
        val sampleSkinsDir = File(repoRoot, "sample-skins")
        val cyberNeonDir = File(sampleSkinsDir, "cyber-neon")
        cyberNeonDir.mkdirs()

        val bgDir = File(cyberNeonDir, "backgrounds").apply { mkdirs() }
        val toolbarIconDir = File(cyberNeonDir, "icons/toolbar").apply { mkdirs() }
        val categoryIconDir = File(cyberNeonDir, "icons/categories").apply { mkdirs() }

        // 1. Write skin.json (Schema Version 2)
        val skinJson = """
        {
          "schemaVersion": 2,
          "id": "cyber-neon",
          "name": "Cyber Neon",
          "version": "1.0.0",
          "author": "XDM Enhanced Team",
          "description": "High-contrast cyberpunk visual theme with glowing neon cyan and emerald accents, custom category icons, wallpapers, full-UI modal styling, and dark grid aesthetics.",
          "minAppVersion": "1.0.0-beta.4",
          "license": "GPL-3.0-or-later",
          "homepage": "https://github.com/jakepanlilio2000/XDM-Enhanced",
          "geometry": {
            "componentArc": 12,
            "buttonArc": 10,
            "rowHeight": 28,
            "sidebarWidth": 220
          },
          "accents": {
            "dark": "#00F0FF",
            "light": "#0284C7"
          },
          "colors": {
            "dark": {
              "windowBackground": "#0B0E14",
              "panelBackground": "#0E121A",
              "sidebarBackground": "#07090D",
              "sidebarBorder": "#1E293B",
              "sidebarForeground": "#E0F7FA",
              "sidebarSelectedForeground": "#00F0FF",
              "sidebarSelectionBackground": "#162338",
              "sidebarHoverBackground": "#101826",
              "listBackground": "#0B0E14",
              "listTitleForeground": "#E0F7FA",
              "listSelectionBackground": "#162338",
              "listHoverBackground": "#101826"
            },
            "light": {
              "windowBackground": "#F0F4F8",
              "panelBackground": "#FFFFFF",
              "sidebarBackground": "#E2E8F0",
              "sidebarBorder": "#CBD5E1",
              "sidebarForeground": "#0F172A",
              "sidebarSelectedForeground": "#0284C7",
              "sidebarSelectionBackground": "#BAE6FD",
              "sidebarHoverBackground": "#E0F2FE",
              "listBackground": "#FFFFFF",
              "listTitleForeground": "#0F172A",
              "listSelectionBackground": "#E0F2FE",
              "listHoverBackground": "#F0F9FF"
            }
          },
          "wallpaper": {
            "dark": "backgrounds/cyber-dark.png",
            "light": "backgrounds/cyber-light.png",
            "mode": "fill",
            "opacity": 0.15
          },
          "layout": {
            "toolbarPosition": "top",
            "sidebarPosition": "left",
            "showStatusBar": true,
            "showSearchBar": true
          },
          "icons": {
            "categories": {
              "documents": "icons/categories/documents.png",
              "compressed": "icons/categories/compressed.png",
              "music": "icons/categories/music.png",
              "videos": "icons/categories/videos.png",
              "programs": "icons/categories/programs.png",
              "images": "icons/categories/images.png",
              "torrents": "icons/categories/torrents.png",
              "all": "icons/categories/all.png"
            },
            "toolbar": {
              "ADD_URL": "icons/toolbar/add.png",
              "RESUME": "icons/toolbar/resume.png",
              "STOP": "icons/toolbar/pause.png",
              "DELETE": "icons/toolbar/delete.png",
              "SETTINGS": "icons/toolbar/settings.png"
            }
          },
          "regions": {
            "main_frame": {
              "background": "#0B0E14",
              "cornerArc": 12,
              "buttonArc": 10
            },
            "toolbar": {
              "height": 42,
              "buttonArc": 10,
              "cornerArc": 10
            },
            "sidebar": {
              "width": 220,
              "background": "#07090D",
              "cornerArc": 10
            },
            "download_list": {
              "height": 32,
              "cardArc": 10,
              "background": "#0B0E14"
            },
            "download_modal": {
              "background": "#0E121A",
              "cornerArc": 14,
              "buttonArc": 10
            },
            "video_download_modal": {
              "background": "#0E121A",
              "cornerArc": 14,
              "buttonArc": 10
            },
            "progress_modal": {
              "background": "#0E121A",
              "cornerArc": 14,
              "buttonArc": 10,
              "progressBarArc": 10
            },
            "torrent_modal": {
              "background": "#0E121A",
              "cornerArc": 14,
              "buttonArc": 10
            },
            "settings": {
              "background": "#0B0E14",
              "cornerArc": 14,
              "buttonArc": 10
            },
            "plugin_manager": {
              "cornerArc": 14,
              "buttonArc": 10,
              "cardArc": 10
            },
            "about": {
              "background": "#0B0E14",
              "cornerArc": 16,
              "buttonArc": 10
            },
            "dialogs": {
              "background": "#131720",
              "cornerArc": 12,
              "buttonArc": 10
            },
            "tooltips_popups": {
              "cornerArc": 10,
              "background": "#131720",
              "foreground": "#00F0FF"
            }
          },
          "uiDefaults": {
            "Component.arc": 12,
            "Button.arc": 10,
            "TextComponent.arc": 12,
            "ScrollBar.thumbArc": 6
          }
        }
        """.trimIndent()
        File(cyberNeonDir, "skin.json").writeText(skinJson)

        // 2. Generate Wallpapers (800x600)
        generateWallpaper(File(bgDir, "cyber-dark.png"), isDark = true)
        generateWallpaper(File(bgDir, "cyber-light.png"), isDark = false)

        // 3. Generate Toolbar Icons (24x24)
        generateIcon(File(toolbarIconDir, "add.png")) { g ->
            g.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.drawLine(12, 5, 12, 19)
            g.drawLine(5, 12, 19, 12)
        }
        generateIcon(File(toolbarIconDir, "resume.png")) { g ->
            g.fillPolygon(intArrayOf(7, 18, 7), intArrayOf(5, 12, 19), 3)
        }
        generateIcon(File(toolbarIconDir, "pause.png")) { g ->
            g.fillRect(6, 5, 4, 14)
            g.fillRect(14, 5, 4, 14)
        }
        generateIcon(File(toolbarIconDir, "delete.png")) { g ->
            g.stroke = BasicStroke(2.0f)
            g.drawRect(6, 8, 12, 12)
            g.drawLine(4, 8, 20, 8)
            g.drawLine(9, 5, 15, 5)
        }
        generateIcon(File(toolbarIconDir, "settings.png")) { g ->
            g.stroke = BasicStroke(2.0f)
            g.drawOval(6, 6, 12, 12)
            g.drawOval(9, 9, 6, 6)
        }

        // 4. Generate Category Icons (24x24)
        val catMap = mapOf(
            "documents.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f)
                g.drawRect(5, 4, 14, 16)
                g.drawLine(8, 8, 14, 8)
                g.drawLine(8, 12, 16, 12)
                g.drawLine(8, 16, 13, 16)
            },
            "compressed.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f)
                g.drawRect(5, 5, 14, 14)
                g.drawLine(12, 5, 12, 19)
                g.fillRect(10, 8, 4, 3)
            },
            "music.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f)
                g.drawLine(8, 6, 18, 4)
                g.drawLine(8, 6, 8, 16)
                g.drawLine(18, 4, 18, 14)
                g.fillOval(5, 14, 6, 5)
                g.fillOval(15, 12, 6, 5)
            },
            "videos.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f)
                g.drawRect(4, 5, 16, 14)
                g.fillPolygon(intArrayOf(10, 15, 10), intArrayOf(9, 12, 15), 3)
            },
            "programs.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f)
                g.drawRect(4, 5, 16, 14)
                g.drawLine(4, 9, 20, 9)
                g.fillOval(6, 6, 2, 2)
                g.fillOval(9, 6, 2, 2)
            },
            "images.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f)
                g.drawRect(4, 5, 16, 14)
                g.fillOval(7, 8, 3, 3)
                g.fillPolygon(intArrayOf(6, 11, 15, 18), intArrayOf(16, 11, 14, 16), 4)
            },
            "torrents.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f)
                g.drawOval(4, 4, 16, 16)
                g.drawLine(12, 7, 12, 15)
                g.drawLine(8, 12, 12, 16)
                g.drawLine(16, 12, 12, 16)
            },
            "all.png" to { g: Graphics2D ->
                g.fillRect(5, 5, 5, 5)
                g.fillRect(14, 5, 5, 5)
                g.fillRect(5, 14, 5, 5)
                g.fillRect(14, 14, 5, 5)
            }
        )

        for ((name, painter) in catMap) {
            generateIcon(File(categoryIconDir, name), painter)
        }

        // 5. Package into .xdmskin archive
        val packageArchive = File(sampleSkinsDir, "cyber-neon.xdmskin")
        SkinPackager.exportSkinPackage(cyberNeonDir, packageArchive)
        assertTrue(packageArchive.exists(), "Cyber neon package archive must exist")
        assertTrue(packageArchive.length() > 0, "Package archive must not be empty")

        // 6. Validate the exported package
        val validation = SkinPackageValidator.validateArchive(packageArchive)
        assertTrue(validation.isValid, "Exported package must be valid: ${validation.errors}")
        assertEquals(2, validation.manifest?.schemaVersion, "Schema version must be 2")
        assertEquals(13, validation.manifest?.regions?.size, "Must declare all 13 regions")

        // 7. Test CLI Validator
        val cliResult = SkinValidatorCli.validateTarget(packageArchive)
        assertTrue(cliResult.isValid, "CLI validation must succeed: ${cliResult.errors}")
    }

    private fun generateWallpaper(file: File, isDark: Boolean) {
        val width = 800
        val height = 600
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val bg = if (isDark) Color(0x0B, 0x0E, 0x14) else Color(0xF0, 0xF4, 0xF8)
        val gridColor = if (isDark) Color(0x00, 0xF0, 0xFF, 30) else Color(0x02, 0x84, 0xC7, 25)
        val accentColor = if (isDark) Color(0x00, 0xF0, 0xFF, 60) else Color(0x02, 0x84, 0xC7, 50)

        g.color = bg
        g.fillRect(0, 0, width, height)

        g.color = gridColor
        g.stroke = BasicStroke(1f)
        for (x in 0 until width step 40) {
            g.drawLine(x, 0, x, height)
        }
        for (y in 0 until height step 40) {
            g.drawLine(0, y, width, y)
        }

        g.color = accentColor
        g.stroke = BasicStroke(2f)
        g.drawOval(width / 2 - 120, height / 2 - 120, 240, 240)
        g.drawOval(width / 2 - 80, height / 2 - 80, 160, 160)

        g.dispose()
        ImageIO.write(img, "png", file)
    }

    private fun generateIcon(file: File, paint: (Graphics2D) -> Unit) {
        generateColoredIcon(file, Color(0x00, 0xF0, 0xFF), paint)
    }

    private fun generateColoredIcon(file: File, strokeColor: Color, paint: (Graphics2D) -> Unit) {
        val size = 24
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = strokeColor
        paint(g)
        g.dispose()
        ImageIO.write(img, "png", file)
    }

    private data class ShowcaseSkinSpec(
        val id: String,
        val name: String,
        val description: String,
        val componentArc: Int,
        val buttonArc: Int,
        val rowHeight: Int,
        val sidebarWidth: Int,
        val accentDark: String,
        val accentLight: String,
        val iconColor: Color,
        val darkPalette: Map<String, String>,
        val lightPalette: Map<String, String>,
        val windowJson: String,
        val widgetsJson: String,
        val layoutJson: String,
        val wallpaperOpacity: Float = 0.24f,
        val paintWallpaper: (Graphics2D, Int, Int, Boolean) -> Unit
    )

    @Test
    fun generateAndValidateFiveShowcaseSkins() {
        val repoRoot = File("..").canonicalFile.let {
            if (File(it, "pom.xml").exists()) it else File(".").canonicalFile
        }
        val sampleSkinsDir = File(repoRoot, "sample-skins")

        val specs = listOf(
            ShowcaseSkinSpec(
                id = "retro-hifi",
                name = "Retro Hi-Fi Console",
                description = "Classic brushed dark-slate hardware deck featuring 3D metallic bevel frame, green phosphor LCD stats ticker, compact shade toggle, segmented LED meters, and scanlines.",
                componentArc = 2,
                buttonArc = 2,
                rowHeight = 50,
                sidebarWidth = 196,
                accentDark = "#00FF66",
                accentLight = "#059669",
                iconColor = Color(0x00, 0xFF, 0x66),
                darkPalette = mapOf(
                    "windowBackground" to "#1D202B",
                    "panelBackground" to "#232634",
                    "sidebarBackground" to "#191C26",
                    "sidebarBorder" to "#3E4458",
                    "sidebarForeground" to "#D7DCE8",
                    "sidebarSelectedForeground" to "#00FF66",
                    "sidebarSelectionBackground" to "#1B2E24",
                    "sidebarHoverBackground" to "#262B3A",
                    "listBackground" to "#12141B",
                    "listTitleForeground" to "#E8FFEC",
                    "listSelectionBackground" to "#1B2E24",
                    "listHoverBackground" to "#1D2230"
                ),
                lightPalette = mapOf(
                    "windowBackground" to "#E2E5EE",
                    "panelBackground" to "#F1F3F8",
                    "sidebarBackground" to "#D4D9E6",
                    "sidebarBorder" to "#8C94A8",
                    "sidebarForeground" to "#1F2430",
                    "sidebarSelectedForeground" to "#047857",
                    "sidebarSelectionBackground" to "#CBE8DA",
                    "sidebarHoverBackground" to "#DFE3EE",
                    "listBackground" to "#F8FAFC",
                    "listTitleForeground" to "#141821",
                    "listSelectionBackground" to "#D1FAE5",
                    "listHoverBackground" to "#EDF2F7"
                ),
                windowJson = """
                  "customTitleBar": true,
                  "hideNativeTitleBar": true,
                  "titleBarHeight": 32,
                  "titleText": "XDM ENHANCED // HI-FI CONSOLE",
                  "titleAlignment": "left",
                  "showAppIcon": true,
                  "showWindowShadeButton": true,
                  "windowShadeHeight": 34,
                  "showStatsTicker": true,
                  "controlButtonsStyle": "beveled",
                  "titleBarGradientStartDark": "#2F3446",
                  "titleBarGradientEndDark": "#1A1D28",
                  "titleBarGradientStartLight": "#C5CADB",
                  "titleBarGradientEndLight": "#9EA5BC",
                  "titleBarForegroundDark": "#E6FFEB",
                  "titleBarForegroundLight": "#111827",
                  "titleBarTexture": {
                    "dark": "backgrounds/titlebar-dark.png",
                    "light": "backgrounds/titlebar-light.png",
                    "mode": "tile",
                    "opacity": 0.45
                  },
                  "frameBorderWidth": 5,
                  "frameCornerArc": 2,
                  "frameBorderDark": "#393F52",
                  "frameBorderLight": "#9CA3B8",
                  "frameHighlightDark": "#858DA6",
                  "frameHighlightLight": "#ECEFF7",
                  "frameShadowDark": "#0F1118",
                  "frameShadowLight": "#555C70"
                """.trimIndent(),
                widgetsJson = """
                  "progressBarStyle": "segmented_led",
                  "progressTrackDark": "#0B110B",
                  "progressTrackLight": "#1E293B",
                  "progressFillStartDark": "#00FF55",
                  "progressFillEndDark": "#FF3B30",
                  "progressFillStartLight": "#10B981",
                  "progressFillEndLight": "#EF4444",
                  "progressSegments": 22,
                  "buttonBevelStyle": "beveled_3d",
                  "buttonSurfaceDark": "#3A3F52",
                  "buttonSurfaceLight": "#D6DBE8",
                  "buttonHighlightDark": "#8C95AE",
                  "buttonShadowDark": "#161822",
                  "buttonHighlightLight": "#FFFFFF",
                  "buttonShadowLight": "#646B80",
                  "showScanlines": true,
                  "scanlineOpacity": 0.08
                """.trimIndent(),
                layoutJson = """
                  "toolbarPosition": "top",
                  "sidebarPosition": "left",
                  "showStatusBar": true,
                  "showSearchBar": true,
                  "showMenuBar": false,
                  "chromeMode": "custom"
                """.trimIndent(),
                wallpaperOpacity = 0.22f,
                paintWallpaper = ::paintRetroHiFiBg
            ),
            ShowcaseSkinSpec(
                id = "anime-sakura",
                name = "Anime Sakura Studio",
                description = "Dreamy anime visual-novel aesthetic with cherry blossom twilight bokeh, rounded glowing pastel cards, blossom window controls, and candy-striped progress bars.",
                componentArc = 16,
                buttonArc = 14,
                rowHeight = 56,
                sidebarWidth = 208,
                accentDark = "#FF6FAE",
                accentLight = "#DB2777",
                iconColor = Color(0xEC, 0x48, 0x99),
                darkPalette = mapOf(
                    "windowBackground" to "#191124",
                    "panelBackground" to "#221731",
                    "sidebarBackground" to "#140D1D",
                    "sidebarBorder" to "#462A63",
                    "sidebarForeground" to "#FCE7F3",
                    "sidebarSelectedForeground" to "#FF85C0",
                    "sidebarSelectionBackground" to "#391D4F",
                    "sidebarHoverBackground" to "#2B183B",
                    "listBackground" to "#160F20",
                    "listTitleForeground" to "#FFF1F7",
                    "listSelectionBackground" to "#391D4F",
                    "listHoverBackground" to "#271737"
                ),
                lightPalette = mapOf(
                    "windowBackground" to "#FFF5F8",
                    "panelBackground" to "#FFFFFF",
                    "sidebarBackground" to "#FFEBF2",
                    "sidebarBorder" to "#F5C2D7",
                    "sidebarForeground" to "#4A1D38",
                    "sidebarSelectedForeground" to "#BE185D",
                    "sidebarSelectionBackground" to "#FBCFE8",
                    "sidebarHoverBackground" to "#FCE7F3",
                    "listBackground" to "#FFF9FB",
                    "listTitleForeground" to "#3B152C",
                    "listSelectionBackground" to "#FBCFE8",
                    "listHoverBackground" to "#FDF2F8"
                ),
                windowJson = """
                  "customTitleBar": true,
                  "hideNativeTitleBar": true,
                  "titleBarHeight": 36,
                  "titleText": "XDM Enhanced :: Sakura Studio",
                  "titleAlignment": "left",
                  "showAppIcon": true,
                  "showWindowShadeButton": true,
                  "windowShadeHeight": 66,
                  "showStatsTicker": true,
                  "controlButtonsStyle": "anime",
                  "titleBarGradientStartDark": "#432259",
                  "titleBarGradientEndDark": "#7D2460",
                  "titleBarGradientStartLight": "#FBCFE8",
                  "titleBarGradientEndLight": "#E9D5FF",
                  "titleBarForegroundDark": "#FFF0F7",
                  "titleBarForegroundLight": "#4A1D38",
                  "frameBorderWidth": 3,
                  "frameCornerArc": 16,
                  "frameBorderDark": "#9D4EDD",
                  "frameBorderLight": "#F472B6",
                  "frameHighlightDark": "#FF99C8",
                  "frameHighlightLight": "#FFFFFF",
                  "frameShadowDark": "#240E36",
                  "frameShadowLight": "#DB2777"
                """.trimIndent(),
                widgetsJson = """
                  "progressBarStyle": "striped_candy",
                  "progressTrackDark": "#2A1B3D",
                  "progressTrackLight": "#FBCFE8",
                  "progressFillStartDark": "#FF6EC7",
                  "progressFillEndDark": "#A78BFA",
                  "progressFillStartLight": "#EC4899",
                  "progressFillEndLight": "#8B5CF6",
                  "progressSegments": 16,
                  "buttonBevelStyle": "pill_glow",
                  "buttonSurfaceDark": "#331D46",
                  "buttonSurfaceLight": "#FCE7F3",
                  "buttonHighlightDark": "#FF75B5",
                  "buttonShadowDark": "#1F112B",
                  "buttonHighlightLight": "#EC4899",
                  "buttonShadowLight": "#F9A8D4"
                """.trimIndent(),
                layoutJson = """
                  "toolbarPosition": "top",
                  "sidebarPosition": "left",
                  "showStatusBar": true,
                  "showSearchBar": true,
                  "showMenuBar": true,
                  "chromeMode": "custom"
                """.trimIndent(),
                wallpaperOpacity = 0.25f,
                paintWallpaper = ::paintSakuraBg
            ),
            ShowcaseSkinSpec(
                id = "steampunk-brass",
                name = "Steampunk Brass Works",
                description = "Victorian clockwork theme featuring riveted antique brass bezel, mahogany walnut chassis, amber vacuum-tube pressure gauges, and interlocking cogwheel blueprints.",
                componentArc = 6,
                buttonArc = 6,
                rowHeight = 54,
                sidebarWidth = 210,
                accentDark = "#E6A832",
                accentLight = "#B45309",
                iconColor = Color(0xE6, 0xA8, 0x32),
                darkPalette = mapOf(
                    "windowBackground" to "#1B120C",
                    "panelBackground" to "#241810",
                    "sidebarBackground" to "#150E09",
                    "sidebarBorder" to "#7A5628",
                    "sidebarForeground" to "#F5DEB3",
                    "sidebarSelectedForeground" to "#FFCA58",
                    "sidebarSelectionBackground" to "#3E2914",
                    "sidebarHoverBackground" to "#2C1D0F",
                    "listBackground" to "#18100A",
                    "listTitleForeground" to "#FBE8C6",
                    "listSelectionBackground" to "#3E2914",
                    "listHoverBackground" to "#261A10"
                ),
                lightPalette = mapOf(
                    "windowBackground" to "#F5ECD8",
                    "panelBackground" to "#FBF6EA",
                    "sidebarBackground" to "#EADBC0",
                    "sidebarBorder" to "#A67C46",
                    "sidebarForeground" to "#3B2412",
                    "sidebarSelectedForeground" to "#92400E",
                    "sidebarSelectionBackground" to "#E6CFA8",
                    "sidebarHoverBackground" to "#EFE0C5",
                    "listBackground" to "#FBF6EA",
                    "listTitleForeground" to "#2E1A0B",
                    "listSelectionBackground" to "#E6CFA8",
                    "listHoverBackground" to "#F3E8D2"
                ),
                windowJson = """
                  "customTitleBar": true,
                  "hideNativeTitleBar": true,
                  "titleBarHeight": 36,
                  "titleText": "CHRONO-XDM // AETHER WORKS",
                  "titleAlignment": "left",
                  "showAppIcon": true,
                  "showWindowShadeButton": true,
                  "windowShadeHeight": 64,
                  "showStatsTicker": true,
                  "controlButtonsStyle": "steampunk",
                  "titleBarGradientStartDark": "#593816",
                  "titleBarGradientEndDark": "#2A1809",
                  "titleBarGradientStartLight": "#CFA360",
                  "titleBarGradientEndLight": "#9C6E31",
                  "titleBarForegroundDark": "#FBE8C6",
                  "titleBarForegroundLight": "#241305",
                  "frameBorderWidth": 6,
                  "frameCornerArc": 4,
                  "frameBorderDark": "#8B6328",
                  "frameBorderLight": "#B3833E",
                  "frameHighlightDark": "#F2C96B",
                  "frameHighlightLight": "#FFF0C2",
                  "frameShadowDark": "#211205",
                  "frameShadowLight": "#5A3A15"
                """.trimIndent(),
                widgetsJson = """
                  "progressBarStyle": "brass_gauge",
                  "progressTrackDark": "#22150A",
                  "progressTrackLight": "#E5D3B3",
                  "progressFillStartDark": "#B45309",
                  "progressFillEndDark": "#FBBF24",
                  "progressFillStartLight": "#92400E",
                  "progressFillEndLight": "#D97706",
                  "progressSegments": 16,
                  "buttonBevelStyle": "riveted_brass",
                  "buttonSurfaceDark": "#382414",
                  "buttonSurfaceLight": "#E6CFA8",
                  "buttonHighlightDark": "#D4AF37",
                  "buttonShadowDark": "#1A1007",
                  "buttonHighlightLight": "#92400E",
                  "buttonShadowLight": "#B38B4D"
                """.trimIndent(),
                layoutJson = """
                  "toolbarPosition": "top",
                  "sidebarPosition": "left",
                  "showStatusBar": true,
                  "showSearchBar": true,
                  "showMenuBar": true,
                  "chromeMode": "custom"
                """.trimIndent(),
                wallpaperOpacity = 0.22f,
                paintWallpaper = ::paintSteampunkBg
            ),
            ShowcaseSkinSpec(
                id = "modern-aurora",
                name = "Modern Aurora Frost",
                description = "Ultra-clean contemporary frosted glass workspace with centered frameless titlebar chrome, flowing northern-lights backdrop ribbons, and luminous sky-indigo progress.",
                componentArc = 14,
                buttonArc = 12,
                rowHeight = 56,
                sidebarWidth = 206,
                accentDark = "#38BDF8",
                accentLight = "#0284C7",
                iconColor = Color(0x38, 0xBD, 0xF8),
                darkPalette = mapOf(
                    "windowBackground" to "#0D1119",
                    "panelBackground" to "#131926",
                    "sidebarBackground" to "#0A0E16",
                    "sidebarBorder" to "#1E293B",
                    "sidebarForeground" to "#CBD5E1",
                    "sidebarSelectedForeground" to "#38BDF8",
                    "sidebarSelectionBackground" to "#172A44",
                    "sidebarHoverBackground" to "#141D2E",
                    "listBackground" to "#0F1520",
                    "listTitleForeground" to "#F1F5F9",
                    "listSelectionBackground" to "#172A44",
                    "listHoverBackground" to "#141D2E"
                ),
                lightPalette = mapOf(
                    "windowBackground" to "#F3F7FB",
                    "panelBackground" to "#FFFFFF",
                    "sidebarBackground" to "#EAF0F8",
                    "sidebarBorder" to "#D1DCE8",
                    "sidebarForeground" to "#334155",
                    "sidebarSelectedForeground" to "#0284C7",
                    "sidebarSelectionBackground" to "#DCEEFB",
                    "sidebarHoverBackground" to "#E2ECF5",
                    "listBackground" to "#FFFFFF",
                    "listTitleForeground" to "#0F172A",
                    "listSelectionBackground" to "#DCEEFB",
                    "listHoverBackground" to "#F1F5F9"
                ),
                windowJson = """
                  "customTitleBar": true,
                  "hideNativeTitleBar": true,
                  "titleBarHeight": 34,
                  "titleText": "XDM Enhanced — Aurora",
                  "titleAlignment": "center",
                  "showAppIcon": true,
                  "showWindowShadeButton": true,
                  "windowShadeHeight": 64,
                  "showStatsTicker": false,
                  "controlButtonsStyle": "minimal",
                  "titleBarGradientStartDark": "#0F172A",
                  "titleBarGradientEndDark": "#11223A",
                  "titleBarGradientStartLight": "#F0F6FC",
                  "titleBarGradientEndLight": "#E2EDF8",
                  "titleBarForegroundDark": "#E2E8F0",
                  "titleBarForegroundLight": "#0F172A",
                  "frameBorderWidth": 1,
                  "frameCornerArc": 14,
                  "frameBorderDark": "#263449",
                  "frameBorderLight": "#CBD5E1",
                  "frameHighlightDark": "#38BDF8",
                  "frameHighlightLight": "#FFFFFF",
                  "frameShadowDark": "#090D14",
                  "frameShadowLight": "#94A3B8"
                """.trimIndent(),
                widgetsJson = """
                  "progressBarStyle": "gradient_glow",
                  "progressTrackDark": "#1B2434",
                  "progressTrackLight": "#E2E8F0",
                  "progressFillStartDark": "#38BDF8",
                  "progressFillEndDark": "#818CF8",
                  "progressFillStartLight": "#0284C7",
                  "progressFillEndLight": "#6366F1",
                  "progressSegments": 16,
                  "buttonBevelStyle": "flat"
                """.trimIndent(),
                layoutJson = """
                  "toolbarPosition": "top",
                  "sidebarPosition": "left",
                  "showStatusBar": true,
                  "showSearchBar": true,
                  "showMenuBar": false,
                  "chromeMode": "custom"
                """.trimIndent(),
                wallpaperOpacity = 0.24f,
                paintWallpaper = ::paintAuroraBg
            ),
            ShowcaseSkinSpec(
                id = "synthwave-outrun",
                name = "Synthwave Outrun 1986",
                description = "80s neon sunset theme with horizon laser grid backdrop, ultraviolet chrome gradient header, CRT scanline texture, neon button outlines, and sunset progress glow.",
                componentArc = 8,
                buttonArc = 8,
                rowHeight = 52,
                sidebarWidth = 204,
                accentDark = "#FF2A85",
                accentLight = "#DB2777",
                iconColor = Color(0xFF, 0x2A, 0x85),
                darkPalette = mapOf(
                    "windowBackground" to "#120826",
                    "panelBackground" to "#1A0C36",
                    "sidebarBackground" to "#0E061E",
                    "sidebarBorder" to "#581C87",
                    "sidebarForeground" to "#F5D0FE",
                    "sidebarSelectedForeground" to "#00E5FF",
                    "sidebarSelectionBackground" to "#3B125E",
                    "sidebarHoverBackground" to "#260E42",
                    "listBackground" to "#100721",
                    "listTitleForeground" to "#FDF4FF",
                    "listSelectionBackground" to "#3B125E",
                    "listHoverBackground" to "#210C3A"
                ),
                lightPalette = mapOf(
                    "windowBackground" to "#FDF2F8",
                    "panelBackground" to "#FFFFFF",
                    "sidebarBackground" to "#F5E8FF",
                    "sidebarBorder" to "#E9D5FF",
                    "sidebarForeground" to "#3B0764",
                    "sidebarSelectedForeground" to "#C026D3",
                    "sidebarSelectionBackground" to "#F3E8FF",
                    "sidebarHoverBackground" to "#FCE7F3",
                    "listBackground" to "#FFFFFF",
                    "listTitleForeground" to "#3B0764",
                    "listSelectionBackground" to "#FCE7F3",
                    "listHoverBackground" to "#FAF5FF"
                ),
                windowJson = """
                  "customTitleBar": true,
                  "hideNativeTitleBar": true,
                  "titleBarHeight": 34,
                  "titleText": "XDM // OUTRUN 1986",
                  "titleAlignment": "left",
                  "showAppIcon": true,
                  "showWindowShadeButton": true,
                  "windowShadeHeight": 64,
                  "showStatsTicker": true,
                  "controlButtonsStyle": "mac",
                  "titleBarGradientStartDark": "#3B0764",
                  "titleBarGradientEndDark": "#9D174D",
                  "titleBarGradientStartLight": "#FBCFE8",
                  "titleBarGradientEndLight": "#E9D5FF",
                  "titleBarForegroundDark": "#FDF4FF",
                  "titleBarForegroundLight": "#3B0764",
                  "frameBorderWidth": 3,
                  "frameCornerArc": 8,
                  "frameBorderDark": "#A21CAF",
                  "frameBorderLight": "#EC4899",
                  "frameHighlightDark": "#00E5FF",
                  "frameHighlightLight": "#FFFFFF",
                  "frameShadowDark": "#1E0536",
                  "frameShadowLight": "#86198F"
                """.trimIndent(),
                widgetsJson = """
                  "progressBarStyle": "gradient_glow",
                  "progressTrackDark": "#1D0F38",
                  "progressTrackLight": "#F5D0FE",
                  "progressFillStartDark": "#FF2A85",
                  "progressFillEndDark": "#00E5FF",
                  "progressFillStartLight": "#DB2777",
                  "progressFillEndLight": "#0284C7",
                  "progressSegments": 18,
                  "buttonBevelStyle": "neon_outline",
                  "buttonSurfaceDark": "#240E42",
                  "buttonSurfaceLight": "#FDF2F8",
                  "buttonHighlightDark": "#FF2A85",
                  "buttonShadowDark": "#120624",
                  "buttonHighlightLight": "#C026D3",
                  "buttonShadowLight": "#F5D0FE",
                  "showScanlines": true,
                  "scanlineOpacity": 0.06
                """.trimIndent(),
                layoutJson = """
                  "toolbarPosition": "top",
                  "sidebarPosition": "left",
                  "showStatusBar": true,
                  "showSearchBar": true,
                  "showMenuBar": false,
                  "chromeMode": "custom"
                """.trimIndent(),
                wallpaperOpacity = 0.25f,
                paintWallpaper = ::paintSynthwaveBg
            )
        )

        for (spec in specs) {
            val dir = File(sampleSkinsDir, spec.id)
            dir.mkdirs()
            val bgDir = File(dir, "backgrounds").apply { mkdirs() }
            val tbDir = File(dir, "icons/toolbar").apply { mkdirs() }
            val catDir = File(dir, "icons/categories").apply { mkdirs() }

            val bgDarkRel = "backgrounds/${spec.id}-dark.png"
            val bgLightRel = "backgrounds/${spec.id}-light.png"
            renderWallpaper(File(dir, bgDarkRel), isDark = true, spec.paintWallpaper)
            renderWallpaper(File(dir, bgLightRel), isDark = false, spec.paintWallpaper)

            if (spec.id == "retro-hifi") {
                paintTitleBarGripTexture(File(bgDir, "titlebar-dark.png"), isDark = true)
                paintTitleBarGripTexture(File(bgDir, "titlebar-light.png"), isDark = false)
            }

            paintShowcaseIcons(tbDir, catDir, spec.iconColor)

            fun paletteJson(p: Map<String, String>) = p.entries.joinToString(",\n          ") {
                "\"${it.key}\": \"${it.value}\""
            }

            val manifest = """
            {
              "schemaVersion": 3,
              "id": "${spec.id}",
              "name": "${spec.name}",
              "version": "1.2.0",
              "author": "XDM Enhanced Team",
              "description": "${spec.description}",
              "minAppVersion": "1.2.0",
              "license": "GPL-3.0-or-later",
              "homepage": "https://github.com/jakepanlilio2000/XDM-Enhanced",
              "geometry": {
                "componentArc": ${spec.componentArc},
                "buttonArc": ${spec.buttonArc},
                "rowHeight": ${spec.rowHeight},
                "sidebarWidth": ${spec.sidebarWidth}
              },
              "accents": {
                "dark": "${spec.accentDark}",
                "light": "${spec.accentLight}"
              },
              "colors": {
                "dark": {
                  ${paletteJson(spec.darkPalette)}
                },
                "light": {
                  ${paletteJson(spec.lightPalette)}
                }
              },
              "wallpaper": {
                "dark": "$bgDarkRel",
                "light": "$bgLightRel",
                "mode": "fill",
                "opacity": ${spec.wallpaperOpacity}
              },
              "layout": {
                ${spec.layoutJson.prependIndent("    ").trim()}
              },
              "window": {
                ${spec.windowJson.prependIndent("    ").trim()}
              },
              "widgets": {
                ${spec.widgetsJson.prependIndent("    ").trim()}
              },
              "icons": {
                "categories": {
                  "documents": "icons/categories/documents.png",
                  "compressed": "icons/categories/compressed.png",
                  "music": "icons/categories/music.png",
                  "video": "icons/categories/videos.png",
                  "videos": "icons/categories/videos.png",
                  "programs": "icons/categories/programs.png",
                  "images": "icons/categories/images.png",
                  "torrents": "icons/categories/torrents.png",
                  "all": "icons/categories/all.png"
                },
                "toolbar": {
                  "ADD_URL": "icons/toolbar/add.png",
                  "RESUME": "icons/toolbar/resume.png",
                  "STOP": "icons/toolbar/pause.png",
                  "DELETE": "icons/toolbar/delete.png",
                  "SETTINGS": "icons/toolbar/settings.png"
                }
              }
            }
            """.trimIndent()
            File(dir, "skin.json").writeText(manifest)

            val archive = File(sampleSkinsDir, "${spec.id}.xdmskin")
            SkinPackager.exportSkinPackage(dir, archive)
            assertTrue(archive.exists() && archive.length() > 0, "Archive must exist for ${spec.id}")

            val res = SkinValidatorCli.validateTarget(archive)
            assertTrue(res.isValid, "${spec.id} must validate cleanly: ${res.errors}")
            assertEquals(3, res.manifest?.schemaVersion, "${spec.id} must declare schemaVersion 3")
        }
    }

    private fun paintShowcaseIcons(tbDir: File, catDir: File, color: Color) {
        generateColoredIcon(File(tbDir, "add.png"), color) { g ->
            g.stroke = BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.drawLine(12, 5, 12, 19)
            g.drawLine(5, 12, 19, 12)
        }
        generateColoredIcon(File(tbDir, "resume.png"), color) { g ->
            g.fillPolygon(intArrayOf(7, 18, 7), intArrayOf(5, 12, 19), 3)
        }
        generateColoredIcon(File(tbDir, "pause.png"), color) { g ->
            g.fillRoundRect(6, 5, 4, 14, 2, 2)
            g.fillRoundRect(14, 5, 4, 14, 2, 2)
        }
        generateColoredIcon(File(tbDir, "delete.png"), color) { g ->
            g.stroke = BasicStroke(2.0f)
            g.drawRect(6, 8, 12, 12)
            g.drawLine(4, 8, 20, 8)
            g.drawLine(9, 5, 15, 5)
        }
        generateColoredIcon(File(tbDir, "settings.png"), color) { g ->
            g.stroke = BasicStroke(2.0f)
            g.drawOval(6, 6, 12, 12)
            g.drawOval(10, 10, 4, 4)
        }
        val catMap = mapOf(
            "documents.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f); g.drawRect(5, 4, 14, 16); g.drawLine(8, 9, 15, 9); g.drawLine(8, 13, 15, 13)
            },
            "compressed.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f); g.drawRect(5, 5, 14, 14); g.fillRect(10, 8, 4, 4)
            },
            "music.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f); g.drawLine(9, 6, 18, 4); g.drawLine(9, 6, 9, 16); g.fillOval(5, 14, 6, 5)
            },
            "videos.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f); g.drawRect(4, 5, 16, 14); g.fillPolygon(intArrayOf(10, 16, 10), intArrayOf(9, 12, 15), 3)
            },
            "programs.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f); g.drawRect(4, 5, 16, 14); g.drawLine(4, 9, 20, 9)
            },
            "images.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f); g.drawRect(4, 5, 16, 14); g.fillOval(7, 8, 3, 3)
            },
            "torrents.png" to { g: Graphics2D ->
                g.stroke = BasicStroke(2f); g.drawOval(4, 4, 16, 16); g.drawLine(12, 7, 12, 16)
            },
            "all.png" to { g: Graphics2D ->
                g.fillRect(5, 5, 6, 6); g.fillRect(13, 5, 6, 6); g.fillRect(5, 13, 6, 6); g.fillRect(13, 13, 6, 6)
            }
        )
        catMap.forEach { (n, p) -> generateColoredIcon(File(catDir, n), color, p) }
    }

    private fun renderWallpaper(file: File, isDark: Boolean, paint: (Graphics2D, Int, Int, Boolean) -> Unit) {
        val w = 960
        val h = 640
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        paint(g, w, h, isDark)
        g.dispose()
        ImageIO.write(img, "png", file)
    }

    private fun paintTitleBarGripTexture(file: File, isDark: Boolean) {
        val w = 32
        val h = 16
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = if (isDark) Color(255, 255, 255, 24) else Color(0, 0, 0, 22)
        for (y in 2 until h step 4) {
            g.drawLine(0, y, w, y)
        }
        g.dispose()
        ImageIO.write(img, "png", file)
    }

    private fun paintRetroHiFiBg(g: Graphics2D, w: Int, h: Int, isDark: Boolean) {
        g.color = if (isDark) Color(0x16, 0x19, 0x22) else Color(0xE2, 0xE6, 0xF0)
        g.fillRect(0, 0, w, h)
        g.color = if (isDark) Color(0x00, 0xFF, 0x66, 38) else Color(0x05, 0x96, 0x69, 32)
        val heights = intArrayOf(65, 120, 180, 145, 220, 170, 95, 200, 240, 155, 110, 185, 130, 80, 160, 210)
        val colW = (w - 80) / heights.size
        for (i in heights.indices) {
            val x = 40 + i * colW
            val bh = heights[i]
            for (sy in 0 until bh step 10) {
                g.fillRect(x, h - 40 - sy, colW - 8, 6)
            }
        }
    }

    private fun paintSakuraBg(g: Graphics2D, w: Int, h: Int, isDark: Boolean) {
        val c1 = if (isDark) Color(0x28, 0x13, 0x3A) else Color(0xFF, 0xE4, 0xEE)
        val c2 = if (isDark) Color(0x52, 0x1B, 0x4E) else Color(0xF3, 0xE8, 0xFF)
        g.paint = java.awt.GradientPaint(0f, 0f, c1, w.toFloat(), h.toFloat(), c2)
        g.fillRect(0, 0, w, h)
        // Bokeh & sakura blossoms
        val petalCol = if (isDark) Color(0xFF, 0x8C, 0xC6, 90) else Color(0xEC, 0x48, 0x99, 65)
        val glowCol = if (isDark) Color(0xF4, 0x72, 0xB6, 40) else Color(0xFF, 0xFF, 0xFF, 110)
        g.color = glowCol
        g.fillOval(w - 280, -60, 340, 340)
        g.fillOval(120, h - 220, 260, 260)
        g.color = petalCol
        val centers = listOf(160 to 130, 340 to 250, 740 to 150, 820 to 360, 560 to 480, 240 to 490)
        for ((cx, cy) in centers) {
            for (k in 0 until 5) {
                val ang = k * Math.PI * 2.0 / 5.0
                val px = (cx + Math.cos(ang) * 18).toInt()
                val py = (cy + Math.sin(ang) * 18).toInt()
                g.fillOval(px - 10, py - 10, 20, 20)
            }
        }
    }

    private fun paintSteampunkBg(g: Graphics2D, w: Int, h: Int, isDark: Boolean) {
        g.color = if (isDark) Color(0x1E, 0x14, 0x0B) else Color(0xF2, 0xE6, 0xCE)
        g.fillRect(0, 0, w, h)
        g.color = if (isDark) Color(0xD4, 0xAF, 0x37, 45) else Color(0x8B, 0x5A, 0x2B, 42)
        fun drawGear(cx: Int, cy: Int, r: Int, teeth: Int) {
            g.stroke = BasicStroke(3f)
            g.drawOval(cx - r, cy - r, r * 2, r * 2)
            g.drawOval(cx - r / 3, cy - r / 3, (r * 2) / 3, (r * 2) / 3)
            for (t in 0 until teeth) {
                val ang = t * 2.0 * Math.PI / teeth
                val x1 = (cx + Math.cos(ang) * (r - 2)).toInt()
                val y1 = (cy + Math.sin(ang) * (r - 2)).toInt()
                val x2 = (cx + Math.cos(ang) * (r + 14)).toInt()
                val y2 = (cy + Math.sin(ang) * (r + 14)).toInt()
                g.drawLine(x1, y1, x2, y2)
            }
        }
        drawGear(780, 170, 95, 14)
        drawGear(650, 290, 62, 10)
        drawGear(180, 490, 80, 12)
    }

    private fun paintAuroraBg(g: Graphics2D, w: Int, h: Int, isDark: Boolean) {
        g.color = if (isDark) Color(0x0B, 0x10, 0x1A) else Color(0xF1, 0xF5, 0xF9)
        g.fillRect(0, 0, w, h)
        val c1 = if (isDark) Color(0x38, 0xBD, 0xF8, 65) else Color(0x02, 0x84, 0xC7, 45)
        val c2 = if (isDark) Color(0x81, 0x8C, 0xF8, 55) else Color(0x63, 0x66, 0xF1, 40)
        g.paint = java.awt.GradientPaint(0f, 0f, c1, w.toFloat(), h / 2f, c2)
        g.fillOval(-120, -80, (w * 0.9).toInt(), (h * 0.7).toInt())
        g.fillOval((w * 0.35).toInt(), (h * 0.2).toInt(), (w * 0.75).toInt(), (h * 0.65).toInt())
    }

    private fun paintSynthwaveBg(g: Graphics2D, w: Int, h: Int, isDark: Boolean) {
        g.color = if (isDark) Color(0x14, 0x09, 0x29) else Color(0xFC, 0xE7, 0xF3)
        g.fillRect(0, 0, w, h)
        val horizon = (h * 0.56).toInt()
        val sunR = 120
        val sx = w / 2
        val sy = horizon - 15
        g.paint = java.awt.GradientPaint(0f, (sy - sunR).toFloat(), Color(0xFF, 0xD1, 0x00, 140), 0f, horizon.toFloat(), Color(0xFF, 0x2A, 0x85, 160))
        g.fillOval(sx - sunR, sy - sunR, sunR * 2, sunR * 2)
        // Perspective neon floor grid
        g.color = if (isDark) Color(0x00, 0xE5, 0xFF, 70) else Color(0xC0, 0x26, 0xD3, 55)
        g.stroke = BasicStroke(1.5f)
        g.drawLine(0, horizon, w, horizon)
        for (i in -8..8) {
            g.drawLine(sx + i * 24, horizon, sx + i * 115, h)
        }
        var y = horizon + 12
        var step = 12
        while (y < h) {
            g.drawLine(0, y, w, y)
            y += step
            step = (step * 1.35).toInt().coerceAtLeast(step + 6)
        }
    }
}

