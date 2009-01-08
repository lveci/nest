package org.esa.nest.dat.views.polarview;

import java.awt.*;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.util.Enumeration;
import java.util.Vector;

public class ColourScale {

    private ColorModel cm;
    private Color[] colors;
    int thresholdCount;
    int[] colorIndexThresholds;
    private int[] defaultIndexThresholds;
    private Vector coloredClients;
    private static Color greyColorTable[];
    private static Color thermalColorTable[];
    private static ColourScale defaultScale = null;

    static {
        greyColorTable = (new Color[]{
                Color.black, Color.black, Color.white, Color.white
        });
        thermalColorTable = (new Color[]{
                Color.black, Color.blue, Color.cyan, Color.green, Color.yellow, Color.red, Color.white
        });
    }

    private double colorIndexValues[];
    private double darkestValue;
    private double darkestIndex;
    private double lightestValue;
    private double lightestIndex;
    private double range;
    private double scale;

    ColourScale(Color colorTable[]) {
        coloredClients = new Vector();
        thresholdCount = colorTable.length;
        colorIndexThresholds = new int[thresholdCount];
        colorIndexThresholds[0] = 0;
        colorIndexThresholds[thresholdCount - 1] = 255;
        colors = colorTable;
        setDefaultThresholds();
    }

    public static ColourScale newMonochromeScale(double range[], Color chromum) {
        Color monochromeColorTable[] = {
                Color.black, Color.black, chromum, chromum
        };
        return new ColourScale(monochromeColorTable, range);
    }

    public static ColourScale newCustomScale(double range[]) {
        return new ColourScale(PolarView.waveColorTable, range);
    }

    ColourScale(Color colorTable[], double range[]) {
        this(colorTable);
        colorIndexValues = new double[thresholdCount];
        setRange(range[0], range[1]);
        createColorMap();
    }

    public boolean isDirectIndex() {
        return false;
    }

    public boolean isIntegerValue() {
        return false;
    }

    public double getTotalRange() {
        return range;
    }

    public double[] getRange() {
        double dRange[] = new double[2];
        dRange[0] = darkestValue;
        dRange[1] = lightestValue;
        return dRange;
    }

    public void setRange(double range[]) {
        setRange(range[0], range[1]);
    }

    public void setRange(int range[]) {
        setRange(range[0], range[1]);
    }

    void setRange(int minValue, int maxValue) {
        setRange((double) minValue, (double) maxValue);
    }

    void setRange(double minValue, double maxValue) {
        darkestValue = minValue;
        lightestValue = maxValue;
        validateRange();
        setEvenThresholds();
        darkestIndex = colorIndexThresholds[0];
        lightestIndex = colorIndexThresholds[thresholdCount - 1];
        updateRange();
    }

    public byte getColorIndex(int value) {
        return getColorIndex((double) value);
    }

    public byte getColorIndex(float value) {
        return getColorIndex((double) value);
    }

    public byte getColorIndex(double value) {
        value -= darkestValue;
        if (value < 0.0D)
            return (byte) (int) darkestIndex;
        if (scale != 0.0D)
            value *= scale;
        value += darkestIndex;
        if (value > lightestIndex)
            return (byte) (int) lightestIndex;
        else
            return (byte) ((int) Math.round(value) & 0xff);
    }

    public int getIntegerColorValue(int index) {
        return (int) Math.round(getDoubleColorValue(index));
    }

    public float getFloatColorValue(int index) {
        return (float) getDoubleColorValue(index);
    }

    public double getDoubleColorValue(int index) {
        double value = (double) index - darkestIndex;
        if (scale != 0.0D)
            value /= scale;
        return value + darkestValue;
    }

    public int getIntegerThresholdValue(int thresholdIndex) {
        return (int) Math.round(getDoubleThresholdValue(thresholdIndex));
    }

    public float getFloatThresholdValue(int thresholdIndex) {
        return (float) getDoubleThresholdValue(thresholdIndex);
    }

    public double getDoubleThresholdValue(int thresholdIndex) {
        return colorIndexValues[thresholdIndex];
    }

    protected void updateColorValues() {
        for (int i = 0; i < thresholdCount; i++) {
            colorIndexValues[i] = getIntegerColorValue(colorIndexThresholds[i]);
        }
    }

    private void validateRange() {
        darkestValue = Math.min(darkestValue, lightestValue);
        range = lightestValue - darkestValue;
        scale = 255D / range;
    }

    public final Color getColor(int value) {
        return new Color(getRGB(value));
    }

    public final Color getColor(float value) {
        return new Color(getRGB(value));
    }

    public final Color getColor(double value) {
        return new Color(getRGB(value));
    }

    int getRGB(int value) {
        return cm.getRGB(getColorIndex(value) & 0xff);
    }

    int getRGB(float value) {
        return cm.getRGB(getColorIndex(value) & 0xff);
    }

