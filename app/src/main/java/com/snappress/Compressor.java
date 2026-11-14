package com.snappress;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public final class Compressor {

    public static final long TARGET_BYTES = 300 * 1024;

    private static final int MAX_QUALITY = 92;
    private static final int MIN_QUALITY = 40;
    private static final int MIN_DIMENSION = 1280;

    public static class Result {
        public final byte[] data;
        public final int width;
        public final int height;
        public final int quality;

        Result(byte[] data, int width, int height, int quality) {
            this.data = data;
            this.width = width;
            this.height = height;
            this.quality = quality;
        }
    }

    private Compressor() {
    }

    public static Bitmap centerCrop34(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int targetW;
        int targetH;
        if (w * 4 > h * 3) {
            targetH = h;
            targetW = h * 3 / 4;
        } else {
            targetW = w;
            targetH = w * 4 / 3;
        }
        if (targetW == w && targetH == h) {
            return src;
        }
        int x = (w - targetW) / 2;
        int y = (h - targetH) / 2;
        return Bitmap.createBitmap(src, x, y, targetW, targetH);
    }

    public static Result compress(Bitmap src, long targetBytes) {
        Bitmap bmp = src;
        int quality = MAX_QUALITY;

        while (true) {
            int found = -1;
            int lo = MIN_QUALITY;
            int hi = MAX_QUALITY;
            while (lo <= hi) {
                int mid = (lo + hi) / 2;
                byte[] candidate = encode(bmp, mid);
                if (candidate.length <= targetBytes) {
                    found = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }

            if (found >= 0) {
                return new Result(encode(bmp, found), bmp.getWidth(), bmp.getHeight(), found);
            }

            int longest = Math.max(bmp.getWidth(), bmp.getHeight());
            if (longest / 2 < MIN_DIMENSION) {
                return new Result(encode(bmp, MIN_QUALITY), bmp.getWidth(), bmp.getHeight(), MIN_QUALITY);
            }

            int newW = Math.max(1, bmp.getWidth() * 3 / 4);
            int newH = Math.max(1, bmp.getHeight() * 3 / 4);
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, newW, newH, true);
            if (scaled != bmp && bmp != src) {
                bmp.recycle();
            }
            bmp = scaled;
        }
    }

    public static Bitmap stampDate(Bitmap src, String dateText) {
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        if (out == null) {
            return src;
        }
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        float textSize = Math.max(28f, out.getWidth() * 0.035f);
        paint.setTextSize(textSize);
        paint.setShadowLayer(textSize / 8f, textSize / 16f, textSize / 16f, Color.BLACK);
        float margin = out.getWidth() * 0.03f;
        float x = out.getWidth() - margin - paint.measureText(dateText);
        float y = out.getHeight() - margin;
        canvas.drawText(dateText, x, y, paint);
        return out;
    }

    private static byte[] encode(Bitmap bmp, int quality) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
    }
}
