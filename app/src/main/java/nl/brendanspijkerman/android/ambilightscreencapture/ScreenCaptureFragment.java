package nl.brendanspijkerman.android.ambilightscreencapture;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.IntegerRes;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.graphics.Color.toArgb;

/**
 * Created by brendan on 26/01/2018.
 */

public class ScreenCaptureFragment extends Fragment implements View.OnClickListener
{

    private static final String TAG = "ScreenCaptureFragment";

    private static final String STATE_RESULT_CODE = "result_code";
    private static final String STATE_RESULT_DATA = "result_data";

    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private static int IMAGES_PRODUCED;
    private int fpsCounter = 0;

    private int mScreenDensity;
    private Handler mHandler;

    private int mResultCode;
    private Intent mResultData;

    private Surface mSurface;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionManager mMediaProjectionManager;
    private Button mButtonToggle;
    private SurfaceView mSurfaceView;
    private ImageReader mImageReader;
    private GridLayout mGridLayout;
    private TextView mScreenCaptureFpsTextView;

    private final Handler mFpsHandler = new Handler();

    private final Runnable mUpdateFps = new Runnable() {
        public void run() {
            updateFps();
            mFpsHandler.postDelayed(mUpdateFps, 1000); // 1 second
        }
    };

    // Color resolution. Each mCaptureGrid element will be 2^(mCaptureBitDepth - 8) pixels in width x height
    private int mCaptureBitDepth = 16;
    // We want to capture a total of 2^(mCaptureBitDepth - 8) samples (the pixel data is already 8 Bits)
    // To get that number in an X x Y pixel grid, we need the square root of 2^(mCaptureBitDepth - 8)
    // This is the equivalent of (2^(mCaptureBitDepth - 8))^1/2
    // Or simply 2^((mCaptureBitDepth - 8) / 2)
    private int mCaptureGridElementResolution = (int)Math.pow(2, ((mCaptureBitDepth - 8) / 2));
    private int mOutputBitDepth = 16;
    private static int CAPTURE_GRID_X_SEGMENTS = 4;
    private static int CAPTURE_GRID_Y_SEGMENTS = 2;
    // Capture segments, X segments, Y segment
    private int[] mCaptureGrid = {CAPTURE_GRID_X_SEGMENTS, CAPTURE_GRID_Y_SEGMENTS};
    // Grid X, Grid Y, rgb
    private float[][][] mCaptureDataFloat;

    private int[] mVirtualDisplayResolution = {
            mCaptureGrid[0] * mCaptureGridElementResolution,
            mCaptureGrid[1] * mCaptureGridElementResolution
    };

    private Context mContext;

    private int mCaptureCounter = 0;

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mResultCode = savedInstanceState.getInt(STATE_RESULT_CODE);
            mResultData = savedInstanceState.getParcelable(STATE_RESULT_DATA);
        }

    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_screen_capture, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mSurfaceView = (SurfaceView) view.findViewById(R.id.surface);
        ViewGroup.LayoutParams params = new LinearLayout.LayoutParams(
                mVirtualDisplayResolution[0],
                mVirtualDisplayResolution[1]);

        mSurfaceView.setLayoutParams(params);
        mSurface = mSurfaceView.getHolder().getSurface();
        mButtonToggle = (Button) view.findViewById(R.id.toggle);
        mButtonToggle.setOnClickListener(this);

        mGridLayout = (GridLayout) view.findViewById(R.id.screenCaptureGridLayout);
        int gridCount = mCaptureGrid[0] * mCaptureGrid[1];

        mScreenCaptureFpsTextView = (TextView) view.findViewById(R.id.screenCaptureFps);

        mFpsHandler.post(mUpdateFps);

        for (int i = 0; i < gridCount; i ++)
        {
            View gridElement = new View(mContext);
            LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                    200 / CAPTURE_GRID_X_SEGMENTS,
                    100 / CAPTURE_GRID_Y_SEGMENTS
            );
            gridElement.setLayoutParams(gridParams);
            gridElement.setBackgroundColor(Color.BLUE);

            mGridLayout.addView(gridElement);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Activity activity = getActivity();
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mMediaProjectionManager = (MediaProjectionManager)
                activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mResultData != null) {
            outState.putInt(STATE_RESULT_CODE, mResultCode);
            outState.putParcelable(STATE_RESULT_DATA, mResultData);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.toggle:
                if (mVirtualDisplay == null) {
                    startScreenCapture();
                } else {
                    stopScreenCapture();
                }
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "User cancelled");
                Toast.makeText(getActivity(), "Cancelled by user", Toast.LENGTH_SHORT).show();
                return;
            }
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            Log.i(TAG, "Starting screen capture");
            mResultCode = resultCode;
            mResultData = data;
            setUpMediaProjection();
            setUpVirtualDisplay();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
