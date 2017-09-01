package adrianzgaljic.com.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;


import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;





/**
 * Created by adrianzgaljic on 02/07/17.
 */

public
class Detector {



    private static final String NUMBERS = "file:///android_asset/numbers.pb";
    private static final String ALL_CHARACTERS = "file:///android_asset/all_characters_2.pb";

    private static final String INPUT_NODE = "input";
    private static final String OUTPUT_NODE = "sftmx";
    private static final String OUTPUT_NODE2 = "output";

    private static int certainty = 25;
    private static boolean onlyNumbers = false;
    private static boolean flipImage = true;
    private static int broj;
    private static double maxDistance;

    private static final int[] INPUT_SIZE = {1, 784};

    private static TensorFlowInferenceInterface inferenceInterface;

    static {
        System.loadLibrary("tensorflow_inference");
    }

    static {
        System.loadLibrary("opencv_java3");

    }

    private static String[] results = new String[]{
            "A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z",
            "a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z",
            "0","1","2","3","4","5","6","7","8","9"};


    public static void init(Context context, boolean onlyNumbers) {
        Detector.onlyNumbers = onlyNumbers;
        inferenceInterface = new TensorFlowInferenceInterface();
        if (onlyNumbers){
            inferenceInterface.initializeTensorFlow(context.getAssets(), NUMBERS);

        } else {
            inferenceInterface.initializeTensorFlow(context.getAssets(), ALL_CHARACTERS);

        }
    }




    public static Map<Point, String> detect(Bitmap bitmapImage) {
        //Log.d("tag"," detekcija");
        Map<Point, String> result = new HashMap<>();
        Mat mat = bitmapToMat(bitmapImage);

        if (flipImage){
            Core.flip(mat.t(), mat, 1);
        }

        List<MatOfPoint> contours = new ArrayList<>();
        List<MatOfPoint> contours2 = new ArrayList<>();

        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);
        //Imgproc.threshold(mat, matn, 130, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C);
        //CameraActivity.storeImage(matToBitmap(matn),"11matcontoursthresh");




        threshImage(mat);


