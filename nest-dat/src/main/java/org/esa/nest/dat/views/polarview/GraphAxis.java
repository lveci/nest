package org.esa.nest.dat.views.polarview;

import org.esa.beam.util.Debug;

import java.awt.*;

public class GraphAxis {
    private static double warmNumbers[] = {
            0.10000000000000001D, 1.0D, 2D, 3D, 5D, 10D
    };

    public static final int TOP_X = 1;
    public static final int BOTTOM_X = 2;
    public static final int LEFT_Y = 3;
    public static final int RIGHT_Y = 4;
    public static final boolean INSIDE = true;
    public static final boolean OUTSIDE = false;
    AxisGraphics gr;
    String name;
    boolean isX;
    boolean isBottomLeft;
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
    private Object values;
    private int length;
    private int breadth;
    private int TouchId;
    private String title;
    private int tickLength;
    private int minorTickLength;
    private int tickCount;
    private int bestTickCount;
    private int minorTickCount;
    private int spacing;
    private String tickNames[] = {
            "0", "0.5", "1"
    };
    private double tickValues[] = {
            0.0D, 0.5D, 1.0D
    };
    private int tickPositions[];
    private Font font;
    private Font titleFont;
    private Color axisColor;
    private Color labelColor;
    private Color gridColor;

    class XAxisGraphics extends AxisGraphics {

        void drawLine(int x1, int y1, int x2, int y2) {
            if (isBottomLeft)
                g.drawLine(x1, -y1, x2, -y2);
            else
                g.drawLine(x1, y1, x2, y2);
        }

        void drawAxisTitle() {
            if (title != null) {
                FontMetrics tfm = g.getFontMetrics(titleFont);
                int x = (length - tfm.stringWidth(title)) / 2;
                int y = tfm.getHeight() * 3;
                g.drawString(title, x, y);
            }
        }

        Rectangle getTickNameBB(String name, int tickPixel, int tickLength, int lineNumber, int lineCount, FontMetrics fm) {
            int height = fm.getAscent();
            int width = fm.stringWidth(name);
            int x = tickPixel;
            int y = tickLength >= 0 ? 0 : tickLength;
            x -= width / 2;
            if (isBottomLeft) {
                y -= Math.round((float) height * ((float) lineNumber + 0.5F));
                y = -y;
            } else {
                y -= Math.round((float) height * ((float) lineNumber - 0.5F));
            }
            return new Rectangle(x, y - height, width, height);
        }

        void drawTickName(String name, int tickPixel, int tickLength, int width, int height, int lineNumber, int lineCount) {
            int x = tickPixel;
            int y = tickLength >= 0 ? 0 : tickLength;
            x -= width / 2;
            if (isBottomLeft) {
                y -= Math.round((float) height * ((float) lineNumber + 0.5F));
                g.drawString(name, x, -y);
            } else {
                y -= Math.round((float) height * ((float) lineNumber - 0.5F));
                g.drawString(name, x, y);
            }
        }

        int maxTickSize() {
            int maxWidth = 0;
            for (int i = 0; i < tickCount; i++)
                maxWidth = Math.max(getTickLabelBounds(tickNames[i], font).width + 6, maxWidth);

            return maxWidth;
        }

        XAxisGraphics() {
        }
    }

    class YAxisGraphics extends AxisGraphics {

        void drawLine(int x1, int y1, int x2, int y2) {
            if (isBottomLeft)
                g.drawLine(y1, -x1, y2, -x2);
            else
                g.drawLine(-y1, -x1, -y2, -x2);
        }

        void drawAxisTitle() {
            if (title != null) {
                FontMetrics fm = g.getFontMetrics(titleFont);
                int y = length + fm.getHeight();
                int w = fm.stringWidth(title);
                int x = isBottomLeft ? -w / 2 : -(w / 2);
                g.drawString(title, x, -y);
            }
        }

        Rectangle getTickNameBB(String name, int tickPixel, int tickLength, int lineNumber, int lineCount, FontMetrics fm) {
            int height = fm.getAscent();
            int width = fm.stringWidth(name);
            int y = tickPixel;
            int x = tickLength >= 0 ? 0 : tickLength;
            y -= height * lineNumber - (height * lineCount) / 2;
            x -= height / 2;
            if (isBottomLeft) {
                x -= width;
                x = -x;
            }
            return new Rectangle(-x, -y - height, width, height);
        }

