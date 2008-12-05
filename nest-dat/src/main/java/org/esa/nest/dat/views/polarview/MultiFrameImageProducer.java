package org.esa.nest.dat.views.polarview;

import java.awt.*;
import java.awt.image.ColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageProducer;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

public abstract class MultiFrameImageProducer implements ImageProducer {
    private Dimension imageSize;
    private Rectangle imageArea;
    protected static final Point p0 = new Point(0, 0);
    private Hashtable properties;
    private Vector theConsumers;
    private int hints;

    MultiFrameImageProducer(int width, int height, int hints) {
        theConsumers = new Vector();
        imageSize = new Dimension(width, height);
        imageArea = new Rectangle(imageSize);
        this.hints = hints & 0xffffffef;
        properties = new Hashtable();
    }

    protected MultiFrameImageProducer(int width, int height) {
        this(width, height, 14);
    }

    protected MultiFrameImageProducer(Dimension d) {
        this(d.width, d.height, 14);
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

    public ColorScale getColorScale() {
        return null;
    }

    public void startProduction(ImageConsumer ic) {
        addConsumer(ic);
    }

    public void requestTopDownLeftRightResend(ImageConsumer imageconsumer) {
    }

    protected abstract ColorModel getColorModel();

    protected abstract void deliverPixels(ImageConsumer imageconsumer, Rectangle rectangle);

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

    protected abstract void updateColorModel();

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
}
