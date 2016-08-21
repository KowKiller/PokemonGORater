package com.kowsoft.pokemongorater;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    private MediaProjection mediaProjection;
    private Handler handler;

    public class LocalBinder extends Binder {
        public ScreenCaptureService getService() {
            return ScreenCaptureService.this;
        }
    }

    public interface CaptureScreenCallback {
        void onScreenReady(Bitmap screen);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        acquireScreenshotPermission();
        createLooperThread();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
    }

    public void setMediaProjection(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
    }

    public void captureScreen(final CaptureScreenCallback callback) {

        if (mediaProjection == null) {
            Toast.makeText(this, "Cannot capture: Media Projection service not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        // get width and height
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        Log.d(this.getClass().getSimpleName(), "Preparing for screenshot acquisition. Size: " + size);

        // start capture reader
        ImageReader imageReader = ImageReader.newInstance(size.x, size.y, PixelFormat.RGBA_8888, 1);
        Log.d(this.getClass().getSimpleName(), "ImageReader Created. W: " + imageReader.getWidth() + " H: " + imageReader.getHeight());

        final VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay("CaptureScreenServiceVirtualDisplay_" + System.currentTimeMillis(),
                size.x,
                size.y,
                getResources().getDisplayMetrics().densityDpi,
                0,
                imageReader.getSurface(),
                null,
                handler);

        Point tmp = new Point();
        virtualDisplay.getDisplay().getSize(tmp);
        Log.d(this.getClass().getSimpleName(), "VirtualDisplay Created. Size: " +  tmp);

        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                try {
                    callback.onScreenReady(acquireScreen(reader));
                } finally {
                    virtualDisplay.release();
                    reader.close();
                }
            }
        }, handler);
    }

    private static Bitmap acquireScreen(ImageReader reader) {
        try (Image image = reader.acquireNextImage()) {
            Log.d(ScreenCaptureService.class.getSimpleName(), "Image Acquired. W: " + image.getWidth() + " H: " + image.getHeight());
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * reader.getWidth();

            Log.d(ScreenCaptureService.class.getSimpleName(), "Debug Info. pixelStride: " + pixelStride + " rowStride: " + rowStride + " rowPadding: " + rowPadding);

            // build bitmap
            Bitmap bitmap = Bitmap.createBitmap(reader.getWidth() + rowPadding / pixelStride, reader.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            Log.d(ScreenCaptureService.class.getSimpleName(), "Bitmap Created. W: " + bitmap.getWidth() + " H: " + bitmap.getHeight());

            bitmap = Bitmap.createBitmap(bitmap, 0, 0, reader.getWidth(), reader.getHeight());
            Log.d(ScreenCaptureService.class.getSimpleName(), "Bitmap Cropped. W: " + bitmap.getWidth() + " H: " + bitmap.getHeight());
            return bitmap;
        }
    }

    private void createLooperThread() {
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                handler = new Handler();
                Looper.loop();
            }
        }.start();
    }

    private void acquireScreenshotPermission() {
        final Intent intent = new Intent(this, AcquireScreenshotPermissionsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private WindowManager getWindowManager() {
        return (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    public static class AcquireScreenshotPermissionsActivity extends Activity implements ServiceConnection {
        private static final int MEDIA_CAPTURE_REQUEST = 1;

        private ScreenCaptureService screenCaptureService;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            bindService(new Intent(this, ScreenCaptureService.class), this, BIND_AUTO_CREATE);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            unbindService(this);
        }


        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == MEDIA_CAPTURE_REQUEST) {
                if (resultCode == Activity.RESULT_OK) {
                    screenCaptureService.setMediaProjection(getMediaManager().getMediaProjection(resultCode, data));
                } else {
                    Toast.makeText(this, "Could not acquire Screenshot Permissions", Toast.LENGTH_SHORT).show();
                }
                finish();
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            screenCaptureService = ((LocalBinder) service).getService();
            startActivityForResult(getMediaManager().createScreenCaptureIntent(), MEDIA_CAPTURE_REQUEST);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            finish();
        }

        private MediaProjectionManager getMediaManager() {
            return (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        }

    }

}