    int getRGB(double value) {
        return cm.getRGB(getColorIndex(value) & 0xff);
    }

    public boolean isDirectRGB() {
        return false;
    }

    public int getThresholdCount() {
        return thresholdCount;
    }

    public int getThresholdIndex(int th) {
        return colorIndexThresholds[th];
    }

    public void resetColorThresholds() {
        if (defaultIndexThresholds != null) {
            colorIndexThresholds = new int[thresholdCount];
            System.arraycopy(defaultIndexThresholds, 0, colorIndexThresholds, 0, thresholdCount);
            update();
        }
    }

    void setDefaultThresholds() {
        defaultIndexThresholds = new int[thresholdCount];
        System.arraycopy(colorIndexThresholds, 0, defaultIndexThresholds, 0, thresholdCount);
    }

    public void setColorThreshold(int i, int index) {
        if (!(i < 1 || i >= thresholdCount - 1)) {
            index = limitColorThreshold(i, index);
            colorIndexThresholds[i] = index;
            update();
        }
    }

    public ColorModel getColorModel() {
        return cm;
    }

    public synchronized void addColoredObject(ColorBar ip) {
        if (!coloredClients.contains(ip)) {
            coloredClients.addElement(ip);
        }
    }

    public synchronized void removeColoredObject(ColorBar ip) {
        coloredClients.removeElement(ip);
    }

    int limitColorThreshold(int i, int index) {
        int lastThreshold = thresholdCount - 1;
        if (i < 1 || i >= lastThreshold)
            return index;
        int upperLimit = colorIndexThresholds[i + 1];
        if (i < lastThreshold - 1)
            upperLimit -= 2;
        int lowerLimit = colorIndexThresholds[i - 1];
        if (i > 1)
            lowerLimit += 2;
        index = Math.min(index, upperLimit);
        index = Math.max(index, lowerLimit);
        return index;
    }

    void setEvenThresholds() {
        int N = thresholdCount - 1;
        int first = 0;
        int last = N;
        if (colors[last].equals(colors[last - 1])) {
            colorIndexThresholds[last] = 255;
            last--;
            N--;
        }
        if (colors[first].equals(colors[first + 1])) {
            colorIndexThresholds[first] = 0;
            first++;
            N--;
        }
        double colorStep = 255D;
        int offset = 0;
        if (isDirectIndex()) {
            double range[] = getRange();
            offset = (int) Math.round(range[0]);
            colorStep = Math.min(colorStep, getTotalRange());
        } else if (isIntegerValue())
            colorStep = Math.min(colorStep, getTotalRange());
        colorStep /= N;
        int i = 0;
        for (int t = first; t <= last; t++) {
            colorIndexThresholds[t] = offset + (int) Math.round((double) i * colorStep);
            i++;
        }
    }

    void createColorMap() {
        int lastThreshold = thresholdCount - 1;
        byte cmap[] = new byte[768];
        Color lastColor = colors[0];
        int lastIndex = colorIndexThresholds[0];
        int c = 0;
        int k = 0;
        for (int i = 1; i < thresholdCount; i++) {
            int cRange = colorIndexThresholds[i] - lastIndex;
            int lastRGB[] = {
                    lastColor.getRed(), lastColor.getGreen(), lastColor.getBlue()
            };
            int nextRGB[] = {
                    colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue()
            };
            for (int j = 0; j < cRange; j++) {
                float nextScale = (float) j / (float) cRange;
                float lastScale = 1.0F - nextScale;
                k++;
                cmap[c++] = (byte) (int) ((float) lastRGB[0] * lastScale + (float) nextRGB[0] * nextScale);
                cmap[c++] = (byte) (int) ((float) lastRGB[1] * lastScale + (float) nextRGB[1] * nextScale);
                cmap[c++] = (byte) (int) ((float) lastRGB[2] * lastScale + (float) nextRGB[2] * nextScale);
            }

            lastColor = colors[i];
            lastIndex = colorIndexThresholds[i];
        }

        Color finalColor = colors[lastThreshold];
        cmap[c++] = (byte) finalColor.getRed();
        cmap[c++] = (byte) finalColor.getGreen();
        cmap[c] = (byte) finalColor.getBlue();
        cm = new IndexColorModel(8, 256, cmap, 0, false);
    }

    synchronized void notifyMapChange() {
        ColorBar ip;
        for (Enumeration elem = coloredClients.elements(); elem.hasMoreElements(); ip.updatedColorMap()) {
            ip = (ColorBar) elem.nextElement();
        }
    }

    synchronized void notifyRangeChange() {
        ColorBar ip;
        for (Enumeration elem = coloredClients.elements(); elem.hasMoreElements(); ip.updatedColorScale()) {
            ip = (ColorBar) elem.nextElement();
        }
    }

    void updateRange() {
        updateColorValues();
        createColorMap();
        notifyRangeChange();
    }

    private void update() {
        updateColorValues();
        createColorMap();
        notifyMapChange();
    }
}