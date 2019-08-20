package nl.brendanspijkerman.android.ambilightscreencapture.model;

import android.graphics.Color;
import android.util.Log;

import java.util.Arrays;

public class GridElement {

    private static final String TAG = "GridElement";
    private static final int SUBPIXELS = 4;

    // DIMENSIONS
    private int width;
    private int height;
    private int[] pixels;
    private int pixelCount;

    // TEMPORAL PARAMETERS
    private int temporalFrameCount;
    // The newest frame will be put in index 0 and will have the highest weight during average calculations
    // So: temporalFrames[0] is the newest frame and will weigh the most
    private double[][] temporalFrames;
    private double temporalWeightExponent = 1;

    // COLORS
    private double averageColor[];
    private double temporalAverageColor[];

    public GridElement(int width, int height, int temporalFrameCount, double temporalWeightExponent)
    {
        this.width = width;
        this.height = height;
        this.temporalFrameCount = temporalFrameCount;
        this.temporalWeightExponent = temporalWeightExponent;
        this.temporalFrames = new double[temporalFrameCount][SUBPIXELS];
        this.pixels = new int[width * height];
        this.pixelCount = width * height;
    }

    public int getWidth()
    {
        return width;

    }

    public void setWidth(int width)
    {
        if (width > 0)
        {
            this.width = width;
            this.pixels = new int[width * height];
        }
        else
        {
            throw new NegativeArraySizeException("Array width cannot be negative or 0");
        }

    }

    public int getHeight()
    {
        return height;

    }

    public void setHeight(int width)
    {
        if (height > 0)
        {
            this.height = height;
            this.pixels = new int[width * height];
        }
        else
        {
            throw new NegativeArraySizeException("Array height cannot be negative or 0");
        }

    }

    public int[] getPixels()
    {
        return pixels;
    }

    public void setPixels(int[] pixels)
    {
        if (pixels.length == (this.width * this.height))
        {
            this.pixels = pixels;
            setAverageColor(calculateAverageColor());
            putTemporalFrame();
            setTemporalAverageColor(calculateTemporalAverageColor());
        }
        else
        {
            throw new IndexOutOfBoundsException(String.format("Pixel count (%d) does not equal width * height (%d x %d)", pixels.length, this.width, this.height));
        }
    }

    public int getPixelCount()
    {
        return (width * height);
    }

    public void setAverageColor(double[] color)
    {
        this.averageColor = color;
    }

    public void setTemporalAverageColor(double[] color)
    {
        this.temporalAverageColor = color;
    }

    public double[] getAverageColor()
    {
        return this.averageColor;
    }

    public double[] calculateAverageColor()
    {
        double[] color = new double[SUBPIXELS];

        int A = 0;
        int R = 0;
        int G = 0;
        int B = 0;

        for (int i = 0; i < this.pixels.length; i++)
        {
            A += (this.pixels[i] >> 24) & 0xff;
            R += (this.pixels[i] >> 16) & 0xff;
            G += (this.pixels[i] >>  8) & 0xff;
            B += (this.pixels[i]      ) & 0xff;
        }

        int colors[] = {A, R, G, B};

//        Log.i(TAG, Arrays.toString(colors));

        int pixelCount = getPixelCount();

        color[0] = A;
        color[1] = R;
        color[2] = G;
        color[3] = B;

        return color;
    }

    private void putTemporalFrame()
    {
        this.temporalFrames = shiftArrayRight(this.temporalFrames, 1);
        double[] averageColor = this.getAverageColor();
        this.temporalFrames[0] = Arrays.copyOf(averageColor, averageColor.length);
    }

    public double[] getTemporalAverageColor()
    {
        return this.temporalAverageColor;
    }

    public double[] calculateTemporalAverageColor()
    {
        double[] color = new double[SUBPIXELS];
        double frameWeight = 1; // TODO adjust to temporalWeightExponent
        double sumFrameWeights = 0;

        // (x+1)^-a

        for (int i = 0; i < this.temporalFrameCount; i++)
        {
            double normalized = (double)i / (double)this.temporalFrameCount;
            frameWeight = Math.pow(normalized + 1, -this.temporalWeightExponent);

            for (int j = 0; j < SUBPIXELS; j++)
            {
                color[j] += (this.temporalFrames[i][j] * frameWeight);
            }

            sumFrameWeights += frameWeight;
        }

        for (int i = 0; i < SUBPIXELS; i++)
        {
            color[i] = color[i] / sumFrameWeights;
        }

        return color;
    }

    private double[][] shiftArrayRight(double[][] array, int shift)
    {
        double[][] shifted = new double[array.length][array[0].length];

        for (int i = 0; i < array.length - shift; i++)
        {
            shifted[i + shift] = array[i];
        }

        return shifted;
    }

}
