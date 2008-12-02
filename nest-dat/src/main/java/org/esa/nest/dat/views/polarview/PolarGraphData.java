package org.esa.nest.dat.views.polarview;

import org.esa.beam.util.Debug;

import java.awt.Color;
import java.awt.Graphics;

public class PolarGraphData extends AbstractGraphData
{

    public PolarGraphData(Object rPoints[], Object tPoints[], Object cValues[])
    {
        this.rPoints = null;
        this.tPoints = null;
        this.cValues = null;
        rData = new AbstractGraphData.AxisData();
        tData = new AbstractGraphData.AxisData();
        setRPoints(rPoints);
        setTPoints(tPoints);
        setColorValues(cValues);
    }

    public void setRAxis(GraphAxis rAxis)
    {
        rData.setAxis(rAxis);
    }

    public void setRPoints(Object rPoints[])
    {
        this.rPoints = rPoints;
        rData.setArrayType(rPoints);
    }

    public void setTPoints(Object tPoints[])
    {
        this.tPoints = tPoints;
        tData.setArrayType(tPoints);
    }

    public void setColorValues(Object cValues[])
    {
        this.cValues = cValues;
        if(cValues != null)
            connectPoints = false;
        else
            connectPoints = true;
        cData.setArrayType(cValues);
        colors = null;
    }

    public void setColor(Color c)
    {
        setColorValues(null);
        super.setColor(c);
    }

    public void setDirOffset(float dirOffset)
    {
        this.dirOffset = dirOffset;
    }

    public void preparePlot()
    {
        if(rPoints == null || tPoints == null)
            throw new IllegalArgumentException();
        plotCount = rPoints.length;
        int yc = tPoints.length;
        if(yc < plotCount)
            plotCount = yc;
        prepareRTPoints();
        prepareColors(cValues);
    }

    public void draw(Graphics g)
    {
        if(rData.axis == null)
            return;
        try
        {
            AxisGraphics gr = rData.axis.getAxisGraphics(g);
            preparePlot();
            if(colors == null)
            {
                g.setColor(lineColor);
                for(int i = 0; i < plotCount; i++)
                    if(xScreenPoints[i] != null)
                    {
                        gr.drawPolyline(xScreenPoints[i], yScreenPoints[i], xScreenPoints[i].length);
                    }

            }
        }
        catch(Throwable e) {
            Debug.trace(e);
        }
    }

    protected void prepareRTPoints()
    {
        if(rData.axis == null)
            return;
        if(xScreenPoints == null || xScreenPoints.length != plotCount)
        {
            xScreenPoints = new int[plotCount][];
            rData.touch();
        }
        if(yScreenPoints == null || yScreenPoints.length != plotCount)
        {
            yScreenPoints = new int[plotCount][];
            rData.touch();
        }
        rData.checkTouchedAxis();
        if(!rData.touched || !tData.touched)
        {
            return;
        } else
        {
            rData.untouch();
            tData.untouch();
            computeScreenPoints((double[][])rPoints, (double[][])tPoints);
            return;
        }
    }

    protected double valueFromScreenPoint(int r)
    {
        return rData.axis.valueFromScreenPoint(r);
    }

    private final void computeScreenPoints(double rPoints[][], double tPoints[][])
    {
        if(rData.axis == null)
            return;
        int np = rPoints.length;
        for(int i = 0; i < np; i++)
            if(rPoints[i] == null)
            {
                xScreenPoints[i] = null;
                yScreenPoints[i] = null;
            } else
            {
                int n = rPoints[i].length;
                if(xScreenPoints[i] == null || xScreenPoints[i].length != n)
                {
                    xScreenPoints[i] = new int[n];
                    yScreenPoints[i] = new int[n];
                }
                for(int j = 0; j < n; j++)
                {
                    double x = rPoints[i][j] * Math.cos(tPoints[i][j] + (double)dirOffset);
                    double y = rPoints[i][j] * Math.sin(tPoints[i][j] + (double)dirOffset);
                    xScreenPoints[i][j] = rData.axis.computeScreenPoint(x);
                    yScreenPoints[i][j] = rData.axis.computeScreenPoint(y);
                }

            }

    }

    protected Object rPoints[];
    protected Object tPoints[];
    protected Object cValues[];
    protected float dirOffset;
    protected AbstractGraphData.AxisData rData;
    protected AbstractGraphData.AxisData tData;
}
