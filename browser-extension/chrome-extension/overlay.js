/**
 * XDM Enhanced — Floating Video Download Overlay
 *
 * Attaches a sleek, unobtrusive floating download button to web video players.
 * Displays real captured streams, qualities, formats, and file sizes.
 * Pure Shadow DOM encapsulation ensures zero conflicts with page stylesheets.
 */
(function () {
    "use strict";

    if (window.__XDM_OVERLAY_INJECTED__) {
        return;
    }
    window.__XDM_OVERLAY_INJECTED__ = true;

    // Store-restricted domains policy (Chrome Web Store & Firefox AMO compliance)
    const BLOCKED_DOMAINS = ["youtube.com", "youtu.be", "youtube-nocookie.com", "youtubekids.com", "ytimg.com"];
    const currentHost = window.location.hostname.toLowerCase();
    if (BLOCKED_DOMAINS.some(d => currentHost === d || currentHost.endsWith("." + d))) {
        return;
    }

    const extRuntime = (typeof browser !== "undefined" && browser.runtime) ? browser.runtime : chrome.runtime;

    // SVG Icons
    const ICONS = {
        download: `<svg viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>`,
        close: `<svg viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>`,
        video: `<svg viewBox="0 0 24 24"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>`,
        check: `<svg viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"></polyline></svg>`
    };

    /**
     * Overlay instance attached to a single HTMLVideoElement.
     */
    class VideoPlayerOverlay {
        constructor(video, manager) {
            this.video = video;
            this.manager = manager;
            this.container = this.resolveContainer();
            this.originalParent = this.container;
            this.host = null;
            this.shadow = null;
            this.btn = null;
            this.dropdown = null;
            this.isOpen = false;
            this.inactivityTimer = null;
            this.resizeObserver = null;
            this.disposed = false;

            this.onMouseMove = this.onMouseMove.bind(this);
            this.onMouseLeave = this.onMouseLeave.bind(this);
            this.onVideoPlayState = this.onVideoPlayState.bind(this);
            this.onKeyDown = this.onKeyDown.bind(this);
            this.onDocClick = this.onDocClick.bind(this);

            this.init();
        }

        resolveContainer() {
            let parent = this.video.parentElement;
            if (!parent || parent === document.body || parent === document.documentElement) {
                return this.video.parentElement || document.body;
            }

            // Look up the tree for a known player wrapper
            let current = parent;
            let depth = 0;
            while (current && current !== document.body && depth < 4) {
                const cls = (current.className || "").toString().toLowerCase();
                if (cls.includes("player") || cls.includes("video-js") || cls.includes("jwplayer") || cls.includes("plyr") || cls.includes("vjs-")) {
                    return current;
                }
                if (current.hasAttribute("data-player") || current.hasAttribute("data-component-player")) {
                    return current;
                }
                current = current.parentElement;
                depth++;
            }
            return parent;
        }

        init() {
            // Ensure container positioning context
            try {
                const computed = window.getComputedStyle(this.container);
                if (computed.position === "static") {
                    this.container.style.position = "relative";
                }
            } catch (e) {}

            // Create Shadow DOM Host
            this.host = document.createElement("div");
            this.host.className = "xdm-overlay-host";
            this.host.setAttribute("data-xdm-overlay", "true");

            this.shadow = this.host.attachShadow({ mode: "open" });
            this.injectStyles();
            this.renderDOM();

            this.container.appendChild(this.host);

            // Event Listeners
            this.container.addEventListener("mousemove", this.onMouseMove, { passive: true });
            this.container.addEventListener("mouseleave", this.onMouseLeave, { passive: true });
            this.video.addEventListener("play", this.onVideoPlayState, { passive: true });
            this.video.addEventListener("pause", this.onVideoPlayState, { passive: true });
            this.video.addEventListener("ended", this.onVideoPlayState, { passive: true });
            document.addEventListener("click", this.onDocClick, true);

            // Responsive Observer
            if (window.ResizeObserver) {
                this.resizeObserver = new ResizeObserver(entries => {
                    for (const entry of entries) {
                        const width = entry.contentRect.width;
                        if (width < 280) {
                            this.host.setAttribute("data-compact", "true");
                        } else {
                            this.host.removeAttribute("data-compact");
                        }
                    }
                });
                this.resizeObserver.observe(this.container);
            }

            this.updateVideos();
            this.showButtonTemporarily();
        }

        injectStyles() {
            const style = document.createElement("style");
            style.textContent = `
:host {
    all: initial;
    position: absolute;
    top: 12px;
    right: 12px;
    z-index: 2147483647;
    pointer-events: none;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    font-size: 13px;
    line-height: 1.4;
    user-select: none;
    -webkit-user-select: none;
    display: block;
}

:host([data-hidden="true"]) {
    display: none !important;
}

:host([data-fullscreen="true"]) {
    position: fixed;
    top: 20px;
    right: 20px;
}

.xdm-overlay-wrapper {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
}

/* Floating Pill Button */
.xdm-btn {
    pointer-events: auto;
    display: inline-flex;
    align-items: center;
    gap: 7px;
    height: 32px;
    padding: 0 12px 0 9px;
    background: rgba(15, 23, 42, 0.88);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    color: #ffffff;
    border: 1px solid rgba(255, 255, 255, 0.22);
    border-radius: 16px;
    font-size: 12px;
    font-weight: 500;
    cursor: pointer;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
    opacity: 0;
    transform: translateY(-2px);
    transition: opacity 0.22s cubic-bezier(0.4, 0, 0.2, 1),
                transform 0.22s cubic-bezier(0.4, 0, 0.2, 1),
                background 0.15s ease,
                border-color 0.15s ease,
                box-shadow 0.15s ease;
}

.xdm-btn:hover {
    background: rgba(14, 165, 233, 0.95);
    border-color: #38bdf8;
    box-shadow: 0 6px 20px rgba(14, 165, 233, 0.45);
    transform: translateY(-3px);
}

.xdm-btn:active {
    transform: translateY(-1px);
    background: #0284c7;
}

.xdm-btn:focus-visible {
    outline: 2px solid #38bdf8;
    outline-offset: 2px;
}

.xdm-btn.xdm-visible,
.xdm-overlay-wrapper:hover .xdm-btn,
.xdm-btn:focus-visible,
.xdm-btn.xdm-active {
    opacity: 1;
    transform: translateY(0);
}

.xdm-btn-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    flex-shrink: 0;
}

.xdm-btn-icon svg {
    width: 16px;
    height: 16px;
    fill: none;
    stroke: currentColor;
    stroke-width: 2.2;
    stroke-linecap: round;
    stroke-linejoin: round;
}

.xdm-btn-label {
    white-space: nowrap;
    letter-spacing: 0.01em;
}

.xdm-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 16px;
    height: 16px;
    padding: 0 5px;
    background: #0ea5e9;
    color: #ffffff;
    border-radius: 8px;
    font-size: 10.5px;
    font-weight: 700;
    line-height: 1;
    margin-left: -2px;
}

.xdm-btn:hover .xdm-badge {
    background: #ffffff;
    color: #0284c7;
}

:host([data-compact="true"]) .xdm-btn-label {
    display: none;
}
:host([data-compact="true"]) .xdm-btn {
    padding: 0 8px;
}

/* Dropdown Menu */
.xdm-dropdown {
    pointer-events: auto;
    position: absolute;
    top: calc(100% + 8px);
    right: 0;
    width: 320px;
    max-width: calc(100vw - 32px);
    background: #0f172a;
    border: 1px solid rgba(255, 255, 255, 0.16);
    border-radius: 10px;
    box-shadow: 0 16px 36px rgba(0, 0, 0, 0.65), 0 0 0 1px rgba(0, 0, 0, 0.2);
    color: #f1f5f9;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    animation: xdmSlideDown 0.18s cubic-bezier(0.16, 1, 0.3, 1);
    transform-origin: top right;
}

@keyframes xdmSlideDown {
    from {
        opacity: 0;
        transform: scale(0.95) translateY(-6px);
    }
    to {
        opacity: 1;
        transform: scale(1) translateY(0);
    }
}

.xdm-dropdown-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 14px;
    background: rgba(30, 41, 59, 0.7);
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.xdm-dropdown-title-group {
    display: flex;
    flex-direction: column;
    overflow: hidden;
    padding-right: 8px;
}

.xdm-dropdown-title {
    font-size: 12.5px;
    font-weight: 600;
    color: #f8fafc;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.xdm-dropdown-subtitle {
    font-size: 11px;
    color: #94a3b8;
    margin-top: 1px;
}

.xdm-close-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    background: transparent;
    border: none;
    color: #94a3b8;
    border-radius: 4px;
    cursor: pointer;
    padding: 0;
    transition: color 0.15s, background 0.15s;
}

.xdm-close-btn:hover {
    color: #f8fafc;
    background: rgba(255, 255, 255, 0.12);
}

.xdm-close-btn svg {
    width: 14px;
    height: 14px;
    stroke: currentColor;
    stroke-width: 2;
}

/* Streams List */
.xdm-streams-list {
    max-height: 240px;
    overflow-y: auto;
    padding: 6px;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.xdm-streams-list::-webkit-scrollbar {
    width: 5px;
}
.xdm-streams-list::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.2);
    border-radius: 3px;
}

.xdm-stream-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 7px 10px;
    border-radius: 6px;
    background: rgba(30, 41, 59, 0.5);
    border: 1px solid rgba(255, 255, 255, 0.06);
    transition: background 0.15s, border-color 0.15s;
}

.xdm-stream-item:hover {
    background: rgba(51, 65, 85, 0.7);
    border-color: rgba(56, 189, 248, 0.35);
}

.xdm-stream-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
    overflow: hidden;
}

.xdm-stream-meta {
    display: flex;
    align-items: center;
    gap: 6px;
}

.xdm-format-tag {
    font-size: 10px;
    font-weight: 700;
    text-transform: uppercase;
    padding: 2px 5px;
    border-radius: 4px;
    background: rgba(14, 165, 233, 0.2);
    color: #38bdf8;
    border: 1px solid rgba(14, 165, 233, 0.4);
}

.xdm-format-tag[data-type="hls"],
.xdm-format-tag[data-type="m3u8"] {
    background: rgba(168, 85, 247, 0.2);
    color: #c084fc;
    border-color: rgba(168, 85, 247, 0.4);
}

.xdm-format-tag[data-type="dash"],
.xdm-format-tag[data-type="mpd"] {
    background: rgba(249, 115, 22, 0.2);
    color: #fb923c;
    border-color: rgba(249, 115, 22, 0.4);
}

.xdm-format-tag[data-type="audio"] {
    background: rgba(34, 197, 94, 0.2);
    color: #4ade80;
    border-color: rgba(34, 197, 94, 0.4);
}

.xdm-format-tag[data-type="subtitle"] {
    background: rgba(20, 184, 166, 0.2);
    color: #2dd4bf;
    border-color: rgba(20, 184, 166, 0.4);
}

.xdm-audio-badge {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    font-size: 9.5px;
    font-weight: 600;
    padding: 1px 4px;
    border-radius: 3px;
    line-height: 1.2;
}

.xdm-audio-badge.xdm-muxed {
    background: rgba(14, 165, 233, 0.15);
    color: #38bdf8;
    border: 1px solid rgba(14, 165, 233, 0.35);
}

.xdm-audio-badge.xdm-no-audio {
    background: rgba(245, 158, 11, 0.18);
    color: #fbbf24;
    border: 1px solid rgba(245, 158, 11, 0.4);
}

.xdm-dropdown-alert {
    margin: 8px 10px 0 10px;
    padding: 7px 10px;
    border-radius: 6px;
    font-size: 11px;
    line-height: 1.35;
    display: flex;
    align-items: flex-start;
    gap: 6px;
    animation: xdmSlideDown 0.15s ease-out;
}

.xdm-dropdown-alert.xdm-alert-error {
    background: rgba(239, 68, 68, 0.18);
    border: 1px solid rgba(239, 68, 68, 0.4);
    color: #fca5a5;
}

.xdm-dropdown-alert.xdm-alert-warning {
    background: rgba(245, 158, 11, 0.18);
    border: 1px solid rgba(245, 158, 11, 0.4);
    color: #fcd34d;
}

.xdm-stream-action.xdm-loading {
    opacity: 0.75;
    pointer-events: none;
}

.xdm-stream-action.xdm-error {
    background: #ef4444;
    color: #ffffff;
}

.xdm-stream-action.xdm-error:hover {
    background: #dc2626;
}

.xdm-quality-tag {
    font-size: 11.5px;
    font-weight: 600;
    color: #f1f5f9;
}

.xdm-stream-size {
    font-size: 11px;
    color: #94a3b8;
}

.xdm-stream-action {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 26px;
    padding: 0 9px;
    background: #0ea5e9;
    color: #ffffff;
    border: none;
    border-radius: 4px;
    font-size: 11.5px;
    font-weight: 500;
    cursor: pointer;
    flex-shrink: 0;
    transition: background 0.15s, transform 0.1s;
}

.xdm-stream-action:hover {
    background: #0284c7;
    transform: scale(1.02);
}

.xdm-stream-action:active {
    transform: scale(0.98);
}

.xdm-stream-action:focus-visible {
    outline: 2px solid #38bdf8;
}

.xdm-stream-action.xdm-sent {
    background: #10b981;
}

/* Empty / Waiting State */
.xdm-empty-state {
    padding: 20px 16px;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    color: #94a3b8;
}

.xdm-empty-icon {
    width: 28px;
    height: 28px;
    stroke: #64748b;
    stroke-width: 1.8;
}

.xdm-empty-title {
    font-size: 12.5px;
    font-weight: 600;
    color: #e2e8f0;
}

.xdm-empty-desc {
    font-size: 11.5px;
    line-height: 1.4;
    color: #94a3b8;
    margin: 0;
}

/* Footer / Actions */
.xdm-dropdown-footer {
    padding: 8px 12px;
    background: rgba(15, 23, 42, 0.9);
    border-top: 1px solid rgba(255, 255, 255, 0.08);
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 11px;
}

.xdm-footer-link {
    background: transparent;
    border: none;
    color: #94a3b8;
    cursor: pointer;
    padding: 4px 6px;
    border-radius: 4px;
    font-size: 11px;
    text-decoration: none;
    transition: color 0.15s, background 0.15s;
}

.xdm-footer-link:hover {
    color: #f8fafc;
    background: rgba(255, 255, 255, 0.08);
}

.xdm-footer-link:focus-visible {
    outline: 2px solid #38bdf8;
}

.xdm-dl-all-btn {
    background: rgba(14, 165, 233, 0.2);
    border: 1px solid rgba(14, 165, 233, 0.5);
    color: #38bdf8;
    padding: 4px 10px;
    border-radius: 4px;
    font-size: 11.5px;
    font-weight: 600;
    cursor: pointer;
    transition: background 0.15s, color 0.15s;
}

.xdm-dl-all-btn:hover {
    background: #0ea5e9;
    color: #ffffff;
}
            `;
            this.shadow.appendChild(style);
        }

        renderDOM() {
            const wrapper = document.createElement("div");
            wrapper.className = "xdm-overlay-wrapper";

            // Button
            this.btn = document.createElement("button");
            this.btn.className = "xdm-btn";
            this.btn.type = "button";
            this.btn.setAttribute("role", "button");
            this.btn.setAttribute("aria-label", "Download video with XDM Enhanced");
            this.btn.setAttribute("aria-haspopup", "true");
            this.btn.setAttribute("aria-expanded", "false");

            this.btn.innerHTML = `
                <span class="xdm-btn-icon">${ICONS.download}</span>
                <span class="xdm-btn-label">Download video</span>
                <span class="xdm-badge" style="display: none;">0</span>
            `;

            this.btn.addEventListener("click", e => {
                e.stopPropagation();
                this.toggleDropdown();
            });

            this.btn.addEventListener("keydown", e => {
                if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    this.toggleDropdown();
                } else if (e.key === "ArrowDown" && !this.isOpen) {
                    e.preventDefault();
                    this.openDropdown();
                    const firstAction = this.dropdown.querySelector(".xdm-stream-action, .xdm-close-btn");
                    if (firstAction) firstAction.focus();
                } else if (e.key === "Escape" && this.isOpen) {
                    e.preventDefault();
                    this.closeDropdown();
                }
            });

            wrapper.appendChild(this.btn);

            // Dropdown Menu Container (populated when opened)
            this.dropdown = document.createElement("div");
            this.dropdown.className = "xdm-dropdown";
            this.dropdown.style.display = "none";
            this.dropdown.setAttribute("role", "menu");
            this.dropdown.setAttribute("aria-label", "Available video qualities and formats");

            wrapper.appendChild(this.dropdown);
            this.shadow.appendChild(wrapper);
        }

        updateVideos() {
            const videos = this.manager.detectedVideos || [];
            const badge = this.shadow.querySelector(".xdm-badge");
            if (badge) {
                if (videos.length > 0) {
                    badge.textContent = videos.length + "";
                    badge.style.display = "inline-flex";
                } else {
                    badge.style.display = "none";
                }
            }

            if (this.isOpen) {
                this.renderDropdownContent();
            }
        }

        onMouseMove() {
            this.showButton();
            this.resetInactivityTimer();
        }

        onMouseLeave() {
            if (!this.isOpen && !this.video.paused) {
                this.scheduleHide();
            }
        }

        onVideoPlayState() {
            if (this.video.paused || this.video.ended) {
                this.showButton();
            } else if (!this.isOpen) {
                this.scheduleHide();
            }
        }

        showButton() {
            if (this.btn) {
                this.btn.classList.add("xdm-visible");
            }
        }

        hideButton() {
            if (this.btn && !this.isOpen) {
                this.btn.classList.remove("xdm-visible");
            }
        }

        showButtonTemporarily() {
            this.showButton();
            this.resetInactivityTimer();
        }

        resetInactivityTimer() {
            if (this.inactivityTimer) {
                clearTimeout(this.inactivityTimer);
            }
            this.inactivityTimer = setTimeout(() => {
                if (!this.isOpen && !this.video.paused && !this.video.ended) {
                    this.hideButton();
                }
            }, 3000);
        }

        scheduleHide() {
            if (this.inactivityTimer) clearTimeout(this.inactivityTimer);
            this.inactivityTimer = setTimeout(() => {
                if (!this.isOpen && !this.video.paused) {
                    this.hideButton();
                }
            }, 1500);
        }

        toggleDropdown() {
            if (this.isOpen) {
                this.closeDropdown();
            } else {
                this.openDropdown();
            }
        }

        openDropdown() {
            this.isOpen = true;
            this.btn.classList.add("xdm-active");
            this.btn.setAttribute("aria-expanded", "true");
            this.renderDropdownContent();
            this.dropdown.style.display = "flex";
            this.showButton();

            // Handle Esc key
            document.addEventListener("keydown", this.onKeyDown);
        }

        closeDropdown() {
            if (!this.isOpen) return;
            this.isOpen = false;
            this.btn.classList.remove("xdm-active");
            this.btn.setAttribute("aria-expanded", "false");
            this.dropdown.style.display = "none";
            document.removeEventListener("keydown", this.onKeyDown);
            this.btn.focus();
            if (!this.video.paused) {
                this.scheduleHide();
            }
        }

        onDocClick(e) {
            if (!this.isOpen) return;
            const path = e.composedPath ? e.composedPath() : [];
            if (!path.includes(this.host)) {
                this.closeDropdown();
            }
        }

        onKeyDown(e) {
            if (e.key === "Escape") {
                e.preventDefault();
                this.closeDropdown();
                return;
            }
            if (e.key === "ArrowDown" || e.key === "ArrowUp") {
                const focusables = Array.from(this.dropdown.querySelectorAll("button, [tabindex='0']"));
                if (focusables.length === 0) return;
                const active = this.shadow ? this.shadow.activeElement : null;
                const index = focusables.indexOf(active);
                e.preventDefault();
                if (e.key === "ArrowDown") {
                    const next = (index >= 0 && index < focusables.length - 1) ? focusables[index + 1] : focusables[0];
                    next.focus();
                } else {
                    const prev = (index > 0) ? focusables[index - 1] : focusables[focusables.length - 1];
                    prev.focus();
                }
            }
        }

        getVideoTitle() {
            // Check video element title or aria-label
            if (this.video.title && this.video.title.trim()) return this.video.title.trim();
            if (this.video.getAttribute("aria-label")) return this.video.getAttribute("aria-label").trim();

            // Check container title
            if (this.container && this.container.getAttribute("aria-label")) {
                return this.container.getAttribute("aria-label").trim();
            }

            // Fallback to detected video item title
            const videos = this.manager.detectedVideos || [];
            if (videos.length > 0 && videos[0].text) {
                return videos[0].text;
            }

            // Fallback to document title
            return document.title || "Web Video";
        }

        parseStreamMeta(info) {
            const raw = (info || "").trim();
            const formatMatch = /^\[([^\]]{1,15})\]\s*(.*)$/.exec(raw);
            let format = formatMatch ? formatMatch[1].trim() : "VIDEO";
            let rest = formatMatch ? formatMatch[2].trim() : raw;

            let quality = "Original";
            const qMatch = /\b(\d{3,4}p|\d{3,4}x\d{3,4}|4k|2k|hd)\b/i.exec(rest);
            if (qMatch) {
                quality = qMatch[1];
            }

            return {
                format: format.toUpperCase(),
                quality: quality,
                rest: rest
            };
        }

        parseStreamDetails(vid) {
            let streamType = (vid.streamType || "").toUpperCase();
            let format = (vid.format || "").toUpperCase();
            let resolution = vid.resolution || "";
            let estimatedSize = vid.estimatedSize || "";
            let hasAudio = vid.hasAudio !== false;
            let frameRate = vid.frameRate || "";

            const raw = (vid.info || "").trim();
            if (!streamType) {
                if (raw.includes("[HLS]") || raw.toLowerCase().includes("m3u8")) {
                    streamType = "HLS";
                    format = format || "HLS";
                } else if (raw.includes("[DASH]") || raw.toLowerCase().includes("mpd")) {
                    streamType = "DASH";
                    format = format || "DASH";
                } else if (vid.subtitle || raw.toLowerCase().includes("subtitle") || raw.toLowerCase().includes(".vtt") || raw.toLowerCase().includes(".srt")) {
                    streamType = "SUBTITLE";
                    format = format || "SUBTITLE";
                } else {
                    streamType = "HTTP";
                }
            }

            if (!format) {
                const formatMatch = /^\[([^\]]{1,15})\]\s*(.*)$/.exec(raw);
                if (formatMatch) {
                    format = formatMatch[1].trim().toUpperCase();
                } else {
                    format = streamType === "HLS" ? "HLS" : streamType === "DASH" ? "DASH" : "MP4";
                }
            }

            if (!resolution) {
                const resMatch = /\b(\d{3,4}p|\d{3,4}x\d{3,4}|4k|2k|hd)\b/i.exec(raw);
                if (resMatch) {
                    resolution = resMatch[1];
                    if (resolution.toLowerCase() === "4k") resolution = "2160p (4K)";
                } else {
                    resolution = streamType === "SUBTITLE" ? "Subtitles" : "Standard";
                }
            }

            if (!estimatedSize) {
                const sizeMatch = /(≈\s*[\d.]+\s*[KMGT]B|~?\s*[\d.]+\s*[KMGT]B)/i.exec(raw);
                if (sizeMatch) {
                    estimatedSize = sizeMatch[1];
                }
            }

            const isEstimate = streamType === "HLS" || streamType === "DASH" || estimatedSize.includes("≈") || estimatedSize.includes("~");
            let sizeDisplay = estimatedSize;
            if (isEstimate && sizeDisplay && !sizeDisplay.includes("est")) {
                if (!sizeDisplay.startsWith("≈")) sizeDisplay = "≈ " + sizeDisplay.replace(/^~/, "").trim();
                sizeDisplay += " (est.)";
            }

            return {
                streamType: streamType,
                format: format,
                resolution: resolution,
                estimatedSize: sizeDisplay || (streamType === "SUBTITLE" ? "Subtitle Track" : "Stream available"),
                hasAudio: hasAudio,
                frameRate: frameRate,
                isEstimate: isEstimate
            };
        }

        extractHeight(res) {
            if (!res) return 0;
            if (res.includes("2160") || res.toLowerCase().includes("4k")) return 2160;
            if (res.includes("1440") || res.toLowerCase().includes("2k")) return 1440;
            if (res.includes("1080")) return 1080;
            if (res.includes("720")) return 720;
            if (res.includes("480")) return 480;
            if (res.includes("360")) return 360;
            if (res.includes("240")) return 240;
            const match = /(\d{3,4})x(\d{3,4})/.exec(res);
            if (match) return parseInt(match[2], 10);
            const pMatch = /(\d{3,4})p/i.exec(res);
            if (pMatch) return parseInt(pMatch[1], 10);
            return 0;
        }

        getProcessedStreams(videos) {
            const seen = new Set();
            const deduplicated = [];

            for (const vid of videos) {
                const details = this.parseStreamDetails(vid);
                const key = `${details.streamType}|${details.format}|${details.resolution}|${details.hasAudio}|${details.estimatedSize}|${vid.text}`;
                if (!seen.has(key)) {
                    seen.add(key);
                    deduplicated.push({ vid, details });
                }
            }

            deduplicated.sort((a, b) => {
                if (a.vid.subtitle !== b.vid.subtitle) return a.vid.subtitle ? 1 : -1;
                const hA = this.extractHeight(a.details.resolution);
                const hB = this.extractHeight(b.details.resolution);
                if (hA !== hB) return hB - hA;
                if (a.details.hasAudio !== b.details.hasAudio) return a.details.hasAudio ? -1 : 1;
                return 0;
            });

            return deduplicated;
        }

        showDropdownAlert(message, type = "error") {
            const alertContainer = this.dropdown ? this.dropdown.querySelector(".xdm-alert-container") : null;
            if (alertContainer) {
                alertContainer.innerHTML = `
                    <div class="xdm-dropdown-alert xdm-alert-${escapeHtml(type)}">
                        <span>${message}</span>
                    </div>
                `;
            }
        }

        renderDropdownContent() {
            const title = this.getVideoTitle();
            const rawVideos = this.manager.detectedVideos || [];
            const processed = this.getProcessedStreams(rawVideos);

            // Check if player uses DRM (Encrypted Media Extensions)
            const isDrmProtected = Boolean(this.video.mediaKeys);

            let html = `
                <div class="xdm-dropdown-header">
                    <div class="xdm-dropdown-title-group">
                        <div class="xdm-dropdown-title" title="${escapeHtml(title)}">${escapeHtml(title)}</div>
                        <div class="xdm-dropdown-subtitle">XDM Enhanced Media Downloader</div>
                    </div>
                    <button class="xdm-close-btn" type="button" aria-label="Close dialog">
                        ${ICONS.close}
                    </button>
                </div>
                <div class="xdm-alert-container"></div>
            `;

            if (isDrmProtected) {
                html += `
                    <div class="xdm-empty-state">
                        <div class="xdm-empty-icon">${ICONS.video}</div>
                        <div class="xdm-empty-title">DRM Protected Stream</div>
                        <p class="xdm-empty-desc">
                            This media source is protected by Encrypted Media Extensions (DRM) and cannot be captured without authorization.
                        </p>
                    </div>
                `;
            } else if (processed.length > 0) {
                html += `<div class="xdm-streams-list" role="list">`;
                processed.forEach(({ vid, details }) => {
                    const typeAttr = details.streamType ? details.streamType.toLowerCase() : "video";
                    let audioBadge = "";
                    if (!details.hasAudio) {
                        audioBadge = `<span class="xdm-audio-badge xdm-no-audio" title="Video track only (no audio)">No Audio</span>`;
                    } else if (details.format.includes("MUXED")) {
                        audioBadge = `<span class="xdm-audio-badge xdm-muxed" title="Video and audio tracks merged">Audio Muxed</span>`;
                    }

                    const frameRateText = details.frameRate ? ` • ${details.frameRate}` : "";

                    html += `
                        <div class="xdm-stream-item" role="listitem">
                            <div class="xdm-stream-info">
                                <div class="xdm-stream-meta">
                                    <span class="xdm-format-tag" data-type="${escapeHtml(typeAttr)}">${escapeHtml(details.format)}</span>
                                    <span class="xdm-quality-tag">${escapeHtml(details.resolution)}${escapeHtml(frameRateText)}</span>
                                    ${audioBadge}
                                </div>
                                <div class="xdm-stream-size">${escapeHtml(details.estimatedSize)}</div>
                            </div>
                            <button class="xdm-stream-action" data-vid-id="${escapeHtml(vid.id)}" type="button" aria-label="Download ${escapeHtml(details.format)} ${escapeHtml(details.resolution)}">
                                <span>${ICONS.download}</span>
                                <span>Download</span>
                            </button>
                        </div>
                    `;
                });
                html += `</div>`;
            } else {
                const directSrc = this.video.currentSrc || this.video.src;
                if (directSrc && directSrc.startsWith("http")) {
                    const isWebm = directSrc.toLowerCase().includes(".webm");
                    const directFormat = isWebm ? "WEBM" : "MP4";
                    const w = this.video.videoWidth;
                    const h = this.video.videoHeight;
                    const resLabel = (w && h) ? `${h}p (${w}x${h})` : "Direct Stream";

                    html += `
                        <div class="xdm-streams-list" role="list">
                            <div class="xdm-stream-item" role="listitem">
                                <div class="xdm-stream-info">
                                    <div class="xdm-stream-meta">
                                        <span class="xdm-format-tag" data-type="video">${directFormat}</span>
                                        <span class="xdm-quality-tag">${escapeHtml(resLabel)}</span>
                                        <span class="xdm-audio-badge xdm-muxed">Direct HTML5</span>
                                    </div>
                                    <div class="xdm-stream-size">Embedded video source</div>
                                </div>
                                <button class="xdm-stream-action" data-direct-url="${escapeHtml(directSrc)}" type="button" aria-label="Download direct HTML5 stream">
                                    <span>${ICONS.download}</span>
                                    <span>Download</span>
                                </button>
                            </div>
                        </div>
                    `;
                } else {
                    html += `
                        <div class="xdm-empty-state">
                            <div class="xdm-empty-icon">${ICONS.video}</div>
                            <div class="xdm-empty-title">Waiting for Media Streams</div>
                            <p class="xdm-empty-desc">
                                Play the video for a moment so XDM can detect available streams, bitrates, and qualities.
                            </p>
                        </div>
                    `;
                }
            }

            // Footer
            html += `
                <div class="xdm-dropdown-footer">
                    <button class="xdm-footer-link" id="hideSiteBtn" type="button">Hide on this site</button>
                    ${processed.length > 1 ? `<button class="xdm-dl-all-btn" id="dlAllBtn" type="button">Download All (${processed.length})</button>` : ""}
                    <button class="xdm-footer-link" id="openSettingsBtn" type="button">Settings</button>
                </div>
            `;

            this.dropdown.innerHTML = html;

            // Wire up Close button
            const closeBtn = this.dropdown.querySelector(".xdm-close-btn");
            if (closeBtn) closeBtn.addEventListener("click", () => this.closeDropdown());

            // Wire up Stream Download buttons with error feedback
            this.dropdown.querySelectorAll(".xdm-stream-action").forEach(btn => {
                btn.addEventListener("click", e => {
                    const vidId = btn.getAttribute("data-vid-id");
                    const directUrl = btn.getAttribute("data-direct-url");

                    btn.disabled = true;
                    btn.classList.add("xdm-loading");
                    btn.innerHTML = `<span>${ICONS.download}</span><span>Sending...</span>`;

                    const onDownloadResult = response => {
                        btn.disabled = false;
                        btn.classList.remove("xdm-loading");

                        if (!response || response.notRunning) {
                            btn.classList.add("xdm-error");
                            btn.innerHTML = `<span>${ICONS.close}</span><span>Offline</span>`;
                            this.showDropdownAlert("XDM is not running. Please start XDM Enhanced to download.", "error");
                        } else if (!response.ok) {
                            btn.classList.add("xdm-error");
                            btn.innerHTML = `<span>${ICONS.close}</span><span>Failed</span>`;
                            this.showDropdownAlert(response.error || "Unable to start download. Stream may have expired.", "warning");
                        } else {
                            btn.classList.add("xdm-sent");
                            btn.innerHTML = `<span>${ICONS.check}</span><span>Sent!</span>`;
                            setTimeout(() => {
                                this.closeDropdown();
                            }, 600);
                        }
                    };

                    if (vidId) {
                        extRuntime.sendMessage({ type: "vid", itemId: vidId }, onDownloadResult);
                    } else if (directUrl) {
                        extRuntime.sendMessage({
                            type: "overlay-download-url",
                            url: directUrl,
                            filename: title
                        }, onDownloadResult);
                    }
                });
            });

            // Wire up Download All button
            const dlAllBtn = this.dropdown.querySelector("#dlAllBtn");
            if (dlAllBtn) {
                dlAllBtn.addEventListener("click", () => {
                    dlAllBtn.disabled = true;
                    dlAllBtn.textContent = "Sending...";
                    let sentCount = 0;
                    processed.forEach(({ vid }) => {
                        extRuntime.sendMessage({ type: "vid", itemId: vid.id }, () => {
                            sentCount++;
                            if (sentCount >= processed.length) {
                                dlAllBtn.textContent = "Sent all!";
                                setTimeout(() => this.closeDropdown(), 600);
                            }
                        });
                    });
                });
            }

            // Wire up Hide Site button
            const hideSiteBtn = this.dropdown.querySelector("#hideSiteBtn");
            if (hideSiteBtn) {
                hideSiteBtn.addEventListener("click", () => {
                    this.manager.hideOverlayOnCurrentSite();
                });
            }

            // Wire up Settings button
            const openSettingsBtn = this.dropdown.querySelector("#openSettingsBtn");
            if (openSettingsBtn) {
                openSettingsBtn.addEventListener("click", () => {
                    extRuntime.sendMessage({ type: "overlay-open-options" });
                    this.closeDropdown();
                });
            }
        }

        handleFullscreenChange(fullscreenEl) {
            if (!this.host) return;

            if (fullscreenEl && (fullscreenEl === this.container || fullscreenEl.contains(this.video))) {
                // Enter fullscreen: move host inside the fullscreen element so it stays on top
                if (this.host.parentElement !== fullscreenEl) {
                    fullscreenEl.appendChild(this.host);
                }
                this.host.setAttribute("data-fullscreen", "true");
                this.showButtonTemporarily();
            } else {
                // Exit fullscreen: restore host to original container
                if (this.host.parentElement !== this.originalParent) {
                    this.originalParent.appendChild(this.host);
                }
                this.host.removeAttribute("data-fullscreen");
            }
        }

        destroy() {
            if (this.disposed) return;
            this.disposed = true;

            if (this.inactivityTimer) clearTimeout(this.inactivityTimer);
            if (this.resizeObserver) this.resizeObserver.disconnect();

            this.container.removeEventListener("mousemove", this.onMouseMove);
            this.container.removeEventListener("mouseleave", this.onMouseLeave);
            this.video.removeEventListener("play", this.onVideoPlayState);
            this.video.removeEventListener("pause", this.onVideoPlayState);
            this.video.removeEventListener("ended", this.onVideoPlayState);
            document.removeEventListener("click", this.onDocClick, true);
            document.removeEventListener("keydown", this.onKeyDown);

            if (this.host && this.host.parentElement) {
                this.host.parentElement.removeChild(this.host);
            }
            this.host = null;
        }
    }

    /**
     * Top-level manager tracking video elements and coordinating with the extension service worker.
     */
    class OverlayManager {
        constructor() {
            this.overlays = new Map();
            this.detectedVideos = [];
            this.enabled = true;
            this.mutationObserver = null;
            this.disposed = false;
        }

        init(config) {
            this.detectedVideos = config.videos || [];

            // Find existing video elements
            this.scanPage();

            // Observe dynamic player additions / removals
            this.setupMutationObserver();

            // Handle Fullscreen transitions
            const onFullscreen = () => {
                const fsEl = document.fullscreenElement || document.webkitFullscreenElement || null;
                for (const overlay of this.overlays.values()) {
                    overlay.handleFullscreenChange(fsEl);
                }
            };
            document.addEventListener("fullscreenchange", onFullscreen, { passive: true });
            document.addEventListener("webkitfullscreenchange", onFullscreen, { passive: true });

            // Listen for runtime updates
            extRuntime.onMessage.addListener((msg, sender, sendResponse) => {
                if (msg.type === "videos-updated") {
                    this.detectedVideos = msg.videos || [];
                    this.updateAllOverlays();
                } else if (msg.type === "overlay-settings-changed") {
                    if (msg.enabled === false) {
                        this.destroyAll();
                    } else {
                        this.scanPage();
                    }
                }
            });
        }

        scanPage() {
            if (this.disposed) return;
            const videoEls = document.querySelectorAll("video");
            videoEls.forEach(v => this.attachToVideo(v));
        }

        attachToVideo(video) {
            if (this.overlays.has(video)) return;
            // Ignore tiny invisible tracking elements
            if (video.offsetWidth === 0 && video.offsetHeight === 0 && video.readyState === 0) {
                // Wait for metadata or display
                const onLoaded = () => {
                    video.removeEventListener("loadedmetadata", onLoaded);
                    this.attachToVideo(video);
                };
                video.addEventListener("loadedmetadata", onLoaded, { once: true });
                return;
            }

            const overlay = new VideoPlayerOverlay(video, this);
            this.overlays.set(video, overlay);
        }

        setupMutationObserver() {
            this.mutationObserver = new MutationObserver(mutations => {
                for (const mut of mutations) {
                    for (const node of mut.addedNodes) {
                        if (node.nodeType === Node.ELEMENT_NODE) {
                            if (node.tagName === "VIDEO") {
                                this.attachToVideo(node);
                            } else if (node.querySelectorAll) {
                                const vids = node.querySelectorAll("video");
                                vids.forEach(v => this.attachToVideo(v));
                            }
                        }
                    }
                    for (const node of mut.removedNodes) {
                        if (node.nodeType === Node.ELEMENT_NODE) {
                            if (node.tagName === "VIDEO") {
                                this.removeVideo(node);
                            } else if (node.querySelectorAll) {
                                const vids = node.querySelectorAll("video");
                                vids.forEach(v => this.removeVideo(v));
                            }
                        }
                    }
                }
            });

            this.mutationObserver.observe(document.documentElement || document.body, {
                childList: true,
                subtree: true
            });
        }

        removeVideo(video) {
            const overlay = this.overlays.get(video);
            if (overlay) {
                overlay.destroy();
                this.overlays.delete(video);
            }
        }

        updateAllOverlays() {
            for (const overlay of this.overlays.values()) {
                overlay.updateVideos();
            }
        }

        hideOverlayOnCurrentSite() {
            extRuntime.sendMessage({
                type: "overlay-hide-site",
                host: window.location.hostname
            }, () => {
                this.destroyAll();
            });
        }

        destroyAll() {
            this.disposed = true;
            if (this.mutationObserver) {
                this.mutationObserver.disconnect();
            }
            for (const overlay of this.overlays.values()) {
                overlay.destroy();
            }
            this.overlays.clear();
        }
    }

    function escapeHtml(str) {
        if (!str) return "";
        return String(str)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    // Initialize overlay manager when authorized by background service worker
    extRuntime.sendMessage({
        type: "overlay-init",
        url: window.location.href,
        host: currentHost
    }, response => {
        if ((extRuntime && extRuntime.lastError) || (typeof chrome !== "undefined" && chrome.runtime && chrome.runtime.lastError) || !response) return;
        if (!response.enabled || !response.overlayEnabled || response.siteExcluded || response.siteOverlayDisabled) {
            return;
        }

        const manager = new OverlayManager();
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", () => manager.init(response), { once: true });
        } else {
            manager.init(response);
        }
    });

})();
