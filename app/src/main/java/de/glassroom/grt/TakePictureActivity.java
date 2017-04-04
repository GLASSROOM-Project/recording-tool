package de.glassroom.grt;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.RelativeLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import de.glassroom.grt.glassroomrecordingtool.R;

public class TakePictureActivity extends Activity {
    private static final String TAG = "GRT_TakePicture";

    private Button bild;
    private WebView webView;

    private Camera mCamera = null;
    private CameraView mCameraView = null;

    protected static final int MEDIA_TYPE_IMAGE = 0;

    private File path;
    // private boolean clicked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_take_picture);

        try {
            mCamera = Camera.open();
            Camera.Parameters params = mCamera.getParameters();
            params.setPictureSize(1600, 1200);
            params.setZoom(0);
            mCamera.setParameters(params);
        } catch (Exception e) {
            Log.d("ERROR", "Failed to get Camera: " + e.getMessage());
        }

        if(mCamera != null) {
            mCameraView = new CameraView(this, mCamera);
            RelativeLayout camera_view = (RelativeLayout)findViewById(R.id.camera_view);
            camera_view.addView(mCameraView);
        }

        path = (File) getIntent().getExtras().get("path");
        // mainHandler = new Handler(getApplicationContext().getMainLooper());

        webView = (WebView)findViewById(R.id.webView);
        // Hier wird die HTML mit dem Button geladen. Muss eventuell noch ersetzt werden?!
        webView.loadUrl("file:///android_asset/takepicture.html");
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
        webView.getSettings().setJavaScriptEnabled(true);

        // clicked = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        webView.loadUrl("javascript:GRT.startCountdown();");
        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        capture();
                    }
                });
            }
        }, 5000);
    }

    @Override
    public void onDestroy() {
        webView.destroy();
        mCamera.release();
        super.onDestroy();
    }

    /*@Override
    public void onUserInteraction() {
        if (!clicked) {
            clicked = true;
            capture();
        }
        super.onUserInteraction();
    }*/

    public void capture(){
        Camera.PictureCallback pictureCB = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                try {
                    FileOutputStream fos = new FileOutputStream(path);
                    fos.write(data);
                    fos.close();
                    setResult(Activity.RESULT_OK);
                    finish();
                } catch(Exception e) {
                    Log.e("MyCameraApp", "Failed to write image file: " + e.getMessage(), e);
                    setResult(Activity.RESULT_CANCELED);
                } finally {
                    finish();
                }
            }
        };
        mCamera.takePicture(null, null, pictureCB);
    }
}
