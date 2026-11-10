# HTTP & HTTPS Download Acceleration

XDM Enhanced uses a dynamic multi-connection segmentation engine designed to maximize throughput over HTTP and HTTPS protocols.

---

## 1. Dynamic Multi-Segment Acceleration

When downloading from a standard web server, single-threaded downloads are often artificially throttled by network latency, TCP window scaling limitations, or per-socket server rate limits.

```
Standard Single-Stream Transfer:
[ Server ] ──────────── Socket 1 (Throttled) ────────────► [ Disk ]

XDM Enhanced Dynamic Segmentation:
[ Server ] ──┬───────── Socket 1 (Chunk 01) ──────────┬──► [ Disk ]
             ├───────── Socket 2 (Chunk 02) ──────────┤    (Direct Block
             ├───────── Socket 3 (Chunk 03) ──────────┤     Assembly)
             └───────── Socket 4 (Chunk 04) ──────────┘
```

### How Dynamic Splitting Works:
1. XDM Enhanced queries the server's byte-range capability using `Range: bytes=0-0`.
2. If supported, the engine splits the total payload into multiple segments (configurable from 1 to 32 streams).
3. As faster segments complete their assigned blocks, the engine dynamically divides remaining unfinished segments into sub-chunks, ensuring all network sockets remain 100% active until the final byte is received.

### Direct Disk Block Assembly:
Unlike legacy download managers that download chunks into separate temporary files and perform a slow secondary copy pass at 100%, XDM Enhanced pre-allocates the target file directly on storage and writes incoming byte blocks to their exact offsets, eliminating disk thrashing and saving write wear on solid-state drives (SSDs).

---

## 2. Resumable Transfers & Network Resiliency

- **Automatic Reconnection:** If an internet disconnection, Wi-Fi drop, or router reset occurs, XDM Enhanced will continuously retry with exponential backoff.
- **Byte-Accurate Resume:** Once connectivity is restored, transfers resume from the exact byte offset where the stream was interrupted.

---

## 3. Link Refresh for Expired URLs

Many cloud storage services, file lockers, and university portals generate temporary download links that expire after 30 to 60 minutes. If a multi-gigabyte transfer pauses or drops after the token expires, the server returns `403 Forbidden` or `410 Gone`.

### Step-by-Step Link Refresh Walkthrough:
1. When a transfer fails due to an expired token, right-click the download in the list &rarr; select **Refresh Link**.
2. A helper window opens. Click **Open in Browser** to visit the original download page.
3. Click the download link again in your browser. The XDM browser extension intercepts the new authenticated URL and passes the fresh token to the desktop client.
4. XDM Enhanced updates the internal request headers and immediately resumes downloading without re-fetching completed chunks.

---

## 4. Connection Tuning & Network Settings

Configure global connection parameters under **Settings** &rarr; **Connection**:

- **Max Connections per Download:** Recommended default is 8 to 16. Setting to 32 can saturate gigabit lines but may be restricted by strict firewall rules.
- **Connection Timeout:** Time to wait before dropping unresponsive sockets (default: 30 seconds).
- **Max Retries:** Number of automatic retry attempts before marking a download as stalled (default: 99).
- **Network Proxy:** Supports HTTP, HTTPS, and SOCKS5 proxies with optional authentication.

---

## Related Articles

* [[First Download Guide|First-Download-Guide]]: Starting your first accelerated transfer.
* [[Queues & Categories|Queues-and-Categories]]: Organizing transfers into sequential batches.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Recovering from stuck transfers.

