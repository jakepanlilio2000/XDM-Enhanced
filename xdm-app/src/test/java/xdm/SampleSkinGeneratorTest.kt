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
        val size = 24
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x00, 0xF0, 0xFF)
        paint(g)
        g.dispose()
        ImageIO.write(img, "png", file)
    }
}

