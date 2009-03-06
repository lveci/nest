package org.esa.nest.dat.views.polarview;

import org.esa.beam.util.Debug;

import java.awt.*;

public class PolarCanvas extends Container {

    private Axis rAxis;
    private Axis cAxis;
    private PolarData data;
    private double rings[];
    //private Glyph ringText[];
    //private StyledText xTitle;
    // private StyledText yTitle;
    private boolean drawCompass;
    private float dirOffset;
    private int plotRadius;

    private boolean opaque;
    Dimension graphSize;
    Point origin = new Point(0, 0);
    private int annotationHeight;
    private Image colorBar;
    private boolean axisDialogEnabled;
    protected static final int MIN_INSETS = 20;

    public PolarCanvas() {
        this(new Axis(Axis.RADIAL), new Axis(4));
    }

    private PolarCanvas(Axis rAxis, Axis cAxis) {
        opaque = false;
        graphSize = new Dimension(200, 100);
        annotationHeight = 20;
        colorBar = null;
        axisDialogEnabled = false;
        data = null;
        //xTitle = new StyledText("E");
        //yTitle = new StyledText("N");
        drawCompass = true;
        dirOffset = 0.0F;
        plotRadius = 0;
        this.rAxis = rAxis;
        this.cAxis = cAxis;
        cAxis.setLocation(4);
        rAxis.setSpacing(0);
        cAxis.setSpacing(0);
        //enableEvents(16L);
        //setBackground(Color.white);
    }

    @Override
    public Font getFont() {
        Font font = super.getFont();
        if (font == null)
            return Font.decode("SansSerif-plain-9");
        else
            return font;
    }

    public Frame getParentFrame() {
        return (Frame) getParentWindow(this);
    }

    private static Window getParentWindow(Component comp) {
        if (comp == null)
            return null;
        if (comp instanceof Window)
            return (Window) comp;
        for (Container c = comp.getParent(); c != null; c = c.getParent())
            if (c instanceof Window)
                return (Window) c;

        return null;
    }

    public boolean getOpaque() {
        return opaque;
    }

    public void setOpaque(boolean opaque) {
        this.opaque = opaque;
    }

    public void setBackground(Color background) {
        opaque = true;
        super.setBackground(background);
    }

    public void setFontSize(int size) {
        Font f = getFont();
        if (f == null)
            return;
        if (f.getSize() != size) {
            setFont(new Font(f.getName(), f.getStyle(), size));
        }
    }

    void fillBackground(Graphics g) {
        Rectangle clip = g.getClipBounds();
        Color col = g.getColor();
        g.setColor(getBackground());
        g.fillRect(clip.x, clip.y, clip.width, clip.height);
        g.setColor(col);
    }

    public void repaint(long tm, int x, int y, int width, int height) {
        try {
            super.repaint(tm, x, y, width, height);
        }
        catch (Throwable e) {
            Debug.trace(e);
        }
    }

    public void update(Graphics g) {
        try {
            paint(g);
        }
        catch (Throwable e) {
            Debug.trace(e);
        }
    }

    public void paint(Graphics g) {
        try {
            if (isShowing()) {
                if (opaque)
                    fillBackground(g);
                g.setColor(getForeground());
                g.setFont(getFont());
                paintLightweightChildren(this, g);
            }

            drawSynchronised(g, getSize());
        }
        catch (Throwable e) {
            Debug.trace(e);
        }
    }

    public void disableAxisDialog() {
        axisDialogEnabled = false;
    }

    final synchronized void drawSynchronised(Graphics g, Dimension size) {
        draw(g, size);
    }

    Rectangle positionPlot(Dimension size, int top, int left, int bottom, int right) {
        //insets.setValue(top, left, bottom, right);
        //Rectangle r = insets.shrinkRect(size);
        Rectangle r = new Rectangle(top, left, (int) size.getWidth() - left - right, (int) size.getHeight() - top - bottom);
        origin = r.getLocation();
        graphSize.setSize(r.width, r.height);
        return r;
    }

    void loadColorBar(ColourScale scale) {
        if (colorBar == null)
            colorBar = createImage(new ColorBar(scale));
    }

    void drawColorBar(Graphics oGraphics, Axis cAxis) {
        drawColorBar(oGraphics, cAxis, 0, 0);
    }

    void drawColorBar(Graphics oGraphics, Axis cAxis, int xo, int yo) {
        Dimension cbSize = new Dimension((int) ((double) graphSize.width * 0.029999999999999999D), (int) ((double) graphSize.height * 0.59999999999999998D));
        Point at = new Point((int) ((double) graphSize.width * 1.04D + (double) xo), -(int) ((double) graphSize.height * 0.80000000000000004D + (double) yo));
        drawColorBar(oGraphics, cAxis, cbSize, at);
    }

