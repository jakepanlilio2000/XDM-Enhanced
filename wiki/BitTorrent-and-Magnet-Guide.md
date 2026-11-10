# BitTorrent & Magnet Link Guide

XDM Enhanced features a high-performance BitTorrent engine powered by **`libtorrent4j`** (wrapping native Rasterbar `libtorrent`), enabling seamless BitTorrent v1 and v2 downloads alongside standard HTTP/HTTPS transfers.

---

## 1. Adding Torrents & Magnets

### Adding via `.torrent` File
- Drag and drop any `.torrent` file into the main XDM Enhanced window.
- Or click the **Add Download (+)** button on the toolbar &rarr; click **Browse** &rarr; select the `.torrent` file.

### Adding via Magnet URI
- Copy any `magnet:?xt=urn:btih:...` link to your clipboard.
- Click **Add Download (+)** and paste the magnet link.
- XDM Enhanced will initiate background metadata resolution via DHT to retrieve the torrent file manifest before prompting you for file selection.

---

## 2. Hierarchical File Selection & Priorities

When metadata is loaded, the **Torrent File Selector** dialog displays the complete file tree:

```
[✓] ArchLinux-2026/
    ├── [✓] archlinux-2026.11.01-x86_64.iso  (1.2 GB)  [ Priority: Maximum ]
    ├── [ ] archlinux-bootstrap.tar.gz       (250 MB)  [ Do Not Download ]
    └── [✓] sha256sums.txt                   (4 KB)    [ Priority: Normal ]
```

### File Priority Controls
- **Do Not Download (0):** Completely excludes the file from piece requests, saving disk space and bandwidth.
- **Normal (1):** Standard swarm download priority.
- **High (4):** Prioritizes pieces belonging to this file over normal files.
- **Maximum (7):** Requests pieces for this file first (useful for sequential previewing).

---

## 3. Swarm Diagnostics & Piece Map

While a torrent download is active, select it and click **Details** to open the diagnostics panel:

- **Peers Tab:** Displays connected peer IP addresses, client software (qBittorrent, Transmission, libtorrent), encryption status, and individual transfer rates.
- **Trackers Tab:** Displays tracker announce URLs, status messages, update countdowns, and seed/peer counts.
- **Piece Map:** Visual grid showing available pieces across the swarm and currently completed blocks on your disk.
- **Integrity Recheck:** Click **Force Recheck** to recalculate piece SHA-1 / SHA-256 hashes against existing on-disk files.

---

## 4. Seeding Rules & Bandwidth Limits

Configure automatic seeding rules under **Settings** &rarr; **BitTorrent**:
- **Share Ratio Limit:** Automatically stop seeding when the share ratio reaches a specific threshold (e.g., `1.0` or `2.0`).
- **Seeding Time Limit:** Stop seeding after a set elapsed duration (e.g., 120 minutes).
- **Global Torrent Limits:** Set maximum upload and download bandwidth dedicated to BitTorrent traffic.

---

## 5. Privacy & Private Trackers

- **BEP 27 Compliance:** For private torrents flagged with the `private` attribute in their metadata, XDM Enhanced strictly disables Distributed Hash Table (DHT), Peer Exchange (PEX), and Local Service Discovery (LSD), ensuring tracking remains confined strictly to authorized private tracker announce URLs.
- **Port Forwarding:** Supports UPnP and NAT-PMP for automatic port mapping through compatible home routers.

---

## Related Articles

* [[Torrent Data Recheck|Torrent-Data-Recheck]]: Resuming or verifying existing external torrent files.
* [[Queues & Categories|Queues-and-Categories]]: Organizing torrents into dedicated folders.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Diagnosing DHT metadata resolution stalls.

