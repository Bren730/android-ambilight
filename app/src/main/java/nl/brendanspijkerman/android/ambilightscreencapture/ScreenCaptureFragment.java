package nl.brendanspijkerman.android.ambilightscreencapture;

import android.app.Activity;
import android.app.Fragment;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;

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
    private long lastSerialWrite = 0;

    private int mScreenDensity;
    private Handler mHandler;
    ScheduledThreadPoolExecutor outputScheduler = new ScheduledThreadPoolExecutor(20);

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

//    private final Handler mSerialOutputHandler = new Handler();

//    class SerialRunnable implements Runnable {
//
//        UsbSerialDevice ser;
//
//        SerialRunnable(UsbSerialDevice ser)
//        {
//            this.ser = ser;
//        }
//
//        public void run() {
//            outputSerial(this.ser);
//            mSerialOutputHandler.postDelayed(this, OUTPUT_MS);
//        }
//    };

    private final Handler mCalculationHandler = new Handler();

    class CalculationRunnable implements Runnable {

        UsbSerialDevice ser;
        GridElement captureGrid[][];
        long time;

        CalculationRunnable(UsbSerialDevice ser, GridElement[][] captureGrid)
        {
            this.captureGrid = captureGrid;
            this.ser = ser;
        }

        public void run() {

                long ms;

                ms = System.currentTimeMillis() - this.time;
//                Log.i(TAG, String.format("loop took %d ms", ms));

                this.time = System.currentTimeMillis();
                long time = System.currentTimeMillis();

                for (int x = 0; x < CAPTURE_GRID_X_SEGMENTS; x++)
                {
                    for (int y = 0; y < CAPTURE_GRID_Y_SEGMENTS; y++)
                    {
                        this.captureGrid[x][y].addFrame(this.captureGrid[x][y].getTemporalAverageColor());


                    }
                }



                ms = System.currentTimeMillis() - time;
//            Log.i(TAG, String.format("Calculating average color took %d ms", ms));

                time = System.currentTimeMillis();

                serialWriteAmbilightData(this.ser);

                ms = System.currentTimeMillis() - time;

//            Log.i(TAG, String.format("Serial write took %d ms", ms));
//            mCalculationHandler.postDelayed(this, 5);
//                mCalculationHandler.post(this);
//            mCalculationHandler.post(this);
            mCalculationHandler.postDelayed(this, 0);
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
    private static final int SMOOTHING_FRAMES = 40;
    private static final int PIXEL_SUBCHANNELS = 4;
    private boolean receivedNewFrame = false;
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
    private UsbDevice particleDevice;
    private UsbDeviceConnection usbConnection;
    private UsbSerialDevice serial;
    private boolean hasUsbPermissions = false;
    private boolean isWriting = false;

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
        scanSerialDevices();

        BroadcastReceiver mUsbAttachReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
//                    showToast("New device connected");
                    scanSerialDevices();
                }
            }
        };

        BroadcastReceiver mUsbDetachReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null) {
                        showToast(String.format("%s disconnected", device.getProductName()));
                    }
                }
            }
        };

        BroadcastReceiver mScreenPowerStateReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (action.equals(Intent.ACTION_SCREEN_ON))
                {
                    Log.e(TAG, "Screen on");
                    serialWriteAmbilightStart(serial);
                }

                if (action.equals(Intent.ACTION_SCREEN_OFF))
                {
                    Log.e(TAG, "Screen off");
                    serialWriteAmbilightStop(serial);
                }
            }
        };

        IntentFilter usbAttachedFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        this.getContext().registerReceiver(mUsbAttachReceiver , usbAttachedFilter);

        IntentFilter usbDetachedFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        this.getContext().registerReceiver(mUsbDetachReceiver , usbDetachedFilter);

        IntentFilter screenPowerStateReceiverFilter = new IntentFilter();
        screenPowerStateReceiverFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenPowerStateReceiverFilter.addAction(Intent.ACTION_SCREEN_OFF);
        this.getContext().registerReceiver(mScreenPowerStateReceiver , screenPowerStateReceiverFilter);

    }

    private void initCaptureGrid()
    {
        for (int i = 0; i < CAPTURE_GRID_X_SEGMENTS; i++)
        {
            for (int j = 0; j < CAPTURE_GRID_Y_SEGMENTS; j++)
            {
                this.mCaptureGrid[i][j] = new GridElement(SMOOTHING_FRAMES, 5);
            }
        }
    }

    private void showToast(String message)
    {
        Context context = this.getContext();
        int duration = Toast.LENGTH_LONG;

        Toast toast = Toast.makeText(context, message, duration);
        toast.show();
    }

    private void scanSerialDevices()
    {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            UsbDevice device;
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                int devicePID = device.getProductId();

                Log.i(TAG, String.format("Vendor ID: %d, Product ID: %d", deviceVID, devicePID));

                if (device.getManufacturerName().equals("Particle") || device.getManufacturerName().equals("Teensyduino"))
                {
                    Log.i(TAG, "Found Particle device, trying to establish a connection");
                    requestUSBPermission(device);

                    // TODO: properly implement USB permissions and callbacks

                }

            }

        }
        else
        {
            // There is no USB devices connected. Send an intent to MainActivity
//            Intent intent = new Intent(ACTION_NO_USB);
//            sendBroadcast(intent);
            Log.e(TAG, "No USB devices connected");
            showToast("no USB devices connected");
        }
    }

    private void connectToSerialDevice(UsbDevice device)
    {
        hasUsbPermissions = true;
        particleDevice = device;
        usbConnection = usbManager.openDevice(particleDevice);
        serial = UsbSerialDevice.createUsbSerialDevice(particleDevice, usbConnection);
        if (serial != null) {
            if (serial.open()) { //Set Serial Connection Parameters.
//                            setUiEnabled(true); //Enable Buttons in UI
                serial.setBaudRate(1000000);
                serial.setDataBits(UsbSerialInterface.DATA_BITS_8);
                serial.setStopBits(UsbSerialInterface.STOP_BITS_1);
                serial.setParity(UsbSerialInterface.PARITY_NONE);
                serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                serial.read(mCallback);

//                SerialRunnable runnable = new SerialRunnable(serial);
//                mSerialOutputHandler.post(runnable);

//                CalculationRunnable runnable = new CalculationRunnable(serial, mCaptureGrid);
//                mCalculationHandler.post(runnable);

                new Timer().scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {


                        while(isWriting)
                        {

                        }

                        for (int x = 0; x < CAPTURE_GRID_X_SEGMENTS; x++)
                        {
                            for (int y = 0; y < CAPTURE_GRID_Y_SEGMENTS; y++)
                            {
                                mCaptureGrid[x][y].addFrame(mCaptureGrid[x][y].getTemporalAverageColor());


                            }
                        }

                        serialWriteAmbilightData(serial);

//                        try {
//                            Thread.sleep(2);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }

                    }
                }, 0, 1);


//                long period = 10; // the period between successive executions
//                outputScheduler.scheduleAtFixedRate(runnable, 0, period, TimeUnit.MILLISECONDS);

                showToast(String.format("Connected to %s", particleDevice.getProductName()));

            } else {
                Log.d("SERIAL", "PORT NOT OPEN");
            }
        }
        else
        {
            Log.d("SERIAL", "PORT IS NULL");
        }
    }

    /*
     * Request user permission. The response will be received in the BroadcastReceiver
     */
    private void requestUSBPermission(final UsbDevice device) {
        PendingIntent mPendingIntent = PendingIntent.getBroadcast(getActivity().getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);

        BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (action.equals(ACTION_USB_PERMISSION)) {

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        showToast("Permission granted");
                        connectToSerialDevice(device);
                    }
                    else
                    {
                        showToast("Permission denied");
                    }
//
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        this.getContext().registerReceiver(mUsbReceiver, filter);

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
        Log.e(TAG, "App paused");
//        stopScreenCapture();

        printDisplayState();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.e(TAG, "App stopped");

        printDisplayState();
    }

    private void printDisplayState()
    {
        Display mainDisplay = getActivity().getWindowManager().getDefaultDisplay();
        int state = mainDisplay.getState();

        WindowManager manager = getActivity().getWindowManager();



        String stateString = "";

        switch(state)
        {
            case Display.STATE_DOZE:
                stateString = "STATE_DOZE";
                break;

            case Display.STATE_DOZE_SUSPEND:
                stateString = "STATE_DOZE_SUSPEND";
                break;

            case Display.STATE_OFF:
                stateString = "STATE_OFF";
                break;

            case Display.STATE_ON:
                stateString = "STATE_ON";
                break;

            case Display.STATE_UNKNOWN:
                stateString = "STATE_UNKNOWN";
                break;

            case Display.STATE_VR:
                stateString = "STATE_VR";
                break;
        }

        Log.e(TAG, String.format("Display state %s (%d)", stateString, mainDisplay.getState()));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "App destroyed");

        showToast("Shutting down ambilight");

        tearDownMediaProjection();
        serial.close();

        Display mainDisplay = getActivity().getWindowManager().getDefaultDisplay();
        mainDisplay.getState();
        Log.e(TAG, String.format("Display state %d", mainDisplay.getState()));
    }

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            Bitmap bitmap = null;

            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    receivedNewFrame = true;
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

                            double[] color = new double[4];

                            for (int i = 0; i < px.length; i++)
                            {
                                color[0] += (px[i] >> 24) & 0xff;
                                color[1] += (px[i] >> 16) & 0xff;
                                color[2] += (px[i] >>  8) & 0xff;
                                color[3] += (px[i]      ) & 0xff;
                            }

                            mCaptureGrid[xSegment][ySegment].addFrame(color);

                            int R, G, B;

                            R = (int)Math.round(mCaptureGrid[xSegment][ySegment].getTemporalAverageColor()[1] / 256);
                            G = (int)Math.round(mCaptureGrid[xSegment][ySegment].getTemporalAverageColor()[2] / 256);
                            B = (int)Math.round(mCaptureGrid[xSegment][ySegment].getTemporalAverageColor()[3] / 256);

