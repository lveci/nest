package org.esa.nest.dat.views.polarview;

import java.awt.*;

abstract class AxisGraphics
{

    AxisGraphics()
    {
        backgroundColor = Color.white;
    }

    public Color getBackground()
    {
        return backgroundColor;
    }

    public void setBackground(Color backgroundColor)
    {
        this.backgroundColor = backgroundColor;
    }

    abstract void drawLine(int i, int j, int k, int l);

    abstract void drawAxisTitle();

    abstract Rectangle getTickNameBB(String s, int i, int j, int k, int l, FontMetrics fontmetrics);

    abstract void drawTickName(String s, int i, int j, int k, int l, int i1, int j1);

    abstract int maxTickSize();

    void setGraphics(Graphics g)
    {
        this.g = g;
    }

    void drawTick(int tickPixel, int tickLength)
    {
        drawLine(tickPixel, 0, tickPixel, tickLength);
    }

    void drawMultiLineTickName(String name, int tickPixel, int tickLength, FontMetrics fm)
    {
        int height = fm.getAscent();
        int leading = Math.max(fm.getLeading(), 3);
        int lineCount = 0;
        int p = 0;
        do
        {
            p = name.indexOf('\n', p);
            lineCount++;
        } while(++p > 0);
        p = 0;
        int f = 0;
        for(int i = 1; i <= lineCount; i++)
        {
            p = name.indexOf('\n', f);
            if(p < 0)
                p = name.length();
            String text = name.substring(f, p);
            Rectangle bb = getTickNameBB(text, tickPixel, tickLength, i, lineCount, fm);
            Color col = g.getColor();
            g.setColor(backgroundColor);
            g.fillRect(bb.x - leading, bb.y, bb.width + 2 * leading, bb.height + leading);
            g.setColor(col);
            g.drawString(text, bb.x, bb.y + fm.getAscent());
            f = p + 1;
        }

    }

    Dimension getTickLabelBounds(String name, Font font)
    {
        FontMetrics fm = g.getFontMetrics(font);
        int maxWidth = 0;
        int height = 0;
        int p = 0;
        int f = 0;
        do
        {
            p = name.indexOf('\n', f);
            int n;
            if(p < 0)
                n = name.length();
            else
                n = p;
            String text = name.substring(f, n);
            maxWidth = Math.max(fm.stringWidth(text), maxWidth);
            height += fm.getAscent();
            f = p + 1;
        } while(f > 0);
        return new Dimension(maxWidth, height);
    }

    void drawPolyline(int x[], int y[], int points)
    {
        if(points == 1)
            g.drawLine(x[0], y[0], x[0], y[0]);
        else
        if(points <= 16380)
        {
            g.drawPolyline(x, y, points);
        } else
        {
            int o = 0;
            for(int N = 16380; points > 1; N = Math.min(points, 16380))
            {
                System.arraycopy(x, o, pointBuffer[0], 0, N);
                System.arraycopy(y, o, pointBuffer[1], 0, N);
                g.drawPolyline(pointBuffer[0], pointBuffer[1], N);
                N--;
                points -= N;
                o += N;
            }

        }
    }

    void drawPolygon(int x[], int y[], int points)
    {
        if(points == 1)
            g.drawLine(x[0], y[0], x[0], y[0]);
        else
            g.drawPolygon(x, y, points);
    }

    void fillPolygon(int x[], int y[], int points)
    {
        if(points == 1)
            g.drawLine(x[0], y[0], x[0], y[0]);
        else
            g.fillPolygon(x, y, points);
    }

    protected Graphics g;
    protected Color backgroundColor;

    public static final int MAX_POINTS = 16380;
    private static final int pointBuffer[][] = {
        new int[16380], new int[16380]
    };

}
