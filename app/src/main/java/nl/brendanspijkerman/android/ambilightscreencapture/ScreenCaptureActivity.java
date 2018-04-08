package nl.brendanspijkerman.android.ambilightscreencapture;

import android.app.Activity;
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
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;

/**
 * Created by brendan on 06/02/2018.
 */

public class ScreenCaptureActivity extends Activity
{

    private static final String TAG = ScreenCaptureActivity.class.getSimpleName();
    private static final int REQUEST_MEDIA_PROJECTION = 1;

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
    private View mAnimatedView;

    // Output bit depth
    private int mOutputBitDepth = 16;
    private static final int OUTPUT_FPS = 60;
    private static final int CAPTURE_MS = (int)Math.floor((1.0 / (float)OUTPUT_FPS) * 1000);
    // We want to capture a total of 2^(mOutputBitDepth - 8) samples (the pixel data is already 8 Bits)
    // To get that number in an X x Y pixel grid, we need the square root of 2^(mOutputBitDepth - 8)
    // This is the equivalent of (2^(mOutputBitDepth - 8))^1/2
    // Or simply 2^((mOutputBitDepth - 8) / 2)
    private int mCaptureGridElementResolution = (int)Math.pow(2, ((mOutputBitDepth - 8) / 2));
    private static final int CAPTURE_GRID_X_SEGMENTS = 4;
    private static final int CAPTURE_GRID_Y_SEGMENTS = 2;
    private static final int SMOOTHING_FRAMES = 10;
    private static final int PIXEL_SUBCHANNELS = 4;
    // Capture segments, X segments, Y segment
    private int[] mCaptureGrid = {CAPTURE_GRID_X_SEGMENTS, CAPTURE_GRID_Y_SEGMENTS};
    // Grid X, Grid Y, rgb
    private float[][][] mCaptureDataFloat = new float[CAPTURE_GRID_X_SEGMENTS][CAPTURE_GRID_Y_SEGMENTS][PIXEL_SUBCHANNELS];
    private float[][][][] mCaptureDataTemporal = new float[SMOOTHING_FRAMES][CAPTURE_GRID_X_SEGMENTS][CAPTURE_GRID_Y_SEGMENTS][PIXEL_SUBCHANNELS];


    private int[] mVirtualDisplayResolution = {
            mCaptureGrid[0] * mCaptureGridElementResolution,
            mCaptureGrid[1] * mCaptureGridElementResolution
    };

    private Context mContext;

    public ScreenCaptureActivity(Context mContext)
    {
        this.mContext = mContext;


    }

    private void checkPermissions()
    {
        Log.i(TAG, "Requesting confirmation");
        // This initiates a prompt dialog for the user to confirm screen projection.
        startActivityForResult(
                mMediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "User cancelled");
                Toast.makeText(this, "Cancelled by user", Toast.LENGTH_SHORT).show();
                return;
            }
            Activity activity = this;
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

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
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

                    for (int ySegment = 0; ySegment < CAPTURE_GRID_Y_SEGMENTS; ySegment++)
                    {

                        for (int xSegment = 0; xSegment < CAPTURE_GRID_X_SEGMENTS; xSegment++)
                        {
                            int absolutePosition = ySegment * CAPTURE_GRID_X_SEGMENTS + xSegment;
                            int width = bitmap.getWidth();
                            int xCoordinate = xSegment * mCaptureGridElementResolution;
                            int yCoordinate = ySegment * mCaptureGridElementResolution;

                            int[] px = new int[mCaptureGridElementResolution * mCaptureGridElementResolution];
                            bitmap.getPixels(
                                    px,
                                    0,
                                    mCaptureGridElementResolution,
                                    xCoordinate,
                                    yCoordinate,
                                    mCaptureGridElementResolution,
                                    mCaptureGridElementResolution
                            );

                            int pixelCounter = 0;

                            for (int k = 0; k < px.length; k++)
                            {
                                A = (px[k] >> 24) & 0xff; // or color >>> 24

                                if (A == 255)
                                {
                                    R += (px[k] >> 16) & 0xff;
                                    G += (px[k] >>  8) & 0xff;
                                    B += (px[k]      ) & 0xff;
                                    pixelCounter++;
                                }

                            }

                            A = A / pixelCounter;
                            R = R / pixelCounter;
                            G = G / pixelCounter;
                            B = B / pixelCounter;

                            View v = mGridLayout.getChildAt(absolutePosition);
                            v.setBackgroundColor(Color.argb(
                                    255,
                                    Math.round(R),
                                    Math.round(G),
                                    Math.round(B)));

                            mCaptureDataFloat[xSegment][ySegment][0] = A * 256;
                            mCaptureDataFloat[xSegment][ySegment][1] = R * 256;
                            mCaptureDataFloat[xSegment][ySegment][2] = G * 256;
                            mCaptureDataFloat[xSegment][ySegment][3] = B * 256;
                        }

                    }

//                    Log.i(TAG, String.format("AVG Color (ARGB): [%f, %f, %f, %f]", A, R, G, B));


                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {

                if (bitmap != null) {
                    bitmap.recycle();
                }

                if (image != null) {
                    image.close();
                }
            }
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

        mImageReader.setOnImageAvailableListener(new ScreenCaptureActivity.ImageAvailableListener(), mHandler);

        mButtonToggle.setText(R.string.stop);
    }

    private void setUpMediaProjection() {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
    }

}
