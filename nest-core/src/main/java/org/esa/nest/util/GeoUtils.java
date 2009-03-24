package org.esa.nest.util;

import org.esa.beam.framework.datamodel.GeoPos;

public final class GeoUtils
{

    private GeoUtils()
    {
    }

    private static final double a = 6378137; // m
    private static final double b = 6356752.315; // m
    private static final double earthFlatCoef = 298.257223563;
    private static final double e2 = 2 / earthFlatCoef - 1 / (earthFlatCoef * earthFlatCoef);
    private static final double ep2 = e2 / (1 - e2);

    /**
     * Convert geodetic coordinate into cartesian XYZ coordinate.
     * @param geoPos The geodetic coordinate of a given pixel.
     * @param xyz The xyz coordinates of the given pixel.
     */
    public static void geo2xyz(GeoPos geoPos, double xyz[]) {

        final double lat = ((double)geoPos.lat);
        final double lon = ((double)geoPos.lon);
        final double alt = 0.0;
        geo2xyz(lat, lon, alt, xyz);
    }

    /**
     * Convert geodetic coordinate into cartesian XYZ coordinate.
     * @param latitude The latitude of a given pixel (in degree).
     * @param longitude The longitude of the given pixel (in degree).
     * @param altitude The altitude of the given pixel (in m)
     * @param xyz The xyz coordinates of the given pixel.
     */
    public static void geo2xyz(double latitude, double longitude, double altitude, double xyz[]) {

        final double lat = latitude * org.esa.beam.util.math.MathUtils.DTOR;
        final double lon = longitude * org.esa.beam.util.math.MathUtils.DTOR;

        final double sinLat = Math.sin(lat);
        final double cosLat = Math.cos(lat);
        final double N = a / Math.sqrt(1 - e2*sinLat*sinLat);

        xyz[0] = (N + altitude) * cosLat * Math.cos(lon); // in m
        xyz[1] = (N + altitude) * cosLat * Math.sin(lon); // in m
        xyz[2] = ((1 - e2) * N + altitude) * sinLat;   // in m
    }

    /**
     * Convert cartesian XYZ coordinate into geodetic coordinate.
     * @param xyz The xyz coordinate of the given pixel.
     * @param geoPos The geodetic coordinate of the given pixel.
     */
    public static void xyz2geo(double xyz[], GeoPos geoPos) {

        final double x = xyz[0];
        final double y = xyz[1];
        final double z = xyz[2];
        final double s = Math.sqrt(x*x + y*y);
        final double theta = Math.atan(z*a/(s*b));

        geoPos.lon = (float)(Math.atan(y/x) * org.esa.beam.util.math.MathUtils.RTOD);
        if (geoPos.lon < 0.0) {
            geoPos.lon += 180.0;
        }

        geoPos.lat = (float)(Math.atan((z + ep2*b*Math.pow(Math.sin(theta), 3)) /
                                       (s - e2*a*Math.pow(Math.cos(theta), 3))) *
                                       org.esa.beam.util.math.MathUtils.RTOD);
    }

}
