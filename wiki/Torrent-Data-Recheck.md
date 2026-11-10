# Torrent Existing Data Recheck Guide

This walkthrough explains how to verify cryptographic piece integrity and resume existing partial or complete torrent files in **XDM Enhanced** without re-downloading previously completed data.

---

## 1. When to Use Data Recheck

- **Migrating from Another Torrent Client:** Moving an active transfer from qBittorrent, Transmission, or uTorrent to XDM Enhanced.
- **Recovering from Unexpected Shutdowns:** Verifying block integrity after an ungraceful system crash or disk disconnect.
- **Resuming After Relocating Files:** Reconnecting an existing download after moving it to a new hard drive or folder.

---

## 2. Prerequisites

- The original `.torrent` file or `magnet:?` link.
- The existing partial or completed files on your local drive.

---

## 3. Step-by-Step Recheck Procedure

### Step 1: Add the Torrent
1. Open XDM Enhanced.
2. Drag and drop the `.torrent` file into the window, or paste the magnet link.

### Step 2: Set the Exact Destination Path
In the **Torrent File Selector** dialog:
- Set the **Save In** destination path to the exact directory where your existing files are stored.
- Ensure the filenames match the files declared in the torrent manifest.

### Step 3: Pause and Trigger Force Recheck
1. Start the download; it will immediately inspect the destination path.
2. If it does not start hashing automatically, click **Pause** on the transfer.
3. Right-click the torrent item in the list &rarr; select **Force Recheck**.

### Step 4: Monitor Hashing Progress
- The status changes to **Checking (X%)**.
- The `libtorrent4j` engine reads the local files block by block, calculating SHA-1 / SHA-256 piece checksums and matching them against the torrent's root hash.
- As valid pieces are confirmed, the progress percentage updates.

### Step 5: Resume the Transfer
- Once rechecking reaches 100% (or reflects your exact partial percentage, e.g. 75.4%), click **Resume**.
- Only missing, corrupted, or unfinished blocks will be requested from the swarm. Completed blocks are preserved.

---

## 4. Common Failure States & Recovery

| Failure Symptom | Cause | Solution |
|:---|:---|:---|
| **Recheck shows 0% despite files being present** | Mismatched root folder name | Torrents often nest files inside a subfolder. Ensure the target folder matches the torrent manifest structure. |
| **Recheck stalls at a specific percentage** | Read permissions error or locked file | Ensure the file is not currently open in another application (e.g. video player or text editor). |
| **Completed file fails hash recheck repeatedly** | Failing storage sector or disk corruption | Run `chkdsk` (Windows) or `fsck` (Linux) to verify physical drive health. |

---

## Related Articles

* [[BitTorrent & Magnet Guide|BitTorrent-and-Magnet-Guide]]: Swarm diagnostics and priority settings.
* [[Queues & Categories|Queues-and-Categories]]: Sorting torrents into dedicated folders.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Recovering from disk write errors.