//        stopScreenCapture();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
//        tearDownMediaProjection();
    }

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            FileOutputStream fos = null;
            Bitmap bitmap = null;

            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * mSurfaceView.getWidth();

                    // create bitmap
                    bitmap = Bitmap.createBitmap(
                            mSurfaceView.getWidth() + rowPadding / pixelStride,
                            mSurfaceView.getHeight(),
                            Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

//                    Log.i(TAG, "SurfaceView width: " + Integer.valueOf(mSurfaceView.getWidth()).toString());
//                    Log.i(TAG, "SurfaceView height: " + Integer.valueOf(mSurfaceView.getHeight()).toString());

                    float A = 0;
                    float R = 0;
                    float G = 0;
                    float B = 0;

                    int childCount = mGridLayout.getChildCount();

                    for (int yCoord = 0; yCoord < CAPTURE_GRID_Y_SEGMENTS; yCoord++)
                    {

                        for (int xCoord = 0; xCoord < CAPTURE_GRID_X_SEGMENTS; xCoord++)
                        {
                            int absolutePosition = yCoord * CAPTURE_GRID_X_SEGMENTS + xCoord;
                            int width = bitmap.getWidth();
                            int xCoordinate = xCoord * mCaptureGridElementResolution;
                            int yCoordinate = yCoord * mCaptureGridElementResolution;

                            int[] px = new int[mCaptureGridElementResolution * mCaptureGridElementResolution * 2];
                            bitmap.getPixels(
                                    px,
                                    0,
                                    mCaptureGridElementResolution,
                                    xCoordinate,
                                    yCoordinate,
                                    mCaptureGridElementResolution,
                                    mCaptureGridElementResolution
                            );

                            for (int k = 0; k < px.length; k++)
                            {
                                A += (px[k] >> 24) & 0xff; // or color >>> 24
                                R += (px[k] >> 16) & 0xff;
                                G += (px[k] >>  8) & 0xff;
                                B += (px[k]      ) & 0xff;
                            }

                            A = A / px.length;
                            R = R / px.length;
                            G = G / px.length;
                            B = B / px.length;

                            View v = mGridLayout.getChildAt(absolutePosition);
                            v.setBackgroundColor(Color.argb(
                                    Math.round(A),
                                    Math.round(R),
                                    Math.round(G),
                                    Math.round(B)));
                        }

                    }

                    Log.i(TAG, "AVG Color: "
                            + Float.valueOf(A).toString() + ", "
                    + Float.valueOf(R).toString() + ", "
                    + Float.valueOf(G).toString() + ", "
                    + Float.valueOf(B).toString());

                    // write bitmap to a file
//                    fos = new FileOutputStream(STORE_DIRECTORY + "/myscreen_" + IMAGES_PRODUCED + ".png");
//                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);

                    IMAGES_PRODUCED++;
                    fpsCounter++;
//                    Log.i(TAG, "captured image: " + IMAGES_PRODUCED);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }

                if (bitmap != null) {
                    bitmap.recycle();
                }

                if (image != null) {
                    image.close();
                }
            }
        }
    }

    private void setUpMediaProjection() {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
    }

    private void tearDownMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private void startScreenCapture() {
        Activity activity = getActivity();
        if (mSurface == null || activity == null) {
            return;
        }
        if (mMediaProjection != null) {
            setUpVirtualDisplay();
        } else if (mResultCode != 0 && mResultData != null) {
            setUpMediaProjection();
            setUpVirtualDisplay();
        } else {
            Log.i(TAG, "Requesting confirmation");
            // This initiates a prompt dialog for the user to confirm screen projection.
            startActivityForResult(
                    mMediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_MEDIA_PROJECTION);
        }
    }

    private void setUpVirtualDisplay() {
        Log.i(TAG, "Setting up a VirtualDisplay: " +
                mVirtualDisplayResolution[0] + "x" + mVirtualDisplayResolution[1]  +
                " (" + mScreenDensity + ")");

        mImageReader = ImageReader.newInstance(
                mVirtualDisplayResolution[0],
                mVirtualDisplayResolution[1],
                PixelFormat.RGBA_8888, 2);

//        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
//                "ScreenCapture",
//                mSurfaceView.getWidth(),
//                mSurfaceView.getHeight(),
//                mScreenDensity,
//                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
//                mImageReader.getSurface(),
//                null,
//                null);

        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "ScreenCapture",
                mVirtualDisplayResolution[0],
                mVirtualDisplayResolution[1],
                mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSurface,
                null,
                null);

        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "ScreenCapture",
                mVirtualDisplayResolution[0],
                mVirtualDisplayResolution[1],
                mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(),
                null,
                null);

        mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);

        mButtonToggle.setText(R.string.stop);
    }

    private void stopScreenCapture() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
        mButtonToggle.setText("Start");
    }

    private void updateFps()
    {

        mScreenCaptureFpsTextView.setText("FPS: " + Integer.valueOf(fpsCounter));
        fpsCounter = 0;
    }

}
