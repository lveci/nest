package org.esa.nest.util;

public final class Constants
{
    public static final double lightSpeed = 299792458.0; //  m / s
    public static final double halfLightSpeed = lightSpeed / 2.0;

    public static final double semiMajorAxis = 6378137.0;      // in m, WGS84 semi-major axis of Earth
    public static final double semiMinorAxis = 6356752.314245; // in m, WGS84 semi-minor axis of Earth

    public static final double MeanEarthRadius = 6371008.7714; // in m (WGS84)

    public static final double oneMillion = 1000000.0;

    public static final double TWO_PI = 2.0*Math.PI;

    private Constants()
    {
    }
}