//                            Log.i(TAG, "Post-division");
//                            Log.i(TAG, Arrays.toString(color) );

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

                    receivedNewFrame = false;
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

//            Log.i(TAG, String.format("AVG Color (ARGB): [%f, %f, %f, %f]",
//                    mCaptureDataDouble[0][0][0],
//                    mCaptureDataDouble[0][0][1],
//                    mCaptureDataDouble[0][0][2],
//                    mCaptureDataDouble[0][0][3]));

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

//    private void outputSerial(UsbSerialDevice ser)
//    {
//        // The packet is built up as follows:
//        // [Header][x_segments][y_segments][data_length][data]
//        // Every segment holds a 16-bit RGB value, big endian in RGB sequence
//        // Example packet:
//        // Header[0xABABABAB] x_segments[0x10] y_segments[0x09] data_length[90] data[0xFF][0x80][0xFF][0x80][0xFF][0x80]...
//
//        try
//        {
//            byte bytes[] = new byte[4 + 2 + 1 + (CAPTURE_GRID_X_SEGMENTS * CAPTURE_GRID_Y_SEGMENTS * 3 * 2)];
//            bytes[0] = HEADER_0;
//            bytes[1] = HEADER_1;
//            bytes[2] = HEADER_2;
//            bytes[3] = HEADER_3;
//
//            bytes[CMD_FUNCTION] = FUNCTION_DATA;
//
//            bytes[CMD_DATA_LENGTH] = 0x03;
//            bytes[CMD_DATA_LENGTH + 1] = 0x60;
//
//            int pos = 1;
//
//            for (int y = 0; y < CAPTURE_GRID_Y_SEGMENTS; y++)
//            {
//                for (int x = 0; x < CAPTURE_GRID_X_SEGMENTS; x++)
//                {
//                    int arrayPosition = ((CAPTURE_GRID_X_SEGMENTS * y) + x) * 3 * 2 + CMD_DATA;
//
//                    double color[] = mCaptureGrid[x][y].getTemporalAverageColor();
//
//                    bytes[arrayPosition + 0] = (byte)((int)color[1] >> 8);
//                    bytes[arrayPosition + 1] = (byte)((int)color[1] & 0xff);
//
//                    bytes[arrayPosition + 2] = (byte)((int)color[2] >> 8);
//                    bytes[arrayPosition + 3] = (byte)((int)color[2] & 0xff);
//
//                    bytes[arrayPosition + 4] = (byte)((int)color[3] >> 8);
//                    bytes[arrayPosition + 5] = (byte)((int)color[3] & 0xff);
//
//                }
//            }
//
//            if (ser != null)
//            {
//                ser.write(bytes);
//                outputFpsCounter++;
//            }
//
//        }
//        catch (Exception e)
//        {
//            Log.e(TAG, e.toString());
//        }
//    }

    private void serialWriteAmbilightData(UsbSerialDevice ser)
    {
//        Log.i(TAG, "Writing to serial");
        // The packet is built up as follows:
        // [Header][x_segments][y_segments][data_length][data]
        // Every segment holds a 16-bit RGB value, big endian in RGB sequence
        // Example packet:
        // Header[0xABABABAB] x_segments[0x10] y_segments[0x09] data_length[90] data[0xFF][0x80][0xFF][0x80][0xFF][0x80]...

        try
        {
            isWriting = true;
            byte bytes[] = new byte[4 + 2 + 1 + (CAPTURE_GRID_X_SEGMENTS * CAPTURE_GRID_Y_SEGMENTS * 3 * 2)];
            bytes[0] = HEADER_0;
            bytes[1] = HEADER_1;
            bytes[2] = HEADER_2;
            bytes[3] = HEADER_3;

            bytes[CMD_FUNCTION] = FUNCTION_DATA;

            bytes[CMD_DATA_LENGTH] = 0x03;
            bytes[CMD_DATA_LENGTH + 1] = 0x60;

            int pos = 1;

            double average[] = {0, 0, 0, 0};
            for (int y = 0; y < CAPTURE_GRID_Y_SEGMENTS; y++)
            {
                for (int x = 0; x < CAPTURE_GRID_X_SEGMENTS; x++)
                {
                    int arrayPosition = ((CAPTURE_GRID_X_SEGMENTS * y) + x) * 3 * 2 + CMD_DATA;

                    double color[] = mCaptureGrid[x][y].getTemporalAverageColor();

                    average[0] += color[0];
                    average[1] += color[1];
                    average[2] += color[2];
                    average[3] += color[3];

//                    if (color[1] < 1000)
//                    {
//                        Log.e(TAG, String.format("Low: %f", color[1]));
//                    }
//
//                    if (color[1] > 40000)
//                    {
//                        Log.e(TAG, String.format("High: %f, %d, %d", color[1], x, y));
//                    }

                    bytes[arrayPosition + 0] = (byte)((int)color[1] >> 8);
                    bytes[arrayPosition + 1] = (byte)((int)color[1] & 0xff);

                    bytes[arrayPosition + 2] = (byte)((int)color[2] >> 8);
                    bytes[arrayPosition + 3] = (byte)((int)color[2] & 0xff);

                    bytes[arrayPosition + 4] = (byte)((int)color[3] >> 8);
                    bytes[arrayPosition + 5] = (byte)((int)color[3] & 0xff);

                }
            }

            average[0] = average[0] / (CAPTURE_GRID_X_SEGMENTS * CAPTURE_GRID_Y_SEGMENTS);
            average[1] = average[1] / (CAPTURE_GRID_X_SEGMENTS * CAPTURE_GRID_Y_SEGMENTS);
            average[2] = average[2] / (CAPTURE_GRID_X_SEGMENTS * CAPTURE_GRID_Y_SEGMENTS);
            average[3] = average[3] / (CAPTURE_GRID_X_SEGMENTS * CAPTURE_GRID_Y_SEGMENTS);

//            Log.i(TAG, String.format("avg: %d, %d, %d", (int)average[1], (int)average[2], (int)average[3]));

            if (ser != null)
            {
                long time = System.currentTimeMillis();
//                Log.i(TAG, String.format("Start Writing %d", time));
                ser.write(bytes);
                outputFpsCounter++;

//                Log.i(TAG, String.format("Done Writing %d, took %d, last frame %d", time, System.currentTimeMillis() - time, System.currentTimeMillis() - lastSerialWrite));
            }

        }
        catch (Exception e)
        {
            Log.e(TAG, e.toString());
            isWriting = false;
        }

        lastSerialWrite = System.currentTimeMillis();
    }

    private void serialWriteAmbilightStop(UsbSerialDevice ser)
    {
        Log.i(TAG, "Sending stop packet");

        byte bytes[] = new byte[4 + 2 + 1];
        bytes[0] = HEADER_0;
        bytes[1] = HEADER_1;
        bytes[2] = HEADER_2;
        bytes[3] = HEADER_3;

        bytes[CMD_FUNCTION] = FUNCTION_STOP;

        bytes[CMD_DATA_LENGTH] = 0x00;
        bytes[CMD_DATA_LENGTH + 1] = 0x00;

        if (ser != null)
        {
            ser.write(bytes);
        }
    }

    private void serialWriteAmbilightStart(UsbSerialDevice ser)
    {
        Log.i(TAG, "Sending start packet");

        byte bytes[] = new byte[4 + 2 + 1];
        bytes[0] = HEADER_0;
        bytes[1] = HEADER_1;
        bytes[2] = HEADER_2;
        bytes[3] = HEADER_3;

        bytes[CMD_FUNCTION] = FUNCTION_START;

        bytes[CMD_DATA_LENGTH] = 0x00;
        bytes[CMD_DATA_LENGTH + 1] = 0x00;

        if (ser != null)
        {
            ser.write(bytes);
        }
    }

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            String data = null;

            if (arg0 != null)
            {
                if (arg0[0] == -1)
                {
                    isWriting = false;
                }
                else
                {
                    isWriting = false;
                }
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        showToast(String.format("%d", requestCode));
        switch (requestCode) {
            case 200:
                break;
        }
    }

}
