package com.tyroneil.experimentalapp;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.util.AndroidException;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class MainActivity extends Activity {
    private static Context context;

    // shared variable
    private static CameraManager cameraManager;
    private static String[] cameraIdList;

    static Handler backgroundHandler;

    // current cameraDevice variable
    private static String cameraId;
    private static CameraDevice cameraDevice;
    private static CameraCharacteristics cameraCharacteristics;

    static CameraCaptureSession captureSession;
    private static int sensorOrientation;

    // variable for preview
    static CaptureRequest.Builder previewRequestBuilder;
    private static Size previewSize;

    // variable for capture
    private CaptureRequest.Builder captureRequestBuilder;
    private CaptureResult captureResult;
    private ImageReader imageReader;
    private DngCreator dngCreator;
    private static int captureFormat;
    private Size captureSize;

    // capture parameters
    static int autoMode;  // CONTROL_MODE (0/1 OFF/AUTO)
    static final int AUTO_MODE_OFF = 0;
    static final int AUTO_MODE_AUTO = 1;

    static int aeMode;  // CONTROL_AE_MODE (0/1 OFF/ON)
    static final int AE_MODE_OFF = 0;
    static final int AE_MODE_ON = 1;

    static int afMode;  // CONTROL_AF_MODE (0/4 OFF/CONTINUOUS_PICTURE)
    static final int AF_MODE_OFF = 0;
    static final int AF_MODE_CONTINUOUS_PICTURE = 4;

    static int awbMode;  // CONTROL_AWB_MODE (0/1 OFF/AUTO)
    static final int AWB_MODE_OFF = 0;
    static final int AWB_MODE_AUTO = 1;

    static long exposureTime;  // SENSOR_EXPOSURE_TIME
    static Range<Long> SENSOR_INFO_EXPOSURE_TIME_RANGE;  // constant for each camera device

    static int sensitivity;  // SENSOR_SENSITIVITY
    static Range<Integer> SENSOR_INFO_SENSITIVITY_RANGE;  // constant for each camera device

    static float focusDistance;  // LENS_FOCUS_DISTANCE
    static float LENS_INFO_HYPERFOCAL_DISTANCE;  // constant for each camera device
    static float LENS_INFO_MINIMUM_FOCUS_DISTANCE;  // constant for each camera device

    static float focalLength;  // LENS_FOCAL_LENGTH
    static float[] LENS_INFO_AVAILABLE_FOCAL_LENGTHS;  // constant for each camera device

    static float aperture;  // LENS_APERTURE
    static float[] LENS_INFO_AVAILABLE_APERTURES;  // constant for each camera device

    static int opticalStabilizationMode;  // LENS_OPTICAL_STABILIZATION_MODE
    static int[] LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION;  // constant for each camera device

    // debug Tool
    static TextView errorMessageTextView;

    static TextView debugMessageTextView;
    static String debugMessage = "";
    static int debugCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MainActivity.context = getApplicationContext();

        UIOperator.initiateContentCameraControl(this);
        UIOperator.initiateContentRangeControl(this);
        UIOperator.initiateContentListControl(this);

        errorMessageTextView = (TextView) findViewById(R.id.textView_errorMessage);
        debugMessageTextView = (TextView) findViewById(R.id.textView_debugMessage);  // debug
    }


    /**
     * The process to create a preview needs to wait for 'CameraDevice', 'CameraCaptureSession' to
     * callback, so there will be three stages.
     *
     * Stage 0: Use 'CameraManager' to query a cameraId, then open the camera.
     * Stage 1:
     *     'CameraDevice.StateCallback.onOpened()' call 'createPreview()' with {@param 1}.
     *     Use info from 'CameraCharacteristics' to set preview image format and size.
     *     Use 'CameraDevice.createCaptureRequest()' to create a 'CaptureRequest.Builder'.
     *     Use 'CaptureRequest.Builder.addTarget()' to set TextureView as the output target.
     *     Use 'CameraDevice.createCaptureSession()' to create a 'CameraCaptureSession'.
     * Stage 2:
     *     'CameraCaptureSession.StateCallback.onConfigured()' call 'createPreview()' with {@param 2}.
     *     Use 'CameraCaptureSession.setRepeatingRequest(CaptureRequest.Builder.build())' to
     *     submit 'CaptureRequest' to 'CameraCaptureSession'.
     *
     * @param stage the stage of creating (int 0, 1, 2)
     */
    static void createPreview(int stage) {
        // TODO: make all this parameters changeable
        autoMode = AUTO_MODE_AUTO;
        aeMode = AE_MODE_ON;
        afMode = AF_MODE_CONTINUOUS_PICTURE;
        awbMode = AWB_MODE_AUTO;

        // TODO: make capture format changeable in settings, then make this default RAW_SENSOR
        captureFormat = 256;

        if (stage == 0) {
            try {
                cameraManager = (CameraManager) MainActivity.context.getSystemService(CameraManager.class);
                cameraIdList = cameraManager.getCameraIdList();
                // TODO: make camera changeable in settings
                for (String e : cameraIdList) {
                    if ((cameraManager.getCameraCharacteristics(e)).get(CameraCharacteristics.LENS_FACING) == 1) {
                        cameraId = e;
                        cameraCharacteristics = cameraManager.getCameraCharacteristics(e);
                        initiateCaptureParameters(cameraCharacteristics);
                        break;
                    }
                    cameraId = cameraIdList[0];
                }
            } catch (CameraAccessException e) {
                displayErrorMessage(e);
            }

            try {
                cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler);
            } catch (CameraAccessException | SecurityException e) {
                displayErrorMessage(e);
            }
        } else if (stage == 1) {
            // got cameraDevice
            // this stage will also be used to refresh the preview parameters after changed camera device
            previewSize = choosePreviewSize(cameraCharacteristics, captureFormat);

            /**
             * Check the 'SENSOR_ORIENTATION' to set width and height appropriately.
             * (Switch width and height if necessary.  )
             */
            sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (sensorOrientation == 90 || sensorOrientation == 270) {
                (UIOperator.previewCRTV_camera_control).setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
            } else {
                (UIOperator.previewCRTV_camera_control).setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
            }

            SurfaceTexture previewSurfaceTexture = (UIOperator.previewCRTV_camera_control).getSurfaceTexture();
            previewSurfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(previewSurfaceTexture);
            try {
                previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, autoMode);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, aeMode);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, afMode);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode);

                if (aeMode == AE_MODE_OFF || autoMode == AUTO_MODE_OFF) {
                    previewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime);
                    previewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, sensitivity);
                }
                if (afMode == AF_MODE_OFF || autoMode == AUTO_MODE_OFF) {
                    previewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance);
                }
                previewRequestBuilder.set(CaptureRequest.LENS_FOCAL_LENGTH, focalLength);
                previewRequestBuilder.set(CaptureRequest.LENS_APERTURE, aperture);

                previewRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, opticalStabilizationMode);

                previewRequestBuilder.addTarget(previewSurface);
                cameraDevice.createCaptureSession(Arrays.asList(previewSurface), captureSessionStateCallback, backgroundHandler);
            } catch (CameraAccessException e) {
                displayErrorMessage(e);
            }
        } else if (stage == 2) {  // got captureSession
            try {
                // Set {@param CaptureCallback} to 'null' if preview does not need and additional process.
                captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                UIOperator.updateCaptureParametersIndicator();
            } catch (CameraAccessException e) {
                displayErrorMessage(e);
            }
        }
    }

    /**
     * Produce a 'CameraDevice', then call 'createPreview(1)'
     */
    private static CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createPreview(1);
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
        }
        @Override
        public void onError(CameraDevice camera, int error) {
        }
    };

    /**
     * Produce a 'CameraCaptureSession', then call 'createPreview(2)'
      */
    private static CameraCaptureSession.StateCallback captureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            captureSession = session;
            createPreview(2);
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
        }
    };


    /**
     * First, get the sensor pixel array size, which is the maximum
     * available size and the output size in RAW_SENSOR format.
     *
     * Second, get the available size in capture format (if it is not RAW_SENSOR (32)), and find
     * the maximum size that has the closest aspect ratio with the sensor pixel array size.
     *
     * Last, in the available size for SurfaceTexture.class format, find an optimized size
     * with the same aspect ratio as the size found in the second step.
     */
    private static Size choosePreviewSize(CameraCharacteristics cameraCharacteristics, int captureFormat) {
        final Size SENSOR_INFO_PIXEL_ARRAY_SIZE = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        int maxPreviewResolution = 1080;  // TODO: make max preview resolution changeable in settings
        Size[] previewSizes = (cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)).getOutputSizes(SurfaceTexture.class);
        Size[] previewSizesFiltered;
        if (captureFormat == 32) {
            previewSizesFiltered = sizesFilter(previewSizes, SENSOR_INFO_PIXEL_ARRAY_SIZE);
        } else {
            Size[] captureSizes = (cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)).getOutputSizes(captureFormat);
            Size[] captureSizesFiltered = sizesFilter(captureSizes, SENSOR_INFO_PIXEL_ARRAY_SIZE);
            previewSizesFiltered = sizesFilter(previewSizes, captureSizesFiltered[0]);
        }
        for (Size e : previewSizesFiltered) {
            if ((e.getWidth() <= e.getHeight()) && (e.getWidth() <= maxPreviewResolution)) {
                return e;
            } else if ((e.getWidth() > e.getHeight()) && (e.getHeight() <= maxPreviewResolution)) {
                return e;
            }
        }
        return previewSizesFiltered[previewSizesFiltered.length - 1];
    }

    private static Size[] sizesFilter(Size[] sizes, Size goalSize) {
        float minimumDeviation = 0.000f;
        ArrayList<Size> sizesFiltered = new ArrayList<>();
        while (sizesFiltered.isEmpty()) {
            for (Size e : sizes) {
                float deviation = ((float) e.getWidth() / e.getHeight()) - ((float) goalSize.getWidth() / goalSize.getHeight());
                if (
                        (deviation == 0.0f)
                        || ((deviation < 0.0f) && (deviation > - minimumDeviation))
                        || ((deviation > 0.0f) && (deviation < minimumDeviation))
                ) {
                    sizesFiltered.add(e);
                }
            }
            minimumDeviation += 0.005f;
        }
        sizesFiltered.sort(sizesComparator);
        return sizesFiltered.toArray(new Size[sizesFiltered.size()]);
    }

    static Comparator<Size> sizesComparator = new Comparator<Size>() {
        @Override
        public int compare(Size size1, Size size0) {
            return Integer.compare(size0.getWidth() * size0.getHeight(), size1.getWidth() * size1.getHeight());
        }
    };


    private static void initiateCaptureParameters(CameraCharacteristics cameraCharacteristics) {
        SENSOR_INFO_EXPOSURE_TIME_RANGE = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        SENSOR_INFO_SENSITIVITY_RANGE = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        LENS_INFO_HYPERFOCAL_DISTANCE = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE);
        LENS_INFO_MINIMUM_FOCUS_DISTANCE = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        LENS_INFO_AVAILABLE_FOCAL_LENGTHS = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        LENS_INFO_AVAILABLE_APERTURES = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
        LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);

        if (SENSOR_INFO_EXPOSURE_TIME_RANGE.contains(100000000L)) {
            exposureTime = 100000000L;  // 0.1s
        } else {
            exposureTime = SENSOR_INFO_EXPOSURE_TIME_RANGE.getUpper();
        }

        if (SENSOR_INFO_SENSITIVITY_RANGE.contains(200)) {
            sensitivity = 200;
        } else if (SENSOR_INFO_SENSITIVITY_RANGE.contains(400)) {
            sensitivity = 400;
        } else {
            sensitivity = SENSOR_INFO_SENSITIVITY_RANGE.getLower();
        }

        focusDistance = LENS_INFO_HYPERFOCAL_DISTANCE;
        focalLength = LENS_INFO_AVAILABLE_FOCAL_LENGTHS[0];
        aperture = LENS_INFO_AVAILABLE_APERTURES[0];

        opticalStabilizationMode = 0;
        for (int e : LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) {
            if (e == 1) {
                opticalStabilizationMode = e;
                break;
            }
        }
    }


