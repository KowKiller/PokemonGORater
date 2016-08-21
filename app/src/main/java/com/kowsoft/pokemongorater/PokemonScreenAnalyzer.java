package com.kowsoft.pokemongorater;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Range;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class PokemonScreenAnalyzer {

    // Thanks to http://poke.isitin.org/ for the values
    private static final double[] CPM = {0.0940000, 0.1351374, 0.1663979, 0.1926509, 0.2157325, 0.2365727, 0.2557201, 0.2735304, 0.2902499, 0.3060574, 0.3210876, 0.3354450, 0.3492127, 0.3624578, 0.3752356, 0.3875924, 0.3995673, 0.4111936, 0.4225000, 0.4335117, 0.4431076, 0.4530600, 0.4627984, 0.4723361, 0.4816850, 0.4908558, 0.4998584, 0.5087018, 0.5173940, 0.5259425, 0.5343543, 0.5426358, 0.5507927, 0.5588306, 0.5667545, 0.5745692, 0.5822789, 0.5898879, 0.5974000, 0.6048188, 0.6121573, 0.6194041, 0.6265671, 0.6336492, 0.6406530, 0.6475810, 0.6544356, 0.6612193, 0.6679340, 0.6745819, 0.6811649, 0.6876849, 0.6941437, 0.7005429, 0.7068842, 0.7131691, 0.7193991, 0.7255756, 0.7317000, 0.7347410, 0.7377695, 0.7407856, 0.7437894, 0.7467812, 0.7497610, 0.7527291, 0.7556855, 0.7586304, 0.7615638, 0.7644861, 0.7673972, 0.7702973, 0.7731865, 0.7760650, 0.7789328, 0.7817901, 0.7846370, 0.7874736, 0.7903000, 0.7931164};

    private static final double MIN_DIST_PERCENT = 0.1;

    public static final int THRESHOLD = 25;
    public static final double ACCUMULATOR = 1.0;
    public static final int ACC_THRESHOLD = 100;
    public static final int ANGLE_THRESH = 245;

    private final int trainerLevel;

    public PokemonScreenAnalyzer(int trainerLevel) {
        this.trainerLevel = trainerLevel;
    }

    public static class Result {
        private final boolean valid;
        private final double level;
        private final Bitmap debugBmp;

        private Result(boolean valid, double level, Bitmap debugBmp) {
            this.valid = valid;
            this.level = level;
            this.debugBmp = debugBmp;
        }

        public static Result valid(double level, Bitmap debugBmp) {
            return new Result(true, level, debugBmp);
        }

        public static Result invalid(Bitmap debugBmp) {
            return new Result(false, 0, debugBmp);
        }

        public boolean isValid() {
            return valid;
        }

        public double getLevel() {
            return level;
        }

        public Bitmap getDebugBitmap() {
            return debugBmp;
        }
    }

    public Result analyze(Bitmap bitmap) {
        Result result = Result.invalid(bitmap);
        Mat orig = new Mat();
        Mat grayMat = new Mat();

        Utils.bitmapToMat(bitmap, orig);

        // Let's only work on top half
        Mat mat = new Mat(orig, new Range(0, orig.rows() / 2));

        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGRA2GRAY);
        Imgproc.GaussianBlur(grayMat, grayMat, new Size(9, 9), 2, 2);

        MatOfPoint3f circles = new MatOfPoint3f();

        int minDist = round(orig.rows() * MIN_DIST_PERCENT);

        Imgproc.HoughCircles(grayMat,
                circles,
                Imgproc.CV_HOUGH_GRADIENT,
                ACCUMULATOR,
                minDist,
                THRESHOLD,
                ACC_THRESHOLD,
                0,
                0);

        Log.d(this.getClass().getSimpleName(), "Detected circles: " + circles.size());
        Log.d(this.getClass().getSimpleName(), "Detected circles data: " + Arrays.toString(circles.toArray()));

        // Level arc center must be close to image center

        Point center = null;
        int radius = 0;
        Scalar centerRange = new Scalar((bitmap.getWidth() - minDist) / 2.0, (bitmap.getWidth() + minDist) / 2.0);

        for (Point3 circle : circles.toArray()) {
            if (circle.x > centerRange.val[0] && circle.x < centerRange.val[1]) {
                center = new Point(circle.x, circle.y);
                radius = (int) Math.round(circle.z);
                break;
            }
        }

        if (center != null) {

            Mat thresh = new Mat();

            Imgproc.threshold(grayMat, thresh, ANGLE_THRESH, 255, Imgproc.THRESH_BINARY);

            Mat threshDist = new Mat();

            Imgproc.distanceTransform(thresh, threshDist, Imgproc.DIST_L2, Imgproc.DIST_MASK_5);

            Imgproc.circle(mat, center, 3, new Scalar(0, 255, 255, 255), -1);
            Imgproc.circle(mat, center, radius, new Scalar(0, 0, 255, 255), 2);

            int max = 0;
            double estPokemonLevel = -1;

            for (double pokemonLevel = 1; pokemonLevel <= trainerLevel + 1.5; pokemonLevel += 0.5) {
                double angleRad = calcAngle(pokemonLevel);
                Point start = new Point(center.x + (radius - 50) * Math.cos(angleRad), center.y - (radius - 50) * Math.sin(angleRad));
                Point end = new Point(center.x + radius * Math.cos(angleRad), center.y - radius * Math.sin(angleRad));
                Imgproc.line(mat, start, end, new Scalar(0, 0, 255, 255), 2);
                int curDist = round(threshDist.get(round(end.y), round(end.x))[0]);
                if (curDist > max) {
                    max = curDist;
                    estPokemonLevel = pokemonLevel;
                }
            }

            if (estPokemonLevel > 0) {
                double angleRad = calcAngle(estPokemonLevel);
                Point end = new Point(center.x + radius * Math.cos(angleRad), center.y - radius * Math.sin(angleRad));
                Imgproc.line(mat, center, end, new Scalar(0, 255, 255, 255), 2);
            }

            Mat threshColor = new Mat();

            Imgproc.cvtColor(thresh, threshColor, Imgproc.COLOR_GRAY2BGRA);

            Mat outMat = new Mat();

            Core.vconcat(Arrays.asList(mat, threshColor), outMat);

            Bitmap outBitmap = Bitmap.createBitmap(outMat.cols(), outMat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(outMat, outBitmap);

            thresh.release();
            threshDist.release();
            threshColor.release();
            outMat.release();

            result = Result.valid(estPokemonLevel, outBitmap);
        }
        orig.release();
        grayMat.release();
        mat.release();
        circles.release();
        return result;
    }

    private double calcAngle(double pokemonLevel) {
        // Thanks to http://poke.isitin.org/ for the formula
        double angleDeg = 180 - ((getCPM(pokemonLevel) - getCPM(1)) * 202.037116 / getCPM(trainerLevel));
        return angleDeg / 180.0 * Math.PI;
    }

    private static double getCPM(double level) {
        int idx = min(max(round(level * 2 - 2), 0), CPM.length - 1);
        return CPM[idx];
    }

    private static int round(double v) {
        return (int) Math.round(v);
    }

}