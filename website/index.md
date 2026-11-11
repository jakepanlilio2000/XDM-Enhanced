---
layout: home

hero:
  name: "XDM Enhanced"
  text: "High-Speed, Modular Download Acceleration"
  tagline: "An independent open-source download manager for desktop. Dynamic segment acceleration, BitTorrent 2.0 swarm engine, FlatLaf custom skins, and decoupled Java plugins."
  image:
    src: /assets/site/branding/xdm-logo.svg
    alt: XDM Enhanced Logo
  actions:
    - theme: brand
      text: Download v1.0.0-beta.6
      link: /downloads
    - theme: alt
      text: Documentation & Guides
      link: /docs/
    - theme: alt
      text: View Source on GitHub
      link: https://github.com/jakepanlilio2000/XDM-Enhanced

features:
  - icon: ⚡
    title: Dynamic Multi-Segment Engine
    details: Splits HTTP/HTTPS downloads into concurrent streams, pre-allocates file storage, and reassembles chunks without post-download secondary copy overhead.
  - icon: 🧲
    title: Native BitTorrent 2.0 & Magnets
    details: Powered by libtorrent4j with DHT/PEX peer discovery, background magnet metadata resolution, interactive file tree filtering, and piece rechecking.
  - icon: 🎨
    title: Full-App Custom Skinning
    details: Choose between modern FlatLaf and authentic Original XDM presets. Create portable .xdmskin packages with custom wallpapers, modal backdrops, and live reload.
  - icon: 🧩
    title: Decoupled Plugin SPI v2
    details: Extensible public Java API with semantic version range matching [1.0.0, 2.0.0), publish-subscribe event bus, and central download task bridge.
  - icon: 🌐
    title: Companion Browser Extensions
    details: Non-intrusive Manifest V3 (Chromium) and V2 (Firefox) extensions with local loopback verification (127.0.0.1:8597) and floating HTML5 media sniffer.
  - icon: 🛡️
    title: Privacy & Zero Pre-Download Guarantee
    details: User-controlled clipboard monitoring strictly discards non-URLs, passwords, and tokens. Preflight only reads HTTP HEAD; never fetches payloads unconfirmed.
---

<div class="card-box" style="margin: 2rem 0; border-left: 4px solid var(--vp-c-brand-1);">
  <div style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 1rem;">
    <div>
      <span class="badge-pill badge-beta">Pre-release Channel</span>
      <strong style="margin-left: 0.5rem; font-size: 1.05rem;">XDM Enhanced v1.0.0-beta.6 is Available</strong>
      <p style="margin: 0.25rem 0 0 0; color: var(--vp-c-text-2); font-size: 0.9rem;">
        Featuring custom skin backgrounds, modal legibility styling, yt-dlp central task list integration, and browser connection self-repair.
      </p>
    </div>
    <div style="display: flex; gap: 0.75rem;">
      <a href="/downloads" class="btn-primary" style="font-size: 0.875rem; padding: 0.5rem 1rem;">Get Installer</a>
      <a href="/releases" class="btn-secondary" style="font-size: 0.875rem; padding: 0.5rem 1rem;">Changelog</a>
    </div>
  </div>
</div>

## Runtime Application Preview

Below is a verified capture of the active desktop user interface running in modern Dark Theme:

<div style="margin: 1.5rem 0; border: 1px solid var(--vp-c-divider); border-radius: 12px; overflow: hidden; background: #0f172a; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);">
  <img 
    src="/assets/site/screenshots/real/main-window-dark.png" 
    alt="XDM Enhanced Desktop Main Window running in Dark Theme showing categorized transfer list"
    style="width: 100%; height: auto; display: block;"
    loading="lazy"
  />
  <div style="padding: 0.85rem 1.25rem; background: var(--vp-c-bg-soft); display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 0.75rem; font-size: 0.85rem; color: var(--vp-c-text-2);">
    <span><span class="badge-pill badge-verified">Verified Runtime Capture</span> Desktop main window in Dark Theme with active transfers</span>
    <a href="/screenshots" style="color: var(--vp-c-brand-1); font-weight: 600;">Browse All Screenshots &rarr;</a>
  </div>
</div>

## Quick Installation & Platform Matrix

XDM Enhanced is built on a high-performance modular Java runtime bundled natively for Windows, and portable across all systems:

<div class="feature-grid">
  <div class="download-card primary-card">
    <div>
      <span class="badge-pill badge-verified">Official Windows Package</span>
      <h3 style="margin-top: 0.75rem;">Windows Setup (x64)</h3>
      <p>Modern NSIS installer with bundled Liberica JDK runtime, system tray integration, start menu shortcuts, and auto-registration.</p>
    </div>
    <div style="margin-top: 1.25rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/XDM-Enhanced-Setup-v1.0.0-beta.6-win-x64.exe" class="btn-primary" style="width: 100%; box-sizing: border-box;">Download Setup .EXE (64.8 MB)</a>
      <div style="margin-top: 0.5rem; text-align: center; font-size: 0.8rem; color: var(--vp-c-text-3);">
        Supports Windows 10 & 11 (64-bit)
      </div>
    </div>
  </div>

  <div class="download-card">
    <div>
      <span class="badge-pill badge-stable">Zero-Install Archive</span>
      <h3 style="margin-top: 0.75rem;">Windows Portable ZIP</h3>
      <p>Fully standalone portable archive. Unpack and run without administrative privileges or registry writes. All settings stay local.</p>
    </div>
    <div style="margin-top: 1.25rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/XDM-Enhanced-v1.0.0-beta.6-windows-x64.zip" class="btn-secondary" style="width: 100%; box-sizing: border-box;">Download Portable .ZIP (75.6 MB)</a>
      <div style="margin-top: 0.5rem; text-align: center; font-size: 0.8rem; color: var(--vp-c-text-3);">
        Zero installation footprint
      </div>
    </div>
  </div>

  <div class="download-card">
    <div>
      <span class="badge-pill badge-stable">Universal Binary</span>
      <h3 style="margin-top: 0.75rem;">Universal Application JAR</h3>
      <p>Platform-agnostic executable JAR for Linux, macOS, and BSD systems with an existing 64-bit Java 25 runtime.</p>
    </div>
    <div style="margin-top: 1.25rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/xdm-app-1.0.0-beta.6.jar" class="btn-secondary" style="width: 100%; box-sizing: border-box;">Download App .JAR (3.8 MB)</a>
      <div style="margin-top: 0.5rem; text-align: center; font-size: 0.8rem; color: var(--vp-c-text-3);">
        Requires 64-bit Java 25
      </div>
    </div>
  </div>
</div>

<div style="text-align: center; margin: 2rem 0;">
  <a href="/downloads" class="btn-secondary" style="padding: 0.75rem 1.5rem; font-size: 0.95rem; text-align: center;">
    View All Downloads, Extensions & SHA-256 Checksums &rarr;
  </a>
</div>
