package xdm.app.ui.components

import xdm.app.utils.px
import xdm.core.downloaders.web.SegmentProgress
import xdm.core.util.FormatHelper.formatSize
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.ToolTipManager
import javax.swing.UIManager

/**
 * High-performance, flicker-free segmented progress visualization component.
 * Displays real segment boundaries, individual connection progress, and distinct states
 * (active, complete, failed, pending).
 */
class SegmentVisualizationBar : SegmentPanel() {

    private val colorComplete = Color(0x2E, 0xCC, 0x71)
    private val colorActiveStart = Color(0x3B, 0x82, 0xF6)
    private val colorActiveEnd = Color(0x60, 0xA5, 0xFA)
    private val colorFailed = Color(0xEF, 0x44, 0x44)
    private val colorDivider = Color(0xE1, 0x1D, 0x48)

    init {
        preferredSize = Dimension(Short.MAX_VALUE.toInt(), 22.px)
        minimumSize = Dimension(0, 22.px)
        isOpaque = false
        ToolTipManager.sharedInstance().registerComponent(this)
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val list = segDet.toList()
        if (list.isEmpty()) return null
        val w = width.toDouble()
        if (w <= 0) return null

        val mouseX = event.x.coerceIn(0, width)
        val seg = if (totalSize > 0) {
            val ratio = mouseX / w
            val bytePos = (ratio * totalSize).toLong()
            list.firstOrNull { bytePos >= it.start && bytePos <= (it.start + it.length) } ?: list.lastOrNull()
        } else {
            val slotWidth = w / list.size
            val index = (mouseX / slotWidth).toInt().coerceIn(0, list.lastIndex)
            list.getOrNull(index)
        } ?: return null

        val index = list.indexOf(seg) + 1
        val len = if (seg.length > 0) seg.length else 0L
        val dl = seg.downloaded.coerceAtMost(if (len > 0) len else Long.MAX_VALUE)
        val percent = if (len > 0) ((dl.toDouble() / len) * 100).toInt().coerceIn(0, 100) else 0
        val sizeStr = if (len > 0) "${formatSize(dl.toDouble())} / ${formatSize(len.toDouble())} ($percent%)" else formatSize(dl.toDouble())
        val statusStr = seg.status.ifBlank { if (dl >= len && len > 0) "Complete" else "Receiving data..." }

        return "<html><b>Segment #$index:</b> $sizeStr<br><b>Status:</b> $statusStr</html>"
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val w = width.toDouble()
            val h = height.toDouble()
            if (w <= 0 || h <= 0) return

            val arc = 6.px.toDouble()
            val clipShape = RoundRectangle2D.Double(0.0, 0.0, w, h, arc, arc)
            g2.clip(clipShape)

            // Background track
            val trackBg = UIManager.getColor("ProgressBar.background") ?: Color(0x25, 0x27, 0x2E)
            g2.color = trackBg
            g2.fill(RoundRectangle2D.Double(0.0, 0.0, w, h, 0.0, 0.0))

            val list = segDet.toList()
            if (list.isEmpty()) return

            val hasTotal = totalSize > 0
            val numSegments = list.size

            for ((i, info) in list.withIndex()) {
                val segStart: Double
                val segEnd: Double

                if (hasTotal) {
                    segStart = (info.start.toDouble() / totalSize) * w
                    segEnd = ((info.start + info.length).toDouble() / totalSize) * w
                } else {
                    val slot = w / numSegments
                    segStart = i * slot
                    segEnd = (i + 1) * slot
                }

                val segW = maxOf(1.0, segEnd - segStart)
                val len = if (info.length > 0) info.length else 0L
                val downloaded = info.downloaded.coerceAtMost(if (len > 0) len else Long.MAX_VALUE)
                val isComplete = len > 0 && downloaded >= len
                val isFailed = info.status.equals("Failed", ignoreCase = true)

                // Fill progress inside this segment
                val fillRatio = when {
                    isComplete -> 1.0
                    len > 0 -> (downloaded.toDouble() / len).coerceIn(0.0, 1.0)
                    downloaded > 0 -> 1.0
                    else -> 0.0
                }

                val fillW = (segW * fillRatio).coerceAtLeast(0.0)
                if (fillW > 0) {
                    val fillRect = RoundRectangle2D.Double(segStart, 0.0, fillW, h, 0.0, 0.0)
                    when {
                        isFailed -> {
                            g2.color = colorFailed
                            g2.fill(fillRect)
                        }
                        isComplete -> {
                            g2.color = colorComplete
                            g2.fill(fillRect)
                        }
                        else -> {
                            // Active download gradient
                            val grad = GradientPaint(
                                segStart.toFloat(), 0f, colorActiveStart,
                                (segStart + fillW).toFloat(), 0f, colorActiveEnd
                            )
                            g2.paint = grad
                            g2.fill(fillRect)
                        }
                    }
                }

                // Vertical boundary line indicating connection start position
                if (i > 0) {
                    g2.color = colorDivider
                    g2.stroke = BasicStroke(1.5f)
                    g2.drawLine(segStart.toInt(), 0, segStart.toInt(), h.toInt())
                }
            }

            // Outline border
            g2.stroke = BasicStroke(1f)
            g2.color = UIManager.getColor("Component.borderColor") ?: Color(0x40, 0x44, 0x4B)
            g2.draw(RoundRectangle2D.Double(0.5, 0.5, w - 1.0, h - 1.0, arc, arc))
        } finally {
            g2.dispose()
        }
    }
}
