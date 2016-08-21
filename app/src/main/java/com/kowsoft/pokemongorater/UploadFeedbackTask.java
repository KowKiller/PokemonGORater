package com.kowsoft.pokemongorater;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadFeedbackTask extends AsyncTask<UploadFeedbackTask.FeedbackData, Void, Boolean> {

    private static final String FEEDBACK_SERVER_URL = "http://scampanelli.altervista.org/pokemongorater/feedback.php";
    private static final MediaType MEDIA_TYPE_PNG = MediaType.parse("image/png");
    public static final String TRAINER_LEVEL = "trainerLevel";
    public static final String OUTPUT_TEXT = "outputText";
    public static final String INPUT_IMAGE = "inputImage";

    private final OkHttpClient httpClient;

    private ProgressDialog progressDialog;
    private AlertDialog resultDialog;

    public UploadFeedbackTask(Context context) {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        progressDialog = new ProgressDialog(context);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage("Uploading feedback data");
        resultDialog = new AlertDialog.Builder(context).setPositiveButton("Ok", null).create();
    }

    public static class FeedbackData {

        private int trainerLevel;
        private String outputText;
        private Uri inputImageUri;

        public static class Builder {

            private int trainerLevel;
            private String outputText;
            private Uri inputImageUri;

            public Builder trainerLevel(int trainerLevel) {
                this.trainerLevel = trainerLevel;
                return this;
            }

            public Builder outputText(String outputText) {
                this.outputText = outputText;
                return this;
            }

            public Builder inputImageUri(Uri inputImageUri) {
                this.inputImageUri = inputImageUri;
                return this;
            }

            public FeedbackData build() {
                return new FeedbackData(this);
            }
        }

        private FeedbackData(Builder builder) {
            trainerLevel = builder.trainerLevel;
            outputText = builder.outputText;
            inputImageUri = builder.inputImageUri;
        }

        public int getTrainerLevel() {
            return trainerLevel;
        }

        public String getOutputText() {
            return outputText;
        }

        public Uri getInputImageUri() {
            return inputImageUri;
        }
    }

    @Override
    protected void onPreExecute() {
        progressDialog.show();
    }

    @Override
    protected Boolean doInBackground(FeedbackData... params) {

        if (params.length != 1) {
            return false;
        }

        FeedbackData feedbackData = params[0];

        try {
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(TRAINER_LEVEL, Integer.toString(feedbackData.getTrainerLevel()))
                    .addFormDataPart(OUTPUT_TEXT, feedbackData.getOutputText())
                    .addFormDataPart(INPUT_IMAGE, "pokemonScreen.png",
                            RequestBody.create(MEDIA_TYPE_PNG, new File(feedbackData.getInputImageUri().getPath())))
                    .build();
            Request request = new Request.Builder()
                    .url(FEEDBACK_SERVER_URL)
                    .post(requestBody)
                    .build();
            Response response = httpClient.newCall(request).execute();
            Log.d(this.getClass().getSimpleName(), "DATA: " + response.body().string());
            return response.isSuccessful();
        } catch (IOException e) {
            Log.e(this.getClass().getSimpleName(), "Error in uploading feedback data to server.", e);
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean result) {
        progressDialog.dismiss();
        resultDialog.setMessage(result ? "Feedback data successfully sent. Thanks for your contribution." : "Could not send feedback data.");
        resultDialog.show();
    }
}
