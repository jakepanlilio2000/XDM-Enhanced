# Skin Customization System & Specification

The **XDM Enhanced Skin Engine** provides declarative, full-application theming. Developers and community designers can package complete visual themes into portable `.xdmskin` archives.

---

## 1. Built-in Skin Presets

XDM Enhanced ships with two permanent built-in skins:

1. **XDM Enhanced (Default):** Modern FlatLaf theme supporting dynamic Light and Dark mode switching, adjustable border radiuses, and vibrant accent swatches (Emerald, Purple, Blue, Amber).
2. **Original XDM:** Authentic recreation of the classic Xtreme Download Manager UI, preserving original toolbar layouts and nostalgic retro styling.

---

## 2. Structure of an `.xdmskin` Package

A `.xdmskin` file is a standard ZIP archive containing a `skin.json` descriptor and associated image assets:

```
my-custom-theme.xdmskin/
├── skin.json                   # Theme manifest and style rules
├── backgrounds/
│   ├── main-window.png         # Main window background wallpaper
│   └── modal-backdrop.png      # Modal dialog backdrop image
└── icons/
    ├── toolbar/                # Add, pause, resume, settings icons
    └── categories/             # Video, audio, program, compressed SVGs
```

---

## 3. The `skin.json` Schema

Below is an annotated example of a complete `skin.json` file:

```json
{
  "id": "com.example.cyber-neon",
  "name": "Cyber Neon",
  "version": "1.0.0",
  "author": "XDM Designer",
  "description": "High-contrast dark cyan theme with geometric wallpapers.",
  "baseTheme": "DARK",
  "colors": {
    "accent": "#06b6d4",
    "background": "#0b1120",
    "sidebar": "#070b14",
    "cardBackground": "#111827",
    "textPrimary": "#f8fafc",
    "textSecondary": "#94a3b8",
    "border": "#1e293b"
  },
  "backgrounds": {
    "mainWindow": {
      "image": "backgrounds/main-window.png",
      "mode": "COVER",
      "opacity": 0.15
    },
    "modalDialog": {
      "image": "backgrounds/modal-backdrop.png",
      "mode": "CENTER",
      "opacity": 0.20
    }
  },
  "styling": {
    "buttonArc": 8,
    "cardArc": 12,
    "toolbarPadding": 6
  }
}
```

### Background Display Modes
- **`COVER`:** Scales image proportionally to fill the entire container.
- **`CONTAIN`:** Scales image proportionally to fit within the container.
- **`TILE`:** Repeats the image as a seamless pattern.
- **`CENTER`:** Centers the image without scaling.

---

## 4. Modal Legibility Preservation

To ensure dialog text remains legible regardless of user-selected background images:
- The `SkinStyleResolver` automatically calculates background luminance.
- If a chosen background has insufficient contrast against default text colors, an automatic contrast scrim is blended beneath text labels.
- Modal dialogs apply an opacity cap (default maximum 30%) on background artwork.

---

## 5. Security & Archive Hardening

To protect users against malicious theme packages:
1. **Zip Slip Defense:** Every entry path in a `.xdmskin` archive is sanitized. Files containing `../` or attempting path traversal outside the extraction sandbox are immediately rejected.
2. **Prohibited Executable Payloads:** Archives containing binary executables (`.exe`, `.dll`, `.so`, `.dylib`, `.class`, `.jar`, `.sh`, `.bat`) fail validation and are deleted before extraction.
3. **Maximum Unpack Limit:** Extraction size is strictly capped at 25 MB to prevent Zip Bomb denial-of-service attacks.

---

## 6. Live Hot-Reloading & Testing

To test your custom skin without restarting XDM Enhanced:
1. Open XDM Enhanced &rarr; **Settings** &rarr; **Appearance**.
2. Click **Install Skin** and select your `.xdmskin` archive.
3. The new skin applies immediately across all open windows, toolbars, and active dialogs.

---

## 7. Authoritative References & Samples

- **Authoritative Specification:** [`docs/SKIN_DEVELOPMENT.md` ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/blob/master/docs/SKIN_DEVELOPMENT.md)
- **Reference Skin Package in Repo:** [`sample-skins/cyber-neon.xdmskin` ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/tree/master/sample-skins)
- **Community Wiki Guide:** [Skin Developer Quick Start ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/wiki/Skin-Developer-Quick-Start)
- **Skin Schema & UI Slots:** [Wiki Schema Reference ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/wiki/Skin-Schema-and-UI-Slots)

