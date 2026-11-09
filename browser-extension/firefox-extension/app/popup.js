"use strict";

/** Previews asked of XDM at once; matches the connection pool of its manifest client. */
const PREVIEW_CONCURRENCY = 5;
const PREVIEWS_KEY = "showPreviews";
const THEME_KEY = "theme";

/**
 * Modernized toolbar popup:
 * - Clear XDM Enhanced branding and live media count badge
 * - Non-closing monitoring toggle
 * - Prominent "Download Current Media" and "Grab All Links" actions
 * - Registered shortcut communication (Alt+Shift+D)
 * - Clear disconnected status and link to unified settings page
 */
class VideoPopup {
    constructor() {
        this.items = [];
        this.showPreviews = true;
        this.loads = new Map();
        this.queue = [];
        this.running = 0;
        this.currentHost = "";
        this.siteExcluded = false;
        this.connected = false;
    }

    run() {
        document.addEventListener('DOMContentLoaded', this.onLoad.bind(this), false);
    }

    onLoad() {
        // Load theme and preview preferences
        chrome.storage.local.get([PREVIEWS_KEY, THEME_KEY], stored => {
            this.showPreviews = !!chrome.runtime.lastError || !stored || stored[PREVIEWS_KEY] !== false;
            const previewCheckbox = document.getElementById("previews");
            if (previewCheckbox) {
                previewCheckbox.checked = this.showPreviews;
            }

            if (stored && stored[THEME_KEY]) {
                this.applyTheme(stored[THEME_KEY]);
            }

            // Request status from background script
            chrome.runtime.sendMessage({ type: "stat" }, this.onMsg.bind(this));
        });

        // Query registered shortcut for Grab All Links (open-bulk)
        this.loadShortcuts();

        const previewsToggle = document.getElementById("previews");
        if (previewsToggle) {
            previewsToggle.addEventListener('change', e => {
                this.showPreviews = e.target.checked;
                chrome.storage.local.set({ [PREVIEWS_KEY]: this.showPreviews });
                this.renderList(this.items);
            });
        }

        // Monitoring toggle: MUST NOT close popup prematurely
        const chkToggle = document.getElementById("chk");
        if (chkToggle) {
            chkToggle.addEventListener('change', e => {
                const enabled = e.target.checked;
                chrome.runtime.sendMessage({ type: "cmd", enabled: enabled });
                this.updateMonitoringPill(enabled);
            });
        }

        // Quick Action 1: Download latest detected media
        const dlCurrentBtn = document.getElementById('downloadCurrentMediaBtn');
        if (dlCurrentBtn) {
            dlCurrentBtn.addEventListener('click', () => {
                if (this.items.length > 0) {
                    const latest = this.items[this.items.length - 1];
                    chrome.runtime.sendMessage({ type: "vid", itemId: latest.id });
                    window.close();
                }
            });
        }

        // Quick Action 2: Grab all links
        const bulkBtn = document.getElementById('bulk');
        if (bulkBtn) {
            bulkBtn.addEventListener('click', () => {
                chrome.tabs.query({ active: true, currentWindow: true }, tabs => {
                    if (tabs && tabs[0]) {
                        chrome.runtime.sendMessage({ type: "bulk", tabId: tabs[0].id });
                    }
                    window.close();
                });
            });
        }

        const clearBtn = document.getElementById('clear');
        if (clearBtn) {
            clearBtn.addEventListener('click', () => {
                chrome.runtime.sendMessage({ type: "clear" });
                this.items = [];
                this.renderList([]);
                this.updateMediaCount(0);
                document.getElementById('empty').hidden = false;
                document.getElementById('actions').hidden = true;
            });
        }

        const blockBtn = document.getElementById('blockSiteBtn');
        if (blockBtn) {
            blockBtn.addEventListener('click', () => {
                if (this.currentHost) {
                    chrome.runtime.sendMessage({ type: "block-site", host: this.currentHost }, () => {
                        window.close();
                    });
                }
            });
        }

        const unblockBtn = document.getElementById('unblockSiteBtn');
        if (unblockBtn) {
            unblockBtn.addEventListener('click', () => {
                if (this.currentHost) {
                    chrome.runtime.sendMessage({ type: "unblock-site", host: this.currentHost }, () => {
                        window.close();
                    });
                }
            });
        }

        const manageBtn = document.getElementById('manageBlockedBtn');
        if (manageBtn) {
            manageBtn.addEventListener('click', () => this.openOptionsPage("exclusions"));
        }

        const settingsBtn = document.getElementById('openSettingsBtn');
        if (settingsBtn) {
            settingsBtn.addEventListener('click', () => this.openOptionsPage("general"));
        }

        const format = document.getElementById('format');
        const hint = document.getElementById('formatHint');
        if (format && hint) {
            format.addEventListener('click', () => {
                const show = hint.hidden;
                hint.hidden = !show;
                format.setAttribute('aria-expanded', show ? 'true' : 'false');
            });
        }
    }

