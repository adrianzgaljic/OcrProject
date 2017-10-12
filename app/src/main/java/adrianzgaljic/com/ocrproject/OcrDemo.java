package adrianzgaljic.com.ocrproject;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;


import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import adrianzgaljic.com.ocr.Detector;


public class OcrDemo extends Activity {

    private static  final int FOCUS_AREA_SIZE= 300;

    private Camera camera;
    private CameraPreview preview;
    private static Camera.Size size;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr_demo);
        Detector.init(this, true);
        Detector.setImageFliped(true );
        Detector.setCertainty(40);



        // Create an instance of Camera
        camera = getCameraInstance();
        Log.d("tag","camera = "+ camera);
        // Create our Preview view and set it as the content of our activity.
        preview = new CameraPreview(this, camera);
        FrameLayout preview = findViewById(R.id.camera_preview);
        preview.addView(this.preview);


        Button captureButton = findViewById(R.id.button_capture);
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // get an image from the camera
                        camera.takePicture(null, null, mPicture);
                    }
                }
        );

        this.preview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (camera != null) {
                    camera.cancelAutoFocus();
                    Camera.Parameters parameters = camera.getParameters();

                    Rect rect = calculateFocusArea(event.getX(), event.getY());

                    List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
                    meteringAreas.add(new Camera.Area(rect, 800));

                    parameters.setFocusAreas(meteringAreas);
                    if (parameters.getFocusMode().equals(
                            Camera.Parameters.FOCUS_MODE_AUTO)) {
                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    }

                    if (parameters.getMaxNumFocusAreas() > 0) {
                        List<Camera.Area> mylist = new ArrayList<Camera.Area>();
                        mylist.add(new Camera.Area(rect, 1000));
                        parameters.setFocusAreas(mylist);
                    }

                    try {
                        camera.cancelAutoFocus();
                        camera.setParameters(parameters);
                        camera.startPreview();
                        camera.autoFocus(new Camera.AutoFocusCallback() {
                            @Override
                            public void onAutoFocus(boolean success, Camera camera) {
                                if (camera.getParameters().getFocusMode().equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                                    Camera.Parameters parameters = camera.getParameters();
                                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                                    if (parameters.getMaxNumFocusAreas() > 0) {
                                        parameters.setFocusAreas(null);
                                    }
                                    camera.setParameters(parameters);
                                    camera.startPreview();
                                }
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return true;
            }
        });

    }

    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            Log.d("tag","camera error "+ e.toString());
            // Camera is not available (in use or does not exist)
        }
        c.setDisplayOrientation(90);

        Camera.Parameters params = c.getParameters();
        List<Camera.Size> ls = params.getSupportedPreviewSizes();
        size = ls.get(0);
        params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        params.setPreviewSize(size.width, size.height);
        c.setParameters(params);
        return c;
    }


    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {



        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, null);
            /*Bitmap bm = BitmapFactory.decodeResource(OcrDemo.this.getResources(),
                    R.drawable.dva);
                    */
            //Bitmap b = rotateBitmap(bm, 90);
            Map<Point, String> result = Detector.detect(b);
            Map<Point, Double> res2 = Detector.getNumbers(result);
            System.out.println(res2);
            Bitmap newBm = drawTextToBitmap(b, OcrDemo.this , res2);
            storeImage(newBm, "ime");

        }
    };

    public static Bitmap matToBitmap(Mat mat){
        Mat tmp = new Mat(mat.height(), mat.width(), CvType.CV_8U, new Scalar(4));
        Bitmap bmp = null;
        try {

            Log.d("ocr", "aaa"+mat.size());
            //Imgproc.cvtColor(mat, tmp, Imgproc.COLOR_GRAY2RGB);
            tmp = mat;
            Log.d("ocr", "aaa2");
            //Imgproc.cvtColor(cropped, tmp, Imgproc.COLOR_GRAY2RGBA);
            bmp = Bitmap.createBitmap(tmp.cols(), tmp.rows(), Bitmap.Config.ARGB_8888);
            Log.d("ocr", "aaa3");
            Utils.matToBitmap(tmp, bmp);

        }
        catch (CvException e){
            e.printStackTrace();
            Log.d("Exception ee","greška "+e.toString());
        }
        return bmp;
    }

    public Mat bitmapToMat(Bitmap bp){
        Mat tmp = new Mat(bp.getWidth(), bp.getHeight(), CvType.CV_8UC1);
        //Bitmap bmp32 = bmpGallery.copy(Bitmap.Config.ARGB_8888, true);
        //Utils.bitmapToMat(bmp32, imgMAT);
        return tmp;

    }


    public static void storeImage(Bitmap bitmap, String name){
        File sdCardDirectory = Environment.getExternalStorageDirectory();
        File image = new File(sdCardDirectory+"/slike", name+".png");
        Log.d("tag"," sdcard= "+sdCardDirectory);

        boolean success = false;

        FileOutputStream outStream;
        try {

            outStream = new FileOutputStream(image);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
        /* 100 to keep full quality of the image */

            outStream.flush();
            outStream.close();
            success = true;
        } catch (FileNotFoundException e) {
            Log.d("tag","spremio sliku nisam "+e.toString());

            e.printStackTrace();
        } catch (IOException e) {
            Log.d("tag","spremio sliku nisam "+e.toString());

            e.printStackTrace();
        }

        Log.d("tag","spremio sliku "+name);



    }



    private Rect calculateFocusArea(float x, float y) {
        int left = clamp(Float.valueOf((x / preview.getWidth()) * 2000 - 1000).intValue(), FOCUS_AREA_SIZE);
        int top = clamp(Float.valueOf((y / preview.getHeight()) * 2000 - 1000).intValue(), FOCUS_AREA_SIZE);

        return new Rect(left, top, left + FOCUS_AREA_SIZE, top + FOCUS_AREA_SIZE);
    }

    private int clamp(int touchCoordinateInCameraReper, int focusAreaSize) {
        int result;
        if (Math.abs(touchCoordinateInCameraReper)+focusAreaSize/2>1000){
            if (touchCoordinateInCameraReper>0){
                result = 1000 - focusAreaSize/2;
            } else {
                result = -1000 + focusAreaSize/2;
            }
        } else{
            result = touchCoordinateInCameraReper - focusAreaSize/2;
        }
        return result;
    }

    public Bitmap drawTextToBitmap(Bitmap bitmap, Context gContext, Map<Point, Double> map) {
        Resources resources = gContext.getResources();
        float scale = resources.getDisplayMetrics().density;


        android.graphics.Bitmap.Config bitmapConfig =
                bitmap.getConfig();
        // set default bitmap config if none
        if(bitmapConfig == null) {
            bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;
        }
        // resource bitmaps are imutable,
        // so we need to convert it to mutable one
        bitmap = bitmap.copy(bitmapConfig, true);

        Canvas canvas = new Canvas(bitmap);
        // new antialised Paint
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // text color - #3D3D3D
        paint.setColor(Color.rgb(61, 61, 61));
        // text size in pixels
        paint.setTextSize((int) (34 * scale));
        // text shadow
        paint.setShadowLayer(1f, 0f, 1f, Color.WHITE);

        // draw text to the Canvas center
        Rect bounds = new Rect();
        //paint.getTextBounds(gText, 0, gText.length(), bounds);
        for (Point p: map.keySet()){
            canvas.drawText(Double.toString(map.get(p)),p.x, p.y, paint);

        }


        return bitmap;
    }
    public static Bitmap rotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();

        matrix.postRotate(90);

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(source,source.getWidth()/2,source.getHeight()/2,true);

        return  Bitmap.createBitmap(scaledBitmap , 0, 0, scaledBitmap .getWidth(), scaledBitmap .getHeight(), matrix, true);
    }




}