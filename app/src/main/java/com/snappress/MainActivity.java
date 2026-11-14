package com.snappress;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.database.Cursor;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 10;

    private PreviewView previewView;
    private View focusRing;
    private TextView status;
    private Button flashButton;

    private ImageCapture imageCapture;
    private Camera camera;
    private int flashMode = ImageCapture.FLASH_MODE_AUTO;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ScaleGestureDetector scaleDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.preview);
        focusRing = findViewById(R.id.focus_ring);
        status = findViewById(R.id.status);
        flashButton = findViewById(R.id.btn_flash);

        flashButton.setOnClickListener(v -> cycleFlash());
        findViewById(R.id.btn_shutter).setOnClickListener(v -> takePhoto());

        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector detector) {
                        if (camera == null) {
                            return false;
                        }
                        ZoomState state = camera.getCameraInfo().getZoomState().getValue();
                        float current = state != null ? state.getZoomRatio() : 1f;
                        camera.getCameraControl().setZoomRatio(current * detector.getScaleFactor());
                        return true;
                    }
                });

        previewView.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            if (!scaleDetector.isInProgress() && event.getAction() == MotionEvent.ACTION_UP) {
                handleTap(event.getX(), event.getY());
            }
            return true;
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                status.setText("Cameratoegang geweigerd. Sta toegang toe in de instellingen.");
            }
        }
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider provider = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();

                provider.unbindAll();
                camera = provider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) {
                status.setText("Camera kon niet worden gestart: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void handleTap(float x, float y) {
        if (camera == null) {
            return;
        }
        MeteringPoint point = previewView.getMeteringPointFactory().createPoint(x, y);
        camera.getCameraControl().startFocusAndMetering(
                new FocusMeteringAction.Builder(point,
                        FocusMeteringAction.FLAG_AF
                                | FocusMeteringAction.FLAG_AE
                                | FocusMeteringAction.FLAG_AWB)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build());
        showFocusRing(x, y);
    }

    private void showFocusRing(float x, float y) {
        focusRing.animate().cancel();
        focusRing.setAlpha(1f);
        focusRing.setX(x - focusRing.getWidth() / 2f);
        focusRing.setY(y - focusRing.getHeight() / 2f);
        focusRing.setVisibility(View.VISIBLE);
        focusRing.setScaleX(1.5f);
        focusRing.setScaleY(1.5f);
        focusRing.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        focusRing.animate().alpha(0f).setStartDelay(2000).setDuration(300).start();
    }

    private void cycleFlash() {
        switch (flashMode) {
            case ImageCapture.FLASH_MODE_AUTO:
                flashMode = ImageCapture.FLASH_MODE_ON;
                flashButton.setText("Flits: aan");
                break;
            case ImageCapture.FLASH_MODE_ON:
                flashMode = ImageCapture.FLASH_MODE_OFF;
                flashButton.setText("Flits: uit");
                break;
            default:
                flashMode = ImageCapture.FLASH_MODE_AUTO;
                flashButton.setText("Flits: auto");
        }
        if (imageCapture != null) {
            imageCapture.setFlashMode(flashMode);
        }
    }

    private void takePhoto() {
        if (imageCapture == null) {
            status.setText("Camera is nog niet klaar.");
            return;
        }
        status.setText("Foto maken…");
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        byte[] jpeg = imageProxyToBytes(image);
                        int rotation = image.getImageInfo().getRotationDegrees();
                        image.close();
                        status.setText("Verwerken…");
                        executor.execute(() -> processPhoto(jpeg, rotation));
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        status.setText("Fout bij foto: " + e.getMessage());
                    }
                });
    }

    private byte[] imageProxyToBytes(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private void processPhoto(byte[] jpeg, int rotation) {
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            if (bitmap == null) {
                publishStatus("Kon de foto niet lezen.");
                return;
            }

            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) {
                    bitmap.recycle();
                }
                bitmap = rotated;
            }

            Bitmap cropped = Compressor.centerCrop34(bitmap);
            if (cropped != bitmap) {
                bitmap.recycle();
            }

            String dateText = new SimpleDateFormat("dd:MM:yyyy", Locale.US).format(new Date());
            Bitmap stamped = Compressor.stampDate(cropped, dateText);
            if (stamped != cropped) {
                cropped.recycle();
            }

            Compressor.Result result = Compressor.compress(stamped, Compressor.TARGET_BYTES);

            String name = nextFileName();

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            Uri uri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                publishStatus("Opslaan mislukt.");
                return;
            }

            try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                os.write(result.data);
            }

            ContentValues update = new ContentValues();
            update.put(MediaStore.Images.Media.IS_PENDING, 0);
            update.put(MediaStore.Images.Media.WIDTH, result.width);
            update.put(MediaStore.Images.Media.HEIGHT, result.height);
            getContentResolver().update(uri, update, null, null);

            String message = "Opgeslagen in Afbeeldingen (Pictures)\n"
                    + formatBytes(jpeg.length) + " → " + formatBytes(result.data.length)
                    + " • " + result.width + "×" + result.height
                    + " • kwaliteit " + result.quality;
            publishStatus(message);
        } catch (Exception e) {
            publishStatus("Fout: " + e.getMessage());
        }
    }

    private String nextFileName() {
        String year = new SimpleDateFormat("yyyy", Locale.US).format(new Date());
        int max = 0;
        Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media.DISPLAY_NAME},
                MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?",
                new String[]{year + "_%"},
                null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String n = cursor.getString(0);
                int dot = n.lastIndexOf('.');
                if (dot > year.length() + 1) {
                    try {
                        max = Math.max(max, Integer.parseInt(n.substring(year.length() + 1, dot)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            cursor.close();
        }
        return year + "_" + String.format(Locale.US, "%04d", max + 1) + ".jpg";
    }

    private void publishStatus(String message) {
        runOnUiThread(() -> status.setText(message));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024 * 1024) {
            return (bytes + 512) / 1024 + " kB";
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
