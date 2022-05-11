package edu.sit744.ass2.util;

import static java.lang.Math.min;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.Image;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.support.metadata.MetadataExtractor;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions;
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions.Orientation;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier.ImageClassifierOptions;

public class Classifier {

    private final ImageClassifier imageClassifier;

    private int MAX_RESULTS = 10;

    private int imageSizeY = 32;
    private int imageSizeX = 32;

    public Classifier(Activity activity,String model)throws IOException {
        BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder();

        // Create the ImageClassifier instance.
        ImageClassifierOptions options =
                ImageClassifierOptions.builder()
                        .setBaseOptions(baseOptionsBuilder.setNumThreads(2).build())
                        .setMaxResults(MAX_RESULTS)
                        .build();
        imageClassifier = ImageClassifier.createFromFileAndOptions(activity, getModelPath(), options);

        // Get the input image size information of the underlying tflite model.
        MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(activity, getModelPath());
        MetadataExtractor metadataExtractor = new MetadataExtractor(tfliteModel);
        // Image shape is in the format of {1, height, width, 3}.
        int[] imageShape = metadataExtractor.getInputTensorShape(/*inputIndex=*/ 0);
        imageSizeY = imageShape[1];
        imageSizeX = imageShape[2];

    }

    public void close() {
        if (imageClassifier != null) {
            imageClassifier.close();
        }
    }

    public int getImageSizeX() {
        return imageSizeX;
    }

    public int getImageSizeY() {
        return imageSizeY;
    }

    public List<Recognition> recognizeImage(final Bitmap bitmap) {

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap,getImageSizeX(),getImageSizeY(),false);
        TensorImage inputImage = TensorImage.fromBitmap(resizedBitmap);
        ImageProcessingOptions imageOptions =
                ImageProcessingOptions.builder()
                        .build();

        List<Classifications> results = imageClassifier.classify(inputImage, imageOptions);

        return getRecognitions(results);
    }

    private static List<Recognition> getRecognitions(List<Classifications> classifications) {

        final ArrayList<Recognition> recognitions = new ArrayList<>();
        // All the demo models are single head models. Get the first Classifications in the results.
        for (Category category : classifications.get(0).getCategories()) {
            recognitions.add(
                    new Recognition(
                            ""+category.getIndex(), category.getLabel(), category.getScore(), null));
        }
        return recognitions;
    }

//    private static Orientation getOrientation(int cameraOrientation) {
//        switch (cameraOrientation / 90) {
//            case 3:
//                return Orientation.BOTTOM_LEFT;
//            case 2:
//                return Orientation.BOTTOM_RIGHT;
//            case 1:
//                return Orientation.TOP_RIGHT;
//            default:
//                return Orientation.TOP_LEFT;
//        }
//    }

    private String getModelPath() {
        return "resnet50_waste_classifier.tflite";
    }

    public static Bitmap getBitmap_V2(Image image) {

        ByteBuffer y_buffer = image.getPlanes()[0].getBuffer();
        byte[] y_bytes = new byte[y_buffer.remaining()];
        y_buffer.get(y_bytes);
        ByteBuffer u_buffer = image.getPlanes()[1].getBuffer();
        byte[] u_bytes = new byte[u_buffer.remaining()];
        u_buffer.get(u_bytes);
        ByteBuffer v_buffer = image.getPlanes()[2].getBuffer();
        byte[] v_bytes = new byte[v_buffer.remaining()];
        v_buffer.get(v_bytes);
        int width = image.getWidth(), height = image.getHeight();


        int[] argb = new int[width * height];

        int Y = 0, U = 0, V = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {

                Y = y_bytes[row * width + col] & 0xff;
                if ((col & 0x1) == 0) {
                    U = u_bytes[row / 2 * width + col] & 0xff;
                    V = v_bytes[row / 2 * width + col] & 0xff;
                }

                int facter=128;
                int r= (int) (Y+1.4022*(V-facter));
                int g= (int) (Y-0.3456*(U-facter)-0.7145*(V-facter));
                int b= (int) (Y+1.771*(U-facter));

                r=r<0?0:(Math.min(r, 255));
                g=g<0?0:(Math.min(g, 255));
                b=b<0?0:(Math.min(b, 255));


                argb[col * height + height - 1 - row] =0xff000000
                        |((r<<16)&0xff0000)
                        | ((g <<8) & 0xff00)
                        |((b)&0xff)
                ;
            }

        }

        return  Bitmap.createBitmap(argb,height,width, Bitmap.Config.ARGB_8888);
    }
}

