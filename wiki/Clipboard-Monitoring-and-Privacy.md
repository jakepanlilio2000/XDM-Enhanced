# Clipboard Monitoring & Privacy Protections

XDM Enhanced features an intelligent, privacy-first clipboard monitoring service designed to detect downloadable links without compromising user secrets or system security.

---

## 1. Enabling or Disabling Clipboard Monitoring

Clipboard monitoring is completely user-controlled:
1. Open XDM Enhanced &rarr; **Settings** (⚙️) &rarr; **Monitoring**.
2. Toggle the checkbox: **Monitor Clipboard for Downloadable URLs**.
3. You can also toggle clipboard monitoring at any time by right-clicking the system tray icon &rarr; **Clipboard Monitoring**.

---

## 2. Privacy & URL Filtering Rules

Unlike aggressive download tools that read all clipboard contents into logs, XDM Enhanced enforces strict privacy filters:

- **Single URL Limit:** Only individual, single-line URLs are parsed. Multi-line text blocks, copied code snippets, and bulk document text are immediately discarded.
- **Maximum Length Bound:** Any clipboard entry exceeding 2,048 characters is discarded immediately to prevent memory abuse or buffer parsing exploits.
- **Protocol Restriction:** Only standard `http://` and `https://` schemas are processed.
- **Secret & Password Discard:** If clipboard content contains common credential patterns (e.g. `password=`, `token=`, `bearer `, `ssh-rsa`, private keys), the string is discarded instantly without logging.

---

## 3. Non-Intrusive Metadata Preflight

When a valid URL is copied, XDM Enhanced initiates a non-intrusive metadata preflight:
1. Performs an HTTP `HEAD` probe to query headers:
   - `Content-Disposition` (to extract the actual file name)
   - `Content-Length` (to verify the total file size)
   - `Accept-Ranges: bytes` (to verify server resume capability)
2. If `HEAD` is rejected by the remote server, it falls back to a 1-byte probe (`Range: bytes=0-0`).
3. The New Download dialog is invoked with metadata pre-filled.

---

## 4. The Zero-Payload Guarantee

> **Privacy Guarantee:** Under no circumstances does the preflight probe download actual file payload chunks prior to your explicit confirmation. Absolutely zero disk writes or multi-segment streams occur until you click **Download Now** or approve auto-start.

### Instant Socket Cancellation
If you do not wish to download the copied file:
- Simply click **Cancel** or press `Escape` on the New Download dialog.
- The in-flight socket probe is aborted immediately, releasing all network sockets and memory buffers.

---

## Related Articles

* [[First Download Guide|First-Download-Guide]]: Basic transfer controls and queue options.
* [[Browser Extension Setup|Browser-Extension-Setup]]: Browser-based link interception.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Resolving preflight metadata errors.

