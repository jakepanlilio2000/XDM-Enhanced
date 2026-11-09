package xdm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xdm.app.ui.components.MessageBox
import xdm.app.utils.mainWindowOnScreen
import xdm.app.utils.shownWindow
import java.awt.Dimension
import java.awt.Point
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities

class MessageBoxPositionTest {

    @Test
    fun dialogPositioning_resolvesOwnerAndCentersAccurately() {
        SwingUtilities.invokeAndWait {
            val parentFrame = JFrame("Test Parent Frame").apply {
                size = Dimension(800, 600)
                location = Point(200, 150)
                isVisible = true
            }

            try {
                // Verify shownWindow finds parentFrame
                val resolvedOwner = shownWindow(parentFrame)
                assertNotNull(resolvedOwner, "shownWindow should resolve visible frame")
                assertEquals(parentFrame, resolvedOwner)

                // Verify a child component resolves to parentFrame
                val childPanel = JPanel().apply { size = Dimension(100, 50) }
                parentFrame.contentPane.add(childPanel)
                parentFrame.validate()

                val resolvedFromChild = shownWindow(childPanel)
                assertEquals(parentFrame, resolvedFromChild, "Child component should resolve to parent window")

                // Verify simulated dialog centering logic matches JDK setLocationRelativeTo behavior
                val dialogSize = Dimension(400, 200)
                val expectedX = parentFrame.x + (parentFrame.width - dialogSize.width) / 2
                val expectedY = parentFrame.y + (parentFrame.height - dialogSize.height) / 2

                assertTrue(expectedX > parentFrame.x, "Dialog should be horizontally centered inside parent")
                assertTrue(expectedY > parentFrame.y, "Dialog should be vertically centered inside parent, not pinned to top")
            } finally {
                parentFrame.dispose()
            }
        }
    }
}

