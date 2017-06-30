package dorkbox.systemTray.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import dorkbox.systemTray.SystemTray;
import dorkbox.util.CacheUtil;

public class HeavyCheckMark {
    // this is a slight adjustment from the original size of the SVG image this came from, translated via Flamingo
    private static final double SVG_ORIG_SIZE = 226.0D;

    // if you change how this mark is drawn, increment the version so that the cached file is correctly regenerated.
    private static final int VERSION = 1;

    /**
     * This saves a scalable CheckMark to a correctly sized PNG file.
     *
     * @param color the color of the CheckMark
     * @param checkMarkSize the size of the CheckMark inside the image. (does not include padding)
     *
     * @param paddingTop amount of padding to apply to the top edge of the icon.
     * @param paddingLeft amount of padding to apply to the left edge of the icon.
     * @param paddingBottom amount of padding to apply to the bottom edge of the icon.
     * @param paddingRight amount of padding to apply to the right edge of the icon.
     */
    public static
    String getFile(Color color, int checkMarkSize, int paddingTop, int paddingLeft , int paddingBottom, int paddingRight) {

        //noinspection StringBufferReplaceableByString
        String name = new StringBuilder().append(checkMarkSize)
                                         .append(paddingTop)
                                         .append(paddingBottom)
                                         .append(paddingLeft)
                                         .append(paddingRight)
                                         .append("_checkMark_")
                                         .append(HeavyCheckMark.VERSION)
                                         .append("_")
                                         .append(color.getRGB())
                                         .append(".png")
                                         .toString();

        final File newFile = CacheUtil.create(name);
        if (newFile.canRead() || newFile.length() == 0) {
            try {
                BufferedImage img = HeavyCheckMark.draw(color, checkMarkSize, paddingTop, paddingLeft, paddingBottom, paddingRight);
                ImageIO.write(img, "png", newFile);
            } catch (Exception e) {
                SystemTray.logger.error("Error creating check-mark image.", e);
            }
        }

        return newFile.getAbsolutePath();
    }

    private static
    BufferedImage draw(final Color color, final int checkMarkSize, final int paddingTop, final int paddingLeft, int paddingBottom, int paddingRight) {
        int sizeX = checkMarkSize + paddingLeft + paddingRight;
        int sizeY = checkMarkSize + paddingTop + paddingBottom;

        double scaleX = checkMarkSize / SVG_ORIG_SIZE;
        double scaleY = checkMarkSize / SVG_ORIG_SIZE;

        BufferedImage img = new BufferedImage(sizeX, sizeY, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        AffineTransform at = new AffineTransform();
        at.translate(paddingLeft, paddingTop);
        at.scale(scaleX, scaleY);
        g2d.setTransform(at);


        GeneralPath shape = new GeneralPath();

        shape.moveTo(70.98225, 156.26561);
        shape.quadTo(81.24785, 134.75002, 95.45094, 109.01561);
        shape.quadTo(109.654144, 83.28121, 127.58374, 57.68751);
        shape.quadTo(145.51344, 32.09381, 153.74004, 23.726612);
        shape.quadTo(161.96654, 15.359412, 167.66194, 10.437512);
        shape.quadTo(173.35724, 5.5156126, 187.84154, 3.2656126);
        shape.quadTo(202.32594, 1.0156126, 209.77914, 1.0156126);
        shape.quadTo(214.70094, 1.0156126, 217.86505, 4.0391126);
        shape.quadTo(221.02914, 7.0625124, 221.02914, 11.984413);
        shape.quadTo(221.02914, 15.781213, 219.20094, 18.523413);
        shape.quadTo(217.37285, 21.265614, 211.60724, 26.750011);
        shape.quadTo(183.90414, 52.20311, 153.95094, 99.312515);
        shape.quadTo(123.99784, 146.42192, 106.41974, 190.57812);
        shape.quadTo(98.12284, 210.82812, 95.73224, 214.34383);
        shape.quadTo(93.34154, 217.85942, 88.41974, 220.88283);
        shape.quadTo(83.49784, 223.90623, 68.16974, 223.90623);
        shape.quadTo(56.07594, 223.90623, 52.70094, 222.42973);
        shape.quadTo(49.32594, 220.95314, 47.49784, 219.26564);
        shape.quadTo(45.669743, 217.57814, 37.09154, 204.64064);
        shape.quadTo(28.232243, 191.00005, 11.357241, 172.43755);
        shape.quadTo(1.6541405, 161.75005, 1.6541405, 154.57814);
        shape.quadTo(1.6541405, 144.17194, 12.69314, 136.43755);
        shape.quadTo(23.732239, 128.70314, 32.16974, 128.70314);
        shape.quadTo(41.45094, 128.70314, 51.99784, 136.22664);
        shape.quadTo(62.54474, 143.75005, 70.98224, 156.26564);
        shape.closePath();

        shape.closePath();

        g2d.setPaint(color);
        g2d.fill(shape);
        g2d.dispose();

        return img;
    }
}

