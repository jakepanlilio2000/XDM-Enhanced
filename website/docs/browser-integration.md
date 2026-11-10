# Browser Setup & Integration Guide

Integrating XDM Enhanced with your web browser allows one-click download capture, streaming video grabbing, and seamless cookie synchronization.

---

## 1. Extension Verification Checklist

Before configuring browser settings, verify that the local communication bridge is operational:

1. Launch the **XDM Enhanced** desktop application.
2. In your browser, open the XDM Enhanced extension popup from your toolbar.
3. The status indicator should show a green **Connected** badge.
4. If it displays "Disconnected", consult the [Troubleshooting Guide](/troubleshooting) for self-repair steps.

---

## 2. Floating Video Sniffer Configuration

The extension automatically detects HTML5 streaming video and audio elements on supported websites.

### Video Sniffer Settings

In the extension popup or options page, you can configure:
- **Show Download Video Button:** Toggle the floating grabber button above video players.
- **Button Position:** Top-Right (default), Top-Left, Bottom-Right, or Bottom-Left.
- **Minimum Video Size Threshold:** Ignore small preview clips or background advertisements (e.g., minimum 5 MB or 60 seconds).
- **Format Filter:** Automatically group detected streams into MP4, WebM, or HLS/DASH containers.

### Grabbing Streaming Videos

1. Navigate to any supported video streaming page.
2. When the video starts playing, a non-intrusive **Download Video** button appears anchored to the player.
3. Click the button to inspect available resolutions (4K, 1080p, 720p, 480p).
4. Select your preferred quality. The stream is sent to the desktop client for segmented assembly.

---

## 3. Link Capture & Exception Domains

### Automatic File Interception

By default, XDM Enhanced intercepts file types with known download extensions:
- **Compressed:** `.zip`, `.rar`, `.7z`, `.tar.gz`, `.iso`
- **Executables:** `.exe`, `.msi`, `.dmg`, `.pkg`, `.deb`, `.rpm`
- **Media:** `.mp4`, `.mkv`, `.mp3`, `.flac`, `.avi`
- **Documents:** `.pdf`, `.epub`, `.doc`, `.docx`

You can add or remove file extensions under **Settings > Monitoring** in the desktop application.

### Exception Domain Rules

If you want XDM Enhanced to ignore downloads from specific internal websites, cloud storage portals, or banking sites:
1. Open XDM Enhanced &rarr; **Settings** &rarr; **Monitoring**.
2. Under **Blocked Sites / Do Not Capture**, add domain patterns:
   ```
   *.internal-company.net
   bank-portal.example.com
   drive.google.com
   ```
3. Downloads from these domains will be handled by your browser's default download manager.

---

## 4. Authentication & Cookie Passing

When XDM Enhanced intercepts a download from a password-protected site:
- **Session Cookies:** The extension extracts active session cookies for the target host and securely transmits them over the local loopback socket (`127.0.0.1:8597`).
- **User-Agent:** The desktop client replicates your exact browser `User-Agent` string to prevent remote servers from resetting authenticated sessions.
- **Zero Third-Party Storage:** Cookies are held in transient memory only for the duration of the transfer and are never written to disk or sent to external servers.

