package nl.brendanspijkerman.android.ambilightscreencapture;

import android.app.Activity;
import android.app.Fragment;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.brendanspijkerman.android.ambilightscreencapture.model.GridElement;

/**
 * Created by brendan on 26/01/2018.
 */

public class ScreenCaptureFragment extends Fragment implements View.OnClickListener, MediaSessionManager.OnActiveSessionsChangedListener
{

    private static final boolean LOG_FPS = true;

    // variable positions
    private static final byte CMD_FUNCTION = (byte)6;
    private static final byte CMD_FUNCTION_LENGTH = (byte)1;
    private static final byte CMD_DATA_LENGTH = (byte)4;
    private static final byte CMD_DATA_LENGTH_LENGTH = (byte)2;
    private static final byte CMD_DATA = (byte)7;

// Header values
    private static final byte HEADER_LENGTH = (byte)4;
    private static final byte HEADER_0 = (byte)0x54;
    private static final byte HEADER_1 = (byte)0xB5;
    private static final byte HEADER_2 = (byte)0xFF;
    private static final byte HEADER_3 = (byte)0xFE;

// Function values
    private static final byte FUNCTION_START = (byte)0x00;
    private static final byte FUNCTION_DATA = (byte)0x01;
    private static final byte FUNCTION_PAUSE = (byte)0x02;
    private static final byte FUNCTION_STOP = (byte)0x03;

    private static final String TAG = "ScreenCaptureFragment";

    private static final String STATE_RESULT_CODE = "result_code";
    private static final String STATE_RESULT_DATA = "result_data";

    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private static final int REQUEST_MEDIA_CONTENT_CONTROL = 2;
    private static int IMAGES_PRODUCED;
    private int screenFpsCounter = 0;
    private int outputFpsCounter = 0;

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

    private final Handler mSerialOutputHandler = new Handler();

    class SerialRunnable implements Runnable {

        UsbSerialDevice ser;

        SerialRunnable(UsbSerialDevice ser)
        {
            this.ser = ser;
        }

        public void run() {
            outputSerial(this.ser);
            mSerialOutputHandler.postDelayed(this, OUTPUT_MS);
        }
    };

    // Output bit depth
    private int mOutputBitDepth = 16;
    private static final int OUTPUT_FPS = 100;
    private static final int OUTPUT_MS = (int)Math.floor((1.0 / (float)OUTPUT_FPS) * 1000);

    // To get N-bit depth data, we need to sample multiple pixels. Since every pixel is already 8 bits,
    // We need 2^(N - 8) pixels to sample. We want these pixels to be sampled from a square portion of the video frame
    // To get the x-y dimensions of our sample segment, we need the square root of 2^(mOutputBitDepth - 8)
    // This is the equivalent of (2^(mOutputBitDepth - 8))^1/2
    // Or simply 2^((mOutputBitDepth - 8) / 2)
    private int mCaptureGridElementResolution = (int)Math.pow(2, ((mOutputBitDepth - 8) / 2));
    private static final int CAPTURE_GRID_X_SEGMENTS = 16;
    private static final int CAPTURE_GRID_Y_SEGMENTS = 9;
    private static final int CAPTURE_GRID_COUNT = CAPTURE_GRID_X_SEGMENTS * CAPTURE_GRID_Y_SEGMENTS;
    private static final int SMOOTHING_FRAMES = 10;
    private static final int PIXEL_SUBCHANNELS = 4;
    // Grid X, Grid Y, rgb
    private double[][][] mCaptureDataDouble = new double[CAPTURE_GRID_X_SEGMENTS][CAPTURE_GRID_Y_SEGMENTS][PIXEL_SUBCHANNELS];

    private int[] mVirtualDisplayResolution = {
            CAPTURE_GRID_X_SEGMENTS * mCaptureGridElementResolution,
            CAPTURE_GRID_Y_SEGMENTS * mCaptureGridElementResolution
    };

    private GridElement mCaptureGrid[][] = new GridElement[CAPTURE_GRID_X_SEGMENTS][CAPTURE_GRID_Y_SEGMENTS];

    private Context mContext;

    private int mCaptureCounter = 0;

