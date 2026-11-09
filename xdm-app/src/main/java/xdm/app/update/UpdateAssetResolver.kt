package xdm.app.update

import xdm.app.OS
import xdm.app.utils.detectOS

/**
 * Resolves platform-specific installation assets and checksum files from GitHub release assets.
 */
object UpdateAssetResolver {

    /**
     * Finds the most appropriate installation asset for the given or detected [targetOs] and [arch].
     */
    fun resolveInstallerAsset(
        assets: List<GithubAsset>,
        targetOs: OS = detectOS(),
        arch: String = System.getProperty("os.arch")?.lowercase() ?: "x64"
    ): GithubAsset? {
        if (assets.isEmpty()) return null

        val isArm = arch.contains("arm") || arch.contains("aarch64")

        return when (targetOs) {
            OS.Windows -> resolveWindowsAsset(assets, isArm)
            OS.Linux -> resolveLinuxAsset(assets, isArm)
            OS.MacOS -> resolveMacAsset(assets, isArm)
        }
    }

    /**
     * Finds the SHA-256 sums file among the release assets.
     */
    fun resolveChecksumAsset(assets: List<GithubAsset>): GithubAsset? {
        return assets.firstOrNull { asset ->
            val name = asset.name.lowercase().trim()
            name == "sha256sums.txt" ||
                    name == "sha256sums" ||
                    name.endsWith(".sha256") ||
                    name.endsWith("sha256sums.txt")
        }
    }

    private fun resolveWindowsAsset(assets: List<GithubAsset>, isArm: Boolean): GithubAsset? {
        val nonChecksum = assets.filterNot { isChecksumFile(it.name) }

        // Prefer arm64 setup if on arm, or x64/x86 setup
        if (isArm) {
            nonChecksum.firstOrNull { it.name.contains("arm64", ignoreCase = true) && it.name.endsWith(".exe", ignoreCase = true) }?.let { return it }
        }

        // 1. Setup executable (e.g. XDM-Enhanced-Setup-v1.0.0-beta.3-win-x64.exe)
        nonChecksum.firstOrNull {
            it.name.contains("setup", ignoreCase = true) && it.name.endsWith(".exe", ignoreCase = true)
        }?.let { return it }

        // 2. Any executable (.exe)
        nonChecksum.firstOrNull {
            it.name.endsWith(".exe", ignoreCase = true)
        }?.let { return it }

        // 3. MSI installer (.msi)
        nonChecksum.firstOrNull {
            it.name.endsWith(".msi", ignoreCase = true)
        }?.let { return it }

        // 4. Windows zip archive
        nonChecksum.firstOrNull {
            it.name.contains("win", ignoreCase = true) && it.name.endsWith(".zip", ignoreCase = true)
        }?.let { return it }

        return nonChecksum.firstOrNull { it.name.endsWith(".zip", ignoreCase = true) }
    }

    private fun resolveLinuxAsset(assets: List<GithubAsset>, isArm: Boolean): GithubAsset? {
        val nonChecksum = assets.filterNot { isChecksumFile(it.name) }

        // 1. AppImage
        nonChecksum.firstOrNull { it.name.endsWith(".AppImage", ignoreCase = true) }?.let { return it }

        // 2. Deb package
        nonChecksum.firstOrNull { it.name.endsWith(".deb", ignoreCase = true) }?.let { return it }

        // 3. RPM package
        nonChecksum.firstOrNull { it.name.endsWith(".rpm", ignoreCase = true) }?.let { return it }

        // 4. Tar.xz / Tar.gz
        nonChecksum.firstOrNull {
            it.name.endsWith(".tar.xz", ignoreCase = true) || it.name.endsWith(".tar.gz", ignoreCase = true)
        }?.let { return it }

        return nonChecksum.firstOrNull { it.name.endsWith(".zip", ignoreCase = true) }
    }

    private fun resolveMacAsset(assets: List<GithubAsset>, isArm: Boolean): GithubAsset? {
        val nonChecksum = assets.filterNot { isChecksumFile(it.name) }

        if (isArm) {
            nonChecksum.firstOrNull {
                (it.name.contains("arm64", ignoreCase = true) || it.name.contains("aarch64", ignoreCase = true)) &&
                        it.name.endsWith(".dmg", ignoreCase = true)
            }?.let { return it }
        }

        // 1. DMG
        nonChecksum.firstOrNull { it.name.endsWith(".dmg", ignoreCase = true) }?.let { return it }

        // 2. PKG
        nonChecksum.firstOrNull { it.name.endsWith(".pkg", ignoreCase = true) }?.let { return it }

        return nonChecksum.firstOrNull { it.name.endsWith(".zip", ignoreCase = true) }
    }

    private fun isChecksumFile(name: String): Boolean {
        val lower = name.lowercase().trim()
        return lower == "sha256sums.txt" ||
                lower == "sha256sums" ||
                lower.endsWith(".sha256") ||
                lower.endsWith(".sha256sum")
    }
}

