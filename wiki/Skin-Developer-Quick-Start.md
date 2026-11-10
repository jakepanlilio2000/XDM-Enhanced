# Skin Developer Quick Start

Learn how to create, package, and test your own custom theme package (`.xdmskin`) for **XDM Enhanced**.

---

## 1. Prerequisites

- XDM Enhanced desktop client installed ([[Installation Guide|Installation-Guide]]).
- Any standard ZIP compression tool or terminal (`zip`).
- A text editor (e.g. VS Code, Notepad) and image editing software.

---

## 2. Directory Layout of an `.xdmskin` Package

Create a local project folder named `my-custom-skin/` with the following structure:

```
my-custom-skin/
├── skin.json                   # Theme metadata & color tokens (Required)
├── backgrounds/                # Wallpaper images
│   ├── main-window.png         # Main window background
│   └── modal-backdrop.png      # Modal dialog backdrop
└── icons/                      # Custom iconography (Optional)
    ├── toolbar/
    │   ├── add.png
    │   └── settings.png
    └── categories/
        ├── videos.png
        └── compressed.png
```

---

## 3. Creating the `skin.json` Descriptor

Create `skin.json` in the root of your folder:

```json
{
  "id": "com.example.neon-cyan",
  "name": "Neon Cyan Theme",
  "version": "1.0.0",
  "author": "Community Designer",
  "description": "High-contrast dark theme with cyan accent swatches.",
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
      "opacity": 0.20
    },
    "modalDialog": {
      "image": "backgrounds/modal-backdrop.png",
      "mode": "CENTER",
      "opacity": 0.25
    }
  },
  "styling": {
    "buttonArc": 8,
    "cardArc": 12,
    "toolbarPadding": 6
  }
}
```

---

## 4. Packaging the Skin Archive

1. Select the contents of your `my-custom-skin/` directory (make sure `skin.json` is at the root of the archive, not nested inside an extra parent folder).
2. Compress into a standard `.zip` file.
3. Rename the file extension from `.zip` to `.xdmskin`:
   ```bash
   # Linux / macOS terminal:
   zip -r ../my-custom-skin.xdmskin .
   
   # Windows PowerShell:
   Compress-Archive -Path * -DestinationPath ..\my-custom-skin.zip
   Rename-Item ..\my-custom-skin.zip my-custom-skin.xdmskin
   ```

---

## 5. Testing with Live Hot-Reload

1. Open XDM Enhanced &rarr; **Settings** &rarr; **Appearance**.
2. Click **Install Skin** &rarr; select `my-custom-skin.xdmskin`.
3. The skin applies immediately without restarting the application!
4. Inspect contrast and text legibility across the main window and modal dialogs.

---

## Related Developer Articles

* [[Skin Schema & UI Slots|Skin-Schema-and-UI-Slots]]: Complete schema and display mode reference.
* [[Appearance & Skins Guide|Appearance-and-Skins-Guide]]: User guide for skin management.
* Reference sample skin: [`sample-skins/cyber-neon.xdmskin`](https://github.com/jakepanlilio2000/XDM-Enhanced/tree/master/sample-skins).

