package com.xiaomi.push.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/* loaded from: classes.dex */
public class MyNotificationIconHelper {
    public static final int KiB = 1024;
    public static final int MiB = 1024 * KiB;

    private static final int CONNECT_TIMEOUT = 8000;
    private static final int READ_TIMEOUT = 20000;
    private static final int READ_UNIT = 1024;
    private static final int STANDARD_DENSITY = 160;
    private static final int STANDARD_ICON_SIZE = 48;
    private static final int MAX_DECODED_DIMENSION = 2048;
    private static final long MAX_DECODED_PIXELS = 1024L * 1024L;

    /* loaded from: classes.dex */
    public static class GetIconResult {
        public Bitmap bitmap;
        public long downloadSize;

        public GetIconResult(Bitmap bitmap, long downloadSize) {
            this.bitmap = bitmap;
            this.downloadSize = downloadSize;
        }
    }

    public static GetIconResult getIconFromUrl(Context context, String urlStr, int maxDownloadBytes) {
        InputStream isForBitmapSize = null;
        GetIconResult result = new GetIconResult(null, 0L);
        try {
            GetDataResult getDataResult = getDataFromUrl(urlStr, maxDownloadBytes);
            if (getDataResult != null) {
                result.downloadSize = getDataResult.downloadSize;
                byte[] data = getDataResult.data;
                if (data != null) {
                    InputStream isForBitmapSize2 = new ByteArrayInputStream(data);
                    try {
                        int sampleSize = getSampleSize(context, isForBitmapSize2);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = sampleSize;
                        result.bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                        isForBitmapSize = isForBitmapSize2;
                    } catch (Exception e) {
                        isForBitmapSize = isForBitmapSize2;
                        MyLog.e(e);
                        IOUtils.closeQuietly(isForBitmapSize);
                        return result;
                    } catch (Throwable th) {
                        isForBitmapSize = isForBitmapSize2;
                        IOUtils.closeQuietly(isForBitmapSize);
                        throw th;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        IOUtils.closeQuietly(isForBitmapSize);
        return result;
    }

    /* loaded from: classes.dex */
    public static class GetDataResult {
        byte[] data;
        int downloadSize;

        public GetDataResult(byte[] data, int downloadSize) {
            this.data = data;
            this.downloadSize = downloadSize;
        }
    }

    private static GetDataResult getDataFromUrl(String urlStr, int maxDownloadBytes) {
        HttpURLConnection conn = null;
        InputStream inputStream = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.connect();
            int contentLen = conn.getContentLength();
            if (contentLen > maxDownloadBytes) {
                MyLog.w("Bitmap size is too big, max size is " + maxDownloadBytes + "  contentLen size is " + contentLen + " from url " + urlStr);
                return null;
            }
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                MyLog.w("Invalid Http Response Code " + responseCode + " received");
                return null;
            }
            inputStream = conn.getInputStream();
            ByteArrayOutputStream tempOutStream = new ByteArrayOutputStream();
            int availableSpace = maxDownloadBytes;
            byte[] dataUnit = new byte[READ_UNIT];
            while (availableSpace > 0) {
                int readCount = inputStream.read(dataUnit, 0, Math.min(READ_UNIT, availableSpace));
                if (readCount == -1) {
                    break;
                }
                availableSpace -= readCount;
                tempOutStream.write(dataUnit, 0, readCount);
            }
            if (availableSpace <= 0 && inputStream.read() != -1) {
                MyLog.w("length " + maxDownloadBytes + " exhausted.");
                return new GetDataResult(null, maxDownloadBytes);
            }
            byte[] data = tempOutStream.toByteArray();
            return new GetDataResult(data, data.length);
        } catch (Throwable e) {
            MyLog.e(e);
            return null;
        } finally {
            IOUtils.closeQuietly(inputStream);
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static Bitmap getIconFromUri(Context context, String uriStr) {
        Bitmap bitmap = null;
        Uri uri = Uri.parse(uriStr);
        InputStream is = null;
        InputStream isForBitmapSize = null;
        try {
            try {
                isForBitmapSize = context.getContentResolver().openInputStream(uri);
                int sampleSize = getSampleSize(context, isForBitmapSize);
                is = context.getContentResolver().openInputStream(uri);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                bitmap = BitmapFactory.decodeStream(is, null, options);
                IOUtils.closeQuietly(is);
            } catch (IOException e) {
                MyLog.e(e);
                IOUtils.closeQuietly(is);
            }
            IOUtils.closeQuietly(isForBitmapSize);
            return bitmap;
        } catch (Throwable th) {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(isForBitmapSize);
            throw th;
        }
    }

    private static int getSampleSize(Context context, InputStream inputStream) {
        if (inputStream == null) {
            return 1;
        }
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, opt);
        if (opt.outWidth == -1 || opt.outHeight == -1) {
            MyLog.w("decode dimension failed for bitmap.");
            return 1;
        }
        int screenDensity = context.getResources().getDisplayMetrics().densityDpi;
        int targetWidth = Math.max(1,
                Math.round((screenDensity / (float) STANDARD_DENSITY) * STANDARD_ICON_SIZE));
        return calculateSampleSize(opt.outWidth, opt.outHeight, targetWidth);
    }

    static int calculateSampleSize(int width, int height, int targetWidth) {
        if (width <= 0 || height <= 0 || targetWidth <= 0) {
            return 1;
        }
        int sampleSize = 1;
        if (width > targetWidth && height > targetWidth) {
            int requested = Math.max(1, Math.min(width / targetWidth, height / targetWidth));
            sampleSize = Integer.highestOneBit(requested);
        }
        while (decodedDimension(width, sampleSize) > MAX_DECODED_DIMENSION
                || decodedDimension(height, sampleSize) > MAX_DECODED_DIMENSION
                || decodedPixels(width, height, sampleSize) > MAX_DECODED_PIXELS) {
            if (sampleSize > Integer.MAX_VALUE / 2) return Integer.MAX_VALUE;
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private static int decodedDimension(int size, int sampleSize) {
        return (size + sampleSize - 1) / sampleSize;
    }

    private static long decodedPixels(int width, int height, int sampleSize) {
        return (long) decodedDimension(width, sampleSize) * decodedDimension(height, sampleSize);
    }
}