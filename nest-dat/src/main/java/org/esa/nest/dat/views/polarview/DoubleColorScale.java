package org.esa.nest.dat.views.polarview;

import java.awt.Color;

class DoubleColorScale extends ColorScale
{

    DoubleColorScale(Color colorTable[], double range[])
    {
        super(colorTable);
        colorIndexValues = new double[thresholdCount];
        setRange(range[0], range[1]);
        createColorMap();
    }

    public boolean isDirectIndex()
    {
        return false;
    }

    public boolean isIntegerValue()
    {
        return false;
    }

    public double getTotalRange()
    {
        return range;
    }

    public double[] getRange()
    {
        double dRange[] = new double[2];
        dRange[0] = darkestValue;
        dRange[1] = lightestValue;
        return dRange;
    }

    public void setRange(double range[])
    {
        setRange(range[0], range[1]);
    }

    public void setRange(int range[])
    {
        setRange(range[0], range[1]);
    }

    public void setRange(int minValue, int maxValue)
    {
        setRange((double)minValue, (double)maxValue);
    }

    public void setRange(double minValue, double maxValue)
    {
        darkestValue = minValue;
        lightestValue = maxValue;
        validateRange();
        setEvenThresholds();
        darkestIndex = colorIndexThresholds[0];
        lightestIndex = colorIndexThresholds[thresholdCount - 1];
        updateRange();
    }

    public byte getColorIndex(int value)
    {
        return getColorIndex((double)value);
    }

    public byte getColorIndex(float value)
    {
        return getColorIndex((double)value);
    }

    public byte getColorIndex(double value)
    {
        value -= darkestValue;
        if(value < 0.0D)
            return (byte)(int)darkestIndex;
        if(scale != 0.0D)
            value *= scale;
        value += darkestIndex;
        if(value > lightestIndex)
            return (byte)(int)lightestIndex;
        else
            return (byte)((int)Math.round(value) & 0xff);
    }

    public int getIntegerColorValue(int index)
    {
        return (int)Math.round(getDoubleColorValue(index));
    }

    public float getFloatColorValue(int index)
    {
        return (float)getDoubleColorValue(index);
    }

    public double getDoubleColorValue(int index)
    {
        double value = (double)index - darkestIndex;
        if(scale != 0.0D)
            value /= scale;
        return value + darkestValue;
    }

    public int getIntegerThresholdValue(int thresholdIndex)
    {
        return (int)Math.round(getDoubleThresholdValue(thresholdIndex));
    }

    public float getFloatThresholdValue(int thresholdIndex)
    {
        return (float)getDoubleThresholdValue(thresholdIndex);
    }

    public double getDoubleThresholdValue(int thresholdIndex)
    {
        return colorIndexValues[thresholdIndex];
    }

    protected void updateColorValues()
    {
        for(int i = 0; i < thresholdCount; i++)
        {
            colorIndexValues[i] = getIntegerColorValue(colorIndexThresholds[i]);
        }

    }

    private void validateRange()
    {
        darkestValue = Math.min(darkestValue, lightestValue);
        range = lightestValue - darkestValue;
        scale = 255D / range;
    }

    private double colorIndexValues[];
    private double darkestValue;
    private double darkestIndex;
    private double lightestValue;
    private double lightestIndex;
    private double range;
    private double scale;
}
