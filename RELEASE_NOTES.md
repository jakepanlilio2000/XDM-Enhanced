# XDM Enhanced v1.2.0

Stable Release of **XDM Enhanced** — introducing **Skin Schema v3** custom window chrome and widget styling, **5 new sample `.xdmskin` theme packages**, and **1:1 Original XDM (`subhra74/xdm`)** UI parity while preserving **Modular Plugin SDK v2** (`xdm-plugin-api`, Specification 2.0.0) compatibility.

---

## Release Highlights

- **Skin Schema v3 Custom Window Chrome & Widget Engine (`chromeMode: "custom"`):**
  - Declarative custom titlebars with gradient fills, header textures, alignment options, live speed/transfer stats ticker (`LcdStatsTicker`), and toggleable compact **WindowShade** frame collapse.
  - Custom window control button styles (`mac`, `beveled`, `anime`, `steampunk`, `minimal`, plus custom PNG control overrides) and 3D beveled/riveted outer chassis borders (`SkinnedFrameBorder`).
  - Custom `SkinnedProgressBarUI` renderers (`segmented_led`, `brass_gauge`, `striped_candy`, `gradient_glow`), `SkinnedButtonStyler` presets (`beveled_3d`, `riveted_brass`, `neon_outline`, `pill_glow`) with contrast protection, and subtle CRT scanline overlays.
  - Enhanced `CategoryTreeRenderer` sidebar selection contrast across both dark and light custom themes.
- **Five New Verified Sample Skins in `sample-skins/` (6 Total):**
  - `retro-hifi.xdmskin` (**Retro Hi-Fi Console**): Brushed metallic bevel deck, textured titlebar, LCD ticker, `34px` shade toggle, segmented LED meters, `beveled_3d` buttons, and CRT scanlines.
  - `anime-sakura.xdmskin` (**Anime Sakura Studio**): Cherry blossom wallpapers in dark and light palettes, custom anime window controls, candy-striped progress bars, and `pill_glow` buttons.
  - `steampunk-brass.xdmskin` (**Steampunk Brass Works**): Mahogany walnut & `6px` riveted brass bezel, Victorian `steampunk` controls, `brass_gauge` progress meters, and `riveted_brass` buttons.
  - `modern-aurora.xdmskin` (**Modern Aurora Frost**): Centered frameless titlebar with `minimal` controls, `1px` frame border, northern-lights ribbons, and `gradient_glow` progress bars.
  - `synthwave-outrun.xdmskin` (**Synthwave Outrun 1986**): Horizon laser grid backdrop, `mac` controls, neon-outlined buttons, sunset glow bars, and subtle CRT scanlines.
  - `cyber-neon.xdmskin` (**Cyber Neon**): Refreshed Schema v2 reference skin covering all 13 UI region slots.
- **1:1 Original XDM (`classic_xdm` / `subhra74/xdm`) UI Parity:**
  - Selecting **Original XDM** switches seamlessly to upstream layout mode (`chromeMode: "upstream"`): `AppToolBar` (`+ New`, `Clear`, `Sort`, `Settings`, `Search`, menu), `FilterListPanel` (`LIBRARY`, `FILE TYPES`, `Stay in touch`), `UpstreamClassicListView`, and FlatMac Dark/Light palettes under the `"Xtreme Download Manager"` window title.
- **Modular Plugin SDK v2 Compatibility Preserved (`API 1.1.0` / Spec `2.0.0`):**
  - Full backward compatibility for all `v1.1.0` and legacy `v1` `.xdmplugin` extensions, quarantine trust prompts, isolated `PluginFilteringClassLoader`, and recovery flags (`--safe-mode`, `--no-plugins`).

---

## Verifying Release Assets

Every release publishes a `SHA256SUMS.txt` file containing cryptographic SHA-256 digests for every attached installer, archive, JAR, extension, plugin, and skin package.

```powershell
# Verify on Windows (PowerShell)
Get-FileHash -Algorithm SHA256 .\XDM-Enhanced-Setup-v1.2.0-win-x64.exe

# Verify on Linux / macOS
sha256sum -c SHA256SUMS.txt
```

---

## Acknowledgments & Upstream Attribution

- Original XDM project created by Subhra Das Gupta ([subhra74/xdm](https://github.com/subhra74/xdm)) and upstream contributors. Upstream documentation is preserved in [README.UPSTREAM.md](README.UPSTREAM.md).
- `libtorrent4j` wrapper created by Alden Torres and the FrostWire team.
- `libtorrent` (Rasterbar) BitTorrent library developed by Arvid Norberg.
- `FlatLaf` look-and-feel developed by FormDev Software GmbH.
- Nullsoft Scriptable Install System (NSIS) project team.
- BellSoft for the Liberica JDK 25 runtime.

---

## License

XDM Enhanced is free, open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**.
See [LICENSE](LICENSE) for the full license text.