        Mat mat2 = mat.clone();
        Imgproc.findContours(mat2, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        mat2 = mat.clone();
        Imgproc.findContours(mat2, contours2, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        Core.bitwise_not(mat, mat);




        Mat matContours = bitmapToMat(bitmapImage);
        if (flipImage){
            Core.flip(matContours.t(), matContours, 1);
            //Core.flip(mat.t(), mat, 1);
        }


        double maxcntr = 0;
        //Log.d("tag"," contours "+contours.size());

        for (int i = 0; i < contours.size(); i++) {
            if (Imgproc.contourArea(contours.get(i)) > maxcntr) {
                maxcntr = Imgproc.contourArea(contours.get(i));
            }
        }



        double sum = 0;
        int count = 0;
        for (int i = 0; i < contours.size(); i++) {
            double contourArea = Imgproc.contourArea(contours.get(i));

            Rect rect = Imgproc.boundingRect(contours.get(i));

            int rows = rect.height;
            int cols = rect.width;

            if (contourArea > 800 && contourArea <= maxcntr) {




                if ((rows / (float) cols < 4) && (cols / (float) rows) < 4) {

                    try {

                        Mat cropped = new Mat(mat, rect);

                        int h2 = rect.height;
                        int w2 = rect.width;

                        int subImageSize = 24;

                        float ratio = subImageSize / (float) h2;
                        int nw = (int) (w2 * ratio);

                        if (nw < subImageSize) {
                            int offset = subImageSize - nw;

                            //Imgproc.rectangle(matContours, new org.org.opencv.core.Point(rect.x,rect.y), new org.org.opencv.core.Point(rect.x+rect.width,rect.y+rect.height), new Scalar(0,255,0), 3);
                            Mat base = Mat.ones(28, 28, CvType.CV_8UC1);

                            Mat subtractmat = Mat.ones(28, 28, CvType.CV_8UC1);
                            for (int j = 0; j < 28; j++) {
                                for (int k = 0; k < 28; k++) {
                                    subtractmat.put(j, k, new double[]{255});
                                }
                            }
                            Imgproc.resize(cropped, cropped, new Size(nw, subImageSize));

                            Imgproc.threshold(cropped, cropped, 130, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C);

                            for (int j = 0; j < subImageSize; j++) {
                                for (int k = 0; k < nw; k++) {
                                    base.put(j + 2, k + 2 + offset / 2, cropped.get(j, k));
                                }
                            }

                            Core.subtract(subtractmat, base, base);


                            if (base.height() == 28 && base.width() == 28) {
                                float n;

                                base = base.reshape(1, 1); // flat 1d
                                float[] flat = new float[784];
                                double[] val = base.get(0, 0);
                                for (int ii = 0; ii < base.height(); ii++) {
                                    for (int j = 0; j < 784; j++) {
                                        n = (float) base.get(ii, j)[0];

                                        n = 1 - (n / 255);
                                        if (n < 0.1) {
                                            n = 0;
                                        }
                                        flat[j] = n;

                                    }
                                }

                                float[] resu;
                                if (onlyNumbers){
                                    resu = new float[10];
                                } else {
                                    resu = new float[62];

                                }
                                inferenceInterface.fillNodeFloat(INPUT_NODE, INPUT_SIZE, flat);
                                inferenceInterface.runInference(new String[]{OUTPUT_NODE});
                                inferenceInterface.readNodeFloat(OUTPUT_NODE, resu);


                                float[] resu2;
                                if (onlyNumbers){
                                    resu2 = new float[10];
                                } else {
                                    resu2 = new float[62];

                                }
                                inferenceInterface.runInference(new String[]{OUTPUT_NODE2});
                                inferenceInterface.readNodeFloat(OUTPUT_NODE2, resu2);
                                //Log.d("tag"," poslije hmm");

                                broj = 0;
                                for (float res: resu2){
                                    //Log.d("tag","certanity="+certainty);
                                    if (res == 1 && resu[broj]*10000 > certainty){
                                        //  result.put(new Point(rect.x+rect.width, rect.y+rect.height), results[broj]);
                                        if (onlyNumbers){
                                            result.put(new Point(rect.x+rect.width, rect.y+rect.height), Integer.toString(broj));
                                            sum += contourArea;
                                            count++;
                                            Log.d("tag"," avg cntr sum = "+sum);

                                            //CameraActivity.storeImage(matToBitmap(cropped), "c_"+contourArea);

                                        } else {
                                            result.put(new Point(rect.x+rect.width, rect.y+rect.height), results[broj]);
                                            //CameraActivity.storeImage(matToBitmap(base2), "c="+results[broj]+"_h2="+h2+"_w2="+w2);


                                        }
                                        //CameraActivity.storeImage(matToBitmap(cropped2), "n="+results[broj]+"_a="+contourArea+"_h2="+h2+"_w2="+w2);
                                        break;
                                    }
                                    broj++;

                                }
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }

            }
        }

        double average = sum/count;
        double lowerBoundary = average / 13;
        double upperBoundary = average / 6;
        Log.d("tag","xr avg cntr size = "+average+" "+lowerBoundary+" "+upperBoundary);
        maxDistance = -0.000002*Math.pow(average,2)+0.035*average+4.25;


        for (int i = 0; i < contours2.size(); i++) {
            double contourArea = Imgproc.contourArea(contours2.get(i));

            Rect rect = Imgproc.boundingRect(contours2.get(i));
            int rows = rect.height;
            int cols = rect.width;
            Mat cropped;

            if (contourArea > lowerBoundary && contourArea < upperBoundary &&
                    (rows / (float) cols < 1.2) && (cols / (float) rows) < 1.2) {
                result.put(new Point(rect.x + rect.width, rect.y + rect.height), ".");
                Log.d("tag"," stavljam");
                Imgproc.rectangle(matContours, new org.opencv.core.Point(rect.x, rect.y), new org.opencv.core.Point(rect.x + rect.width, rect.y + rect.height), new Scalar(255, 255, 255), 3);
                cropped = new Mat(mat, rect);
                //CameraActivity.storeImage(matToBitmap(cropped), "size_"+Double.toString(contourArea));

            }
        }


        Mat mat3 = bitmapToMat(bitmapImage);
        if (flipImage){
            Core.flip(mat3.t(), mat3, 1);

        }

        Imgproc.cvtColor(mat3, mat3, Imgproc.COLOR_RGB2GRAY);
        Imgproc.cvtColor(matContours, matContours, Imgproc.COLOR_RGB2GRAY);


        for (Point p: result.keySet()){
            Imgproc.putText(mat3, result.get(p), new org.opencv.core.Point(p.x, p.y), Core.FONT_HERSHEY_COMPLEX, 3, new Scalar(0,0,0), 5);
        }
        //CameraActivity.storeImage(matToBitmap(mat3), "rezultat");
        //CameraActivity.storeImage(matToBitmap(matContours), "konture");



/*
        for (Point p: res.keySet()){
            Imgproc.putText(mat3, Double.toString(res.get(p)), new org.org.opencv.core.Point(p.x, p.y), Core.FONT_HERSHEY_COMPLEX, 1.5, new Scalar(255,255,255), 3);
        }
        */

        //CameraActivity.storeImage(matToBitmap(mat3), "000000");


        //Log.d("tag"," results= "+result);
        return result;
    }

    private static Mat bitmapToMat(Bitmap bp) {
        Mat tmp = new Mat(bp.getWidth(), bp.getHeight(), CvType.CV_8UC1);
        Utils.bitmapToMat(bp, tmp);
        return tmp;

    }

    private static Bitmap matToBitmap(Mat mat) {
        Bitmap bmp = null;
        Mat tmp = new Mat(mat.height(), mat.width(), CvType.CV_8U, new Scalar(4));
        try {
            //Imgproc.cvtColor(seedsImage, tmp, Imgproc.COLOR_RGB2BGRA);
            Imgproc.cvtColor(mat, tmp, Imgproc.COLOR_GRAY2RGBA, 4);
            bmp = Bitmap.createBitmap(tmp.cols(), tmp.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(tmp, bmp);
            //Log.d("tag","bitmap ok");
        } catch (Exception e) {
            e.printStackTrace();
            //Log.d("Exception",e.getMessage());}
            //Log.d("tag","bitmap ok" + bmp);

        }

        return bmp;


    }

    public static void setCertainty(int certainty) {
        if (onlyNumbers){
            Detector.certainty = certainty*100;
        } else {
            Detector.certainty = certainty;
        }
    }

    public static void setImageFliped(boolean flipImage) {
        Detector.flipImage = flipImage;
    }

    private static void threshImage(Mat original){



        Imgproc.adaptiveThreshold(original, original, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 21,7);
        //CameraActivity.storeImage(matToBitmap(original), "thresh8");
        Imgproc.dilate(original, original, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));
        //CameraActivity.storeImage(matToBitmap(original), "thdilate");
        Imgproc.erode(original, original, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(6,6)));
        //CameraActivity.storeImage(matToBitmap(original), "rj");
        Core.bitwise_not(original, original);
        //CameraActivity.storeImage(matToBitmap(original),"11matcontoursadaptive");
    }

    public static  Map<Point, Double> getNumbers(Map<Point, String> inputMap){
        Map<Point, Double> result = new HashMap<>();
        boolean added;
        Map<Point, ArrayList<Point>> mapOfHorizontalClusters = new HashMap<>();
        for (Point p1: inputMap.keySet()){
            added = false;
            for (Point p2: mapOfHorizontalClusters.keySet()){
                if (Math.abs(p1.y-p2.y)<25){
                    mapOfHorizontalClusters.get(p2).add(p1);
                    added = true;
                    break;
                }
            }
            if (!added){
                ArrayList list = new ArrayList<Point>();
                list.add(p1);
                mapOfHorizontalClusters.put(p1, list);
            }
        }

        for (Point p: mapOfHorizontalClusters.keySet()){
            ArrayList<Point> array = mapOfHorizontalClusters.get(p);
            Collections.sort(array, new Comparator<Point>() {
                @Override
                public int compare(Point p1, Point p2) {
                    return p1.x - p2.x;
                }
            });

            int a = 10;
            double rez = 0;
            double lastPos = 0;
            boolean mul = true;

            //Log.d("tag","xr line "+line);
            //Log.d("tag","xr maxdistance "+maxDistance);

            Point firstPoint = null;
            for (Point p2: array){
                //Log.d("tag","xr input="+inputMap.get(p2));
                //Log.d("tag","xr mul="+mul);
                //Log.d("tag","xr a="+a);
                //Log.d("tag","xr result="+result);


                if (!inputMap.get(p2).equals(".")){

                    if (firstPoint == null){
                        firstPoint = p2;
                    }

                    //Log.d("tag","xr razlika="+(p2.x -lastPos));

                    if (p2.x -lastPos <maxDistance || lastPos == 0){

                        if (mul){
                            rez = rez*a +  Integer.parseInt(inputMap.get(p2));
                        } else {
                            if (!inputMap.get(p2).equals("0")){
                                rez = rez + Integer.parseInt(inputMap.get(p2))/(double)a;
                            }
                            a =10*a;

                        }
                        lastPos = p2.x;
                        result.put(firstPoint, rez);


                    } else {
                        //Log.d("tag","vece");

                        mul = true;
                        a = 10;
                        lastPos = p2.x;
                        firstPoint = p2;
                        rez = Double.parseDouble(inputMap.get(p2));
                        result.put(firstPoint, rez);
                        //Log.d("tag","xr spremam ="+rez);


                    }

                } else {
                    if (lastPos > 0){
                        lastPos += 80;
                    }
                    mul = false;

                }
                //System.out.print(p.x+","+p.y+"="+inputMap.get(p)+"  ");
            }
            //Log.d("tag","xr result="+result);


        }



        return result;
    }


}
