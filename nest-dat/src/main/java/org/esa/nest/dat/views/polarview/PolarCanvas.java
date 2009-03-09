package org.esa.nest.dat.views.polarview;

import org.esa.beam.util.Debug;

import java.awt.*;

public class PolarCanvas extends Container {

    private Axis radialAxis;
    private Axis colourAxis;
    private PolarData data = null;
    private double rings[] = null;
    //private Glyph ringText[];
    //private StyledText xTitle;
    // private StyledText yTitle;
    private float dirOffset;
    private int plotRadius;
    private boolean opaque;
    private final Dimension graphSize = new Dimension(200, 100);
    private Point origin = new Point(0, 0);

    private Image colorBar = null;

    public PolarCanvas() {
        this(new Axis(Axis.RADIAL), new Axis(Axis.RIGHT_Y));
    }

    private PolarCanvas(Axis radialAxis, Axis colourAxis) {
        opaque = false;
        //xTitle = new StyledText("E");
        //yTitle = new StyledText("N");
        dirOffset = 0.0F;
        plotRadius = 0;
        this.radialAxis = radialAxis;
        this.colourAxis = colourAxis;
        colourAxis.setLocation(4);
        radialAxis.setSpacing(0);
        colourAxis.setSpacing(0);

        enableEvents(16L);
        setBackground(Color.white);
    }

    @Override
    public Font getFont() {
        final Font font = super.getFont();
        if (font == null)
            return Font.decode("SansSerif-plain-9");
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
        for (Container c = comp.getParent(); c != null; c = c.getParent()) {
            if (c instanceof Window)
                return (Window) c;
        }
        return null;
    }

    @Override
    public void setBackground(Color background) {
        opaque = true;
        super.setBackground(background);
    }

    public void setFontSize(int size) {
        final Font f = getFont();
        if (f == null)
            return;
        if (f.getSize() != size) {
            setFont(new Font(f.getName(), f.getStyle(), size));
        }
    }

    private void fillBackground(Graphics g) {
        final Rectangle clip = g.getClipBounds();
        final Color col = g.getColor();
        g.setColor(getBackground());
        g.fillRect(clip.x, clip.y, clip.width, clip.height);
        g.setColor(col);
    }

