package org.esa.nest.dat.views.polarview;

import org.esa.nest.util.MathUtils;

import java.awt.*;

public class PolarGraphCanvas extends GraphCanvas
{

    public PolarGraphCanvas()
    {
        this(new PolarGraphAxis(), new GraphAxis(4));
    }

    public PolarGraphCanvas(PolarGraphAxis rAxis, GraphAxis cAxis)
    {
        data = null;
        //xTitle = new StyledText("E");
       // yTitle = new StyledText("N");
        drawCompass = true;
        dirOffset = 0.0F;
        plotRadius = 0;
        this.rAxis = rAxis;
        this.cAxis = cAxis;
        cAxis.setLocation(4);
        cAxis.setName("Colour");
        rAxis.setSpacing(0);
        cAxis.setSpacing(0);
        //enableEvents(16L);
        //setBackground(Color.white);
    }

    public GraphAxis getRAxis()
    {
        return rAxis;
    }

    public GraphAxis getCAxis()
    {
        return cAxis;
    }

    public PolarGraphData getData()
    {
        return data;
    }

    public void setData(PolarGraphData data)
    {
        this.data = data;
        if(data == null)
        {
            return;
        } else
        {
            data.setRAxis(rAxis);
            data.setDirOffset(dirOffset);
            data.setCAxis(cAxis);
            return;
        }
    }

    //public void setRings(double rings[], Glyph ringText[])
    //{
    //    this.rings = rings;
        //this.ringText = ringText;
   //}

    public float getDirOffset()
    {
        return dirOffset;
    }

    public void setDirOffset(float dirOffset)
    {
        this.dirOffset = dirOffset;
        if(data != null)
            data.setDirOffset(dirOffset);
        drawCompass = true;
    }

    public void setCompassNames(String eStr, String nStr)
    {
        if(eStr == null || nStr == null)
        {
            return;
        } else
        {
            //xTitle.setText(eStr);
            //yTitle.setText(nStr);
            drawCompass = true;
            return;
        }
    }

    public void disableCompass()
    {
        drawCompass = false;
    }

    public void enableCompass()
    {
        drawCompass = true;
    }

    public double[] getRTheta(Point oP)
    {
        Point p = new Point(oP);
        p.y = origin.y - p.y;
        p.x = p.x - origin.x;
        if(Math.abs(p.y) > plotRadius || Math.abs(p.x) > plotRadius)
        {
            return null;
        } else
        {
            int r = (int)Math.sqrt(p.x * p.x + p.y * p.y);
            double rV = data.valueFromScreenPoint(r);
            double rTh[] = {
                rV, (360D - (Math.atan2(p.x, p.y) * 180D) / 3.1415926535897931D) % 360D
            };
            return rTh;
        }
    }

    public GraphAxis selectAxis(Point oP)
    {
        Point p = new Point(oP);
        p.y = origin.y - p.y;
        p.x = p.x - origin.x;
        if(Math.abs(p.y) < plotRadius)
        {
            if(Math.abs(p.x) < plotRadius)
                return rAxis;
            if(p.x > graphSize.width / 2)
                return cAxis;
        }
        return null;
    }

    protected void draw(Graphics g, Dimension size)
    {
        final int annotationHeight = 100;//getAnnotationHeight(g);
        final int top = Math.max((int)(size.height* 0.05), 20);
        final int left = Math.max((int)(size.width* 0.10), 20);
        final int bottom = Math.max((int)(size.height* 0.5)+ annotationHeight, 20);
        final int right = Math.max((int)(size.width * 0.15), 20);
        Rectangle r = positionPlot(size, top, left, bottom , right);
        plotRadius = Math.min(r.width / 2, r.height / 2);
        Dimension quadrantSize = new Dimension(plotRadius, plotRadius);
        g.translate(origin.x, origin.y + r.height);
        if(data != null)
        {
            loadColorBar(data.getColorScale());
            drawColorBar(g, cAxis);
        }
        g.translate(-origin.x, -origin.y - r.height);
        Point xYo = new Point(origin.x, origin.y + r.height);
        int xYs = origin.x / 2;
        origin.y = origin.y + r.height / 2;
        origin.x = origin.x + r.width / 2;
        Graphics oGraphics = g.create();
        oGraphics.translate(origin.x, origin.y);
        rAxis.setSize(quadrantSize);
        if(data != null)
            data.draw(oGraphics);
        oGraphics.setColor(Color.darkGray);
        if(rings != null)
        {
            for(int ri = 0; ri < rings.length; ri++)
            {
                int rad = rAxis.computeScreenPoint(rings[ri]);
                int rad2 = rad + rad;
                oGraphics.drawOval(-rad, -rad, rad2, rad2);
                //if(ringText != null && ringText[ri] != null)
                //    ringText[ri].drawCentered(oGraphics, 0, -(rad + ringText[ri].getDescent(oGraphics) * 3));
            }

        } else
        {
            rAxis.draw(oGraphics);
        }
        oGraphics.translate(-origin.x, -origin.y);
        oGraphics.dispose();
        //drawAnnotation(g, size);
    }

    private PolarGraphAxis rAxis;
    private GraphAxis cAxis;
    private PolarGraphData data;
    private double rings[];
    //private Glyph ringText[];
    //private StyledText xTitle;
   // private StyledText yTitle;
    private boolean drawCompass;
    private float dirOffset;
    private int plotRadius;
}
