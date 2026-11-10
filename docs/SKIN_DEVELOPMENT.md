# XDM Enhanced — Custom Skin Development SDK & Specification

Welcome to the **XDM Enhanced Skin Development SDK**. This guide provides complete documentation for modders, UI designers, and theme developers to create, test, package, and distribute custom visual skins for **XDM Enhanced**.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Package Format (`.xdmskin`)](#2-package-format-xdmskin)
3. [Manifest Schema (`skin.json`)](#3-manifest-schema-skinjson)
4. [Customization Capabilities](#4-customization-capabilities)
   - [Geometry & Component Arcs](#geometry--component-arcs)
   - [Accents & Color Palettes](#accents--color-palettes)
   - [Window Wallpapers & Backgrounds](#window-wallpapers--backgrounds)
   - [Custom Typography & Fonts](#custom-typography--fonts)
   - [Layout Constraints & Positioning](#layout-constraints--positioning)
   - [Category & Toolbar Icons](#category--toolbar-icons)
   - [FlatLaf UI Defaults](#flatlaf-ui-defaults)
5. [Security & Validation Rules](#5-security--validation-rules)
6. [Validating Skins with the CLI Tool](#6-validating-skins-with-the-cli-tool)
7. [Installing and Exporting via the UI](#7-installing-and-exporting-via-the-ui)
8. [Step-by-Step Tutorial: Creating a Custom Skin](#8-step-by-step-tutorial-creating-a-custom-skin)
9. [Sample Skin: Cyber Neon](#9-sample-skin-cyber-neon)

---

## 1. Overview

XDM Enhanced supports a declarative, sandboxed skinning engine. Unlike legacy systems that allow arbitrary bytecode execution, XDM Enhanced skins are **purely declarative JSON archives**. They contain vector or raster graphics, fonts, layout preferences, and FlatLaf styling variables without running arbitrary code or compromising system security.

### Core Principles
- **No Executable Code:** Skins cannot execute Java bytecode, shell scripts, or native code.
- **Dynamic Hot-Reloading:** Skins switch instantly at runtime without restarting the application or dropping active transfers.
- **Safe Fallback:** If a custom skin is corrupt or unreadable, the application safely and automatically falls back to the default `XDM Enhanced` skin.
- **Cross-Platform:** `.xdmskin` packages work identically on Windows, Linux, and macOS.

---

## 2. Package Format (`.xdmskin`)

A custom skin package is a standard ZIP archive with the `.xdmskin` file extension.

### Internal Directory Layout

```text
my-skin.xdmskin
├── skin.json                         # Required: Root manifest definition
├── backgrounds/                      # Optional: Wallpaper & background images
│   ├── wallpaper-dark.png
│   └── wallpaper-light.png
├── icons/                            # Optional: Custom icon replacements
│   ├── categories/
│   │   ├── documents.png
│   │   ├── compressed.png
│   │   ├── music.png
│   │   ├── videos.png
│   │   ├── programs.png
│   │   ├── images.png
│   │   ├── torrents.png
│   │   └── all.png
│   └── toolbar/
│       ├── add.png
│       ├── resume.png
│       ├── pause.png
│       ├── delete.png
│       └── settings.png
├── fonts/                            # Optional: TrueType (.ttf) or OpenType (.otf) fonts
│   └── custom-font.ttf
└── styles/                           # Optional: Documentation, license, or readme
    └── README.md
```

---

## 3. Manifest Schema (`skin.json`)

The `skin.json` file resides at the root of the archive and declares all metadata, geometry, color schemes, and asset bindings.

### Full Example Manifest

```json
{
  "id": "cyber-neon",
  "name": "Cyber Neon",
  "version": "1.0.0",
  "author": "XDM Enhanced Team",
  "description": "High-contrast cyberpunk visual theme with glowing neon cyan and emerald accents, custom category icons, wallpapers, and dark grid aesthetics.",
  "minAppVersion": "1.0.0-beta.4",
  "license": "GPL-3.0-or-later",
  "homepage": "https://github.com/jakepanlilio2000/XDM-Enhanced",
  "geometry": {
    "componentArc": 12,
    "buttonArc": 10,
    "popupArc": 12,
    "rowHeight": 28,
    "sidebarWidth": 210,
    "toolbarIconSize": 20,
    "categoryIconSize": 18
  },
  "accents": {
    "dark": "#00F0FF",
    "light": "#0284C7",
    "selection": "#00F0FF"
  },
  "colors": {
    "dark": {
      "background": "#0B0E14",
      "sidebar": "#07090D",
      "card": "#131720",
      "text": "#E0F7FA",
      "mutedText": "#546E7A",
      "border": "#1E293B",
      "categoryColors": {
        "compressed": "#F59E0B",
        "documents": "#3B82F6",
        "music": "#EC4899",
        "videos": "#8B5CF6",
        "programs": "#10B981",
        "images": "#06B6D4",
        "torrents": "#6366F1"
      }
    },
    "light": {
      "background": "#F0F4F8",
      "sidebar": "#E2E8F0",
      "card": "#FFFFFF",
      "text": "#0F172A",
      "mutedText": "#64748B",
      "border": "#CBD5E1"
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
  "font": {
    "family": "Inter",
    "path": "fonts/Inter-Regular.ttf",
    "size": 13
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
  "uiDefaults": {
    "Component.arc": 12,
    "Button.arc": 10,
    "TextComponent.arc": 12,
    "ScrollBar.showButtons": true
  }
}
```

### Manifest Fields Reference

| Field | Type | Required | Description |
| :--- | :---: | :---: | :--- |
| `id` | String | **Yes** | Unique identifier matching `^[a-z0-9_-]{3,32}$`. Cannot be `enhanced` or `classic_xdm`. |
| `name` | String | **Yes** | User-friendly display name (1 to 64 characters). |
| `version` | String | **Yes** | Semantic version string (e.g., `1.0.0`). |
| `author` | String | No | Author or organization name. |
| `description` | String | No | Short overview of the skin theme and style. |
| `minAppVersion`| String | No | Minimum compatible XDM Enhanced version (e.g. `1.0.0-beta.4`). |
| `license` | String | No | SPDX license identifier (e.g., `GPL-3.0-or-later`, `MIT`). |
| `homepage` | String | No | URL to skin repository or website. |
| `attributionNotice` | String | No | Attribution or upstream copyright notice. |
| `geometry` | Object | No | UI dimensions, component radii, and component sizes. |
| `accents` | Object | No | Primary accent colors in hex format (`#RRGGBB`). |
| `colors` | Object | No | Palette overrides for dark and light themes. |
| `wallpaper` | Object | No | Background image configurations, scaling mode, and opacity. |
| `font` | Object | No | Custom bundled TrueType or OpenType font definition. |
| `layout` | Object | No | Main window structural layout preferences. |
| `icons` | Object | No | Relative paths to category and toolbar icon replacements. |
| `uiDefaults` | Object | No | Arbitrary FlatLaf UI property overrides (arcs, borders, colors). |

---

## 4. Customization Capabilities

### Geometry & Component Arcs
Control corner rounding and component sizing:
- `componentArc`: Base radius for text inputs, panels, cards (0 to 48 px).
- `buttonArc`: Corner radius for buttons and toggles (0 to 48 px).
- `popupArc`: Corner radius for menus, tooltips, and dialog popups (0 to 48 px).
- `rowHeight`: Table and card row heights (20 to 60 px).
- `sidebarWidth`: Width of the category navigation sidebar (120 to 400 px).
- `toolbarIconSize`: Toolbar icon dimensions (16 to 48 px).
- `categoryIconSize`: Sidebar category icon dimensions (14 to 36 px).

### Accents & Color Palettes
Define distinct color palettes for light and dark themes:
- `accents.dark`: Hex color used for buttons, progress bars, active selections in dark mode.
- `accents.light`: Hex color used in light mode.
- `colors.dark.background`: Window and root panel background color.
- `colors.dark.sidebar`: Sidebar background color.
- `colors.dark.card`: Raised surface and download item background.
- `colors.dark.text`: Primary foreground text color.
- `colors.dark.mutedText`: Secondary/disabled text color.
- `colors.dark.border`: Line and hairline separator color.
- `colors.dark.categoryColors`: Map of category IDs to distinctive badge colors.

### Window Wallpapers & Backgrounds
Skins can paint a subtle background wallpaper across the download view area:
- `mode`:
  - `fill`: Stretches image to cover the window area while preserving aspect ratio.
  - `fit`: Scales image to fit completely within view dimensions.
  - `tile`: Repeats pattern seamlessly across horizontal and vertical space.
  - `center`: Centers image at original dimensions without scaling.
- `opacity`: Alpha blending level from `0.0` (invisible) to `1.0` (fully opaque). Recommended: `0.05` to `0.20` for subtle texture without impeding text readability.

### Custom Typography & Fonts
Include TrueType (`.ttf`) or OpenType (`.otf`) fonts in the `fonts/` directory:
```json
"font": {
  "family": "Inter",
  "path": "fonts/Inter-Regular.ttf",
  "size": 13
}
```
The font is safely loaded in-memory via `Font.createFont` without requiring administrative installation on the host operating system.

### Layout Constraints & Positioning
Customize the placement of main window elements:
- `toolbarPosition`:
  - `"top"`: Standard placement above download list (default).
  - `"bottom"`: Positioned at the bottom above the status bar.
  - `"hidden"`: Hides the main toolbar. Menus and keyboard shortcuts remain fully operational.
- `sidebarPosition`:
  - `"left"`: Standard placement on the left (default).
  - `"right"`: Placed on the right side.
  - `"hidden"`: Sidebar hidden for ultra-compact minimalist views.
- `showStatusBar`: `true` or `false` to toggle disk and transfer rate status bar.
- `showSearchBar`: `true` or `false` to toggle quick search input on the toolbar.

### Category & Toolbar Icons
Map category IDs and action keys to custom PNG/JPG/WebP/GIF icons:

#### Category Icon Keys
- `documents`
- `compressed`
- `music`
- `videos`
- `programs`
- `images`
- `torrents`
- `all`

#### Toolbar Action Keys
- `ADD_URL`
- `ADD_MAGNET`
- `ADD_TORRENT`
- `RESUME`
- `STOP`
- `STOP_ALL`
- `DELETE`
- `DELETE_COMPLETED`
- `SCHEDULER`
- `SETTINGS`
- `SORT`

### FlatLaf UI Defaults
Pass direct styling keys into FlatLaf's UIManager:
```json
"uiDefaults": {
  "Button.arc": 10,
  "Component.arc": 12,
  "TextComponent.arc": 12,
  "ScrollBar.thumbArc": 6
}
```

---

## 5. Security & Validation Rules

XDM Enhanced enforces strict package validation during installation to protect users from malicious files, path traversal, or resource exhaustion:

1. **Zip Slip Protection:** Archives containing `..` or absolute path traversal sequences in entry names are immediately rejected.
2. **File Count Limits:** Maximum of **500 entries** per package.
3. **Decompression Bomb Protection:** Maximum uncompressed total size of **50 MB**; maximum single entry size of **15 MB**.
4. **Executable Code Prohibition:** Any package containing executable or script extensions is strictly blocked. Prohibited file types include:
   - `.exe`, `.dll`, `.so`, `.dylib`
   - `.jar`, `.class`, `.dex`
   - `.bat`, `.cmd`, `.ps1`, `.vbs`
   - `.sh`, `.bash`, `.zsh`
   - `.js`, `.py`, `.rb`, `.pl`
5. **Allowed File Formats:** Packages may only contain:
   - Configuration: `.json`
   - Images: `.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`
   - Fonts: `.ttf`, `.otf`
   - Documentation: `.txt`, `.md`
6. **Manifest ID Regex:** `^[a-z0-9_-]{3,32}$`. Built-in identifiers (`enhanced`, `classic_xdm`) cannot be overwritten.
7. **Version Compatibility:** Semantic comparison ensures the user's application version satisfies `minAppVersion`.

---

## 6. Validating Skins with the CLI Tool

XDM Enhanced includes a command-line validator for developers:

### Usage

```bash
java -cp xdm-app/target/xdm-app.jar xdm.app.skins.SkinValidatorCli <path-to-xdmskin-or-folder>
```

### Example Output on Success

```text
==================================================
VALIDATION PASSED: Cyber Neon (cyber-neon)
Version:     1.0.0
Author:      XDM Enhanced Team
Min App:     1.0.0-beta.4
License:     GPL-3.0-or-later
Arcs:        Component=12px, Button=10px
Row Height:  28px
Sidebar W:   210px
Toolbar Pos: top
Sidebar Pos: left
==================================================
```

### Exit Codes
- `0`: Valid package, passed all checks.
- `1`: Invalid package or missing required files. Detailed diagnostics are printed to standard error.

---

## 7. Installing and Exporting via the UI

### To Install a Skin:
1. Open XDM Enhanced.
2. Navigate to **Settings** (`Tools → Settings` or gear icon) and select **Appearance**.
3. Under the **Skins & Themes** section, click **Install Skin...**.
4. Select any `.xdmskin` package. The skin is validated, installed into the `skins/` directory, and immediately available for preview and selection.
5. Click **Apply Skin** to activate it across all open windows.

### To Export a Custom Skin:
1. In **Appearance Settings**, select the custom skin from the dropdown list.
2. Click **Export Skin...**.
3. Choose destination folder and file name (`<skin-name>.xdmskin`).

### To Delete a Custom Skin:
1. Select the custom skin from the dropdown.
2. Click **Delete Skin** and confirm. (Built-in skins cannot be deleted).

### Open Skins Directory:
- Click **Open Skins Folder** to view and manage unpacked skin directories directly in Windows Explorer, macOS Finder, or Linux file manager.

---

## 8. Step-by-Step Tutorial: Creating a Custom Skin

### Step 1: Create Skin Directory
Create a folder named `my-dark-theme`:
```bash
mkdir my-dark-theme
cd my-dark-theme
mkdir backgrounds icons icons/categories icons/toolbar
```

### Step 2: Create `skin.json`
Create `skin.json` with your custom color scheme and attributes:
```json
{
  "id": "my-dark-theme",
  "name": "My Dark Theme",
  "version": "1.0.0",
  "author": "Your Name",
  "description": "Minimalist sleek dark theme.",
  "minAppVersion": "1.0.0-beta.4",
  "license": "MIT",
  "accents": {
    "dark": "#8B5CF6",
    "light": "#7C3AED"
  },
  "geometry": {
    "componentArc": 14,
    "buttonArc": 10
  }
}
```

### Step 3: Add Custom Assets
Place your icons in `icons/` and optional wallpaper in `backgrounds/`.

### Step 4: Validate
Validate the unpacked directory:
```bash
java -cp xdm-app/target/xdm-app.jar xdm.app.skins.SkinValidatorCli ./my-dark-theme
```

### Step 5: Archive
Compress the contents into a ZIP file with the extension `.xdmskin`:
```bash
# Ensure skin.json is at the root of the archive
zip -r ../my-dark-theme.xdmskin ./*
```

---

## 9. Sample Skin: Cyber Neon

A complete working sample skin package is included in the project repository:
- Unpacked Source: [`sample-skins/cyber-neon/`](../sample-skins/cyber-neon/)
- Packaged Binary: [`sample-skins/cyber-neon.xdmskin`](../sample-skins/cyber-neon.xdmskin)

Feel free to inspect and use Cyber Neon as a starting template for your own skin designs!

