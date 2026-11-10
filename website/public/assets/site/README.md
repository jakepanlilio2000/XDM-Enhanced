# XDM Enhanced Website Media Assets

This directory (`website/public/assets/site/`) is the **canonical, isolated media repository** for the XDM Enhanced project website. It contains production brand vectors, modular background graphics, architectural illustrations, and drop-in UI screenshot placeholders.

All web asset routes are prefixed with the base path `/XDM-Enhanced/assets/site/`.

---

## Directory Organization

```
website/public/assets/site/
├── backgrounds/         # High-resolution SVG hero and section backdrops
├── branding/            # Official vector logo, icon, and wordmark assets
├── illustrations/       # Feature architecture diagrams (SVG)
├── screenshots/         # UI screenshot templates and captures (PNG)
├── media-manifest.json  # Comprehensive machine-readable asset registry
└── README.md            # Replacement, optimization, and redaction guide
```

---

## 1:1 In-Place Replacement Guide

Every screenshot template is designed for seamless **one-to-one drop-in replacement**. Replacing a template does **not** require modifying website markup, templates, or CSS routes.

### Screenshot Inventory & Specifications

| Target Filename | Resolution | Aspect Ratio | Intended View / State | Status |
|:---|:---:|:---:|:---|:---:|
| `screenshots/main-window-light.png` | 1600 × 900 | 16:9 | Main window, Light Theme, active transfers list | `template` |
| `screenshots/main-window-dark.png` | 1600 × 900 | 16:9 | Main window, Dark Theme, active transfers list | `template` |
| `screenshots/download-progress.png` | 1200 × 800 | 3:2 | Active download progress dialog with segmented bar | `template` |
| `screenshots/torrent-manager.png` | 1600 × 900 | 16:9 | BitTorrent manager with peer swarm and piece map | `template` |
| `screenshots/skin-manager.png` | 1400 × 900 | 14:9 | Settings > Appearance > Skin & Background selector | `template` |
| `screenshots/plugin-manager.png` | 1400 × 900 | 14:9 | Settings > Plugins > Installed plugins & provider status | `template` |
| `screenshots/browser-extension.png` | 600 × 800 | 3:4 | Browser action popup showing desktop bridge connected | `template` |
| `screenshots/browser-settings.png` | 1200 × 800 | 3:2 | Settings > Monitoring / Browser Native Messaging host | `template` |
| `screenshots/nsis-installer.png` | 900 × 650 | ~1.38:1 | Windows NSIS modern setup wizard components screen | `template` |

### Step-by-Step Replacement Workflow

1. **Capture the Screenshot**:
   - Run XDM Enhanced at 100% or 125% DPI scaling (standard display density).
   - Capture clean, unscaled window frames using native OS screenshot tools (e.g. Snipping Tool, Flameshot) with window shadows enabled or neutral background padding.
2. **Review Privacy & Redaction Guidelines** (see below) before saving.
3. **Overwrite the Existing File**:
   - Save directly to the exact target filename under `website/public/assets/site/screenshots/`.
4. **Update `media-manifest.json`**:
   - Change `"status": "template"` to `"status": "verified"`.
   - Update `"fileSize"` with the new byte count.
   - Increment `stats.verified` and decrement `stats.template`.

---

## Privacy, Security & Redaction Guidelines

Screenshots published to the public website must never leak sensitive user or development environment data. Ensure the following items are sanitized before committing:

- [ ] **Local User Paths**: Obscure paths containing usernames (e.g., `C:\Users\JohnDoe\Downloads` or `/home/alex/Downloads`). Use generic directories like `C:\Downloads` or `/home/user/Downloads`.
- [ ] **Transfer URLs & Domains**: Use standard open-source distribution mirrors or sample test files (e.g., `https://releases.ubuntu.com/24.04/ubuntu-24.04-desktop-amd64.iso`, `https://cdn.kernel.org/...`). Never display internal company URLs, confidential intranet links, or restricted storage buckets.
- [ ] **Credentials & Headers**: Verify that Basic Auth headers, bearer tokens, API keys, session cookies, and proxy credentials in download properties or logs are obscured or absent.
- [ ] **BitTorrent Swarm Data**: Do not capture private tracker torrents, passkeys, internal IP addresses, or copyrighted payloads. Use public distributions (e.g., Arch Linux, LibreOffice).
- [ ] **Browser Bookmarks & Tabs**: When capturing browser integration popups, ensure personal bookmarks bar, open tab titles, and history entries are not visible.

---

## Asset Optimization & Compression

To ensure fast page load speeds and low bandwidth consumption on GitHub Pages, optimize all images before committing:

### PNG Screenshots
- **Color Space**: sRGB without proprietary ICC profiles.
- **Optimization Tool**: Run [`oxipng`](https://github.com/shssoichiro/oxipng) or [`pngquant`](https://pngquant.org/):
  ```bash
  # Lossless maximum compression
  oxipng -o 4 --strip all website/public/assets/site/screenshots/*.png
  ```

### SVG Branding & Illustrations
- **Optimization Tool**: Run [`svgo`](https://github.com/svg/svgo):
  ```bash
  svgo --multipass website/public/assets/site/illustrations/*.svg
  svgo --multipass website/public/assets/site/backgrounds/*.svg
  ```
- **SVGs must preserve `viewBox`**: Never strip `viewBox` attributes during optimization.

### WebP Alternatives (Optional)
If providing modern dual-format fallbacks in HTML `<picture>` elements:
```bash
cwebp -q 85 -alpha_filter best screenshot.png -o screenshot.webp
```

---

## Branding & Licensing

- `branding/xdm-logo.svg`: Verified official vector icon derived from XDM Enhanced master assets (`packaging/icons/xdm-logo.svg`). Licensed under GNU GPL v3.0 / Apache License 2.0.
- All SVG illustrations and background patterns are original assets authored specifically for XDM Enhanced documentation and web distribution.

