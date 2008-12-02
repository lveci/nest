package org.esa.nest.dat.views.polarview;

import org.esa.beam.util.Debug;

import java.awt.*;

public abstract class GraphCanvas extends Container
{
    protected boolean opaque;

    public GraphCanvas()
    {
        opaque = false;
        graphSize = new Dimension(200, 100);
        annotationHeight = 20;
        colorBar = null;
        axisDialogEnabled = false;
    }

    public Font getFont()
    {
        Font font = super.getFont();
        if(font == null)
            return Font.decode("SansSerif-plain-9");
        else
            return font;
    }

    public Frame getParentFrame()
    {
        return (Frame)getParentWindow(this);
    }

    public static Window getParentWindow(Component comp)
    {
        if(comp == null)
            return null;
        if(comp instanceof Window)
            return (Window)comp;
        for(Container c = comp.getParent(); c != null; c = c.getParent())
            if(c instanceof Window)
                return (Window)c;

        return null;
    }

    public boolean getOpaque()
    {
        return opaque;
    }

    public void setOpaque(boolean opaque)
    {
        this.opaque = opaque;
    }

    public void setBackground(Color background)
    {
        opaque = true;
        super.setBackground(background);
    }

    public void setFontSize(int size)
    {
        Font f = getFont();
        if(f == null)
            return;
        if(f.getSize() != size) {
            setFont(new Font(f.getName(), f.getStyle(), size));
        }
    }

    public void fillBackground(Graphics g)
    {
        Rectangle clip = g.getClipBounds();
        Color col = g.getColor();
        g.setColor(getBackground());
        g.fillRect(clip.x, clip.y, clip.width, clip.height);
        g.setColor(col);
    }

    public void repaint(long tm, int x, int y, int width, int height)
    {
        try
        {
            super.repaint(tm, x, y, width, height);
        }
        catch(Throwable e)
        {
            Debug.trace(e);
        }
    }

    public void update(Graphics g)
    {
        try
        {
            paint(g);
        }
        catch(Throwable e)
        {
            Debug.trace(e);
        }
    }

    public void paint(Graphics g)
    {
        try
        {
            if(isShowing())
            {
                if(opaque)
                    fillBackground(g);
                g.setColor(getForeground());
                g.setFont(getFont());
                paintLightweightChildren(this, g);
            }

            drawSynchronised(g, getSize());
        }
        catch(Throwable e)
        {
            Debug.trace(e);
        }
    }

    public void disableAxisDialog()
    {
        axisDialogEnabled = false;
    }

    public abstract GraphAxis selectAxis(Point point);

    protected abstract void draw(Graphics g, Dimension dimension);

    protected final synchronized void drawSynchronised(Graphics g, Dimension size)
    {
        draw(g, size);
    }

    protected Rectangle positionPlot(Dimension size, int top, int left, int bottom, int right)
    {
        //insets.setValue(top, left, bottom, right);
        //Rectangle r = insets.shrinkRect(size);
        Rectangle r = new Rectangle(top, left, (int)size.getWidth()-left-right, (int)size.getHeight()-top-bottom);
        origin = r.getLocation();
        graphSize.setSize(r.width, r.height);
        return r;
    }

    protected void loadColorBar(ColorScale scale)
    {
        if(colorBar == null)
            colorBar = createImage(new ColorBarProducer(scale));
    }

    protected void drawColorBar(Graphics oGraphics, GraphAxis cAxis)
    {
        drawColorBar(oGraphics, cAxis, 0, 0);
    }

    protected void drawColorBar(Graphics oGraphics, GraphAxis cAxis, int xo, int yo)
    {
        Dimension cbSize = new Dimension((int)((double)graphSize.width * 0.029999999999999999D), (int)((double)graphSize.height * 0.59999999999999998D));
        Point at = new Point((int)((double)graphSize.width * 1.04D + (double)xo), -(int)((double)graphSize.height * 0.80000000000000004D + (double)yo));
        drawColorBar(oGraphics, cAxis, cbSize, at);
    }

    protected void drawColorBar(Graphics oGraphics, GraphAxis cAxis, Dimension cbSize, Point at)
    {
        oGraphics.translate(at.x, at.y);
        oGraphics.drawImage(colorBar, 0, 0, cbSize.width, cbSize.height, this);
        oGraphics.drawRect(0, 0, cbSize.width, cbSize.height);
        oGraphics.translate(cbSize.width, cbSize.height);
        cAxis.draw(oGraphics, cbSize);
        oGraphics.translate(-cbSize.width - at.x, -cbSize.height - at.y);
    }

    protected void finalize()
        throws Throwable
    {
        if(colorBar != null)
            colorBar.flush();
        colorBar = null;
        super.finalize();
    }

    public static void paintLightweightChildren(Container c, Graphics g)
    {
        if(!c.isShowing()) return;

        Rectangle clip;
        int i;
        int ncomponents = c.getComponentCount();
        clip = g.getClipBounds();
        i = ncomponents - 1;

        while(i >= 0) {
            Component comp;
            Graphics cg;
            Component component[] = c.getComponents();
            comp = component[i];
            if(comp == null || !comp.isVisible())
                continue; /* Loop/switch isn't completed */
            Rectangle bounds = comp.getBounds();
            Rectangle cr;
            if(clip == null)
                cr = new Rectangle(bounds);
            else
                cr = bounds.intersection(clip);
            if(cr.isEmpty())
                continue; /* Loop/switch isn't completed */
            cg = g.create();
            cg.setClip(cr);
            cg.translate(bounds.x, bounds.y);
            try
            {
                comp.paint(cg);
            }
            catch(Throwable e)
            {

            }

            cg.dispose();
            i--;
        }
    }

    protected Dimension graphSize;
    protected Point origin = new Point(0,0);
    private int annotationHeight;
    protected Image colorBar;
    protected boolean axisDialogEnabled;
    protected static final int MIN_INSETS = 20;
}