//    private void takePhoto() {
//        try {
//            if (autoMode == 1) {
//                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
//            } else {
//                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
//                previewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime);
//                previewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, sensitivity);
//            }
//            captureRequestBuilder.addTarget(imageReader.getSurface());
//            captureSession.capture(captureRequestBuilder.build(), captureSessionCaptureCallback, backgroundHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private ImageReader.OnImageAvailableListener captureImageAvailableListener = new ImageReader.OnImageAvailableListener() {
//        @Override
//        public void onImageAvailable(ImageReader reader) {
//            if (captureFormat == ImageFormat.RAW_SENSOR) {
//                dngCreator = new DngCreator(cameraCharacteristics, captureResult);
////                dngCreator.setOrientation(sensorOrientation);
//                File imageFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/LongShoot/RAW_TEST.DNG");
//                try {
//                    FileOutputStream imageOutput = new FileOutputStream(imageFile);
//                    dngCreator.writeImage(imageOutput, reader.acquireLatestImage());
//                } catch (IOException e) {
//                    e.printStackTrace();
//                } finally {
//                    reader.close();
//                }
//            }
//        }
//    };


    static void displayErrorMessage(Exception error) {
        errorMessageTextView.setBackgroundResource(R.color.colorSurface);
        errorMessageTextView.setText(error.toString());
    }
}