    applyTheme(theme) {
        if (theme === "light" || theme === "dark") {
            document.documentElement.setAttribute("data-theme", theme);
        } else {
            document.documentElement.removeAttribute("data-theme");
        }
    }

    loadShortcuts() {
        if (chrome.commands && chrome.commands.getAll) {
            chrome.commands.getAll(commands => {
                const bulkCmd = commands && commands.find(c => c.name === "open-bulk");
                const hintElem = document.getElementById("bulkShortcutHint");
                if (hintElem) {
                    if (bulkCmd && bulkCmd.shortcut) {
                        hintElem.textContent = bulkCmd.shortcut;
                        hintElem.hidden = false;
                    } else {
                        hintElem.hidden = true;
                    }
                }
            });
        }
    }

    openOptionsPage(section) {
        const url = chrome.runtime.getURL(`options.html${section ? '#' + section : ''}`);
        if (chrome.runtime.openOptionsPage) {
            chrome.runtime.openOptionsPage();
        } else {
            chrome.tabs.create({ url: url });
        }
        window.close();
    }

    updateMonitoringPill(enabled) {
        const chk = document.getElementById("chk");
        if (chk) chk.checked = enabled;
        const toggle = document.querySelector(".header-toggle");
        if (toggle) {
            toggle.title = enabled ? "Browser monitoring is active" : "Browser monitoring is paused";
        }
    }

    updateMediaCount(count) {
        const badge = document.getElementById("mediaCountBadge");
        if (badge) {
            badge.textContent = `${count} Detected`;
            badge.classList.toggle("has-items", count > 0);
        }
        const dlCurrentBtn = document.getElementById('downloadCurrentMediaBtn');
        if (dlCurrentBtn) {
            dlCurrentBtn.disabled = count === 0 || !this.connected;
        }
    }

    onMsg(response) {
        if (chrome.runtime.lastError || !response) {
            this.setConnectionState(false);
            return;
        }

        this.connected = response.connected === true;
        this.setConnectionState(this.connected);

        this.updateMonitoringPill(response.enabled === true);

        // Connected to an XDM older than extension expects
        if (response.outdated) {
            if (response.xdmVersion) {
                document.getElementById("outdatedVersion").textContent = "XDM Enhanced " + response.xdmVersion;
            }
            if (response.updateUrl) {
                document.getElementById("outdatedLink").href = response.updateUrl;
            }
            document.getElementById("outdated").hidden = false;
            document.getElementById("previewsToggle").hidden = true;
        }

        this.currentHost = response.siteHost || "";
        this.siteExcluded = response.siteExcluded === true;

        const blockBtn = document.getElementById("blockSiteBtn");
        if (blockBtn) {
            blockBtn.hidden = !this.currentHost || response.blockedSite || this.siteExcluded;
        }

        if (response.blockedSite) {
            document.getElementById('blockedSite').hidden = false;
            document.getElementById('bulk').disabled = true;
            this.updateMediaCount(0);
            return;
        }

        if (this.siteExcluded) {
            const notice = document.getElementById('siteExcludedNotice');
            if (notice) notice.hidden = false;
            const title = document.getElementById('siteExcludedTitle');
            if (title && this.currentHost) {
                title.textContent = "Monitoring paused for " + this.currentHost;
            }
            const desc = document.getElementById('siteExcludedDesc');
            if (desc && this.currentHost) {
                desc.textContent = "Downloads and videos on " + this.currentHost + " are left to the browser.";
            }
            document.getElementById('bulk').disabled = true;
            document.getElementById('list').hidden = true;
            document.getElementById('empty').hidden = true;
            document.getElementById('actions').hidden = true;
            this.updateMediaCount(0);
            return;
        }

        const list = response.list || [];
        this.items = list;
        this.renderList(list);
        this.updateMediaCount(list.length);

        document.getElementById('empty').hidden = list.length > 0;
        document.getElementById('actions').hidden = list.length === 0;
    }

