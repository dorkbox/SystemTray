package dorkbox.systemTray;

import dorkbox.util.LocationResolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 *
 */
public
class ImageUtil {

    private static final File TEMP_DIR = new File(System.getProperty("java.io.tmpdir"));

    protected static final Charset UTF_8 = Charset.forName("UTF-8");
    static MessageDigest digest;

    private static final Map<String, String> resourceToFilePath = new HashMap<String, String>();
    private static final long runtimeRandom = new SecureRandom().nextLong();

    /**
     *  appIndicator/gtk require strings (which is the path)
     *  swing version loads as an image (which can be stream or path, we use path)
     */
    public static synchronized
    String iconPath(String fileName) throws IOException {
        // if we already have this fileName, reuse it
        final String cachedFile = resourceToFilePath.get(fileName);
        if (cachedFile != null) {
            return cachedFile;
        }

        // is file sitting on drive
        File iconTest = new File(fileName);
        if (iconTest.isFile() && iconTest.canRead()) {
            final String absolutePath = iconTest.getAbsolutePath();

            resourceToFilePath.put(fileName, absolutePath);
            return absolutePath;
        }
        else {
            // suck it out of a URL/Resource (with debugging if necessary)
            final URL systemResource = LocationResolver.getResource(fileName);
            final String filePath = makeFileViaUrl(systemResource);
            resourceToFilePath.put(fileName, filePath);
            return filePath;
        }
    }

    /**
     *  appIndicator/gtk require strings (which is the path)
     *  swing version loads as an image (which can be stream or path, we use path)
     */
    public static synchronized
    String iconPath(final URL fileResource) throws IOException {
        // if we already have this fileName, reuse it
        final String cachedFile = resourceToFilePath.get(fileResource.getPath());
        if (cachedFile != null) {
            return cachedFile;
        }

        final String filePath = makeFileViaUrl(fileResource);
        resourceToFilePath.put(fileResource.getPath(), filePath);
        return filePath;
    }


    /**
     *  appIndicator/gtk require strings (which is the path)
     *  swing version loads as an image (which can be stream or path, we use path)
     */
    public static synchronized
    String iconPath(final String cacheName, final InputStream fileStream) throws IOException {
        // if we already have this fileName, reuse it
        final String cachedFile = resourceToFilePath.get(cacheName);
        if (cachedFile != null) {
            return cachedFile;
        }

        final String filePath = makeFileViaStream(cacheName, fileStream);
        resourceToFilePath.put(cacheName, filePath);
        return filePath;
    }

    /**
     * NO CACHING OF INPUTSTREAM!
     *
     *  appIndicator/gtk require strings (which is the path)
     *  swing version loads as an image (which can be stream or path, we use path)
     */
    @Deprecated
    public static synchronized
    String iconPathNoCache(final InputStream fileStream) throws IOException {
        return makeFileViaStream(Long.toString(System.currentTimeMillis()), fileStream);
    }


    /**
     * @param resourceUrl the url to copy to a file on disk
     * @return the full path of the resource copied to disk, or null if invalid
     */
    private static
    String makeFileViaUrl(final URL resourceUrl) throws IOException {
        if (resourceUrl == null) {
            throw new IOException("resourceUrl is null");
        }

        InputStream inStream = resourceUrl.openStream();

        // suck it out of a URL/Resource (with debugging if necessary)
        String cacheName = resourceUrl.getPath();
        return makeFileViaStream(cacheName, inStream);
    }

    /**
     * @param cacheName needs name+extension for the resource
     * @param resourceStream the resource to copy to a file on disk
     *
     * @return the full path of the resource copied to disk, or null if invalid
     */
    private static
    String makeFileViaStream(final String cacheName, final InputStream resourceStream) throws IOException {
        if (cacheName == null) {
            throw new IOException("cacheName is null");
        }
        if (resourceStream == null) {
            throw new IOException("resourceStream is null");
        }

        // figure out the fileName
        byte[] bytes = cacheName.getBytes(UTF_8);
        File newFile;

        // can be wimpy, only one at a time
        String hash = hashName(bytes);

        String extension = "";
        int dot = cacheName.lastIndexOf('.');
        if (dot > -1) {
            extension = cacheName.substring(dot + 1);
        }

        newFile = new File(TEMP_DIR, "SYSTRAY_" + hash + '.' + extension).getAbsoluteFile();
        if (SystemTray.isKDE) {
            // KDE is unique per run, so this prevents buildup
            newFile.deleteOnExit();
        }

        // copy out to a temp file, as a hash of the file name

        OutputStream outStream = null;
        try {
            outStream = new FileOutputStream(newFile);

            byte[] buffer = new byte[2048];
            int read;
            while ((read = resourceStream.read(buffer)) > 0) {
                outStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            // Send up exception
            String message = "Unable to copy icon '" + cacheName + "' to temporary location: '" + newFile.getAbsolutePath() + "'";
            SystemTray.logger.error(message, e);
            throw new IOException(message);
        } finally {
            try {
                resourceStream.close();
            } catch (Exception ignored) {
            }
            try {
                if (outStream != null) {
                    outStream.close();
                }
            } catch (Exception ignored) {
            }
        }

        return newFile.getAbsolutePath();
    }

    private static
    String hashName(byte[] nameChars) {
        digest.reset();
        digest.update(nameChars);

        // For KDE4, it must also be unique across runs
        if (SystemTray.isKDE) {
            byte[] longBytes = new byte[8];
            ByteBuffer wrap = ByteBuffer.wrap(longBytes);
            wrap.putLong(runtimeRandom);
            digest.update(longBytes);
        }

        // convert to alpha-numeric. see https://stackoverflow.com/questions/29183818/why-use-tostring32-and-not-tostring36
        return new BigInteger(1, digest.digest()).toString(32).toUpperCase(Locale.US);
    }

    public static
    void init() throws NoSuchAlgorithmException {
        ImageUtil.digest = MessageDigest.getInstance("MD5");
    }
}
