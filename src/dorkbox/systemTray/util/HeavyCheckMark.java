package dorkbox.systemTray.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;

public class HeavyCheckMark {
    // if you change how this mark is drawn, increment the version so that the cached file is correctly regenerated.
    public static int VERSION = 1;

    public static
    BufferedImage draw(final Font fontForSpecificHeight, final Color color) {
        // Because font metrics is based on a graphics context, we need to create a small, temporary image to determine the width and height
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setFont(fontForSpecificHeight);

        FontMetrics fm = g2d.getFontMetrics();
        int width = fm.stringWidth("X");
        int height = fm.getHeight();
        g2d.dispose();

        // make it square
        if (width > height) {
            height = width;
        }

        double adjustment = 1.5; // empirical testing, this is the best size adjustment

        int setSize = 256; // this is the size the shape is drawn at
        int wantedSize = 16; // this is the target size, adjusted via 'adjustment'
        double ratio = 1.0 / (setSize / wantedSize) * adjustment;

        System.err.println("ratio " + ratio);

        img = new BufferedImage(height, height, BufferedImage.TYPE_INT_ARGB);
        g2d = img.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        AffineTransform at = new AffineTransform();
        double xAdjustment = (height / 2.0) - (HeavyCheckMark.width() * ratio) / 2.0;
        double yAdjustment = -((((y() * adjustment) - y()) + ((setSize-height()) * ratio / 2.0)) * ratio);

        at.translate(xAdjustment, yAdjustment);
        g2d.setTransform(at);


        GeneralPath shape = new GeneralPath();

        shape.moveTo(79.1719 * ratio, 320.7656 * ratio);
        shape.quadTo(89.4375 * ratio, 299.25 * ratio, 103.6406 * ratio, 273.5156 * ratio);
        shape.quadTo(117.8438 * ratio, 247.7812 * ratio, 135.7734 * ratio, 222.1875 * ratio);
        shape.quadTo(153.7031 * ratio, 196.5938 * ratio, 161.9297 * ratio, 188.2266 * ratio);
        shape.quadTo(170.1562 * ratio, 179.8594 * ratio, 175.8516 * ratio, 174.9375 * ratio);
        shape.quadTo(181.5469 * ratio, 170.0156 * ratio, 196.0312 * ratio, 167.7656 * ratio);
        shape.quadTo(210.5156 * ratio, 165.5156 * ratio, 217.9688 * ratio, 165.5156 * ratio);
        shape.quadTo(222.8906 * ratio, 165.5156 * ratio, 226.0547 * ratio, 168.5391 * ratio);
        shape.quadTo(229.2188 * ratio, 171.5625 * ratio, 229.2188 * ratio, 176.4844 * ratio);
        shape.quadTo(229.2188 * ratio, 180.2812 * ratio, 227.3906 * ratio, 183.0234 * ratio);
        shape.quadTo(225.5625 * ratio, 185.7656 * ratio, 219.7969 * ratio, 191.25 * ratio);
        shape.quadTo(192.0938 * ratio, 216.7031 * ratio, 162.1406 * ratio, 263.8125 * ratio);
        shape.quadTo(132.1875 * ratio, 310.9219 * ratio, 114.6094 * ratio, 355.0781 * ratio);
        shape.quadTo(106.3125 * ratio, 375.3281 * ratio, 103.9219 * ratio, 378.8438 * ratio);
        shape.quadTo(101.5312 * ratio, 382.3594 * ratio, 96.6094 * ratio, 385.3828 * ratio);
        shape.quadTo(91.6875 * ratio, 388.4062 * ratio, 76.3594 * ratio, 388.4062 * ratio);
        shape.quadTo(64.2656 * ratio, 388.4062 * ratio, 60.8906 * ratio, 386.9297 * ratio);
        shape.quadTo(57.5156 * ratio, 385.4531 * ratio, 55.6875 * ratio, 383.7656 * ratio);
        shape.quadTo(53.8594 * ratio, 382.0781 * ratio, 45.2812 * ratio, 369.1406 * ratio);
        shape.quadTo(36.4219 * ratio, 355.5 * ratio, 19.5469 * ratio, 336.9375 * ratio);
        shape.quadTo(9.8438 * ratio, 326.25 * ratio, 9.8438 * ratio, 319.0781 * ratio);
        shape.quadTo(9.8438 * ratio, 308.6719 * ratio, 20.8828 * ratio, 300.9375 * ratio);
        shape.quadTo(31.9219 * ratio, 293.2031 * ratio, 40.3594 * ratio, 293.2031 * ratio);
        shape.quadTo(49.6406 * ratio, 293.2031 * ratio, 60.1875 * ratio, 300.7266 * ratio);
        shape.quadTo(70.7344 * ratio, 308.25 * ratio, 79.1719 * ratio, 320.7656 * ratio);
        shape.closePath();

        g2d.setPaint(color);
        g2d.fill(shape);
        g2d.dispose();

        return img;
    }

    /**
     * Returns the width of the checkmark
     */
    private static int width() {
        return 219;
    }

    /**
     * Returns the height of the checkmark
     */
    private static int height() {
        return 222;
    }

    /**
     * Returns the X of the checkmark
     */
    private static int x() {
        return 10;
    }

    /**
     * Returns the Y of the checkmark
     */
    private static int y() {
        return 166;
    }
}

