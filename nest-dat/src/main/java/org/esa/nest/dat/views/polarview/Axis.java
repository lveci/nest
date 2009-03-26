package org.esa.nest.dat.views.polarview;

import java.awt.*;

public class Axis {
    private static final double warmNumbers[] = {0.1D, 1.0D, 2D, 3D, 5D, 10D};

    public static final int TOP_X = 1;
    public static final int BOTTOM_X = 2;
    public static final int LEFT_Y = 3;
    public static final int RIGHT_Y = 4;
    public static final int RADIAL = 5;

    private AxisGraphics gr = null;
    private boolean isX;
    private boolean ticksInside;
    private boolean withGrid;
    private double minValue;
    private double maxValue;
    private double axisRange;
    private double tickRange;
    private double minData;
    private double maxData;
    private double minRange;
    private boolean visible;
    private boolean autoRange;
    private int length;
    private int breadth;
    private int TouchId;
    private String title;
    private int tickLength;
    private int tickCount;
    private int bestTickCount;
    private int spacing;
    private String tickNames[] = {"0", "0.5", "1"};
    private double tickValues[] = {0.0D, 0.5D, 1.0D};
    private int tickPositions[] = null;
    private Font font;
    private Font titleFont;
    private Color axisColor;
    private Color labelColor;
    private Color gridColor;

    public Axis(int orientation) {
        isX = true;
        ticksInside = false;
        withGrid = false;
        minValue = 0.0D;
        maxValue = 1.0D;
        axisRange = maxValue - minValue;
        tickRange = maxValue - minValue;
        minData = 0.0D;
        maxData = 1.0D;
        minRange = 0.0D;
        visible = true;
        autoRange = true;
        length = 0;
        breadth = 0;
        TouchId = 0;
        title = null;
        tickLength = -5;
        tickCount = 3;
        bestTickCount = 3;
        spacing = Math.abs(tickLength);
        font = getFont("default.font.plot.axis.tick", "SansSerif-plain-9");
        titleFont = getFont("default.font.plot.axis.title", "SansSerif-plain-9");
        axisColor = Color.white;
        labelColor = Color.black;
        gridColor = Color.darkGray;
        setLocation(orientation);
    }

    private static Font getFont(String propertyName, String defaultFont) {
        String fontSpec = System.getProperty(propertyName, defaultFont);
        return Font.decode(fontSpec);
    }

    private void clearData() {
        title = "";
        setRange(0.0D, 1.0D);
        setTickCount(3);
    }

    public void setTitle(String str) {
        title = str;
    }

    public AxisGraphics getAxisGraphics(Graphics g) {
        gr.setGraphics(g);
        return gr;
    }

    public void setSpacing(int spacing) {
        this.spacing = spacing;
    }

    public final double[] getRange() {
        return new double[]{minValue, maxValue};
    }

    public void setAutoRange(boolean autoRange) {
        if (!this.autoRange && autoRange)
            setApproximateRange(minData, maxData);
        this.autoRange = autoRange;
    }

    public void setDataRange(double range[]) {
        setDataRange(range[0], range[1]);
    }

    void setDataRange(double minValue, double maxValue) {
        minData = minValue;
        maxData = maxValue;
        if (autoRange)
            setApproximateRange(minValue, maxValue);
    }

    void setApproximateRange(double minValue, double maxValue) {
        if (minValue == maxValue) {
            minValue--;
            maxValue++;
        }
        double range = maxValue - minValue;
        if (Math.abs(range) < minRange) {
            maxValue = minValue + minRange;
            range = maxValue - minValue;
        }
        final double step = warmNumber(range / 5D, true);
        final double first = Math.floor(minValue / step + 1.0000000000000001E-005D) * step;
        final int count = (int) Math.ceil((maxValue - first) / step);
        setRange(first, first + (double) count * step, count + 1);
    }

    private static double warmNumber(double thevalue, boolean up) {
        final boolean negative = thevalue < 0.0D;
        double val = thevalue;
        if (negative)
            val = -val;
        int exponent = (int) Math.floor(Math.log10(val));
        val *= Math.pow(10D, -exponent);
        int i;
        for (i = warmNumbers.length - 1; i > 0; i--) {
            if (val > warmNumbers[i])
                break;
        }
        if (up)
            val = warmNumbers[i + 1];
        else
            val = warmNumbers[i];
        val *= Math.pow(10D, exponent);
        if (negative)
            val = -val;
        return val;
    }

    void setRange(double rangeMin, double rangeMax) {
        if (rangeMin == minValue && rangeMax == maxValue)
            return;
        setApproximateRange(rangeMin, rangeMax);
        minValue = rangeMin;
        maxValue = rangeMax;
        if (minValue == maxValue) {
            minValue--;
            maxValue++;
        }
        axisRange = maxValue - minValue;
        if (Math.abs(axisRange) < minRange) {
            maxValue = minValue + minRange;
            axisRange = maxValue - minValue;
        }
        bestTickCount = tickCount;
        setTickCount(tickCount);
    }

    public void setRange(double minValue, double maxValue, int tickCount) {
        if (minValue == this.minValue && maxValue == this.maxValue && tickCount == bestTickCount)
            return;
        if (minValue == maxValue) {
            minValue--;
            maxValue++;
        }
        this.minValue = minValue;
        this.maxValue = maxValue;
        tickRange = axisRange = maxValue - minValue;
        if (Math.abs(axisRange) < minRange) {
            maxValue = minValue + minRange;
            tickRange = axisRange = maxValue - minValue;
        }
        bestTickCount = tickCount;
        setTickCount(tickCount);
    }

