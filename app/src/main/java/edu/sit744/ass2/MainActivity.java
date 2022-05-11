package edu.sit744.ass2;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;


import androidx.fragment.app.FragmentManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import edu.sit744.ass2.util.Classifier;
import edu.sit744.ass2.util.Recognition;
import edu.sit744.ass2.view.AutoFitTextureView;

public class MainActivity extends AppCompatActivity {

    private FragmentManager fragmentManager;
    private FragmentTransaction fragmentTransaction;
    private CameraFragment cameraFragment;
    private ImageFragment imageFragment;

    private AutoFitTextureView textureView;

    public Button cameraButton;
    public Button imageButton;
    public TextView recycleTextView;

    public TextView cardboardTextView;
    public TextView glassTextView;
    public TextView metalTextView;
    public TextView paperTextView;
    public TextView plasticTextView;
    public TextView clothingTextView;
    public TextView electronicTextView;
    public TextView furnitureTextView;
    public TextView organicTextView;
    public TextView trashTextView;
    private TextView[] textViewList;

    private ActivityResultLauncher<Intent> albumListener;

    private Classifier wasteClassifier;

    private Handler inferenceHandler;
    private HandlerThread inferenceHandlerThread;

    private Handler cameraHandler;
    private HandlerThread cameraHandlerThread;

    private Boolean isInit = true;
    private String cameraId = null;

