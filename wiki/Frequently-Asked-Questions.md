# Frequently Asked Questions (FAQ)

Answers to common questions regarding **XDM Enhanced** features, licensing, security, and usage.

---

## 1. General Questions

### Is XDM Enhanced completely free and open-source?
Yes. XDM Enhanced is 100% free software licensed under the **GNU General Public License v3.0 (GPL-3.0)** and Apache License 2.0. There are zero advertisements, zero paywalls, zero bundled adware, and zero third-party telemetry trackers.

### What is the relationship between XDM Enhanced and original XDM?
XDM Enhanced is an independent community fork of [Xtreme Download Manager (XDM)](https://github.com/subhra74/xdm), created by **Subhra Das Gupta (`subhra74`)**. While retaining the segmented streaming concepts of the original project, XDM Enhanced was founded to upgrade the codebase to Liberica JDK 25, add native BitTorrent 2.0 via `libtorrent4j`, introduce custom FlatLaf skins and modal styling, and build a decoupled public Java plugin platform (SPI v2).

---

## 2. Download & Media Questions

### Why can't XDM Enhanced download videos from Netflix, Prime Video, or Spotify?
Commercial streaming providers protect their media with hardware-enforced Digital Rights Management (DRM) technologies such as Google Widevine, Apple FairPlay, and Microsoft PlayReady. Standard open-source download accelerators cannot bypass or decrypt DRM-protected streams.

### Can I download YouTube videos?
For authorized YouTube content, install the official `ytdlp-downloader.xdmplugin` add-on ([[Plugin Installation Guide|Plugin-Installation-Guide]]) which bridges the popular open-source `yt-dlp` tool directly into XDM Enhanced's central download scheduler.

### How do I refresh an expired temporary download link?
Right-click the expired download item &rarr; select **Refresh Link**. A helper window opens allowing you to visit the download page and generate a fresh URL. XDM Enhanced captures the new token and resumes downloading from the exact byte where it paused.

---

## 3. System & Privacy Questions

### Does XDM Enhanced collect analytics or telemetry data?
**No.** XDM Enhanced collects zero telemetry, zero analytics, and zero user metrics. The desktop client communicates exclusively with the download URLs you initiate, the BitTorrent swarm peers you connect with, and local loopback (`127.0.0.1:8597`) for browser extension handshakes.

### Can I run multiple instances of XDM Enhanced?
No. XDM Enhanced enforces single-instance execution via a local file lock. If you attempt to launch a second instance, command-line arguments (such as new download URLs) are forwarded to the already-running desktop instance, and the duplicate process exits cleanly.

### Where are my settings and download history stored?
- **Installed Mode:** `C:\Users\<YourUser>\.xdm\` (Windows) or `~/.xdm/` (Linux / macOS).
- **Portable Mode:** Stored locally inside the portable application directory under `data/`.

---

## 4. Uninstallation

### How do I completely remove XDM Enhanced?
1. **Windows:** Open Windows Settings &rarr; Apps &rarr; Installed Apps &rarr; select **XDM Enhanced** &rarr; click **Uninstall**.
2. **Browser Extensions:** Right-click the XDM Enhanced icon in your browser toolbar &rarr; select **Remove from Chrome** / **Remove Extension**.
3. **User Data Cleanup (Optional):** Delete `C:\Users\<YourUser>\.xdm\` to remove remaining logs and download history databases.

---

## Related Articles

* [[Installation Guide|Installation-Guide]]: Supported platforms and setup options.
* [[Browser Extension Setup|Browser-Extension-Setup]]: Configuring Chrome and Firefox integration.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Resolving unexpected behavior.

