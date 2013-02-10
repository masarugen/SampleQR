
package me.gensan.sampleqr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

public class SampleQrActivity extends Activity implements SurfaceHolder.Callback,
        AutoFocusCallback, PreviewCallback {

    private final String TAG = "SampleQrActivity";

    private static final int MIN_PREVIEW_PIXELS = 470 * 320;
    private static final int MAX_PREVIEW_PIXELS = 1280 * 720;
    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private Point mPreviewSize;
    private float mPreviewWidthRatio;
    private float mPreviewHeightRatio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_qr);
        initCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCamera = Camera.open();
        setPreviewSize();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(holder);
            }
        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(mPreviewSize.x, mPreviewSize.y);
            mCamera.setParameters(parameters);
            mCamera.startPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    private void initCamera() {
        mSurfaceView = (SurfaceView) findViewById(R.id.preview);
        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(this);
    }

    private void setPreviewSize() {
        Camera.Parameters parameters = mCamera.getParameters();
        List<Size> rawPreviewSizes = parameters.getSupportedPreviewSizes();
        List<Size> supportPreviewSizes = new ArrayList<Size>(rawPreviewSizes);
        Collections.sort(supportPreviewSizes, new Comparator<Size>() {
            @Override
            public int compare(Size lSize, Size rSize) {
                int lPixels = lSize.width * lSize.height;
                int rPixels = rSize.width * rSize.height;
                if (rPixels < lPixels) {
                    return -1;
                }
                if (rPixels > lPixels) {
                    return 1;
                }
                return 0;
            }
        });
        WindowManager manager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        int screenWidth = display.getWidth();
        int screenHeight = display.getHeight();
        float screenAspectRatio = (float) screenWidth / (float) screenHeight;
        Point bestSize = null;
        float diff = Float.POSITIVE_INFINITY;
        for (Size supportPreviewSize : supportPreviewSizes) {
            int supportWidth = supportPreviewSize.width;
            int supportHeight = supportPreviewSize.height;
            int pixels = supportWidth * supportHeight;
            if (pixels < MIN_PREVIEW_PIXELS || pixels > MAX_PREVIEW_PIXELS) {
                continue;
            }
            boolean isPortrait = supportWidth < supportHeight;
            int previewWidth = isPortrait ? supportHeight : supportWidth;
            int previewHeight = isPortrait ? supportWidth : supportHeight;
            if (previewWidth == screenWidth && previewHeight == screenHeight) {
                mPreviewSize = new Point(supportWidth, supportHeight);
                mPreviewWidthRatio = 1;
                mPreviewHeightRatio = 1;
                return;
            }
            float aspectRatio = (float) previewWidth / (float) previewHeight;
            float newDiff = Math.abs(aspectRatio - screenAspectRatio);
            if (newDiff < diff) {
                bestSize = new Point(supportWidth, supportHeight);
                diff = newDiff;
            }
        }
        if (bestSize == null) {
            Size defaultSize = parameters.getPreviewSize();
            bestSize = new Point(defaultSize.width, defaultSize.height);
        }
        mPreviewSize = bestSize;
        mPreviewWidthRatio = (float) mPreviewSize.x / (float) screenWidth;
        mPreviewHeightRatio = (float) mPreviewSize.y / (float) screenHeight;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Result rawResult = null;
        View target = (View) findViewById(R.id.target);
        int left = (int) (target.getLeft() * mPreviewWidthRatio);
        int top = (int) (target.getTop() * mPreviewHeightRatio);
        int width = (int) (target.getWidth() * mPreviewWidthRatio);
        int height = (int) (target.getHeight() * mPreviewHeightRatio);
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data, mPreviewSize.x,
                mPreviewSize.y, left, top, width, height, false);
        if (source != null) {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            MultiFormatReader multiFormatReader = new MultiFormatReader();
            try {
                rawResult = multiFormatReader.decode(bitmap);
                Toast.makeText(getApplicationContext(), rawResult.getText(), Toast.LENGTH_LONG)
                        .show();
            } catch (ReaderException re) {
                Toast.makeText(getApplicationContext(), "read error: " + re.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        if (success) {
            mCamera.setOneShotPreviewCallback(this);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mCamera != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mCamera.autoFocus(this);
            }
        }
        return super.onTouchEvent(event);
    }
}
