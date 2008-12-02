package org.esa.nest.dat.views.polarview;

import java.awt.*;
import java.awt.image.*;
import java.util.*;

public abstract class SimpleImageProducer
    implements ImageProducer
{

    protected SimpleImageProducer(int width, int height, int hints)
    {
        theConsumers = new Vector();
        imageSize = new Dimension(width, height);
        imageArea = new Rectangle(imageSize);
        this.hints = hints;
        properties = new Hashtable();
    }

    protected SimpleImageProducer(int width, int height)
    {
        this(width, height, 30);
    }

    protected SimpleImageProducer(Dimension d)
    {
        this(d.width, d.height);
    }

    public synchronized void addConsumer(ImageConsumer ic)
    {
        if(isConsumer(ic))
            return;
        addConsumerToList(ic);
        try
        {
            initConsumer(ic);
            deliverPixels(ic, imageArea);
            if(isConsumer(ic))
                ic.imageComplete(3);
            if(isConsumer(ic))
            {
                ic.imageComplete(1);
                removeConsumer(ic);
            }
        }
        catch(Exception e)
        {
            if(isConsumer(ic))
                ic.imageComplete(1);
        }
    }

    public synchronized boolean isConsumer(ImageConsumer ic)
    {
        return theConsumers.contains(ic);
    }

    public synchronized void removeConsumer(ImageConsumer ic)
    {
        theConsumers.removeElement(ic);
    }

    public synchronized void removeAllConsumers()
    {
        for(Enumeration elem = theConsumers.elements(); elem.hasMoreElements();)
        {
            ImageConsumer ic = (ImageConsumer)elem.nextElement();
            ic.imageComplete(3);
            if(isConsumer(ic))
                ic.imageComplete(1);
        }

        theConsumers.removeAllElements();
    }

    public ColorScale getColorScale()
    {
        return null;
    }

    public void updatedColorScale()
    {
    }

    public void updatedColorMap()
    {
    }

    public void startProduction(ImageConsumer ic)
    {
        addConsumer(ic);
    }

    public void requestTopDownLeftRightResend(ImageConsumer imageconsumer)
    {
    }

    protected abstract ColorModel getColorModel();

    protected abstract void deliverPixels(ImageConsumer imageconsumer, Rectangle rectangle);

    protected void setProperties(Hashtable properties)
    {
        this.properties = properties;
    }

    protected void initConsumer(ImageConsumer ic)
    {
        if(isConsumer(ic))
            ic.setDimensions(imageSize.width, imageSize.height);
        if(isConsumer(ic))
            ic.setProperties(properties);
        if(isConsumer(ic))
            ic.setColorModel(getColorModel());
        if(isConsumer(ic))
            ic.setHints(hints);
    }

    protected synchronized Enumeration getConsumers()
    {
        return theConsumers.elements();
    }

    protected synchronized void addConsumerToList(ImageConsumer ic)
    {
        theConsumers.addElement(ic);
    }

    protected void finalize()
    {
        removeAllConsumers();
    }

    protected Dimension imageSize;
    protected Rectangle imageArea;
    protected static final Point p0 = new Point(0, 0);
    private Hashtable properties;
    private Vector theConsumers;
    private int hints;

}
