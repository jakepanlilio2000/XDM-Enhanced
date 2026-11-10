# About XDM Enhanced & Attribution

**XDM Enhanced** is an independently maintained, open-source download manager designed for maximum speed, reliable multi-protocol downloading, and deep modular customization.

---

## 1. Project Mission & Lineage

XDM Enhanced began as an independent fork of [Xtreme Download Manager (XDM)](https://github.com/subhra74/xdm), originally created and maintained by **Subhra Das Gupta (`subhra74`)**. 

While retaining the battle-tested multi-connection segmentation concepts of original XDM, XDM Enhanced was founded to:
1. **Modernize the Technology Stack:** Upgrade to Liberica JDK 25 and modern Maven modular architecture (`xdm-core`, `xdm-manager`, `xdm-app`, `xdm-plugin-api`).
2. **Add Native BitTorrent 2.0 Support:** Integrate high-performance `libtorrent4j` for BitTorrent v1/v2 transfers and background magnet metadata resolution.
3. **Decouple Third-Party Extensibility:** Introduce an independent Java Plugin Platform (SPI v2) with semantic version range matching.
4. **Deliver Full-App Custom Theming:** Introduce modern FlatLaf dynamic styling, declarative `.xdmskin` theme archives, custom window wallpapers, and modal legibility styling.
5. **Strengthen Privacy & Security:** Implement strict user-controlled clipboard filtering, zero-payload link preflight probes, and hardened subprocess execution.

---

## 2. Upstream Credits & Acknowledgments

We extend our sincere gratitude and credit to:
- **Subhra Das Gupta (`subhra74`):** Original creator and principal developer of Xtreme Download Manager. Upstream documentation and license notices are permanently preserved in [README.UPSTREAM.md](https://github.com/jakepanlilio2000/XDM-Enhanced/blob/master/README.UPSTREAM.md).
- **The FlatLaf Team (FormDev Software):** For the exceptional Swing look-and-feel library that makes modern desktop Java interfaces possible.
- **The Libtorrent Team (Arvid Norberg & Alden Torres):** For `libtorrent` (Rasterbar) and `libtorrent4j`.
- **The Open Source Community:** All translators, testers, and contributors who test and improve XDM Enhanced.

---

## 3. Open Source Licensing

XDM Enhanced is distributed under the terms of the **GNU General Public License v3.0 (GPL-3.0)** and derivative compatible open-source licenses:

- **Core Application & GUI:** Licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html).
- **Plugin API (`xdm-plugin-api`):** Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0) to facilitate permissive third-party plugin development.
- **Third-Party Libraries:**
  - `libtorrent` & `libtorrent4j`: BSD 3-Clause License.
  - `FlatLaf`: Apache License 2.0.
  - `VitePress`: MIT License.

You are free to use, modify, study, and distribute this software in accordance with the licenses.

---

## 4. Independent Fork Statement

**XDM Enhanced** is an independent community fork and is not officially affiliated with, endorsed by, or sponsored by Subhra Das Gupta or the original XDM project. All trademarks belong to their respective owners.

---

## 5. Project Navigation & External Resources

- **GitHub Source Repository:** [https://github.com/jakepanlilio2000/XDM-Enhanced](https://github.com/jakepanlilio2000/XDM-Enhanced)
- **Latest Pre-release:** [GitHub Releases (v1.0.0-beta.6)](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.0.0-beta.6)
- **Community Wiki:** [GitHub Wiki (Staged in `wiki/`)](https://github.com/jakepanlilio2000/XDM-Enhanced/wiki)
- **Issue & Regression Tracker:** [GitHub Issues](https://github.com/jakepanlilio2000/XDM-Enhanced/issues)
- **Original Upstream Repository:** [https://github.com/subhra74/xdm](https://github.com/subhra74/xdm)