        void drawTickName(String name, int tickPixel, int tickLength, int width, int height, int lineNumber, int lineCount) {
            int y = tickPixel;
            int x = tickLength >= 0 ? 0 : tickLength;
            y -= height * lineNumber - (height * lineCount) / 2;
            x -= height / 2;
            if (isBottomLeft) {
                x -= width;
                g.drawString(name, x, -y);
            } else {
                g.drawString(name, -x, -y);
            }
        }

        int maxTickSize() {
            int maxHeight = 0;
            for (int i = 0; i < tickCount; i++)
                maxHeight = Math.max(getTickLabelBounds(tickNames[i], font).height + 6, maxHeight);

            return maxHeight;
        }

        YAxisGraphics() {
        }
    }


    public GraphAxis(int orientation) {
        isX = true;
        isBottomLeft = true;
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
        values = null;
        length = 0;
        breadth = 0;
        TouchId = 0;
        title = null;
        tickLength = -5;
        minorTickLength = -3;
        tickCount = 3;
        bestTickCount = 3;
        minorTickCount = 0;
        spacing = Math.abs(tickLength);
        font = getFont("default.font.plot.axis.tick", "SansSerif-plain-9");
        titleFont = getFont("default.font.plot.axis.title", "SansSerif-plain-9");
        axisColor = Color.black;
        labelColor = Color.black;
        gridColor = Color.gray;
        setLocation(orientation);
    }

    private static Font getFont(String propertyName, String defaultFont) {
        String fontSpec = System.getProperty(propertyName, defaultFont);
        return Font.decode(fontSpec);
    }

    public void setValues(Object values) {
        this.values = values;
        if (values == null)
            clearData();
    }

    public Object getValues() {
        return values;
    }

    final void clearData() {
        title = "";
        values = null;
        setRange(0.0D, 1.0D);
        setTickCount(3);
    }

