package org.esa.nest.dat.views.polarview;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.*;


public class ColorBarProducer extends MultiFrameImageProducer
{

    public ColorBarProducer(ColorScale colorScale)
    {
        super(barSize.width, barSize.height, 3);
        this.colorScale = colorScale;
        colorScale.addColoredObject(this);
        updateColorModel();
    }

    public static Dimension getBarSize()
    {
        return new Dimension(barSize);
    }

    public void updateColorModel()
    {
        model = colorScale.getColorModel();
    }

    protected void deliverPixels(ImageConsumer ic, Rectangle area)
    {
        if(model instanceof DirectColorModel)
        {
            for(int i = 0; i < barSize.width; i++)
                ic.setPixels(i, 0, 1, barSize.height, model, barRGBPixels, 0, 1);

        } else
        {
            for(int i = 0; i < barSize.width; i++)
                ic.setPixels(i, 0, 1, barSize.height, model, barPixels, 0, 1);

        }
    }

    protected ColorModel getColorModel()
    {
        return model;
    }

    private ColorScale colorScale;
    private ColorModel model;
    private static Dimension barSize;
    private static byte barPixels[];
    private static int barRGBPixels[];

    static 
    {
        barSize = new Dimension(24, 256);
        barPixels = new byte[barSize.height];
        barRGBPixels = new int[barSize.height];
        int p = 0;
        float scale = 0xffffff / (barSize.height - 1);
        for(int i = barSize.height - 1; i >= 0; i--)
        {
            barPixels[p] = (byte)i;
            barRGBPixels[p] = (int)((float)p * scale);
            p++;
        }

    }
}
