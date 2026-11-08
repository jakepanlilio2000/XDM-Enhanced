package packaging.icons;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class GenerateIcons {

    public static BufferedImage renderMaster(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        double scale = size / 512.0;
        g.scale(scale, scale);

        // 1. Squircle container
        RoundRectangle2D squircle = new RoundRectangle2D.Double(40, 40, 432, 432, 104, 104);
        LinearGradientPaint bgPaint = new LinearGradientPaint(
            40f, 40f, 472f, 472f,
            new float[]{0.0f, 0.45f, 1.0f},
            new Color[]{new Color(10, 16, 28), new Color(15, 23, 42), new Color(24, 38, 64)}
        );
        g.setPaint(bgPaint);
        g.fill(squircle);

        // Subtle inner glow / border
        LinearGradientPaint borderPaint = new LinearGradientPaint(
            40f, 40f, 472f, 472f,
            new float[]{0.0f, 0.5f, 1.0f},
            new Color[]{new Color(56, 189, 248, 220), new Color(37, 99, 235, 120), new Color(14, 116, 144, 40)}
        );
        g.setPaint(borderPaint);
        g.setStroke(new BasicStroke(3.0f));
        g.draw(squircle);

        // 2. Stylized X - Right Arm (back diagonal)
        Path2D.Double armRight = new Path2D.Double();
        armRight.moveTo(336, 126);
        armRight.curveTo(346, 126, 386, 126, 386, 136);
        armRight.lineTo(176, 386);
        armRight.curveTo(166, 386, 126, 386, 126, 376);
        armRight.lineTo(336, 126);
        armRight.closePath();

        LinearGradientPaint armRightPaint = new LinearGradientPaint(
            386f, 126f, 126f, 386f,
            new float[]{0.0f, 0.6f, 1.0f},
            new Color[]{new Color(30, 58, 138), new Color(29, 78, 216), new Color(14, 116, 144)}
        );
        g.setPaint(armRightPaint);
        g.fill(armRight);

        // 3. Stylized X - Left Arm (crossing diagonal)
        Path2D.Double armLeft = new Path2D.Double();
        armLeft.moveTo(126, 136);
        armLeft.curveTo(126, 126, 166, 126, 176, 126);
        armLeft.lineTo(386, 376);
        armLeft.curveTo(386, 386, 346, 386, 336, 386);
        armLeft.lineTo(126, 136);
        armLeft.closePath();

        LinearGradientPaint armLeftPaint = new LinearGradientPaint(
            126f, 126f, 386f, 386f,
            new float[]{0.0f, 0.4f, 1.0f},
            new Color[]{new Color(6, 182, 212), new Color(2, 132, 199), new Color(37, 99, 235)}
        );
        g.setPaint(armLeftPaint);
        g.fill(armLeft);

        // 4. Downward Download Arrow (integrated hero element)
        Path2D.Double arrow = new Path2D.Double();
        arrow.moveTo(232, 132);
        arrow.curveTo(232, 112, 280, 112, 280, 132);
        arrow.lineTo(280, 246);
        arrow.lineTo(344, 246);
        arrow.lineTo(256, 396);
        arrow.lineTo(168, 246);
        arrow.lineTo(232, 246);
        arrow.closePath();

        // Shadow behind arrow for 3D elevation
        g.setColor(new Color(0, 0, 0, 110));
        AffineTransform tx = AffineTransform.getTranslateInstance(0, 5);
        g.fill(tx.createTransformedShape(arrow));

        // Arrow gradient fill
        LinearGradientPaint arrowPaint = new LinearGradientPaint(
            256f, 112f, 256f, 396f,
            new float[]{0.0f, 0.25f, 0.65f, 1.0f},
            new Color[]{new Color(255, 255, 255), new Color(186, 230, 253), new Color(56, 189, 248), new Color(2, 132, 199)}
        );
        g.setPaint(arrowPaint);
        g.fill(arrow);

        // Crisp highlight stroke on arrow
        g.setPaint(new Color(255, 255, 255, 140));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(arrow);

        g.dispose();
        return img;
    }

    public static BufferedImage renderMacTrayGlyph(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double scale = size / 512.0;
        g.scale(scale, scale);

        // Black monochrome silhouette of the X-Arrow glyph
        g.setColor(new Color(0, 0, 0, 220));

        // Right Arm
        Path2D.Double armRight = new Path2D.Double();
        armRight.moveTo(336, 126);
        armRight.curveTo(346, 126, 386, 126, 386, 136);
        armRight.lineTo(176, 386);
        armRight.curveTo(166, 386, 126, 386, 126, 376);
        armRight.lineTo(336, 126);
        armRight.closePath();
        g.fill(armRight);

        // Left Arm
        Path2D.Double armLeft = new Path2D.Double();
        armLeft.moveTo(126, 136);
        armLeft.curveTo(126, 126, 166, 126, 176, 126);
        armLeft.lineTo(386, 376);
        armLeft.curveTo(386, 386, 346, 386, 336, 386);
        armLeft.lineTo(126, 136);
        armLeft.closePath();
        g.fill(armLeft);

        // Center Arrow
        Path2D.Double arrow = new Path2D.Double();
        arrow.moveTo(232, 132);
        arrow.curveTo(232, 112, 280, 112, 280, 132);
        arrow.lineTo(280, 246);
        arrow.lineTo(344, 246);
        arrow.lineTo(256, 396);
        arrow.lineTo(168, 246);
        arrow.lineTo(232, 246);
        arrow.closePath();
        g.fill(arrow);

        g.dispose();
        return img;
    }

    public static BufferedImage scaleImage(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Crops transparent borders to square edge-to-edge content (as UiHelper.kt does for Windows icons). */
    public static BufferedImage cropToArtwork(BufferedImage src) {
        int minX = src.getWidth(), minY = src.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                if (((src.getRGB(x, y) >>> 24)) > 16) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) return src;
        int side = Math.max(maxX - minX, maxY - minY) + 1;
        int left = Math.max(0, Math.min(src.getWidth() - side, (minX + maxX + 1 - side) / 2));
        int top = Math.max(0, Math.min(src.getHeight() - side, (minY + maxY + 1 - side) / 2));
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src.getSubimage(left, top, side, side), 0, 0, null);
        g.dispose();
        return out;
    }

    public static void writeIco(File icoFile, BufferedImage croppedMaster, BufferedImage uncropped16) throws Exception {
        int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 256};
        List<byte[]> entries = new ArrayList<>();
        for (int size : SIZES) {
            BufferedImage image = (size <= 16) ? uncropped16 : scaleImage(croppedMaster, size, size);
            entries.add(size >= 256 ? png(image) : bmp(image));
        }

        ByteBuffer header = ByteBuffer.allocate(6 + 16 * SIZES.length).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0).putShort((short) 1).putShort((short) SIZES.length);
        int offset = header.capacity();
        for (int i = 0; i < SIZES.length; i++) {
            byte side = (byte) (SIZES[i] >= 256 ? 0 : SIZES[i]);
            header.put(side).put(side).put((byte) 0).put((byte) 0)
                  .putShort((short) 1).putShort((short) 32)
                  .putInt(entries.get(i).length).putInt(offset);
            offset += entries.get(i).length;
        }

        try (FileOutputStream out = new FileOutputStream(icoFile)) {
            out.write(header.array());
            for (byte[] entry : entries) out.write(entry);
        }
        System.out.println("Wrote " + icoFile + " (" + offset + " bytes)");
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static byte[] bmp(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int maskRow = ((w + 31) / 32) * 4;
        int pixels = w * h * 4;
        ByteBuffer b = ByteBuffer.allocate(40 + pixels + maskRow * h).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(40).putInt(w).putInt(h * 2).putShort((short) 1).putShort((short) 32)
         .putInt(0).putInt(pixels + maskRow * h).putInt(0).putInt(0).putInt(0).putInt(0);
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                b.put((byte) argb).put((byte) (argb >> 8)).put((byte) (argb >> 16)).put((byte) (argb >>> 24));
            }
        }
        return b.array();
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.out.println("Generating XDM Enhanced brand icons...");

        BufferedImage master512 = renderMaster(512);
        BufferedImage croppedMaster = cropToArtwork(master512);

        // App resources
        File imgDir = new File("xdm-app/src/main/resources/images");
        imgDir.mkdirs();

        int[] appSizes = {16, 24, 32, 48, 64, 128, 256, 512};
        BufferedImage icon16 = null;
        for (int s : appSizes) {
            BufferedImage scaled = (s == 512) ? master512 : scaleImage(master512, s, s);
            if (s == 16) icon16 = scaled;
            File dest = new File(imgDir, "xdm-logo-" + s + ".png");
            ImageIO.write(scaled, "png", dest);
            System.out.println("Wrote " + dest);
        }

        // Mac tray monochrome icons
        int[] traySizes = {32, 44, 64, 88};
        for (int s : traySizes) {
            BufferedImage tray = renderMacTrayGlyph(s);
            File dest = new File(imgDir, "xdm-tray-mac-" + s + ".png");
            ImageIO.write(tray, "png", dest);
            System.out.println("Wrote " + dest);
        }

        // Browser extensions
        File chromeDir = new File("browser-extension/chrome-extension");
        File ffDir = new File("browser-extension/firefox-extension");
        for (File dir : new File[]{chromeDir, ffDir}) {
            if (dir.exists()) {
                for (int s : new int[]{16, 48, 128}) {
                    BufferedImage scaled = scaleImage(master512, s, s);
                    ImageIO.write(scaled, "png", new File(dir, "icon" + s + ".png"));
                }
            }
        }

        // Packaging ICO
        File icoFile = new File("packaging/icons/xdm.ico");
        writeIco(icoFile, croppedMaster, icon16);

        System.out.println("All branding icon variants generated successfully.");
    }
}