    public AxisGraphics getAxisGraphics(Graphics g) {
        gr.setGraphics(g);
        return gr;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean getVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public int getSpacing() {
        return spacing;
    }

    public void setSpacing(int spacing) {
        this.spacing = spacing;
    }

    public boolean isAutoRange() {
        return autoRange;
    }

    public final double[] getRange() {
        return new double[]{minValue, maxValue};
    }

    public final double getRangeMin() {
        return minValue;
    }

    public final double getRangeMax() {
        return maxValue;
    }

    public final double[] getDataRange() {
        return new double[]{minData, maxData};
    }

    public final double getDataRangeMin() {
        return minData;
    }

    public final double getDataRangeMax() {
        return maxData;
    }

    public void setMinRange(double minRange) {
        this.minRange = minRange;
    }

    public void setAutoRange(boolean autoRange) {
        if (!this.autoRange && autoRange)
            setApproximateRange(minData, maxData);
        this.autoRange = autoRange;
    }

    public void setDataRange(double minValue, double maxValue) {
        setDataRange(minValue, maxValue, java.lang.Double.class);
    }

    public void setDataRange(double range[]) {
        setDataRange(range[0], range[1], java.lang.Double.class);
    }

    public void setDataRange(double range[], Class dataType) {
        setDataRange(range[0], range[1], dataType);
    }

    void setDataRange(double minValue, double maxValue, Class dataType) {
        minData = minValue;
        maxData = maxValue;
        if (autoRange)
            setApproximateRange(minValue, maxValue);
    }

    public void setApproximateRange(double range[]) {
        setApproximateRange(range[0], range[1]);
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
        double step = warmNumber(range / 5D, true);
        double first = Math.floor(minValue / step + 1.0000000000000001E-005D) * step;
        int count = (int) Math.ceil((maxValue - first) / step);
        setRange(first, first + (double) count * step, count + 1);
    }

    private static double warmNumber(double value, boolean up) {
        boolean negative = value < 0.0D;
        if (negative)
            value = -value;
        int exponent = (int) Math.floor(Math.log10(value));
        value *= Math.pow(10D, -exponent);
        int i;
        for (i = warmNumbers.length - 1; i > 0; i--)
            if (value > warmNumbers[i])
                break;

        if (up)
            value = warmNumbers[i + 1];
        else
            value = warmNumbers[i];
        value *= Math.pow(10D, exponent);
        if (negative)
            value = -value;
        return value;
    }

    public final void setRange(double range[]) {
        setRange(range[0], range[1]);
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

    void setTickCount(int newTickCount) {
        tickCount = Math.abs(newTickCount);
        double absMin = Math.abs(minValue);
        double absMax = Math.abs(maxValue);
        double largestValue = absMin <= absMax ? absMax : absMin;
        int exponent = (int) Math.floor(Math.log10(largestValue));
        if (Math.abs(exponent) < 5)
            exponent = 0;
        double first = minValue;
        int lastTickIndex = tickCount - 1;
        double step = tickRange / (double) lastTickIndex;
        if (Math.abs((first + step) - minValue) < Math.abs(step * 0.14999999999999999D)) {
            tickCount = lastTickIndex--;
            first += step;
            tickRange -= step;
        }
        if (Math.abs((first + tickRange) - maxValue) > Math.abs(step * 0.84999999999999998D)) {
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

    public void setTickOrientation(boolean orientation) {
        ticksInside = orientation;
    }

    void setFont(Font font) {
        this.font = font;
    }

    public Font getFont() {
        return font;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    void setLocation(int orientation) {
        switch (orientation) {
            case 1: // '\001'
            default:
                isX = true;
                name = "X'";
                isBottomLeft = false;
                gr = new XAxisGraphics();
                break;

            case 2: // '\002'
                isX = true;
                name = "X";
                isBottomLeft = true;
                gr = new XAxisGraphics();
                break;

            case 3: // '\003'
                isX = false;
                name = "Y";
                isBottomLeft = true;
                gr = new YAxisGraphics();
                break;

            case 4: // '\004'
                isX = false;
                name = "Y'";
                isBottomLeft = false;
                gr = new YAxisGraphics();
                break;
        }
    }

    public boolean isTouchedSince(int TouchId) {
        return TouchId != this.TouchId;
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

    public int getLength() {
        return length;
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
        setFont(font);
        FontMetrics fm = g.getFontMetrics(font);
        g.setColor(axisColor);
        gr.drawLine(0, 0, length + 2 * spacing, 0);
        int tickLength = this.tickLength;
        if (ticksInside)
            tickLength = -tickLength;
        int maxTickCount = (int) (((float) length + 0.5F) / (float) gr.maxTickSize());
        int minTickCount = Math.min(maxTickCount, bestTickCount);
        if (tickCount > maxTickCount)
            setTickCount(maxTickCount);
        else if (tickCount < minTickCount)
            setTickCount(minTickCount);
        for (int i = 0; i < tickCount; i++)
            gr.drawTick(tickPositions[i], tickLength);

        g.setColor(labelColor);
        g.setFont(font);
        for (int i = 0; i < tickCount; i++) {
            String s = tickNames[i];
            gr.drawMultiLineTickName(s, tickPositions[i], tickLength, fm);
        }

        g.setFont(titleFont);
        gr.drawAxisTitle();
        if (!withGrid)
            return;
        g.setColor(gridColor);
        for (int i = 0; i < tickCount; i++)
            gr.drawTick(tickPositions[i], breadth);

    }

    String valueToString(double v, int exponent) {
        if (exponent != 0) {
            float vm = (float) (v * Math.pow(10D, -exponent));
            if (vm != 0.0F)
                return Float.toString(vm) + "e" + exponent;
        }
        return Float.toString((float) v);
    }

    public double valueFromString(String s) {
        try {
            return new Double(s);
        }
        catch (Exception e) {
            Debug.trace(e);
        }
        return 0.0D;
    }

    public final int computeScreenPoint(double value) {
        int p = spacing + (int) (((double) length * (value - minValue)) / axisRange);
        if (isX)
            return p;
        else
            return -p;
    }

    public final double valueFromScreenPoint(int p) {
        if (!isX)
            p = -p;
        return ((double) (p - spacing) * axisRange) / (double) length + minValue;
    }

    private void computeTickPositions() {
        if (tickPositions == null || tickPositions.length != tickCount)
            tickPositions = new int[tickCount];
        for (int i = 0; i < tickCount; i++)
            if (isX)
                tickPositions[i] = computeScreenPoint(tickValues[i]);
            else
                tickPositions[i] = -computeScreenPoint(tickValues[i]);

        TouchId++;
    }

}
