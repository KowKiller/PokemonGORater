package com.kowsoft.pokemongorater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.IOException;

public class PokemonAnalysisActivity extends AppCompatActivity {

    public static final String PREFS_FILE = "preferences";
    public static final String TRAINER_LEVEL_KEY = "trainer_level";
    private static final int MIN_TRAINER_LVL = 1;
    private static final int MAX_TRAINER_LVL = 40;

    private Handler handler;
    private Bitmap inputBmp;
    private boolean openCVLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pokemon_analysis);

        createLooperThread();

        findViewById(R.id.dismiss).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.changeLevel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLevelChangeDialog();
            }
        });

        findViewById(R.id.uploadFeedback).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadFeedbackData();
            }
        });

        loadPokemonImage();

        if (inputBmp == null) {
            Toast.makeText(this, "Could not load Pokémon image.", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, getOpenCVLoadedHandler());
        }

    }

    private void uploadFeedbackData() {
        new UploadFeedbackTask(this).execute(new UploadFeedbackTask.FeedbackData.Builder()
                .trainerLevel(getTrainerLevel())
                .outputText(((TextView) findViewById(R.id.pokemonLevelText)).getText().toString())
                .inputImageUri(getInputImageUri())
                .build());
    }

    @Override
    protected void onResume() {
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, getOpenCVLoadedHandler());
        super.onResume();
    }

    @NonNull
    private BaseLoaderCallback getOpenCVLoadedHandler() {
        return new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                if (status == LoaderCallbackInterface.SUCCESS && !openCVLoaded) {
                    openCVLoaded = true;
                    updateShownTrainerLevel(getTrainerLevel());
                    triggerPokemonDataAnalysis();
                }
                super.onManagerConnected(status);
            }
        };
    }

    private void showLevelChangeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Update your trainer level:");
        final NumberPicker trainerLevelPicker = new NumberPicker(this);
        trainerLevelPicker.setMinValue(MIN_TRAINER_LVL);
        trainerLevelPicker.setMaxValue(MAX_TRAINER_LVL);
        trainerLevelPicker.setValue(getTrainerLevel());
        builder.setView(trainerLevelPicker);
        builder.setPositiveButton("Done", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                exportTrainerLevelPref(trainerLevelPicker.getValue());
                triggerPokemonDataAnalysis();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.create().show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
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

    private void exportTrainerLevelPref(int trainerLevel) {
        if (trainerLevel > 0) {
            SharedPreferences sp = getSharedPreferences(PREFS_FILE, Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putInt(TRAINER_LEVEL_KEY, trainerLevel);
            editor.commit();
            updateShownTrainerLevel(trainerLevel);
        }
    }

    private void updateShownTrainerLevel(int trainerLevel) {
        ((TextView) findViewById(R.id.trainerLevelText)).setText("Trainer Level: " + Integer.toString(trainerLevel));
    }

    private int getTrainerLevel() {
        SharedPreferences sp = getSharedPreferences(PREFS_FILE, Activity.MODE_PRIVATE);
        return sp.getInt(TRAINER_LEVEL_KEY, MIN_TRAINER_LVL);
    }

    private void triggerPokemonDataAnalysis() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                showLoadingBar();
                PokemonScreenAnalyzer.Result result = new PokemonScreenAnalyzer(getTrainerLevel()).analyze(inputBmp);
                showAnalysisResult(result);
            }
        });

    }

    private void showAnalysisResult(final PokemonScreenAnalyzer.Result result) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getDebugBmpView().setImageBitmap(result.getDebugBitmap());
                findViewById(R.id.outputSection).setVisibility(View.VISIBLE);
                findViewById(R.id.loadingBar).setVisibility(View.GONE);
                if (result.isValid()) {
                    ((TextView) findViewById(R.id.pokemonLevelText)).setText("Estimated Pokémon Level: " + result.getLevel());
                } else {
                    ((TextView) findViewById(R.id.pokemonLevelText)).setText("Could not determine Pokémon Level");
                }
            }
        });
    }

    private void showLoadingBar() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.outputSection).setVisibility(View.GONE);
                findViewById(R.id.loadingBar).setVisibility(View.VISIBLE);
            }
        });
    }


    private void loadPokemonImage() {
        final Uri imageUri = getInputImageUri();
        if (imageUri != null) {
            try {
                inputBmp = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
            } catch (IOException e) {
                Log.e(this.getClass().getSimpleName(), "Exception in loading image", e);
            }
        }
    }

    private Uri getInputImageUri() {
        final Uri imageUri;
        if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
            imageUri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        } else {
            imageUri = getIntent().getData();
        }
        return imageUri;
    }

    private ImageView getDebugBmpView() {
        return (ImageView) findViewById(R.id.debugBitmap);
    }
}
