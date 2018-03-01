package com.example.android.androcv3;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;
import android.support.v7.widget.Toolbar;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity{

    JavaCameraView javaCameraView;
    Mat mRgba, mRgbaT, mGray, mThreshold, mInvert, mEdges;
    Mat mRgbaTFD, mGrayFD, mThresholdFD;
    Toolbar toolbar;
    Button toggleCam, toggleMode;
    Switch toggleFaceDetection;
    int index = 0;
    int mode = 0;
    int absFaceSize;
    Boolean faceDetection = false;
    CascadeClassifier faceClassifier = null;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private void settingUpFaceClassifier(){
        final InputStream is;
        try {
            is = getResources().openRawResource(R.raw.face);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "face_frontal.xml");

            FileOutputStream os;
            os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }

            is.close();
            os.close();

            faceClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
        } catch (IOException e) {
            Log.i("Mainactivity", "face cascade not found");
        }
    }

    BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(MainActivity.this) {
        @Override
        public void onManagerConnected(int status) {
            super.onManagerConnected(status);
            switch (status){
                case BaseLoaderCallback.SUCCESS:
                    settingUpFaceClassifier();
                    javaCameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);

            }
        }
    };

    CameraBridgeViewBase.CvCameraViewListener2 cvCameraViewListener2 = new CameraBridgeViewBase.CvCameraViewListener2() {

        @Override
        public void onCameraViewStarted(int width, int height) {
            mRgba = new Mat(height, width, CvType.CV_16UC4);
            mInvert = new Mat(height, width, CvType.CV_16UC4);
            mRgbaT = new Mat(height, width, CvType.CV_16UC4);
            mGray = new Mat(height, width, CvType.CV_16UC4);
            mThreshold = new Mat(height, width, CvType.CV_16UC4);
            mRgbaTFD = new Mat(height, width, CvType.CV_16UC4);
            mGrayFD = new Mat(height, width, CvType.CV_16UC4);
            mThresholdFD = new Mat(height, width, CvType.CV_16UC4);
            mEdges = new Mat(height, width, CvType.CV_16UC4);

            absFaceSize = (int)(height*0.25);
        }

        @Override
        public void onCameraViewStopped() {
            if (mode == 0)
                mRgba.release();
            else if (mode == 1)
                mGray.release();
            else if (mode == 2)
                mThreshold.release();
            else if (mode == 3)
                mEdges.release();
            else
                mInvert.release();
        }

        @Override
        public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
            mRgba = inputFrame.rgba();

            // Transposing the mRgba matrix to get a 90 degree rotated matrix and then, storing it in mRgbaT
            Core.transpose(mRgba, mRgbaT);

            // Then, flipping (mirror-like) the mRgbaT to get a perfectly oriented matrix which will be displayed in the frame
            Core.flip(mRgbaT, mRgbaT, Core.BORDER_REPLICATE);

            // Flipping (mirror-like) the mRgbaT vertically if the camera if front camera
            if (index == 1)
                Core.flip(mRgbaT, mRgbaT, Core.BORDER_CONSTANT);

            // Applying bitwise not to generate an inverted frame
            Core.bitwise_not(mRgbaT, mInvert);

            // Resizing mRgbaT to the size of original input frame
            Imgproc.resize(mRgbaT, mRgbaT, mRgba.size());

            // Extracting a grayscale image of the mRgbaT and then, storing it in mGray
            Imgproc.cvtColor(mRgbaT, mGray, Imgproc.COLOR_RGB2GRAY);

            // Extracting Canny edges and storing them in mEdges Mat
            Imgproc.Canny(mGray, mEdges, 100, 255);

            // Extracting a threshold image of mGray and then, storing it in mThreshold
            Core.inRange(mGray, new Scalar(45, 20, 10), new Scalar(75, 255, 255), mThreshold);

            mGrayFD = mGray;
            mRgbaTFD = mRgbaT;
            mThresholdFD = mThreshold;

            MatOfRect faces = new MatOfRect();


            if (faceClassifier != null) {
                faceClassifier.detectMultiScale(mGray, faces,  1.1, 2, 2, new Size(absFaceSize, absFaceSize), new Size());
                Rect[] facesArray = faces.toArray();
                for (Rect rect : facesArray){
                    Imgproc.rectangle(mRgbaTFD, rect.tl(), rect.br(), new Scalar(255, 0, 0), 3);
                    Imgproc.rectangle(mGrayFD, rect.tl(), rect.br(), new Scalar(255, 255, 255), 3);
                    Imgproc.rectangle(mThresholdFD, rect.tl(), rect.br(), new Scalar(0, 255, 255), 2);
                }
            }

            if (faceDetection){
                // Returning the Mat on the basis of the toggle mode provided by the user
                if (mode == 0)
                    return mRgbaTFD;
                else if (mode == 1)
                    return mGrayFD;
                else if (mode == 2)
                    return mThresholdFD;
                else if (mode == 3)
                    return mEdges;
                else
                    return mInvert;
            } else {
                // Returning the Mat on the basis of the toggle mode provided by the user
                if (mode == 0)
                    return mRgbaT;
                else if (mode == 1)
                    return mGray;
                else if (mode == 2)
                    return mThreshold;
                else if (mode == 3)
                    return mEdges;
                else
                    return mInvert;
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Checking if the app has camera permission approved by the user or not
        // If not, then ask for the permission
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        if (!OpenCVLoader.initDebug()){
            Toast.makeText(MainActivity.this, "Sorted !!!", Toast.LENGTH_LONG).show();
            return;
        }

        javaCameraView = findViewById(R.id.javaCamView);

        toggleCam = findViewById(R.id.toggleCam);
        toggleMode = findViewById(R.id.toggleMode);
        toggleFaceDetection = findViewById(R.id.toggleFaceDetection);


        // Toggle switch for face detection
        toggleFaceDetection.setChecked(false);
        javaCameraView.setCvCameraViewListener(cvCameraViewListener2);
        toggleFaceDetection.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                javaCameraView.disableView();
                faceDetection = isChecked;
                javaCameraView.setCvCameraViewListener(cvCameraViewListener2);
                javaCameraView.enableView();
            }
        });

        // When toggle camera button is clicked by the user
        toggleCam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                javaCameraView.disableView();
                if (index == 0)
                    index = 1;
                else
                    index = 0;

                javaCameraView.setCameraIndex(index);
                javaCameraView.enableView();
            }
        });

        // When toggle mode button is clicked by the user
        toggleMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                javaCameraView.disableView();
                if (mode == 0)
                    mode = 1;
                else if (mode == 1)
                    mode = 2;
                else if (mode == 2)
                    mode = 3;
                else if (mode == 3)
                    mode = 4;
                else
                    mode = 0;
                javaCameraView.setCvCameraViewListener(cvCameraViewListener2);
                javaCameraView.enableView();
            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (javaCameraView != null){
            javaCameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null){
            javaCameraView.disableView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()){
            baseLoaderCallback.onManagerConnected(BaseLoaderCallback.SUCCESS);
        } else {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_11, MainActivity.this, baseLoaderCallback);
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

}