    @Override
    public void repaint(long tm, int x, int y, int width, int height) {
        try {
            super.repaint(tm, x, y, width, height);
        } catch (Throwable e) {
            Debug.trace(e);
        }
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    @Override
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
        } catch (Throwable e) {
            Debug.trace(e);
        }
    }

    private synchronized void drawSynchronised(Graphics g, Dimension size) {
        draw(g, size);
    }

    private Rectangle positionPlot(Dimension size, int x, int y, int bottom, int right) {
        //insets.setValue(top, left, bottom, right);
        //Rectangle r = insets.shrinkRect(size);
        final Rectangle r = new Rectangle(x, y,
                (int) size.getWidth() - x - right, (int) size.getHeight() - y - bottom);
        origin = r.getLocation();
        graphSize.setSize(r.width, r.height);
        return r;
    }

    private void loadColorBar(ColourScale scale) {
        if (colorBar == null)
            colorBar = createImage(new ColorBar(scale));
    }

    private void drawColorBar(Graphics g, Axis cAxis) {
        final Dimension cbSize = new Dimension((int) (graphSize.width * 0.03D),
                (int) (Math.min(200, graphSize.height * 0.6D)));
        final Point at = new Point(0, -100);

        g.translate(at.x, at.y);
        g.drawImage(colorBar, 0, 0, cbSize.width, cbSize.height, this);
        g.drawRect(0, 0, cbSize.width, cbSize.height);
        g.translate(cbSize.width, cbSize.height);
        cAxis.draw(g, cbSize);
        g.translate(-cbSize.width - at.x, -cbSize.height - at.y);
    }

    @Override
    protected void finalize()
            throws Throwable {
        if (colorBar != null)
            colorBar.flush();
        colorBar = null;
        super.finalize();
    }

    private static void paintLightweightChildren(Container c, Graphics g) {
        if (!c.isShowing()) return;

        final int ncomponents = c.getComponentCount();
        final Rectangle clip = g.getClipBounds();

        int i = ncomponents - 1;
        while (i >= 0) {
            final Component component[] = c.getComponents();
            final Component comp = component[i];
            if (comp == null || !comp.isVisible())
                continue;
            final Rectangle bounds = comp.getBounds();
            Rectangle cr;
            if (clip == null)
                cr = new Rectangle(bounds);
            else
                cr = bounds.intersection(clip);
            if (cr.isEmpty())
                continue;

            final Graphics cg = g.create();
            cg.setClip(cr);
            cg.translate(bounds.x, bounds.y);
            try {
                comp.paint(cg);
            } catch (Throwable e) {
                //
            }

            cg.dispose();
            i--;
        }
    }

    public Axis getRadialAxis() {
        return radialAxis;
    }

    public Axis getColourAxis() {
        return colourAxis;
    }

    public PolarData getData() {
        return data;
    }

    public void setData(PolarData data) {
        this.data = data;
        if (data != null) {
            data.setRAxis(radialAxis);
            data.setDirOffset(dirOffset);
            data.setCAxis(colourAxis);
        }
    }

    public void setRings(double rings[], String ringText[]) {
        this.rings = rings;
        //this.ringText = ringText;
    }

    public float getDirOffset() {
        return dirOffset;
    }

    public void setDirOffset(float dirOffset) {
        this.dirOffset = dirOffset;
        if (data != null)
            data.setDirOffset(dirOffset);
    }

    public void setCompassNames(String eStr, String nStr) {
        if (eStr == null || nStr == null) {
        } else {
            //xTitle.setText(eStr);
            //yTitle.setText(nStr);
        }
    }

    public double[] getRTheta(Point oP) {
        final Point p = new Point(oP);
        p.y = origin.y - p.y;
        p.x -= origin.x;
        if (Math.abs(p.y) > plotRadius || Math.abs(p.x) > plotRadius) {
            return null;
        } else {
            final int r = (int) Math.sqrt(p.x * p.x + p.y * p.y);
            final double rV = data.valueFromScreenPoint(r);
            return new double[]{rV, (360D - (Math.atan2(p.x, p.y) * 180D) / Math.PI) % 360D};
        }
    }

    public Axis selectAxis(Point oP) {
        final Point p = new Point(oP);
        p.y = origin.y - p.y;
        p.x -= origin.x;
        if (Math.abs(p.y) < plotRadius) {
            if (Math.abs(p.x) < plotRadius)
                return radialAxis;
            if (p.x > graphSize.width / 2)
                return colourAxis;
        }
        return null;
    }

    private void draw(Graphics g, Dimension size) {
        final int annotationHeight = 100;//getAnnotationHeight(g);
        final int x = Math.max((int) (size.height * 0.05), 10);
        final int y = Math.max((int) (size.width * 0.05), 10);
        final int bottom = Math.max((int) (size.height * 0.1) + annotationHeight, 20);
        final int right = Math.max((int) (size.width * 0.1), 20);
        final Rectangle r = positionPlot(size, x, y, bottom, right);
        plotRadius = Math.min(r.width / 2, r.height / 2);
        final Dimension quadrantSize = new Dimension(plotRadius, plotRadius);
        g.translate(origin.x, origin.y + r.height);
        if (data != null) {
            loadColorBar(data.getColorScale());
            drawColorBar(g, colourAxis);
        }
        g.translate(-origin.x, -origin.y - r.height);
        final Point xYo = new Point(origin.x, origin.y + r.height);
        final int xYs = origin.x / 2;
        origin.y += r.height / 2;
        origin.x += r.width / 2;
        final Graphics oGraphics = g.create();
        oGraphics.translate(origin.x, origin.y);
        radialAxis.setSize(quadrantSize);
        if (data != null)
            data.draw(oGraphics);
        oGraphics.setColor(Color.darkGray);
        if (rings != null) {
            for (double ring : rings) {
                final int rad = radialAxis.computeScreenPoint(ring);
                final int rad2 = rad + rad;
                oGraphics.drawOval(-rad, -rad, rad2, rad2);
                //if(ringText != null && ringText[ri] != null)
                //    ringText[ri].drawCentered(oGraphics, 0, -(rad + ringText[ri].getDescent(oGraphics) * 3));
            }
        } //else {
            radialAxis.draw(oGraphics);
       // }
        oGraphics.translate(-origin.x, -origin.y);
        oGraphics.dispose();
        //drawAnnotation(g, size);
    }

}
