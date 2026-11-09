"use strict";

function normalizeHostRule(input) {
    if (typeof window !== "undefined" && window.BlockedSites && window.BlockedSites.normalizeHost) {
        return window.BlockedSites.normalizeHost(input);
    }
    if (!input || typeof input !== "string") return "";
    var str = input.trim().toLowerCase();
    if (!str) return "";
    var cleaned = str.replace(/^[a-z][a-z0-9+.-]*:\/\//i, "");
    cleaned = cleaned.replace(/[:/?#].*$/, "");
    cleaned = cleaned.replace(/^\*\./, "").replace(/^\./, "");
    cleaned = cleaned.replace(/\.$/, "");
    if (!cleaned || cleaned.includes(" ") || !/^([a-z0-9-]+\.)*[a-z0-9-]+$/i.test(cleaned)) {
        return "";
    }
    return cleaned;
}

const BLOCKED_SITES_KEY = "userBlockedSites";
const BULK_PREFS_KEY = "bulkPrefs";
const PREVIEWS_KEY = "showPreviews";
const THEME_KEY = "theme";
const BADGE_COUNTER_KEY = "badgeCounter";
const NOTIFICATIONS_KEY = "notifications";
const FILE_EXTS_KEY = "defFileExtensions";
const MIN_VIDEO_SIZE_KEY = "minVideoSize";
const CAPTURE_STREAMS_KEY = "captureStreams";

const DEFAULT_EXTENSIONS = ".mp4, .m3u8, .mpd, .ts, .webm, .mkv, .flv, .avi, .zip, .rar, .7z, .tar, .gz, .iso, .exe, .msi, .apk, .dmg, .pkg, .pdf";

class ExtensionOptions {
    constructor() {
        this.currentTab = "general";
        this.rules = [];
        this.keywords = [];
        this.keywordMatchMode = "AND";
    }

    init() {
        document.addEventListener("DOMContentLoaded", () => {
            this.bindNavigation();
            this.loadAllPreferences();
            this.bindEvents();

            // Handle hash navigation (e.g. #exclusions)
            if (window.location.hash) {
                const target = window.location.hash.replace("#", "").toLowerCase();
                this.switchTab(target);
            }
        });
    }

    bindNavigation() {
        const tabBtns = document.querySelectorAll(".nav-tab-btn");
        tabBtns.forEach(btn => {
            btn.addEventListener("click", () => {
                const tab = btn.dataset.tab;
                this.switchTab(tab);
            });
        });
    }

    switchTab(tabName) {
        const tabBtn = document.querySelector(`.nav-tab-btn[data-tab="${tabName}"]`);
        const section = document.getElementById(`section-${tabName}`);
        if (!tabBtn || !section) return;

        document.querySelectorAll(".nav-tab-btn").forEach(b => b.classList.remove("active"));
        document.querySelectorAll(".options-section").forEach(s => s.classList.remove("active"));

        tabBtn.classList.add("active");
        section.classList.add("active");
        this.currentTab = tabName;
        window.location.hash = tabName;
    }

    loadAllPreferences() {
        // Load monitoring status from background
        chrome.runtime.sendMessage({ type: "stat" }, response => {
            if (response && response.enabled !== undefined) {
                const monToggle = document.getElementById("optMonitoring");
                if (monToggle) monToggle.checked = response.enabled;
            }
        });

        // Load storage values
        chrome.storage.local.get([
            BLOCKED_SITES_KEY,
            BULK_PREFS_KEY,
            PREVIEWS_KEY,
            THEME_KEY,
            BADGE_COUNTER_KEY,
            NOTIFICATIONS_KEY,
            FILE_EXTS_KEY,
            MIN_VIDEO_SIZE_KEY,
            CAPTURE_STREAMS_KEY
        ], stored => {
            if (chrome.runtime.lastError || !stored) return;

            // General
            if (stored[BADGE_COUNTER_KEY] !== undefined) {
                document.getElementById("optBadgeCounter").checked = stored[BADGE_COUNTER_KEY];
            }
            if (stored[NOTIFICATIONS_KEY] !== undefined) {
                document.getElementById("optNotifications").checked = stored[NOTIFICATIONS_KEY];
            }

            // Detection
            document.getElementById("optFileExtensions").value = stored[FILE_EXTS_KEY] || DEFAULT_EXTENSIONS;
            if (stored[MIN_VIDEO_SIZE_KEY] !== undefined) {
                document.getElementById("optMinVideoSize").value = stored[MIN_VIDEO_SIZE_KEY];
            }
            if (stored[CAPTURE_STREAMS_KEY] !== undefined) {
                document.getElementById("optCaptureStreams").checked = stored[CAPTURE_STREAMS_KEY];
            }
            if (stored[PREVIEWS_KEY] !== undefined) {
                document.getElementById("optPreviews").checked = stored[PREVIEWS_KEY];
            }

            // Theme
            const theme = stored[THEME_KEY] || "system";
            const themeRadio = document.querySelector(`input[name="themeMode"][value="${theme}"]`);
            if (themeRadio) themeRadio.checked = true;
            this.applyTheme(theme);

            // Exclusions
            this.rules = Array.isArray(stored[BLOCKED_SITES_KEY]) ? stored[BLOCKED_SITES_KEY] : [];
            this.renderRules();

            // Keywords
            const bulkPrefs = stored[BULK_PREFS_KEY] || {};
            this.keywords = Array.isArray(bulkPrefs.keywords) ? [...bulkPrefs.keywords] : [];
            this.keywordMatchMode = bulkPrefs.keywordMatchMode === "OR" ? "OR" : "AND";

            const matchRadio = document.querySelector(`input[name="keywordMatchMode"][value="${this.keywordMatchMode}"]`);
            if (matchRadio) matchRadio.checked = true;
            this.renderKeywords();
        });
    }

    bindEvents() {
        // Monitoring toggle
        document.getElementById("optMonitoring").addEventListener("change", e => {
            chrome.runtime.sendMessage({ type: "cmd", enabled: e.target.checked });
        });

        // Badges & Notifications
        document.getElementById("optBadgeCounter").addEventListener("change", e => {
            chrome.storage.local.set({ [BADGE_COUNTER_KEY]: e.target.checked });
        });
        document.getElementById("optNotifications").addEventListener("change", e => {
            chrome.storage.local.set({ [NOTIFICATIONS_KEY]: e.target.checked });
        });

        // Detection settings
        document.getElementById("optFileExtensions").addEventListener("input", e => {
            chrome.storage.local.set({ [FILE_EXTS_KEY]: e.target.value.trim() });
        });
        document.getElementById("resetFileExtBtn").addEventListener("click", () => {
            document.getElementById("optFileExtensions").value = DEFAULT_EXTENSIONS;
            chrome.storage.local.set({ [FILE_EXTS_KEY]: DEFAULT_EXTENSIONS });
        });
        document.getElementById("optMinVideoSize").addEventListener("change", e => {
            chrome.storage.local.set({ [MIN_VIDEO_SIZE_KEY]: parseInt(e.target.value, 10) });
        });
        document.getElementById("optCaptureStreams").addEventListener("change", e => {
            chrome.storage.local.set({ [CAPTURE_STREAMS_KEY]: e.target.checked });
        });
        document.getElementById("optPreviews").addEventListener("change", e => {
            chrome.storage.local.set({ [PREVIEWS_KEY]: e.target.checked });
        });

        // Theme selection
        document.querySelectorAll('input[name="themeMode"]').forEach(radio => {
            radio.addEventListener("change", e => {
                const selected = e.target.value;
                chrome.storage.local.set({ [THEME_KEY]: selected });
                this.applyTheme(selected);
            });
        });

        // Website Exclusions: Add Rule
        document.getElementById("addSiteBtn").addEventListener("click", () => this.addExclusionRule());
        document.getElementById("newSiteInput").addEventListener("keydown", e => {
            if (e.key === "Enter") {
                e.preventDefault();
                this.addExclusionRule();
            }
        });

        // Search exclusions
        document.getElementById("searchRulesInput").addEventListener("input", () => this.renderRules());

        // Clear all exclusions
        document.getElementById("clearAllRulesBtn").addEventListener("click", () => {
            if (confirm("Are you sure you want to remove all configured website exclusions?")) {
                this.rules = [];
                this.persistRules();
            }
        });

        // Keyword Filters: Add Tag
        document.getElementById("addKeywordBtn").addEventListener("click", () => this.addKeywordTag());
        document.getElementById("newKeywordInput").addEventListener("keydown", e => {
            if (e.key === "Enter") {
                e.preventDefault();
                this.addKeywordTag();
            }
        });

        // Keyword Match Mode
        document.querySelectorAll('input[name="keywordMatchMode"]').forEach(radio => {
            radio.addEventListener("change", e => {
                this.keywordMatchMode = e.target.value;
                this.persistKeywords();
            });
        });

        // Clear all keywords
        document.getElementById("clearKeywordsBtn").addEventListener("click", () => {
            this.keywords = [];
            this.persistKeywords();
        });
    }

    applyTheme(theme) {
        if (theme === "light" || theme === "dark") {
            document.documentElement.setAttribute("data-theme", theme);
        } else {
            document.documentElement.removeAttribute("data-theme");
        }
    }

    // ---- Website Exclusions logic ----

    addExclusionRule() {
        const input = document.getElementById("newSiteInput");
        const feedback = document.getElementById("inputFeedback");
        const raw = (input.value || "").trim();

        if (!raw) {
            feedback.textContent = "Please enter a valid domain or host.";
            feedback.hidden = false;
            return;
        }

        const normalized = normalizeHostRule(raw);
        if (!normalized) {
            feedback.textContent = "Invalid format. Enter a domain like 'example.com' or wildcard '*.domain.org'.";
            feedback.hidden = false;
            return;
        }

        if (this.rules.includes(normalized)) {
            feedback.textContent = `'${normalized}' is already in your exclusions list.`;
            feedback.hidden = false;
            return;
        }

        feedback.hidden = true;
        input.value = "";
        this.rules.unshift(normalized);
        this.persistRules();
    }

    persistRules() {
        chrome.storage.local.set({ [BLOCKED_SITES_KEY]: this.rules }, () => {
            this.renderRules();
        });
    }

    removeRule(ruleToRemove) {
        this.rules = this.rules.filter(r => r !== ruleToRemove);
        this.persistRules();
    }

    renderRules() {
        const list = document.getElementById("rulesList");
        const countSpan = document.getElementById("ruleCount");
        const emptyDiv = document.getElementById("emptyRules");
        const searchVal = (document.getElementById("searchRulesInput").value || "").trim().toLowerCase();

        countSpan.textContent = this.rules.length;
        list.replaceChildren();

        const filtered = searchVal ? this.rules.filter(r => r.toLowerCase().includes(searchVal)) : this.rules;

        if (filtered.length === 0) {
            emptyDiv.hidden = false;
            emptyDiv.textContent = this.rules.length === 0
                ? "No websites are currently excluded. XDM monitors downloads across all permitted sites."
                : `No exclusions match '${searchVal}'.`;
            return;
        }

        emptyDiv.hidden = true;
        filtered.forEach(rule => {
            const li = document.createElement("li");
            li.className = "rule-item";

            const nameSpan = document.createElement("span");
            nameSpan.className = "rule-name";
            nameSpan.textContent = rule;

            const actionsDiv = document.createElement("div");
            actionsDiv.className = "rule-actions";

            const delBtn = document.createElement("button");
            delBtn.className = "secondary-button btn-small";
            delBtn.type = "button";
            delBtn.textContent = "Remove";
            delBtn.addEventListener("click", () => this.removeRule(rule));

            actionsDiv.appendChild(delBtn);
            li.appendChild(nameSpan);
            li.appendChild(actionsDiv);
            list.appendChild(li);
        });
    }

    // ---- Keyword Filters logic ----

    addKeywordTag() {
        const input = document.getElementById("newKeywordInput");
        const raw = (input.value || "").trim();
        if (!raw) return;

        if (!this.keywords.includes(raw)) {
            this.keywords.push(raw);
            this.persistKeywords();
        }
        input.value = "";
    }

    removeKeywordTag(kw) {
        this.keywords = this.keywords.filter(k => k !== kw);
        this.persistKeywords();
    }

    persistKeywords() {
        chrome.storage.local.get(BULK_PREFS_KEY, stored => {
            const prefs = (stored && stored[BULK_PREFS_KEY]) || {};
            prefs.keywords = this.keywords;
            prefs.keywordMatchMode = this.keywordMatchMode;
            chrome.storage.local.set({ [BULK_PREFS_KEY]: prefs }, () => {
                this.renderKeywords();
            });
        });
    }

    renderKeywords() {
        const container = document.getElementById("keywordChips");
        const emptyHint = document.getElementById("emptyKeywords");
        container.replaceChildren();

        if (this.keywords.length === 0) {
            emptyHint.hidden = false;
            return;
        }

        emptyHint.hidden = true;
        this.keywords.forEach(kw => {
            const chip = document.createElement("span");
            chip.className = "keyword-chip";

            const text = document.createElement("span");
            text.textContent = kw;

            const rmBtn = document.createElement("button");
            rmBtn.className = "chip-remove";
            rmBtn.type = "button";
            rmBtn.textContent = "×";
            rmBtn.title = `Remove '${kw}'`;
            rmBtn.addEventListener("click", () => this.removeKeywordTag(kw));

            chip.appendChild(text);
            chip.appendChild(rmBtn);
            container.appendChild(chip);
        });
    }
}

new ExtensionOptions().init();