    void drawColorBar(Graphics oGraphics, Axis cAxis, Dimension cbSize, Point at) {
        oGraphics.translate(at.x, at.y);
        oGraphics.drawImage(colorBar, 0, 0, cbSize.width, cbSize.height, this);
        oGraphics.drawRect(0, 0, cbSize.width, cbSize.height);
        oGraphics.translate(cbSize.width, cbSize.height);
        cAxis.draw(oGraphics, cbSize);
        oGraphics.translate(-cbSize.width - at.x, -cbSize.height - at.y);
    }

    protected void finalize()
            throws Throwable {
        if (colorBar != null)
            colorBar.flush();
        colorBar = null;
        super.finalize();
    }

    private static void paintLightweightChildren(Container c, Graphics g) {
        if (!c.isShowing()) return;

        Rectangle clip;
        int i;
        int ncomponents = c.getComponentCount();
        clip = g.getClipBounds();
        i = ncomponents - 1;

        while (i >= 0) {
            Component comp;
            Graphics cg;
            Component component[] = c.getComponents();
            comp = component[i];
            if (comp == null || !comp.isVisible())
                continue; /* Loop/switch isn't completed */
            Rectangle bounds = comp.getBounds();
            Rectangle cr;
            if (clip == null)
                cr = new Rectangle(bounds);
            else
                cr = bounds.intersection(clip);
            if (cr.isEmpty())
                continue; /* Loop/switch isn't completed */
            cg = g.create();
            cg.setClip(cr);
            cg.translate(bounds.x, bounds.y);
            try {
                comp.paint(cg);
            }
            catch (Throwable e) {

            }

            cg.dispose();
            i--;
        }
    }

    public Axis getRAxis() {
        return rAxis;
    }

    public Axis getCAxis() {
        return cAxis;
    }

    public PolarData getData() {
        return data;
    }

    public void setData(PolarData data) {
        this.data = data;
        if (data != null) {
            data.setRAxis(rAxis);
            data.setDirOffset(dirOffset);
            data.setCAxis(cAxis);
        }
    }

    //public void setRings(double rings[], Glyph ringText[])
    //{
    //    this.rings = rings;
    //this.ringText = ringText;
    //}

    public float getDirOffset() {
        return dirOffset;
    }

    public void setDirOffset(float dirOffset) {
        this.dirOffset = dirOffset;
        if (data != null)
            data.setDirOffset(dirOffset);
        drawCompass = true;
    }

    public void setCompassNames(String eStr, String nStr) {
        if (eStr == null || nStr == null) {
        } else {
            //xTitle.setText(eStr);
            //yTitle.setText(nStr);
            drawCompass = true;
        }
    }

    public void disableCompass() {
        drawCompass = false;
    }

    public void enableCompass() {
        drawCompass = true;
    }

    public double[] getRTheta(Point oP) {
        final Point p = new Point(oP);
        p.y = origin.y - p.y;
        p.x = p.x - origin.x;
        if (Math.abs(p.y) > plotRadius || Math.abs(p.x) > plotRadius) {
            return null;
        } else {
            final int r = (int) Math.sqrt(p.x * p.x + p.y * p.y);
            final double rV = data.valueFromScreenPoint(r);
            return new double[]{rV, (360D - (Math.atan2(p.x, p.y) * 180D) / 3.1415926535897931D) % 360D};
        }
    }

    public Axis selectAxis(Point oP) {
        final Point p = new Point(oP);
        p.y = origin.y - p.y;
        p.x = p.x - origin.x;
        if (Math.abs(p.y) < plotRadius) {
            if (Math.abs(p.x) < plotRadius)
                return rAxis;
            if (p.x > graphSize.width / 2)
                return cAxis;
        }
        return null;
    }

    protected void draw(Graphics g, Dimension size) {
        final int annotationHeight = 100;//getAnnotationHeight(g);
        final int top = Math.max((int) (size.height * 0.05), 20);
        final int left = Math.max((int) (size.width * 0.10), 20);
        final int bottom = Math.max((int) (size.height * 0.5) + annotationHeight, 20);
        final int right = Math.max((int) (size.width * 0.15), 20);
        Rectangle r = positionPlot(size, top, left, bottom, right);
        plotRadius = Math.min(r.width / 2, r.height / 2);
        Dimension quadrantSize = new Dimension(plotRadius, plotRadius);
        g.translate(origin.x, origin.y + r.height);
        if (data != null) {
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
        if (data != null)
            data.draw(oGraphics);
        oGraphics.setColor(Color.darkGray);
        if (rings != null) {
            for (double ring : rings) {
                int rad = rAxis.computeScreenPoint(ring);
                int rad2 = rad + rad;
                oGraphics.drawOval(-rad, -rad, rad2, rad2);
                //if(ringText != null && ringText[ri] != null)
                //    ringText[ri].drawCentered(oGraphics, 0, -(rad + ringText[ri].getDescent(oGraphics) * 3));
            }

        } else {
            rAxis.draw(oGraphics);
        }
        oGraphics.translate(-origin.x, -origin.y);
        oGraphics.dispose();
        //drawAnnotation(g, size);
    }

}
