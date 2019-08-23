package nl.brendanspijkerman.android.ambilightscreencapture.model;

import android.graphics.Color;
import android.util.Log;

import java.util.Arrays;

public class GridElement {

    private static final String TAG = "GridElement";
    private static final int SUBPIXELS = 4;

    // TEMPORAL PARAMETERS
    private int temporalFrameCount;
    // The newest frame will be put in index 0 and will have the highest weight during average calculations
    // So: temporalFrames[0] is the newest frame and will weigh the most
    private double[][] temporalFrames;
    private double temporalWeightExponent = 1;

    // COLORS
    private double temporalAverageColor[];

    public GridElement(int temporalFrameCount, double temporalWeightExponent)
    {
        this.temporalFrameCount = temporalFrameCount;
        this.temporalWeightExponent = temporalWeightExponent;
        this.temporalFrames = new double[temporalFrameCount][SUBPIXELS];
    }

    public void addFrame(double[] color)
    {
        if (color.length == 4)
        {
            this.temporalFrames = shiftArrayRight(this.temporalFrames, 1);
            this.temporalFrames[0] = Arrays.copyOf(color, color.length);
        }
    }

    public double[] getTemporalAverageColor()
    {
        return this.calculateTemporalAverageColor();
    }

    public double[] calculateTemporalAverageColor()
    {
        double[] color = new double[SUBPIXELS];

        double frameWeight = 1; // TODO adjust to temporalWeightExponent
        double sumFrameWeights = 0;

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