    setConnectionState(connected) {
        this.connected = connected;
        const banner = document.getElementById("disconnectedNotice");
        if (banner) banner.hidden = connected;

        const pill = document.getElementById("connectionStatus");
        const text = document.getElementById("connectionText");
        if (pill && text) {
            pill.className = `status-pill ${connected ? "connected" : "disconnected"}`;
            text.textContent = connected ? "Connected" : "Disconnected";
        }

        const bulkBtn = document.getElementById("bulk");
        if (bulkBtn && !this.siteExcluded) {
            bulkBtn.disabled = !connected;
        }
        const dlCurrentBtn = document.getElementById("downloadCurrentMediaBtn");
        if (dlCurrentBtn) {
            dlCurrentBtn.disabled = !connected || this.items.length === 0;
        }
    }

    renderList(items) {
        const list = document.getElementById("list");
        const template = document.getElementById("rowTemplate");
        list.replaceChildren();

        items.slice().reverse().forEach(item => {
            const row = template.content.cloneNode(true);
            const button = row.querySelector('.video-item');
            const badge = row.querySelector('.format-badge:not(.kind-badge)');
            const meta = this.splitInfo(item.info);

            row.querySelector('.video-title').textContent = this.titleOf(item.text, meta.format);
            if (meta.format) {
                badge.textContent = meta.format;
                badge.hidden = false;
            }
            row.querySelector('.video-size').textContent = meta.rest;
            button.title = item.text;
            button.addEventListener('click', () => {
                chrome.runtime.sendMessage({ type: "vid", itemId: item.id });
                window.close();
            });

            if (item.subtitle) {
                row.querySelector('.thumb').classList.add('subtitle');
            }
            if (item.badge) {
                const kind = row.querySelector('.kind-badge');
                kind.textContent = item.badge;
                kind.hidden = false;
            }
            if (item.previewable && this.showPreviews) {
                this.preview(item.id, row.querySelector('.thumb'));
            }
            list.appendChild(row);
        });
    }

    preview(id, thumb) {
        thumb.classList.add('loading');
        this.loadPreview(id).then(images => {
            thumb.classList.remove('loading');
            this.showPreview(thumb, images && images[0]);
        }).catch(err => {
            thumb.classList.remove('loading');
            thumb.title = "Preview unavailable: " + (err && err.message ? err.message : err);
        });
    }

    loadPreview(id) {
        let load = this.loads.get(id);
        if (!load) {
            load = FramePreviews.cached(id).then(images =>
                images && images.length ? images : this.enqueue(() => FramePreviews.load(id)));
            this.loads.set(id, load);
        }
        return load;
    }

    enqueue(task) {
        return new Promise((resolve, reject) => {
            this.queue.push({ task, resolve, reject });
            this.pump();
        });
    }

    pump() {
        while (this.running < PREVIEW_CONCURRENCY && this.queue.length) {
            const { task, resolve, reject } = this.queue.shift();
            this.running++;
            task().then(resolve, reject).finally(() => {
                this.running--;
                this.pump();
            });
        }
    }

    showPreview(thumb, image) {
        if (!image) return;
        const img = document.createElement('img');
        img.alt = "";
        img.src = image;
        thumb.replaceChildren(img);
        thumb.classList.add('has-preview');
    }

    splitInfo(info) {
        const text = (info || "").trim();
        const match = /^\[([^\]]{1,12})\]\s*(.*)$/.exec(text);
        return match ? { format: match[1].trim(), rest: match[2].trim() } : { format: "", rest: text };
    }

    titleOf(name, format) {
        const text = name || "";
        if (!format) return text;
        const suffix = "." + format.toLowerCase();
        return text.toLowerCase().endsWith(suffix) ? text.slice(0, -suffix.length) : text;
    }
}

var popup = new VideoPopup();
popup.run();
