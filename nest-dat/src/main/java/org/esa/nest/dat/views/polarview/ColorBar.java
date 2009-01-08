package org.esa.nest.dat.views.polarview;

import java.awt.*;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageProducer;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Enumeration;


public class ColorBar implements ImageProducer {
    private ColourScale colourScale;
    private ColorModel model;
    private static Dimension barSize = new Dimension(24, 256);
    private static byte barPixels[] = new byte[barSize.height];
    private static int barRGBPixels[] = new int[barSize.height];

    private Dimension imageSize;
    private Rectangle imageArea;
    protected static final Point p0 = new Point(0, 0);
    private Hashtable properties;
    private Vector theConsumers;
    private int hints;

    static {
        int p = 0;
        float scale = 0xffffff / (barSize.height - 1);
        for (int i = barSize.height - 1; i >= 0; i--) {
            barPixels[p] = (byte) i;
            barRGBPixels[p] = (int) ((float) p * scale);
            p++;
        }
    }

    public ColorBar(ColourScale colourScale) {
        theConsumers = new Vector();
        imageSize = new Dimension(barSize.width, barSize.height);
        imageArea = new Rectangle(imageSize);
        this.hints = 3 & 0xffffffef;
        properties = new Hashtable();

        this.colourScale = colourScale;
        colourScale.addColoredObject(this);
        updateColorModel();
    }

    public synchronized boolean isConsumer(ImageConsumer ic) {
        return theConsumers.contains(ic);
    }

    public synchronized void removeConsumer(ImageConsumer ic) {
        theConsumers.removeElement(ic);
    }

    synchronized void removeAllConsumers() {
        for (Enumeration elem = theConsumers.elements(); elem.hasMoreElements();) {
            ImageConsumer ic = (ImageConsumer) elem.nextElement();
            ic.imageComplete(3);
            if (isConsumer(ic))
                ic.imageComplete(1);
        }

        theConsumers.removeAllElements();
    }

    public ColourScale getColorScale() {
        return null;
    }

    public void startProduction(ImageConsumer ic) {
        addConsumer(ic);
    }

    public void requestTopDownLeftRightResend(ImageConsumer imageconsumer) {
    }

    protected void setProperties(Hashtable properties) {
        this.properties = properties;
    }

    void initConsumer(ImageConsumer ic) {
        if (isConsumer(ic))
            ic.setDimensions(imageSize.width, imageSize.height);
        if (isConsumer(ic))
            ic.setProperties(properties);
        if (isConsumer(ic))
            ic.setColorModel(getColorModel());
        if (isConsumer(ic))
            ic.setHints(hints);
    }

    synchronized Enumeration getConsumers() {
        return theConsumers.elements();
    }

    synchronized void addConsumerToList(ImageConsumer ic) {
        theConsumers.addElement(ic);
    }

    protected void finalize() {
        removeAllConsumers();
    }

    public synchronized void addConsumer(ImageConsumer ic) {
        if (isConsumer(ic))
            return;
        addConsumerToList(ic);
        try {
            initConsumer(ic);
            if (readyToSendAll()) {
                deliverPixels(ic, imageArea);
                if (isConsumer(ic))
                    ic.imageComplete(2);
            }
        }
        catch (Exception e) {
            if (isConsumer(ic))
                ic.imageComplete(1);
        }
    }

    synchronized void resend() {
        if (readyToSendAll())
            resend(imageArea);
    }

    public void updatedColorMap() {
        updateColorModel();
        resendColorModel();
        resend();
    }

    public void updatedColorScale() {
        updatedColorMap();
    }

    boolean readyToSendAll() {
        return true;
    }

    synchronized void resend(Rectangle area) {
        Enumeration con = getConsumers();
        while (con.hasMoreElements()) {
            ImageConsumer ic = (ImageConsumer) con.nextElement();
            try {
                deliverPixels(ic, area);
                if (isConsumer(ic))
                    ic.imageComplete(2);
            }
            catch (Exception e) {
                if (isConsumer(ic))
                    ic.imageComplete(1);
            }
        }
    }

    protected synchronized void resend(ImageConsumer ic, Rectangle area) {
        if (ic == null) {
            resend(area);
            return;
        }
        if (isConsumer(ic))
            try {
                deliverPixels(ic, area);
                if (isConsumer(ic))
                    ic.imageComplete(2);
            }
            catch (Exception e) {
                if (isConsumer(ic))
                    ic.imageComplete(1);
            }
    }

    synchronized void resendColorModel() {
        ImageConsumer ic;
        for (Enumeration elem = getConsumers(); elem.hasMoreElements(); ic.setColorModel(getColorModel()))
            ic = (ImageConsumer) elem.nextElement();
    }

    public static Dimension getBarSize() {
        return new Dimension(barSize);
    }

    protected void updateColorModel() {
        model = colourScale.getColorModel();
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
