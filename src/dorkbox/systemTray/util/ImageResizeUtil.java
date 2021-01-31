/*
 * Copyright 2016 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.systemTray.util;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import dorkbox.systemTray.SystemTray;
import dorkbox.util.CacheUtil;
import dorkbox.util.IO;
import dorkbox.util.ImageUtil;

public
class ImageResizeUtil {

    // - appIndicator/gtk require strings (which is the path)
    // - swing version loads as an image (which can be stream or path, we use path)
    private final CacheUtil cache;

    public ImageResizeUtil(CacheUtil cache) {
        this.cache = cache;
    }

    public
    File getTransparentImage() {
        // here, it doesn't matter what size the image is, as long as there is an image, the text in the menu will be shifted correctly
        // it is HIGHLY unlikely that the menu entry will be smaller than 4px.
        return getTransparentImage(4);
    }

    public
    File getTransparentImage(final int imageSize) {
        // NOTE: this does not need to be called on the EDT
        try {
            final File newFile = cache.create(imageSize + "_empty.png");
            return ImageUtil.createImage(imageSize, newFile, null);
        } catch (IOException e) {
            throw new RuntimeException("Unable to generate transparent image! Something is severely wrong!");
        }
    }

    public synchronized
    File getErrorImage(int size) {
        if (size == 0) {
            // default size
            size = 32;
        }

        try {
            InputStream imageStream = ImageResizeUtil.class.getResource("error_32.png").openStream();

            // have to resize the image to be whatever size we specify
            imageStream = makeByteArrayInputStream(imageStream);
            imageStream.mark(0);

            // check if we already have this file information saved to disk, based on size + hash of data
            final String cacheName = size + "_" + CacheUtil.createNameAsHash(imageStream);
            ((ByteArrayInputStream) imageStream).reset();  // casting to avoid unnecessary try/catch for IOException


            // if we already have this fileName, reuse it
            final File check = cache.check(cacheName);
            if (check != null) {
                return check;
            }

            // we have to hop through hoops.
            File resizedFile = resizeFileNoCheck(size, imageStream);

            // now cache that file
            return cache.save(cacheName, resizedFile);
        } catch (Exception e) {
            // this must be thrown
            throw new RuntimeException("Serious problems! Unable to extract error image, this should NEVER happen!", e);
        }
    }

    private
    File resizeAndCache(final int size, final File file) {
        return resizeAndCache(size, file.getAbsolutePath());
    }

    private
    File resizeAndCache(final int size, final String fileName) {
        if (fileName == null) {
            return null;
        }

        try {
            FileInputStream fileInputStream = new FileInputStream(fileName);
            File file = resizeAndCache(size, fileInputStream);
            fileInputStream.close();

            return file;
        } catch (Exception e) {
            // have to serve up the error image instead.
            SystemTray.logger.error("Error reading image. Using error icon instead", e);
            return getErrorImage(size);
        }
    }

    @SuppressWarnings("Duplicates")
    private synchronized
    File resizeAndCache(final int size, InputStream imageStream) {
        if (imageStream == null) {
            return null;
        }

        final String cacheName;

        // no cached file, so we resize then save the new one.
        boolean needsResize = true;
        try {
            imageStream = makeByteArrayInputStream(imageStream);
            imageStream.mark(0);

            // check if we already have this file information saved to disk, based on size + hash of data
            cacheName = size + "_" + CacheUtil.createNameAsHash(imageStream);
            ((ByteArrayInputStream) imageStream).reset();  // casting to avoid unnecessary try/catch for IOException


            // if we already have this fileName, reuse it
            final File check = cache.check(cacheName);
            if (check != null) {
                return check;
            }


            imageStream.mark(0);
            Dimension imageSize = ImageUtil.getImageSize(imageStream);
            //noinspection NumericCastThatLosesPrecision
            if (size == (int) imageSize.getHeight() && size == (int) imageSize.getWidth()) {
                // we can reuse this URL (it's the correct size).
                needsResize = false;
            }
        } catch (Exception e) {
            // have to serve up the error image instead.
            SystemTray.logger.error("Error getting image size. Using error icon instead", e);
            return getErrorImage(size);
        } finally {
            ((ByteArrayInputStream) imageStream).reset();  // casting to avoid unnecessary try/catch for IOException
        }



        if (needsResize) {
            // we have to hop through hoops.
            try {
                File resizedFile = resizeFileNoCheck(size, imageStream);

                // now cache that file
                try {
                    return cache.save(cacheName, resizedFile);
                } catch (Exception e) {
                    // have to serve up the error image instead.
                    SystemTray.logger.error("Error caching image. Using error icon instead", e);
                    return getErrorImage(size);
                }

            } catch (Exception e) {
                // have to serve up the error image instead.
                SystemTray.logger.error("Error resizing image. Using error icon instead", e);
                return getErrorImage(size);
            }

        } else {
            // no resize necessary, just cache as is.
            try {
                return cache.save(cacheName, imageStream);
            } catch (Exception e) {
                // have to serve up the error image instead.
                SystemTray.logger.error("Error caching image. Using error icon instead", e);
                return getErrorImage(size);
            }
        }
    }

    // if this input stream is NOT a ByteArrayInputStream, make it one.
    private static
    InputStream makeByteArrayInputStream(InputStream imageStream) throws IOException {
        if (!(imageStream instanceof ByteArrayInputStream)) {
            // have to make a copy of the inputStream, but only if necessary
            ByteArrayOutputStream byteArrayOutputStream = IO.copyStream(imageStream);
            imageStream.close();

            imageStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        }
        return imageStream;
    }


    /**
     * Resizes the given InputStream to the specified height. No checks are performed if it's the correct height to begin with.
     *
     * Additionally, the image is scaled to where it's largest dimension will always be <= to the size.
     *
     * @return the file on disk that is the resized icon
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private
    File resizeFileNoCheck(final int size, InputStream inputStream) throws IOException {
        // have to resize the file (and return the new path)

        File newFile = cache.create("temp_resize.png");
        // if it's already there, we have to delete it
        newFile.delete();

        Image image = ImageUtil.getImageImmediate(ImageIO.read(inputStream));

        BufferedImage bufferedImage = ImageUtil.getBufferedImage(image);

        // resize the image, keep aspect ratio
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        if (width > height) {
            bufferedImage = ImageUtil.resizeImage(bufferedImage, size, -1);
        }
        else {
            bufferedImage = ImageUtil.resizeImage(bufferedImage, -1, size);
        }

        // make the image "square" so there is padding on the sides that are smaller
        bufferedImage = ImageUtil.getSquareBufferedImage(bufferedImage);

        // now write out the new one
        ImageIO.write(bufferedImage, "png", newFile); // made up extension

        return newFile;
    }


    public
    File shouldResizeOrCache(final boolean isTrayImage, final File imageFile) {
        if (imageFile == null) {
            return null;
        }

        if (SystemTray.AUTO_SIZE) {
            return resizeAndCache(getSize(isTrayImage), imageFile);
        } else {
            return imageFile;
        }
    }


    public
    File shouldResizeOrCache(final boolean isTrayImage, final String imagePath) {
        if (imagePath == null) {
            return null;
        }

        if (SystemTray.AUTO_SIZE) {
            return resizeAndCache(getSize(isTrayImage), imagePath);
        } else {
            return new File(imagePath);
        }
    }

    public
    File shouldResizeOrCache(final boolean isTrayImage, final URL imageUrl) {
        if (imageUrl == null) {
            return null;
        }

        try {
            if (SystemTray.AUTO_SIZE) {
                InputStream inputStream = imageUrl.openStream();
                File file = resizeAndCache(getSize(isTrayImage), inputStream);
                inputStream.close();

                return file;
            } else {
                return cache.save(imageUrl);
            }
        } catch (Exception e) {
            // have to serve up the error image instead.
            SystemTray.logger.error("Error reading image. Using error icon instead", e);
            return getErrorImage(getSize(isTrayImage));
        }
    }

    public
    File shouldResizeOrCache(final boolean isTrayImage, final InputStream imageStream) {
        if (imageStream == null) {
            return null;
        }

        if (SystemTray.AUTO_SIZE) {
            return resizeAndCache(getSize(isTrayImage), imageStream);
        } else {
            try {
                return cache.save(imageStream);
            } catch (IOException e) {
                SystemTray.logger.error("Error checking cache for information. Using error icon instead", e);
                return getErrorImage(0);
            }
        }
    }


    public
    File shouldResizeOrCache(final boolean isTrayImage, final Image image) {
        if (image == null) {
            return null;
        }

        try {
            final Image trayImage =  ImageUtil.getImageImmediate(image);
            BufferedImage bufferedImage = ImageUtil.getBufferedImage(trayImage);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", os);
            InputStream imageInputStream = new ByteArrayInputStream(os.toByteArray());


            File file;
            if (SystemTray.AUTO_SIZE) {
                file = resizeAndCache(getSize(isTrayImage), imageInputStream);
            } else {
                file = cache.save(imageInputStream);
            }

            imageInputStream.close(); // ByteArrayOutputStream doesn't do anything, but here for completeness + documentation
            return file;
        } catch (Exception e) {
            // have to serve up the error image instead.
            SystemTray.logger.error("Error reading image. Using error icon instead", e);
            return getErrorImage(getSize(isTrayImage));
        }
    }

    public
    File shouldResizeOrCache(final boolean isTrayImage, final ImageInputStream imageStream) {
        if (imageStream == null) {
            return null;
        }

        try {
            ByteArrayOutputStream byteArrayOutputStream = IO.copyStream(imageStream);
            ByteArrayInputStream fileStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());

            if (SystemTray.AUTO_SIZE) {
                return resizeAndCache(getSize(isTrayImage), fileStream);
            } else {
                return cache.save(fileStream);
            }
        } catch (Exception e) {
            // have to serve up the error image instead.
            SystemTray.logger.error("Error reading image. Using error icon instead", e);
            return getErrorImage(getSize(isTrayImage));
        }
    }

    private static
    int getSize(final boolean isTrayImage) {
        int size;
        if (isTrayImage) {
            // system tray image
            size = SizeAndScalingUtil.TRAY_SIZE;
        } else {
            // menu image
            size = SizeAndScalingUtil.TRAY_MENU_SIZE;
        }

        return size;
    }
}
