package xdm.app.power

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.I8N
import xdm.app.utils.px
import xdm.core.util.Logger
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*

class CompletionCountdownDialog(
    parent: java.awt.Window? = null,
    val action: CompletionAction,
    val failedCount: Int = 0,
    val initialSeconds: Int = 30,
    val onPerform: (CompletionAction) -> Unit,
    val onCancel: () -> Unit = {}
) : JDialog(parent, I8N.text("MSG_COUNTDOWN_TITLE") ?: "Downloads Finished", ModalityType.APPLICATION_MODAL) {

    private var remainingSeconds = initialSeconds
    private val completed = AtomicBoolean(false)
    private var swingTimer: Timer? = null

    private val lblTitle = JLabel(I8N.text("MSG_COUNTDOWN_TITLE") ?: "Downloads Finished").apply {
        font = font.deriveFont(Font.BOLD, 16.px.toFloat())
    }

    private val lblDesc = JLabel().apply {
        font = font.deriveFont(Font.PLAIN, 13.px.toFloat())
    }

    private val lblCountdown = JLabel(remainingSeconds.toString(), SwingConstants.CENTER).apply {
        font = font.deriveFont(Font.BOLD, 42.px.toFloat())
        putClientProperty(FlatClientProperties.STYLE, "foreground: \$Component.accentColor")
    }

    private val progressBar = JProgressBar(0, initialSeconds).apply {
        value = initialSeconds
        isStringPainted = false
        preferredSize = Dimension(320.px, 6.px)
    }

    private val btnCancel = JButton(I8N.text("MSG_CANCEL") ?: "Cancel").apply {
        preferredSize = Dimension(100.px, 32.px)
        addActionListener { cancelAction() }
    }

    private val btnPerformNow = JButton(I8N.text("MSG_PERFORM_NOW") ?: "Perform Now").apply {
        preferredSize = Dimension(120.px, 32.px)
        putClientProperty(FlatClientProperties.STYLE, "background: \$Component.accentColor; foreground: #ffffff")
        addActionListener { performAction() }
    }

    init {
        isResizable = false
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                cancelAction()
            }
        })

        // Escape key closes/cancels
        rootPane.registerKeyboardAction(
            { cancelAction() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        )

        updateDescription()

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(24.px, 28.px, 24.px, 28.px)

            add(lblTitle)
            add(Box.createVerticalStrut(8.px))
            add(lblDesc)
            add(Box.createVerticalStrut(20.px))
            add(lblCountdown)
            add(Box.createVerticalStrut(14.px))
            add(progressBar)
            add(Box.createVerticalStrut(24.px))

            val buttonsPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                isOpaque = false
                add(Box.createHorizontalGlue())
                add(btnCancel)
                add(Box.createHorizontalStrut(12.px))
                add(btnPerformNow)
            }
            add(buttonsPanel)
        }

        contentPane.add(content, BorderLayout.CENTER)
        pack()
        setLocationRelativeTo(parent)

        startTimer()
    }

    private fun startTimer() {
        swingTimer = Timer(1000) {
            remainingSeconds--
            if (remainingSeconds <= 0) {
                lblCountdown.text = "0"
                progressBar.value = 0
                swingTimer?.stop()
                performAction()
            } else {
                lblCountdown.text = remainingSeconds.toString()
                progressBar.value = remainingSeconds
                updateDescription()
            }
        }.apply {
            isRepeats = true
            start()
        }
    }

    private fun updateDescription() {
        val actionText = when (action) {
            CompletionAction.SLEEP -> (I8N.text("ACTION_SLEEP") ?: "sleep").lowercase()
            CompletionAction.SHUTDOWN -> (I8N.text("ACTION_SHUTDOWN") ?: "shut down").lowercase()
            CompletionAction.HIBERNATE -> (I8N.text("ACTION_HIBERNATE") ?: "hibernate").lowercase()
            CompletionAction.DO_NOTHING -> "finish"
        }

        val text = if (failedCount > 0) {
            val template = I8N.text("MSG_COUNTDOWN_DESC_WITH_FAILURES")
                ?: "All downloads finished (%d failed). Your computer will %s in %d seconds."
            template.format(failedCount, actionText, remainingSeconds)
        } else {
            val template = I8N.text("MSG_COUNTDOWN_DESC")
                ?: "All downloads finished. Your computer will %s in %d seconds."
            template.format(actionText, remainingSeconds)
        }
        lblDesc.text = "<html><body style='width: 280px;'>$text</body></html>"
    }

    fun cancelAction() {
        if (!completed.compareAndSet(false, true)) return
        swingTimer?.stop()
        dispose()
        Logger.info("Completion power action (${action.name}) cancelled by user")
        onCancel()
    }

    fun performAction() {
        if (!completed.compareAndSet(false, true)) return
        swingTimer?.stop()
        dispose()
        Logger.info("Completion power action (${action.name}) confirmed, executing now")
        onPerform(action)
    }
}
