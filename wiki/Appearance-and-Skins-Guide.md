# Appearance & Skin Customization Guide

XDM Enhanced features an extensible skinning engine with full support for modern FlatLaf themes, custom background wallpapers, color accent swatches, and portable `.xdmskin` packages.

---

## 1. Built-in Skin Presets

XDM Enhanced includes two permanent built-in theme presets:

1. **XDM Enhanced (Default):**
   - Modern, high-DPI desktop interface powered by FlatLaf.
   - Dynamic **Light**, **Dark**, or **Follow System** theme switching.
   - Four vibrant accent swatches: **Emerald Green**, **Purple**, **Ocean Blue**, and **Amber Gold**.
2. **Original XDM:**
   - Authentic recreation of the classic Xtreme Download Manager interface.
   - Preserves retro toolbar iconography and nostalgic layout for legacy users.

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

Community designers can package full themes into `.xdmskin` archives containing custom icons, fonts, and wallpapers:

1. Download a `.xdmskin` package (e.g. [`cyber-neon.xdmskin`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/cyber-neon.xdmskin)).
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

