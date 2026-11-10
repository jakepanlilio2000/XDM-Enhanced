# BitTorrent & Magnet Link Guide

XDM Enhanced features a native BitTorrent client engine powered by **`libtorrent4j`** (wrapping Rasterbar `libtorrent`), enabling BitTorrent v1 and v2 downloads alongside HTTP/HTTPS segmented transfers.

---

## 1. Adding Torrents & Magnet URIs

### Opening a `.torrent` File
- Drag and drop any `.torrent` file directly into the XDM Enhanced window.
- Or click the **Add Download (+)** button on the toolbar, select **Browse**, and open the `.torrent` file.

### Adding a Magnet Link
- Copy any `magnet:?xt=urn:btih:...` link to your clipboard.
- Click **Add Download (+)** and paste the magnet link.
- XDM Enhanced will initiate background metadata resolution via DHT to retrieve the torrent's file manifest before showing the download dialog.

---

## 2. Hierarchical File Selection & Priorities

When the torrent metadata is loaded, the **Torrent File Selector** dialog displays the complete file tree:

```
[✓] Ubuntu-24.04-Desktop/
    ├── [✓] ubuntu-24.04-desktop-amd64.iso  (4.2 GB)  [ Priority: Maximum ]
    ├── [ ] documentation.pdf               (12 MB)   [ Do Not Download ]
    └── [✓] release-notes.txt               (45 KB)   [ Priority: Normal ]
```

### Priority Levels
- **Do Not Download (0):** The file is completely excluded from piece requests.
- **Normal (1):** Standard swarm download priority.
- **High (4):** Prioritizes pieces belonging to this file over normal files.
- **Maximum (7):** Requests pieces for this file first (useful for sequential previewing).

---

## 3. Swarm Diagnostics & Peer Map

While a torrent download is active, select it and click **Details** to open the diagnostics panel:

- **Peers Tab:** Displays connected peer IP addresses, client software (e.g. qBittorrent, Transmission), connection flags, encryption status, and individual upload/download rates.
- **Trackers Tab:** Displays tracker URLs, announce status, update intervals, and seed/peer counts.
- **Piece Map:** Visual grid showing piece availability across the swarm and currently completed blocks on your disk.
- **Integrity Recheck:** Click **Force Recheck** to verify piece SHA-1 / SHA-256 hashes against existing on-disk files.

---

## 4. Seeding Rules & Bandwidth Controls

### Automatic Seeding Thresholds

Configure seeding rules under **Settings > BitTorrent**:
- **Share Ratio Limit:** Automatically stop seeding when the share ratio reaches a specific threshold (e.g., `1.0` or `2.0`).
- **Seeding Time Limit:** Stop seeding after a predetermined duration (e.g., 120 minutes).
- **Unlimited Seeding:** Continue seeding until manually paused.

### Torrent Speed Limits

- **Global Torrent Limits:** Set maximum upload and download bandwidth allocated across all active torrents.
- **Per-Torrent Limits:** Right-click an individual torrent to set dedicated rate limits without altering other downloads.

---

## 5. Privacy & Private Trackers

- **BEP 27 Compliance:** For private torrents flagged with the `private` attribute in their metadata, XDM Enhanced strictly disables Distributed Hash Table (DHT), Peer Exchange (PEX), and Local Service Discovery (LSD), ensuring tracking remains confined strictly to authorized private tracker announce URLs.
- **Port Forwarding:** Supports UPnP and NAT-PMP for automatic port mapping through compatible home routers.

