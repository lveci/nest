package org.esa.nest.dat.views.polarview;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.ImageConsumer;
import java.util.Enumeration;

public abstract class MultiFrameImageProducer extends SimpleImageProducer
{

    protected MultiFrameImageProducer(int width, int height, int hints)
    {
        super(width, height, hints & 0xffffffef);
    }

    protected MultiFrameImageProducer(int width, int height)
    {
        super(width, height, 14);
    }

    protected MultiFrameImageProducer(Dimension d)
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
            if(readyToSendAll())
            {
                deliverPixels(ic, imageArea);
                if(isConsumer(ic))
                    ic.imageComplete(2);
            }
        }
        catch(Exception e)
        {
            if(isConsumer(ic))
                ic.imageComplete(1);
        }
    }

    public synchronized void resend()
    {
        if(readyToSendAll())
            resend(imageArea);
    }

    public void updatedColorMap()
    {
        updateColorModel();
        resendColorModel();
        resend();
    }

    public void updatedColorScale()
    {
        updatedColorMap();
    }

    protected abstract void updateColorModel();

    protected boolean readyToSendAll()
    {
        return true;
    }

    protected synchronized void resend(Rectangle area)
    {
        Enumeration con = getConsumers();
        while(con.hasMoreElements())
        {
            ImageConsumer ic = (ImageConsumer)con.nextElement();
            try
            {
                deliverPixels(ic, area);
                if(isConsumer(ic))
                    ic.imageComplete(2);
            }
            catch(Exception e)
            {
                if(isConsumer(ic))
                    ic.imageComplete(1);
            }
        }
    }

    protected synchronized void resend(ImageConsumer ic, Rectangle area)
    {
        if(ic == null)
        {
            resend(area);
            return;
        }
        if(isConsumer(ic))
            try
            {
                deliverPixels(ic, area);
                if(isConsumer(ic))
                    ic.imageComplete(2);
            }
            catch(Exception e)
            {
                if(isConsumer(ic))
                    ic.imageComplete(1);
            }
    }

    protected synchronized void resendColorModel()
    {
        ImageConsumer ic;
        for(Enumeration elem = getConsumers(); elem.hasMoreElements(); ic.setColorModel(getColorModel()))
            ic = (ImageConsumer)elem.nextElement();

    }
}