    /** A {@link Semaphore} to prevent the app from exiting before closing the camera. */
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader previewReader;
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest previewRequest;
    private Size previewSize = new Size(1600,1200);
    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final CaptureResult partialResult) {}

                @Override
                public void onCaptureCompleted(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final TotalCaptureResult result) {}
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initInferenceHandler();
        initCameraHandler();
        initAllElement();
        initFragment();
        initActivityResultListener();


        try {
            wasteClassifier = new Classifier(this, "");
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Initialize setting

    }



    @Override
    public synchronized void onStart() {
        super.onStart();
        if (isInit){
            isInit = false;
            onClickCameraButton();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

    }

    @Override
    public synchronized void onPause() {
//        handlerThread.quitSafely();
//        try {
//            handlerThread.join();
//            handlerThread = null;
//            handler = null;
//        } catch (final InterruptedException e) {
//            e.printStackTrace();
//        }

        super.onPause();
    }

    @Override
    public synchronized void onStop() {
        super.onStop();
    }

    @Override
    public synchronized void onDestroy() {
        stopInferenceHandler();
        stopCameraHandler();
        super.onDestroy();
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (inferenceHandler != null) {
            inferenceHandler.post(r);
        }
    }

    private void initFragment() {
        fragmentManager = getSupportFragmentManager();

        cameraFragment = CameraFragment.newInstance();
        imageFragment = ImageFragment.newInstance();

        fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.fragmentContainerView, imageFragment);
        fragmentTransaction.add(R.id.fragmentContainerView, cameraFragment);
        fragmentTransaction.commit();

    }

    private void initAllElement() {
        cameraButton = findViewById(R.id.camera_button);
        imageButton = findViewById(R.id.image_button);
        recycleTextView = findViewById(R.id.recycle_textView);

        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickCameraButton();
            }
        });

        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickImageButton();
            }
        });

        cardboardTextView = findViewById(R.id.cardboard_textView);
        glassTextView = findViewById(R.id.glass_textView);
        metalTextView = findViewById(R.id.metal_textView);
        paperTextView = findViewById(R.id.paper_textView);
        plasticTextView = findViewById(R.id.plastic_textView);
        clothingTextView = findViewById(R.id.clothing_textView);
        electronicTextView = findViewById(R.id.electronic_textView);
        furnitureTextView = findViewById(R.id.furniture_textView);
        organicTextView = findViewById(R.id.organic_textView);
        trashTextView = findViewById(R.id.trash_textView);

        textViewList = new TextView[]{
                cardboardTextView,
                clothingTextView,
                electronicTextView,
                furnitureTextView,
                glassTextView,
                metalTextView,
                organicTextView,
                paperTextView,
                plasticTextView,
                trashTextView
        };

        textureView = findViewById(R.id.textureView);

    }

    private void initInferenceHandler() {
        inferenceHandlerThread = new HandlerThread("inference");
        inferenceHandlerThread.start();
        inferenceHandler = new Handler(inferenceHandlerThread.getLooper());
    }

    private void stopInferenceHandler() {
        inferenceHandlerThread.quitSafely();
        try {
            inferenceHandlerThread.join();
            inferenceHandlerThread = null;
            inferenceHandler = null;
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void initActivityResultListener() {
        albumListener = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            handleImageDetection(result);
                        }
                    }
                });
    }

    public void showCameraFragment() {
        fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.hide(imageFragment)
                .show(cameraFragment)
                .commit();

    }

    public void showImageFragment() {
        fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.hide(cameraFragment)
                .show(imageFragment)
                .commit();
    }

    public void onClickImageButton() {
        closeCamera();
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
        } else {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            albumListener.launch(intent);
        }
        showImageFragment();
    }

    public void onClickCameraButton() {
        AutoFitTextureView textureView = (AutoFitTextureView) cameraFragment.getTextureView();
        showCameraFragment();
        textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(),textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }

    }

    /*
        Image Detection

     */
    private void handleImageDetection(ActivityResult result) {
        Intent intent = result.getData();
        Uri uri = intent.getData();
        String imagePath = getImagePath(uri);
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath, opt);

        imageFragment.setImage(bitmap);

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        if (wasteClassifier != null) {
                            List<Recognition> recognitions = wasteClassifier.recognizeImage(bitmap);
//                            for (Recognition recognition : recognitions) {
//                                System.out.println(recognition.toString());
//                            }

                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            showResults(recognitions);
                                            showRecyclable(recognitions);
                                        }
                                    });
                        }

                    }
                });


    }

    private String getImagePath(Uri uri) {
        String imagePath = "";
        if (DocumentsContract.isDocumentUri(this, uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                String id = docId.split(":")[1];
                String selection = MediaStore.Images.Media._ID + "=" + id;
                imagePath = getImagePathByContent(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
            } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public downloads"), Long.valueOf(docId));
                imagePath = getImagePathByContent(contentUri, null);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            imagePath = getImagePathByContent(uri, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            imagePath = uri.getPath();
        }
        return imagePath;
    }


    @SuppressLint("Range")
    private String getImagePathByContent(Uri uri, String selection) {
        String path = null;
        Cursor cursor = getContentResolver().query(uri, null, selection, null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }


    /*
     * Real-Time Detection
     *
     * */

    /** Starts a background thread and its {@link Handler}. */
    private void initCameraHandler() {
        cameraHandlerThread = new HandlerThread("ImageListener");
        cameraHandlerThread.start();
        cameraHandler = new Handler(cameraHandlerThread.getLooper());
    }

    /** Stops the background thread and its {@link Handler}. */
    private void stopCameraHandler() {
        cameraHandlerThread.quitSafely();
        try {
            cameraHandlerThread.join();
            cameraHandlerThread = null;
            cameraHandler = null;
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }


    private String chooseCamera() {
        if (cameraId==null){
            final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try {
                for (final String cameraId : manager.getCameraIdList()) {
                    final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                    // We don't use a front facing camera in this sample.
                    final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        continue;
                    }
                    final StreamConfigurationMap map =
                            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map == null) {
                        continue;
                    }
                    return cameraId;
                }

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }else{
            return cameraId;
        }
        return cameraId;
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {
                    openCamera(width,height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {}
            };

    private final CameraDevice.StateCallback stateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(final CameraDevice cd) {
                    // This method is called when the camera is opened.  We start camera preview here.
                    cameraOpenCloseLock.release();
                    cameraDevice = cd;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(final CameraDevice cd) {
                    cameraOpenCloseLock.release();
                    cd.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(final CameraDevice cd, final int error) {
                    cameraOpenCloseLock.release();
                    cd.close();
                    cameraDevice = null;
                }
            };

    private void openCamera(final int width, final int height) {
        System.out.println("View Width:"+width);
        System.out.println("View Height:"+height);

        String cameraId = chooseCamera();
//        setUpCameraOutputs();
        configureTransform(cameraFragment.getTextureView(),width, height);
//        final Activity activity = getActivity();
        final CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA},
                        50);
            }else{
                manager.openCamera(cameraId, stateCallback, cameraHandler);
            }
        } catch (final CameraAccessException e) {
            e.printStackTrace();
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void configureTransform(final TextureView textureView,final int viewWidth,final int viewHeight){
        final Activity activity = this;
        if (null == textureView || null == previewSize || null == activity) {
            return;
        }
        final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            System.out.println("RUNNNN!!!");
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale =
                    Math.max(
                            (float) viewHeight / previewSize.getHeight(),
                            (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);

    }

    /** Closes the current {@link CameraDevice}. */
    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != previewReader) {
                previewReader.close();
                previewReader = null;
            }
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    private boolean processing = false;

    /** Creates a new {@link CameraCaptureSession} for camera preview. */
    private void createCameraPreviewSession() {
        try {
            final SurfaceTexture texture = cameraFragment.getTextureView().getSurfaceTexture();
            assert texture != null;


            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // This is the output Surface we need to start preview.
            final Surface surface = new Surface(texture);


            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            // Create the reader for the preview frames.
            previewReader =
                    ImageReader.newInstance(
                            previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

            final Integer[] freqFlag = {0};
            previewReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    Image image = imageReader.acquireLatestImage();
                    if (image == null) {
                        return;
                    }

                    if (!processing){
                        runInBackground(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        processing = true;
                                        freqFlag[0] += 1;
                                        if (wasteClassifier != null && freqFlag[0] %5==0) {
                                            freqFlag[0] = 0;
                                            List<Recognition> recognitions = wasteClassifier.recognizeImage(Classifier.getBitmap_V2(image));
//                                            for (Recognition recognition : recognitions) {
//                                                System.out.println(recognition.toString());
//                                            }
                                            runOnUiThread(
                                                    new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            showResults(recognitions);
                                                            showRecyclable(recognitions);
                                                        }
                                                    });

                                        }
                                        image.close();
                                        processing = false;
                                    }
                                });
                    }else{
                        image.close();
                    }
                }
            }, cameraHandler);

            previewRequestBuilder.addTarget(previewReader.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, previewReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // Finally, we start displaying the camera preview.
                                previewRequest = previewRequestBuilder.build();
                                captureSession.setRepeatingRequest(
                                        previewRequest, captureCallback, cameraHandler);
                            } catch (final CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(getApplicationContext(),"Failed",Toast.LENGTH_SHORT);
                        }
                    },
                    null);
        } catch (final CameraAccessException e) {
            e.printStackTrace();
        }
    }



    private void showResults(List<Recognition> recognitions){
        for(Recognition recognition : recognitions){
            textViewList[Integer.parseInt(recognition.getId())].setText(String.format("(%.2f%%) ", recognition.getConfidence() * 100.0f));
        }
    }

    private void showRecyclable(List<Recognition> recognitions){
        Recognition recognition = recognitions.get(0);
        int id = Integer.parseInt(recognition.getId());
        if (id==0||id==4||id==5||id==7||id==8){
            recycleTextView.setText("Recyclable Waste: "+recognition.getTitle());
            recycleTextView.setTextAppearance(R.style.WhiteFontStyle);
            recycleTextView.setBackground(getResources().getDrawable(R.drawable.green_gradual,null));
        }else{
            recycleTextView.setText("Non-Recyclable Waste: "+recognition.getTitle());
            recycleTextView.setTextAppearance(R.style.BlackFontStyle);
            recycleTextView.setBackground(getResources().getDrawable(R.drawable.red_gradual,null));
        }
    }
}