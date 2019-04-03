package nl.brendanspijkerman.android.ambilightscreencapture.model;

import android.graphics.Color;

public class GridElement {

    private int width;
    private int height;
    private int subpixelCount = 4;
    private int[][][] pixels;
    private int pixelCount;

    private float alpha;
    private float red;
    private float green;
    private float blue;

    private boolean useAlpha = true;

    public GridElement(int width, int height)
    {
        this.width = width;
        this.height = height;
        this.pixels = new int[width][height][subpixelCount];
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
            this.pixels = new int[width][height][subpixelCount];
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
            this.pixels = new int[width][height][subpixelCount];
        }
        else
        {
            throw new NegativeArraySizeException("Array height cannot be negative or 0");
        }

    }

    public int[][][] getPixels()
    {
        return pixels;
    }

    public void setPixelsRGB(int[][][] pixels)
    {
        if (pixels.length == (this.width * this.height))
        {
            this.pixels = pixels;
        }
        else
        {
            throw new IndexOutOfBoundsException(String.format("Pixel count (%d) does not equal width * height (%d x %d)", pixels.length, this.width, this.height));
        }
    }

    public void setPixelsFromColor(int[] pixels)
    {
        if (pixels.length == getPixelCount())
        {
            for (int x = 0; x < width; x++)
            {
                for (int y = 0; y < width; y++)
                {

                    int pos = (x * y) + x;
                    int pixelCounter = 0;

                    int A = 0;
                    int R = 0;
                    int G = 0;
                    int B = 0;

                    A = (pixels[pos] >> 24) & 0xff; // or color >>> 24
                    R = (pixels[pos] >> 16) & 0xff;
                    G = (pixels[pos] >>  8) & 0xff;
                    B = (pixels[pos]      ) & 0xff;

                    this.pixels[x][y][0] = A;
                    this.pixels[x][y][1] = R;
                    this.pixels[x][y][2] = G;
                    this.pixels[x][y][3] = B;
                }
            }
        }
        else
        {
            throw new IndexOutOfBoundsException(String.format("Pixel count does not equal "));
        }
    }

    public int getPixelCount()
    {
        return (width * height);
    }

    public float[] getAverageColor()
    {
        float[] color = new float[subpixelCount];
        for (int x = 0; x < width; x ++)
        {
            for (int y = 0; y < height; y++)
            {
                for (int subpixel = 0; subpixel < subpixelCount; subpixel++)
                {
                    color[subpixel] += this.pixels[x][y];
                }
            }
        }

        for (int subpixel = 0; subpixel < subpixelCount; subpixel++)
        {
            color[subpixel] = color[subpixel] / getPixelCount();
        }

        return color;
    }

}