    private void setTickCount(int newTickCount) {
        tickCount = Math.abs(newTickCount);
        final double absMin = Math.abs(minValue);
        final double absMax = Math.abs(maxValue);
        final double largestValue = absMin <= absMax ? absMax : absMin;
        int exponent = (int) Math.floor(Math.log10(largestValue));
        if (Math.abs(exponent) < 5)
            exponent = 0;
        double first = minValue;
        int lastTickIndex = tickCount - 1;
        double step = tickRange / (double) lastTickIndex;
        if (Math.abs((first + step) - minValue) < Math.abs(step * 0.15D)) {
            tickCount = lastTickIndex--;
            first += step;
            tickRange -= step;
        }
        if (Math.abs((first + tickRange) - maxValue) > Math.abs(step * 0.85D)) {
            tickCount = lastTickIndex--;
            tickRange -= step;
        }
        if (tickCount < 3) {
            lastTickIndex = 2;
            tickCount = 3;
            first = minValue;
            tickRange = axisRange;
            step = tickRange / (double) lastTickIndex;
        }
        if (tickValues == null || tickValues.length != tickCount) {
            tickValues = new double[tickCount];
            tickNames = new String[tickCount];
        }
        for (int i = 1; i < lastTickIndex; i++) {
            double v = first + (double) i * step;
            tickValues[i] = v;
            tickNames[i] = valueToString(v, exponent);
        }

        tickValues[0] = minValue;
        tickNames[0] = valueToString(minValue, exponent);
        tickValues[lastTickIndex] = maxValue;
        tickNames[lastTickIndex] = valueToString(maxValue, exponent);
        computeTickPositions();
    }

    final void setLocation(int orientation) {
        switch (orientation) {
            case TOP_X:
            default:
                isX = true;
                gr = new AxisGraphics.XAxisGraphics(false);
                break;
            case BOTTOM_X:
                isX = true;
                gr = new AxisGraphics.XAxisGraphics(true);
                break;
            case LEFT_Y:
                isX = false;
                gr = new AxisGraphics.YAxisGraphics(true);
                break;
            case RIGHT_Y:
                isX = false;
                gr = new AxisGraphics.YAxisGraphics(false);
                break;
            case RADIAL:
                isX = true;
                gr = new AxisGraphics.XAxisGraphics(false);
                break;
        }
    }

    public int getTouchId() {
        return TouchId;
    }

    public void setSize(Dimension graphSize) {
        int length;
        int breadth;
        if (isX) {
            length = graphSize.width - 2 * spacing;
            breadth = graphSize.height - 2 * spacing;
        } else {
            length = graphSize.height - 2 * spacing;
            breadth = graphSize.width - 2 * spacing;
        }
        this.breadth = breadth;
        if (length != this.length) {
            this.length = length;
            computeTickPositions();
        }
    }

    public Color getBackground() {
        return gr.getBackground();
    }

    public void setBackground(Color backgroundColor) {
        gr.setBackground(backgroundColor);
    }

    public void draw(Graphics g, Dimension graphSize) {
        setSize(graphSize);
        draw(g);
    }

    public void draw(Graphics g) {
        if (!visible)
            return;
        gr.setGraphics(g);
        final FontMetrics fm = g.getFontMetrics(font);
        g.setColor(axisColor);
        gr.drawLine(0, 0, length + 2 * spacing, 0);
        int tickLength = this.tickLength;
        if (ticksInside)
            tickLength = -tickLength;
        final int maxTickCount = (int) (((float) length + 0.5F) / (float) gr.maxTickSize(tickNames, tickCount, font));
        final int minTickCount = Math.min(maxTickCount, bestTickCount);
        if (tickCount > maxTickCount)
            setTickCount(maxTickCount);
        else if (tickCount < minTickCount)
            setTickCount(minTickCount);
        for (int i = 0; i < tickCount; i++) {
            gr.drawTick(tickPositions[i], tickLength);
        }
        g.setColor(labelColor);
        g.setFont(font);
        for (int i = 0; i < tickCount; i++) {
            String s = tickNames[i];
            gr.drawMultiLineTickName(s, tickPositions[i], tickLength, fm);
        }

        g.setFont(titleFont);
        gr.drawAxisTitle(title, titleFont, length);
        if (!withGrid)
            return;
        g.setColor(gridColor);
        for (int i = 0; i < tickCount; i++) {
            gr.drawTick(tickPositions[i], breadth);
        }
    }

    private static String valueToString(double v, int exponent) {
        if (exponent != 0) {
            final float vm = (float) (v * Math.pow(10D, -exponent));
            if (vm != 0.0F)
                return Float.toString(vm) + "e" + exponent;
        }
        return Float.toString((float) v);
    }

    public final int computeScreenPoint(double value) {
        final int p = spacing + (int) (((double) length * (value - minValue)) / axisRange);
        if (isX) return p;
        return -p;
    }

    private void computeTickPositions() {
        if (tickPositions == null || tickPositions.length != tickCount)
            tickPositions = new int[tickCount];
        for (int i = 0; i < tickCount; i++) {
            if (isX)
                tickPositions[i] = computeScreenPoint(tickValues[i]);
            else
                tickPositions[i] = -computeScreenPoint(tickValues[i]);
        }
        TouchId++;
    }

}
