package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.I8N
import xdm.app.ui.components.AppMenuHandler
import xdm.app.ui.screens.NetworkOptimizerDialog
import java.io.File
import javax.swing.JComboBox
import javax.swing.JCheckBox
import javax.swing.JSpinner
import javax.swing.SwingUtilities

class NetworkOptimizerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var config: AppConfig

    @BeforeEach
    fun setUp() {
        I8N.loadTexts("en")
        config = AppConfig(tempDir.absolutePath)
        AppContext.config = config
    }

    @AfterEach
    fun tearDown() {
        I8N.loadTexts("en")
    }

    @Test
    fun testDialogProfileSelectionUpdatesControls() {
        var dialog: NetworkOptimizerDialog? = null
        SwingUtilities.invokeAndWait {
            dialog = NetworkOptimizerDialog(null)
        }
        val dlg = dialog!!

        try {
            SwingUtilities.invokeAndWait {
                val cmbProfile = getField<JComboBox<NetworkOptimizerDialog.Profile>>(dlg, "cmbProfile")
                val spnSegments = getField<JSpinner>(dlg, "spnSegments")
                val spnParallel = getField<JSpinner>(dlg, "spnParallel")
                val spnTimeout = getField<JSpinner>(dlg, "spnTimeout")
                val chkSpeedLimiter = getField<JCheckBox>(dlg, "chkSpeedLimiter")
                val spnSpeedLimit = getField<JSpinner>(dlg, "spnSpeedLimit")

                // Test Broadband
                cmbProfile.selectedItem = NetworkOptimizerDialog.Profile.BROADBAND
                assertEquals(32, spnSegments.value)
                assertEquals(5, spnParallel.value)
                assertEquals(45, spnTimeout.value)
                assertFalse(chkSpeedLimiter.isSelected)

                // Test Balanced
                cmbProfile.selectedItem = NetworkOptimizerDialog.Profile.BALANCED
                assertEquals(8, spnSegments.value)
                assertEquals(3, spnParallel.value)
                assertEquals(60, spnTimeout.value)
                assertFalse(chkSpeedLimiter.isSelected)

                // Test Metered
                cmbProfile.selectedItem = NetworkOptimizerDialog.Profile.METERED
                assertEquals(4, spnSegments.value)
                assertEquals(1, spnParallel.value)
                assertEquals(90, spnTimeout.value)
                assertTrue(chkSpeedLimiter.isSelected)
                assertEquals(512, spnSpeedLimit.value)

                // Test Aggressive
                cmbProfile.selectedItem = NetworkOptimizerDialog.Profile.AGGRESSIVE
                assertEquals(64, spnSegments.value)
                assertEquals(8, spnParallel.value)
                assertEquals(30, spnTimeout.value)
                assertFalse(chkSpeedLimiter.isSelected)
            }
        } finally {
            SwingUtilities.invokeAndWait { dlg.dispose() }
        }
    }

    @Test
    fun testSpinnerEditSwitchesToCustomProfile() {
        var dialog: NetworkOptimizerDialog? = null
        SwingUtilities.invokeAndWait {
            dialog = NetworkOptimizerDialog(null)
        }
        val dlg = dialog!!

        try {
            SwingUtilities.invokeAndWait {
                val cmbProfile = getField<JComboBox<NetworkOptimizerDialog.Profile>>(dlg, "cmbProfile")
                val spnSegments = getField<JSpinner>(dlg, "spnSegments")

                cmbProfile.selectedItem = NetworkOptimizerDialog.Profile.BALANCED
                assertEquals(NetworkOptimizerDialog.Profile.BALANCED, cmbProfile.selectedItem)

                spnSegments.value = 24
                assertEquals(NetworkOptimizerDialog.Profile.CUSTOM, cmbProfile.selectedItem)
            }
        } finally {
            SwingUtilities.invokeAndWait { dlg.dispose() }
        }
    }

    @Test
    fun testApplyOptimizationPersistsToConfig() {
        var dialog: NetworkOptimizerDialog? = null
        SwingUtilities.invokeAndWait {
            dialog = NetworkOptimizerDialog(null)
        }
        val dlg = dialog!!

        try {
            SwingUtilities.invokeAndWait {
                val spnSegments = getField<JSpinner>(dlg, "spnSegments")
                val spnParallel = getField<JSpinner>(dlg, "spnParallel")
                val spnTimeout = getField<JSpinner>(dlg, "spnTimeout")
                val chkSpeedLimiter = getField<JCheckBox>(dlg, "chkSpeedLimiter")
                val spnSpeedLimit = getField<JSpinner>(dlg, "spnSpeedLimit")

                spnSegments.value = 20
                spnParallel.value = 7
                spnTimeout.value = 45
                chkSpeedLimiter.isSelected = true
                spnSpeedLimit.value = 1024

                val applyMethod = NetworkOptimizerDialog::class.java.getDeclaredMethod("applyOptimization")
                applyMethod.isAccessible = true
                applyMethod.invoke(dlg)
            }

            assertEquals(20, config.maxSegments)
            assertEquals(7, config.maxParallelDownloads)
            assertEquals(45, config.readTimeoutSeconds)
            assertTrue(config.speedLimiterEnabled)
            assertEquals(1024, config.speedLimit)
        } finally {
            SwingUtilities.invokeAndWait { dlg.dispose() }
        }
    }

    @Test
    fun testAppMenuHandlerWiring() {
        SwingUtilities.invokeAndWait {
            AppMenuHandler.optimizeRWin()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(obj: Any, fieldName: String): T {
        val f = obj.javaClass.getDeclaredField(fieldName)
        f.isAccessible = true
        return f.get(obj) as T
    }
}
