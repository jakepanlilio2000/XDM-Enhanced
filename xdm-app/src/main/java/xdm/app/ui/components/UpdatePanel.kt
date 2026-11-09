package xdm.app.ui.components

import xdm.app.ui.screens.UpdateDialog
import xdm.app.update.ReleaseInfo
import xdm.app.utils.RemixIcon
import javax.swing.SwingUtilities

/**
 * A slim banner shown at the bottom of the main window when a newer release is
 * available. Hidden by default; [showUpdate] makes it visible and wires the
 * button to open the update dialog. Must be mutated on the EDT.
 */
class UpdatePanel : BannerPanel(RemixIcon.INSTALL_FILL, "BTN_UPDATE_NOW", "BTN_UPDATE_DISMISS") {

    private var currentReleaseInfo: ReleaseInfo? = null
    private var downloadUrl: String = ""

    init {
        actionButton.apply {
            text = "Update"
            toolTipText = "Open update dialog"
            addActionListener { openUpdateDialog() }
        }
        badgeLabel.apply {
            text = "Pre-release"
            toolTipText = "This is a pre-release (beta) build and may be less stable"
        }
    }

    /**
     * Shows the banner with a message referencing [version] and remembers [url] and [releaseInfo]; a [preRelease]
     * version also gets the "Pre-release" badge.
     */
    fun showUpdate(version: String, url: String, preRelease: Boolean, releaseInfo: ReleaseInfo? = null) {
        currentReleaseInfo = releaseInfo
        downloadUrl = url
        messageLabel.text = if (preRelease) "A new pre-release version ($version) of XDM Enhanced is available."
        else "A new version ($version) of XDM Enhanced is available."
        badgeLabel.isVisible = preRelease
        setShown(true)
    }

    private fun openUpdateDialog() {
        UpdateDialog.open(
            SwingUtilities.windowForComponent(this),
            manualCheck = (currentReleaseInfo == null),
            releaseInfo = currentReleaseInfo
        )
    }
}
