package org.esa.nest.doris.util;

public final class Constants
{

    // General constants: DORIS var names
    // TODO: refactor and integrate in nest-core/Constants.java
    // TODO: are variable names consistent with JAVA naming convention?
    public static final double SOL    = 299792458.0; // m/s
    public static final double EPS    = 1e-13;
    public static final int    NaN    = -999;
    public static final int    Inf    = 99999;
    public static final double M_PI   = Math.PI;
    public static final double PI     = 4* Math.atan(1);
    public static final int    EIGHTY = 200; // most likely never used?
    public static final int    ONE27  = 127;

    private Constants()
    {
    }

}