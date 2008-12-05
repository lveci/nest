package org.esa.nest.dat.views.polarview;

import java.awt.*;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.ImageConsumer;


public class ColorBarProducer extends MultiFrameImageProducer {
    private ColorScale colorScale;
    private ColorModel model;
    private static Dimension barSize = new Dimension(24, 256);
    private static byte barPixels[] = new byte[barSize.height];
    private static int barRGBPixels[] = new int[barSize.height];

    static {
        int p = 0;
        float scale = 0xffffff / (barSize.height - 1);
        for (int i = barSize.height - 1; i >= 0; i--) {
            barPixels[p] = (byte) i;
            barRGBPixels[p] = (int) ((float) p * scale);
            p++;
        }
    }

    public ColorBarProducer(ColorScale colorScale) {
        super(barSize.width, barSize.height, 3);
        this.colorScale = colorScale;
        colorScale.addColoredObject(this);
        updateColorModel();
    }

    public static Dimension getBarSize() {
        return new Dimension(barSize);
    }

    protected void updateColorModel() {
        model = colorScale.getColorModel();
    }

    protected void deliverPixels(ImageConsumer ic, Rectangle area) {
        if (model instanceof DirectColorModel) {
            for (int i = 0; i < barSize.width; i++) {
                ic.setPixels(i, 0, 1, barSize.height, model, barRGBPixels, 0, 1);
            }

        } else {
            for (int i = 0; i < barSize.width; i++) {
                ic.setPixels(i, 0, 1, barSize.height, model, barPixels, 0, 1);
            }
        }
    }

    protected ColorModel getColorModel() {
        return model;
    }


}