    // USB Variables
    private static final String ACTION_USB_PERMISSION = "nl.brendanspijkerman.android.ambilightscreencapture.USB_PERMISSION";

    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection usbConnection;
    private UsbSerialDevice serial;
    private boolean hasUsbPermissions = false;

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
        initCaptureGrid();
        usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        findSerialPortDevice();

    }

    private void initCaptureGrid()
    {
        for (int i = 0; i < CAPTURE_GRID_X_SEGMENTS; i++)
        {
            for (int j = 0; j < CAPTURE_GRID_Y_SEGMENTS; j++)
            {
                this.mCaptureGrid[i][j] = new GridElement(
                        mCaptureGridElementResolution,
                        mCaptureGridElementResolution,
                        SMOOTHING_FRAMES,
                        5);
            }
        }
    }

    private void findSerialPortDevice() {

        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                int devicePID = device.getProductId();

                Log.i(TAG, String.format("Vendor ID: %d, Product ID: %d", deviceVID, devicePID));

                if (device.getManufacturerName().equals("Particle"))
                {
                    Log.i(TAG, "Found Particle device, trying to establish a connection");
                    requestUserPermission();

                    // TODO: properly implement USB permissions and callbacks

                    hasUsbPermissions = true;
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
            Log.e(TAG, "No USB devices connected");
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
        int gridCount = CAPTURE_GRID_X_SEGMENTS * CAPTURE_GRID_Y_SEGMENTS;

        mScreenCaptureFpsTextView = (TextView) view.findViewById(R.id.screenCaptureFps);
        mAnimatedView = (View) view.findViewById(R.id.animatedView);

        Animation rotation = AnimationUtils.loadAnimation(mContext, R.anim.translate_and_rotate);
        rotation.setFillAfter(true);
        mAnimatedView.startAnimation(rotation);

        mFpsHandler.post(mUpdateFps);
        SerialRunnable runnable = new SerialRunnable(serial);
        mSerialOutputHandler.post(runnable);

        for (int i = 0; i < gridCount; i ++)
        {
            View gridElement = new View(mContext);

            if (i % 3 == 0)
            {
                gridElement.setBackgroundColor(getResources().getColor(R.color.lb_action_text_color));
            }
            else
            {
                gridElement.setBackgroundColor(getResources().getColor(R.color.lb_basic_card_info_bg_color));
            }

            LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                    200 / CAPTURE_GRID_X_SEGMENTS,
                    100 / CAPTURE_GRID_Y_SEGMENTS
            );
            gridElement.setLayoutParams(gridParams);
//            gridElement.setBackgroundColor(Color.BLUE);

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

                            mCaptureGrid[xSegment][ySegment].setPixels(px);
                            double[] color;
                            color = mCaptureGrid[xSegment][ySegment].getTemporalAverageColor();

                            Log.i(TAG, "Pre-division");
                            Log.i(TAG, Arrays.toString(color) );

                            int R, G, B;

                            R = (int)Math.round(color[1] / 256);
                            G = (int)Math.round(color[2] / 256);
                            B = (int)Math.round(color[3] / 256);



                            Log.i(TAG, "Post-division");
                            Log.i(TAG, Arrays.toString(color) );

                            View v = mGridLayout.getChildAt(absolutePosition);
                            v.setBackgroundColor(Color.argb(
                                    255,
                                    R,
                                    G,
                                    B));

                            mCaptureDataDouble[xSegment][ySegment][0] = color[0];
                            mCaptureDataDouble[xSegment][ySegment][1] = color[1];
                            mCaptureDataDouble[xSegment][ySegment][2] = color[2];
                            mCaptureDataDouble[xSegment][ySegment][3] = color[3];
                        }

                    }

//                    Log.i(TAG, String.format("AVG Color (ARGB): [%f, %f, %f, %f]", A, R, G, B));

                    IMAGES_PRODUCED++;
                    screenFpsCounter++;
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
                    mCaptureDataDouble[0][0][0],
                    mCaptureDataDouble[0][0][1],
                    mCaptureDataDouble[0][0][2],
                    mCaptureDataDouble[0][0][3]));

            String fps = String.format("FPS: %d", screenFpsCounter);
            String outputFps = String.format("Serial fps: %d", outputFpsCounter);

            Log.i(TAG, fps);
            Log.i(TAG, outputFps);

        }

        String msg = String.format("FPS, output: %d, %d", screenFpsCounter, outputFpsCounter);
        mScreenCaptureFpsTextView.setText(msg);

        screenFpsCounter = 0;
        outputFpsCounter = 0;


//        ComponentName cn = new ComponentName(NotificationListenerExample, NotificationListenerExample.class);
//        MediaSessionManager mediaSessionManager = (MediaSessionManager) mContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
//        mediaSessionManager.addOnActiveSessionsChangedListener(this, new ComponentName(mContext, NotificationListenerExample.class));
//        int b = 0;
//        Log.i(TAG, "Playing media: " + mediaSessionManager.getActiveSessions(null).toString());

    }

    private void outputSerial(UsbSerialDevice ser)
    {
        // The packet is built up as follows:
        // [Header][x_segments][y_segments][data_length][data]
        // Every segment holds a 16-bit RGB value, big endian in RGB sequence
        // Example packet:
        // Header[0xABABABAB] x_segments[0x10] y_segments[0x09] data_length[90] data[0xFF][0x80][0xFF][0x80][0xFF][0x80]...

        try
        {
            byte bytes[] = new byte[4 + 2 + 1 + (CAPTURE_GRID_X_SEGMENTS * CAPTURE_GRID_Y_SEGMENTS * 3 * 2)];
            bytes[0] = HEADER_0;
            bytes[1] = HEADER_1;
            bytes[2] = HEADER_2;
            bytes[3] = HEADER_3;

            bytes[CMD_FUNCTION] = FUNCTION_DATA;

            bytes[CMD_DATA_LENGTH] = 0x03;
            bytes[CMD_DATA_LENGTH + 1] = 0x60;

            int pos = 1;

            for (int y = 0; y < CAPTURE_GRID_Y_SEGMENTS; y++)
            {
                for (int x = 0; x < CAPTURE_GRID_X_SEGMENTS; x++)
                {
                    int arrayPosition = ((CAPTURE_GRID_X_SEGMENTS * y) + x) * 3 * 2 + CMD_DATA;

                    double color[] = mCaptureGrid[x][y].getTemporalAverageColor();

                    bytes[arrayPosition + 0] = (byte)((int)color[1] >> 8);
                    bytes[arrayPosition + 1] = (byte)((int)color[1] & 0xff);

                    bytes[arrayPosition + 2] = (byte)((int)color[2] >> 8);
                    bytes[arrayPosition + 3] = (byte)((int)color[2] & 0xff);

                    bytes[arrayPosition + 4] = (byte)((int)color[3] >> 8);
                    bytes[arrayPosition + 5] = (byte)((int)color[3] & 0xff);

                }
            }

            if (ser != null)
            {
                ser.write(bytes);
                outputFpsCounter++;
            }

        }
        catch (Exception e)
        {
            Log.e(TAG, e.toString());
        }
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
