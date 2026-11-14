package com.snappress;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.TextView;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_CAPTURE = 1;

    private Uri pendingUri;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        Button capture = findViewById(R.id.btn_capture);
        capture.setOnClickListener(v -> startCapture());
    }

    private void startCapture() {
        String name = "SnapPress_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + ".jpg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures");

        pendingUri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingUri);
        startActivityForResult(intent, REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) {
            return;
        }
        final Uri uri = pendingUri;
        pendingUri = null;
        if (uri == null) {
            return;
        }
        if (resultCode != RESULT_OK) {
            getContentResolver().delete(uri, null, null);
            status.setText("Geen foto gemaakt.");
            return;
        }

        status.setText("Foto verwerken…");
        executor.execute(() -> processPhoto(uri));
    }

    private void processPhoto(Uri uri) {
        try {
            byte[] original = readAll(uri);
            Bitmap bitmap = decodeOriented(original);
            if (bitmap == null) {
                publish(uri, null, "Kon de foto niet lezen.");
                return;
            }

            Bitmap cropped = Compressor.centerCrop34(bitmap);
            if (cropped != bitmap) {
                bitmap.recycle();
            }

            Compressor.Result result = Compressor.compress(cropped, Compressor.TARGET_BYTES);

            try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                os.write(result.data);
            }

            ContentValues update = new ContentValues();
            update.put(MediaStore.Images.Media.IS_PENDING, 0);
            update.put(MediaStore.Images.Media.WIDTH, result.width);
            update.put(MediaStore.Images.Media.HEIGHT, result.height);
            getContentResolver().update(uri, update, null, null);

            publish(uri, result,
                    "Opgeslagen in Afbeeldingen (Pictures)\n"
                            + formatBytes(original.length) + " → " + formatBytes(result.data.length)
                            + " • " + result.width + "×" + result.height
                            + " • kwaliteit " + result.quality);
        } catch (Exception e) {
            publish(uri, null, "Fout: " + e.getMessage());
        }
    }

    private Bitmap decodeOriented(byte[] bytes) {
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bmp == null) {
            return null;
        }
        int rotation = 0;
        try {
            ExifInterface exif = new ExifInterface(new java.io.ByteArrayInputStream(bytes));
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotation = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotation = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotation = 270;
                    break;
                default:
                    rotation = 0;
            }
        } catch (IOException ignored) {
        }
        if (rotation == 0) {
            return bmp;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(
                bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        if (rotated != bmp) {
            bmp.recycle();
        }
        return rotated;
    }

    private void publish(Uri uri, Compressor.Result result, String message) {
        if (result == null) {
            try {
                getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {
            }
        }
        runOnUiThread(() -> status.setText(message));
    }

    private byte[] readAll(Uri uri) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("Kan bestand niet openen");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024 * 1024) {
            return (bytes + 512) / 1024 + " kB";
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
