# Appearance & Skin Customization Guide

XDM Enhanced features an extensible skinning engine with full support for modern FlatLaf themes, custom background wallpapers, color accent swatches, and portable `.xdmskin` packages.

---

## 1. Built-in Skin Presets

XDM Enhanced includes two permanent built-in theme presets:

1. **XDM Enhanced (Default):**
   - Modern, high-DPI desktop interface powered by FlatLaf.
   - Dynamic **Light**, **Dark**, or **Follow System** theme switching.
   - Four vibrant accent swatches: **Emerald Green**, **Purple**, **Ocean Blue**, and **Amber Gold**.
2. **Original XDM (`classic_xdm`):**
   - Authentic 1:1 recreation of the upstream [`subhra74/xdm`](https://github.com/subhra74/xdm) interface (`AppToolBar`, `FilterListPanel`, classic transfer list, FlatMac Dark/Light palettes).

---

## 2. Customizing Theme & Background Wallpapers

You can set custom wallpaper artwork behind the main window and transfer lists:

1. Open XDM Enhanced &rarr; **Settings** (⚙️) &rarr; **Appearance**.
2. Under **Theme**, choose between **Light**, **Dark**, or **Original XDM**.
3. Under **Background Wallpaper**:
   - Click **Browse** and select any PNG, JPG, or WebP image.
   - **Display Mode:**
     - `COVER`: Scales image proportionally to fill the full window.
     - `CONTAIN`: Scales image proportionally to fit without cropping.
     - `TILE`: Repeats the image as a pattern across the canvas.
     - `CENTER`: Centers the image at original scale.
   - **Opacity Slider:** Adjust background transparency (recommended: 15% to 30% for optimal text legibility).
4. Click **Apply**. The new visual theme applies immediately via live hot-reloading.

---

## 3. Installing Custom Skin Packages (`.xdmskin`)

Community designers can package full themes (Schema v1–v3) into `.xdmskin` archives containing custom window chrome, WindowShade collapse, custom progress meters, beveled buttons, scanlines, icons, fonts, and wallpapers:

1. Download a sample `.xdmskin` package from the [`v1.2.0` release](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.2.0) or [`sample-skins/`](https://github.com/jakepanlilio2000/XDM-Enhanced/tree/master/sample-skins):
   - [`retro-hifi.xdmskin`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/retro-hifi.xdmskin) (Retro Hi-Fi Console)
   - [`anime-sakura.xdmskin`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/anime-sakura.xdmskin) (Anime Sakura Studio)
   - [`steampunk-brass.xdmskin`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/steampunk-brass.xdmskin) (Steampunk Brass Works)
   - [`modern-aurora.xdmskin`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/modern-aurora.xdmskin) (Modern Aurora Frost)
   - [`synthwave-outrun.xdmskin`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/synthwave-outrun.xdmskin) (Synthwave Outrun 1986)
   - [`cyber-neon.xdmskin`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/cyber-neon.xdmskin) (Cyber Neon)
2. In XDM Enhanced &rarr; **Settings > Appearance** &rarr; click **Install Skin**.
3. Select the `.xdmskin` file.
4. The skin is validated, installed into your local settings directory (`~/.xdm/skins/`), and applied instantly.

---

## 4. Modal Dialog Legibility Preservation

To guarantee dialog readability regardless of your chosen wallpaper:
- The `SkinStyleResolver` continuously calculates background luminance.
- If a chosen background has insufficient contrast against default text colors, an automatic contrast scrim is blended beneath text labels.
- Modal dialogs apply an opacity cap (maximum 30%) on background artwork.

---

## 5. Security & Safe Fallback

To protect users against corrupt archives:
- **Zip Slip Defense:** Archives containing malicious path traversals (`../`) are rejected immediately.
- **Executable Blocking:** Packages containing `.exe`, `.dll`, `.class`, or shell scripts are rejected.
- **Safe Fallback:** If a skin file is corrupted or missing required assets, XDM Enhanced automatically falls back to default FlatLaf to ensure the application remains fully operable.

---

## Related Articles

* [[Skin Developer Quick Start|Skin-Developer-Quick-Start]]: Authoring your own `.xdmskin` packages.
* [[Skin Schema & UI Slots|Skin-Schema-and-UI-Slots]]: Complete technical reference for `skin.json`.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Diagnosing missing wallpaper or visual glitches.

