package com.kowsoft.pokemongorater;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class OverlayService extends Service {
    private static final long SCREENSHOT_DELAY_MS = 100;
    private final CaptureServiceConnection captureServiceConnection = new CaptureServiceConnection();

    private Handler handler;
    private Button button;
    private ScreenCaptureService screenCaptureService;

    public class LocalBinder extends Binder {
        public OverlayService getService() {
            return OverlayService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        handler = new Handler();

        bindService(new Intent(this, ScreenCaptureService.class), captureServiceConnection, BIND_AUTO_CREATE);

        button = new Button(this);
        button.setText("Poké\nCaptor");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(v);
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.LEFT | Gravity.TOP;
        getWindowManager().addView(button, params);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(captureServiceConnection);
        if (button != null) {
            getWindowManager().removeView(button);
            button = null;
        }
    }

    private void showMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.capture:
                        capturePokemonData();
                        break;
                    case R.id.quit:
                        stopSelf();
                        break;
                }
                return true;
            }
        });
        popup.inflate(R.menu.rater_menu);
        popup.show();
    }

    private void capturePokemonData() {
        button.setVisibility(View.INVISIBLE);
        // we need a delay, otherwise the menu will be shown in the screenshot
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (screenCaptureService != null) {
                    screenCaptureService.captureScreen(new ScreenCaptureService.CaptureScreenCallback() {
                        @Override
                        public void onScreenReady(final Bitmap screen) {
                            Uri uri = saveImageFile(screen);
                            startPokemonAnalysisActivity(uri);
                            screen.recycle();
                        }

                    });
                } else {
                    Toast.makeText(OverlayService.this, "Could not find screen capture service.", Toast.LENGTH_SHORT).show();
                    button.setVisibility(View.VISIBLE);
                }
            }
        }, SCREENSHOT_DELAY_MS);
    }

    private void startPokemonAnalysisActivity(Uri uri) {
        Intent intent = new Intent(this, PokemonAnalysisWrapperActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setDataAndType(uri, "image/*");
        startActivity(intent);
    }

    private Uri saveImageFile(Bitmap screen) {
        File imageFile = new File(getFilesDir(), "tmp_screenshot");
        Uri uri = Uri.fromFile(imageFile);
        try (FileOutputStream outputStream = new FileOutputStream(imageFile);) {
            screen.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
        } catch (IOException e) {
            Log.e(this.getClass().getSimpleName(), "Exception in saving image file", e);
        }
        return uri;
    }

    private WindowManager getWindowManager() {
        return (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    private void runOnUiThread(Runnable runnable) {
        handler.post(runnable);
    }

    private class CaptureServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            screenCaptureService = ((ScreenCaptureService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            screenCaptureService = null;
        }
    }

    public static class PokemonAnalysisWrapperActivity extends Activity implements ServiceConnection {
        private static final int POKEMON_ANALYSIS_REQUEST = 1;

        private OverlayService overlayService;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            bindService(new Intent(this, OverlayService.class), this, 0);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            unbindService(this);
        }


        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == POKEMON_ANALYSIS_REQUEST) {
                overlayService.button.setVisibility(View.VISIBLE);
                finish();
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            overlayService = ((LocalBinder) service).getService();
            Intent intent = new Intent(this, PokemonAnalysisActivity.class);
            intent.setDataAndType(getIntent().getData(), getIntent().getType());
            startActivityForResult(intent, POKEMON_ANALYSIS_REQUEST);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            finish();
        }

    }
}
