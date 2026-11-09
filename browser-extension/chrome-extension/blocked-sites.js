"use strict";

/**
 * Sites the extension never takes anything from, whatever XDM's own settings say. The Chrome Web
 * Store does not allow extensions that download from YouTube, so the extension stays out of it
 * entirely: no video capture, no bulk picker, no download or context-menu handover. Whatever
 * happens on these sites is left to the browser.
 */
export const BLOCKED_DOMAINS = [
    "youtube.com",
    "youtu.be",
    "youtube-nocookie.com",
    "youtubekids.com",
    "ytimg.com"          // thumbnails and player assets
];

export const USER_BLOCKED_SITES_KEY = "userBlockedSites";

/** True for one of the domains above or any subdomain of it ("m.youtube.com", "i.ytimg.com"). */
export function isBlockedHost(hostname) {
    if (!hostname) {
        return false;
    }
    const host = normalizeHost(hostname);
    if (!host) {
        return false;
    }
    return BLOCKED_DOMAINS.some(d => host === d || host.endsWith("." + d));
}

/** isBlockedHost for a URL or an origin; false for anything that does not parse. */
export function isBlockedUrl(url) {
    if (!url) {
        return false;
    }
    try {
        return isBlockedHost(new URL(url).hostname);
    } catch (e) {
        return false;
    }
}

/**
 * Safely normalizes an input string into a lowercase host name or domain rule.
 * Strips protocols (http://, https://), ports (:8080), paths (/...), query strings (?q=1),
 * hashes (#anchor), and leading wildcards/dots (*.example.com -> example.com).
 * Prevents substring confusion and malformed inputs.
 */
export function normalizeHost(input) {
    if (!input || typeof input !== "string") {
        return "";
    }
    let str = input.trim().toLowerCase();
    if (!str) {
        return "";
    }

    // If a protocol scheme is present, use URL parser when possible
    if (/^[a-z][a-z0-9+.-]*:\/\//i.test(str)) {
        try {
            str = new URL(str).hostname;
        } catch (e) {
            str = str.replace(/^[a-z]+:\/\//i, "").split("/")[0];
        }
    } else {
        // Strip path, query, hash
        str = str.split("/")[0].split("?")[0].split("#")[0];
    }

    // Strip port if present (e.g. localhost:8080 or example.com:443)
    if (str.includes(":") && !str.startsWith("[")) {
        str = str.split(":")[0];
    }

    // Strip leading wildcard or dot prefix (e.g. *.example.com or .example.com -> example.com)
    str = str.replace(/^(\*\.)+/, "").replace(/^\.+/, "").replace(/\.+$/, "");

    // Validate that the host contains only valid domain characters (RFC 1123 / IDN)
    // Characters allowed: alphanumeric, dashes, dots, underscores (punycode xn-- allowed)
    if (!/^[a-z0-9]([a-z0-9-_.]*[a-z0-9])?$/i.test(str)) {
        return "";
    }

    return str;
}

/**
 * Safely tests whether a hostname matches a host rule.
 * An exact match or a proper subdomain matches (e.g. 'sub.example.com' matches 'example.com').
 * Substring prefixes or suffixes without a dot boundary do NOT match
 * (e.g. 'notexample.com' does NOT match 'example.com', 'bad.company' does NOT match 'bad.com').
 */
export function matchesHostRule(hostname, rule) {
    if (!hostname || !rule) {
        return false;
    }
    const host = normalizeHost(hostname);
    const target = normalizeHost(rule);
    if (!host || !target) {
        return false;
    }
    return host === target || host.endsWith("." + target);
}

/**
 * Checks whether a given hostname or URL matches any exclusion rule in the given lists,
 * or is store-blocked (YouTube).
 */
export function isHostExcluded(hostnameOrUrl, userBlockedSites = [], desktopBlockedHosts = []) {
    if (!hostnameOrUrl) {
        return false;
    }
    let host = "";
    if (typeof hostnameOrUrl === "string" && (hostnameOrUrl.includes("://") || hostnameOrUrl.startsWith("//"))) {
        try {
            host = new URL(hostnameOrUrl).hostname;
        } catch (e) {
            host = normalizeHost(hostnameOrUrl);
        }
    } else {
        host = normalizeHost(hostnameOrUrl);
    }
    if (!host) {
        return false;
    }

    // Check store-blocked domains
    if (isBlockedHost(host)) {
        return true;
    }

    // Check user-configured website exclusions
    if (Array.isArray(userBlockedSites) && userBlockedSites.some(rule => matchesHostRule(host, rule))) {
        return true;
    }

    // Check desktop XDM blocked hosts
    if (Array.isArray(desktopBlockedHosts) && desktopBlockedHosts.some(rule => matchesHostRule(host, rule))) {
        return true;
    }

    return false;
}

/**
 * Loads user blocked sites from chrome.storage.local.
 * Returns an array of normalized host rules.
 */
export async function getUserBlockedSites() {
    try {
        const stored = await chrome.storage.local.get(USER_BLOCKED_SITES_KEY);
        const list = stored && stored[USER_BLOCKED_SITES_KEY];
        if (Array.isArray(list)) {
            return list.map(normalizeHost).filter(Boolean);
        }
        return [];
    } catch (e) {
        return [];
    }
}

/**
 * Persists user blocked sites to chrome.storage.local.
 */
export async function saveUserBlockedSites(sites) {
    const normalized = Array.from(new Set((sites || []).map(normalizeHost).filter(Boolean)));
    await chrome.storage.local.set({ [USER_BLOCKED_SITES_KEY]: normalized });
    return normalized;
}

/**
 * Adds a site to the user blocked list and persists.
 * Returns { ok: true, sites: [...] } or { ok: false, error: "..." }.
 */
export async function addUserBlockedSite(input) {
    const host = normalizeHost(input);
    if (!host) {
        return { ok: false, error: "Invalid hostname or URL" };
    }
    const current = await getUserBlockedSites();
    if (current.includes(host)) {
        return { ok: false, error: "Site is already in the exclusion list", sites: current };
    }
    current.push(host);
    const updated = await saveUserBlockedSites(current);
    return { ok: true, sites: updated, added: host };
}

/**
 * Removes a site from the user blocked list and persists.
 * Returns { ok: true, sites: [...] }.
 */
export async function removeUserBlockedSite(input) {
    const host = normalizeHost(input);
    if (!host) {
        return { ok: false, error: "Invalid hostname" };
    }
    const current = await getUserBlockedSites();
    const updated = current.filter(item => item !== host);
    const saved = await saveUserBlockedSites(updated);
    return { ok: true, sites: saved, removed: host };
}

/**
 * Edits an existing site rule in the user blocked list and persists.
 */
export async function editUserBlockedSite(oldInput, newInput) {
    const oldHost = normalizeHost(oldInput);
    const newHost = normalizeHost(newInput);
    if (!newHost) {
        return { ok: false, error: "Invalid new hostname or URL" };
    }
    const current = await getUserBlockedSites();
    const index = current.indexOf(oldHost);
    if (index === -1) {
        return { ok: false, error: "Existing rule not found" };
    }
    if (oldHost !== newHost && current.includes(newHost)) {
        return { ok: false, error: "New rule already exists in exclusion list" };
    }
    current[index] = newHost;
    const updated = await saveUserBlockedSites(current);
    return { ok: true, sites: updated, edited: newHost };
}

export const normalizeHostRule = normalizeHost;
