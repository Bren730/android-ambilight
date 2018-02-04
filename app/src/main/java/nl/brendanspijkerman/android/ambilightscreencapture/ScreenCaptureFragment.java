package nl.brendanspijkerman.android.ambilightscreencapture;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.Handler;
import android.service.notification.NotificationListenerService;
import android.support.annotation.IntegerRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.graphics.Color.toArgb;

/**
 * Created by brendan on 26/01/2018.
 */

public class ScreenCaptureFragment extends Fragment implements View.OnClickListener, MediaSessionManager.OnActiveSessionsChangedListener
{

    private static final boolean LOG_FPS = true;

    private static final String TAG = "ScreenCaptureFragment";

    private static final String STATE_RESULT_CODE = "result_code";
    private static final String STATE_RESULT_DATA = "result_data";

    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private static final int REQUEST_MEDIA_CONTENT_CONTROL = 2;
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
    private View mAnimatedView;

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
    private static final int CAPTURE_GRID_X_SEGMENTS = 4;
    private static final int CAPTURE_GRID_Y_SEGMENTS = 2;
    // Capture segments, X segments, Y segment
    private int[] mCaptureGrid = {CAPTURE_GRID_X_SEGMENTS, CAPTURE_GRID_Y_SEGMENTS};
    // Grid X, Grid Y, rgb
    private float[][][] mCaptureDataFloat = new float[CAPTURE_GRID_X_SEGMENTS][CAPTURE_GRID_Y_SEGMENTS][4];
    private static final int SMOOTHING_FRAMES = 10;

    private int[] mVirtualDisplayResolution = {
            mCaptureGrid[0] * mCaptureGridElementResolution,
            mCaptureGrid[1] * mCaptureGridElementResolution
    };

    private Context mContext;

    private int mCaptureCounter = 0;

    // USB Variables
    private static final String ACTION_USB_PERMISSION = "nl.brendanspijkerman.android.ambilightscreencapture.USB_PERMISSION";

    UsbManager usbManager;
    UsbDevice device;
    UsbDeviceConnection usbConnection;
    UsbSerialDevice serial;

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

        usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);

        findSerialPortDevice();

    }

    private void findSerialPortDevice() {
        // This snippet will try to open the first encountered usb device connected, excluding usb root hubs
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                int devicePID = device.getProductId();

                Log.i(TAG, String.format("Vendor ID: %d, Product ID: %d", deviceVID, devicePID));

                if (device.getManufacturerName().equals("Particle") && device.getProductName().equals("Photon"))
                {
                    Log.i(TAG, "Found Particle Photon device, trying to establish a connection");

                    usbConnection = usbManager.openDevice(device);
                    serial = UsbSerialDevice.createUsbSerialDevice(device, usbConnection);
                    if (serial != null) {
                        if (serial.open()) { //Set Serial Connection Parameters.
//                            setUiEnabled(true); //Enable Buttons in UI
                            serial.setBaudRate(115200);
                            serial.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serial.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serial.setParity(UsbSerialInterface.PARITY_NONE);
                            serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serial.read(mCallback); //

                        } else {
                            Log.d("SERIAL", "PORT NOT OPEN");
                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                    }
                }

                if (deviceVID != 0x1d6b && (devicePID != 0x0001 && devicePID != 0x0002 && devicePID != 0x0003) && deviceVID != 0x5c6 && devicePID != 0x904c) {

                    // There is a device connected to our Android device. Try to open it as a Serial Port.
                    requestUserPermission();
                    keep = false;
                } else {
//                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
            if (!keep) {
                // There is no USB devices connected (but usb host were listed). Send an intent to MainActivity.
//                Intent intent = new Intent(ACTION_NO_USB);
//                sendBroadcast(intent);
            }
        } else {
            // There is no USB devices connected. Send an intent to MainActivity
//            Intent intent = new Intent(ACTION_NO_USB);
//            sendBroadcast(intent);
        }
    }

    /*
     * Request user permission. The response will be received in the BroadcastReceiver
     */
    private void requestUserPermission() {
        PendingIntent mPendingIntent = PendingIntent.getBroadcast(getActivity().getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
        usbManager.requestPermission(device, mPendingIntent);
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
        mAnimatedView = (View) view.findViewById(R.id.animatedView);

        Animation rotation = AnimationUtils.loadAnimation(mContext, R.anim.rotate);
        rotation.setFillAfter(true);
        mAnimatedView.startAnimation(rotation);

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

                    byte bytes[] = new byte[1 + (2 * 3 * 2)];
                    bytes[0] = (byte)255;
//                        bytes[1] = (byte)255;

                    int pos = 1;

                    for (int i = 0; i < 2; i++)
                    {



                        for (int j = 0; j < 3; j++)
                        {
                            int captureGridElementX = (i == 0) ? 0 : 3;
                            byte high = (byte)Math.floor(mCaptureDataFloat[captureGridElementX][0][j + 1] / 256);
                            byte low = (byte)(mCaptureDataFloat[captureGridElementX][0][j + 1] - (high * 256));

                            bytes[pos] = high;
                            bytes[pos + 1] = low;

                            pos += 2;
                        }
                    }
                    serial.write(bytes);

//                    Log.i(TAG, String.format("AVG Color (ARGB): [%f, %f, %f, %f]", A, R, G, B));

                    IMAGES_PRODUCED++;
                    fpsCounter++;
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

    @Override
    public void onActiveSessionsChanged(List<MediaController> mediaControllers) {
        Log.i(TAG, "sessions changed");
        if (mediaControllers != null && !mediaControllers.isEmpty()) {
//            this.mediaController = mediaControllers.get(0);
//            mediaController.registerCallback(this);
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
        if (LOG_FPS)
        {

            Log.i(TAG, String.format("AVG Color (ARGB): [%f, %f, %f, %f]",
                    mCaptureDataFloat[0][0][0],
                    mCaptureDataFloat[0][0][1],
                    mCaptureDataFloat[0][0][2],
                    mCaptureDataFloat[0][0][3]));
            Log.i(TAG, String.format("FPS: %d", fpsCounter));

        }

        mScreenCaptureFpsTextView.setText(String.format("FPS: %d", fpsCounter));

        fpsCounter = 0;


//        ComponentName cn = new ComponentName(NotificationListenerExample, NotificationListenerExample.class);
//        MediaSessionManager mediaSessionManager = (MediaSessionManager) mContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
//        mediaSessionManager.addOnActiveSessionsChangedListener(this, new ComponentName(mContext, NotificationListenerExample.class));
//        int b = 0;
//        Log.i(TAG, "Playing media: " + mediaSessionManager.getActiveSessions(null).toString());

    }

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            String data = null;
            try {
                data = new String(arg0, "UTF-8");
                data.concat("/n");
//                tvAppend(textView, data);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 200:

                break;
        }
    }

}
