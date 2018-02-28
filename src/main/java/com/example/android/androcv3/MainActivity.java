package com.example.android.androcv3;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.support.v7.widget.Toolbar;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class MainActivity extends AppCompatActivity{

    JavaCameraView javaCameraView;
    Mat mRgba, mRgbaT, mGray, mThreshold;
    Toolbar toolbar;
    Button toggleCam, toggleMode;
    int index = 0;
    int mode = 0;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(MainActivity.this) {
        @Override
        public void onManagerConnected(int status) {
            super.onManagerConnected(status);
            switch (status){
                case BaseLoaderCallback.SUCCESS:
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
            mRgbaT = new Mat(height, width, CvType.CV_16UC4);
            mGray = new Mat(height, width, CvType.CV_16UC4);
            mThreshold = new Mat(height, width, CvType.CV_16UC4);

        }

        @Override
        public void onCameraViewStopped() {
            if (mode == 0)
                mRgba.release();
            else if (mode == 1)
                mGray.release();
            else
                mThreshold.release();
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

            // Resizing mRgbaT to the size of original input frame
            Imgproc.resize(mRgbaT, mRgbaT, mRgba.size());

            // Extracting a grayscale image of the mRgbaT and then, storing it in mGray
            Imgproc.cvtColor(mRgbaT, mGray, Imgproc.COLOR_RGB2GRAY);

            // Extracting a threshold image of mGray and then, storing it in mThreshold
            Core.inRange(mGray, new Scalar(45, 20, 10), new Scalar(75, 255, 255), mThreshold);

            // Returning the Mat on the basis of the toggle mode provided by the user
            if (mode == 0)
                return mRgbaT;
            else if (mode == 1)
                return mGray;
            else
                return mThreshold;
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

        if (OpenCVLoader.initDebug()){
            Toast.makeText(MainActivity.this, "Sorted !!!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(MainActivity.this, "Unsorted !!!", Toast.LENGTH_LONG).show();
        }

        toolbar = findViewById(R.id.toolbar);

        javaCameraView = findViewById(R.id.javaCamView);
        javaCameraView.setCvCameraViewListener(cvCameraViewListener2);

        toggleCam = findViewById(R.id.toggleCam);
        toggleMode = findViewById(R.id.toggleMode);

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
