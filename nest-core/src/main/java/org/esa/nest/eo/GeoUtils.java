/*
 * Copyright (C) 2013 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.eo;

import Jama.Matrix;
import org.apache.commons.math.util.FastMath;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.nest.datamodel.Orbits;

import static org.esa.beam.util.math.MathUtils.DTOR;

public final class GeoUtils
{
    private static final double EPS5 = 1e-5;
    private static final double EPS = 1e-10;

    public enum EarthModel { WGS84, GRS80 }

    private GeoUtils()
    {
    }

    /**
     * Convert geodetic coordinate into cartesian XYZ coordinate (WGS84 geodetic system is used).
     * @param geoPos The geodetic coordinate of a given pixel.
     * @param xyz The xyz coordinates of the given pixel.
     */
    public static void geo2xyz(final GeoPos geoPos, final double xyz[]) {
        geo2xyz(geoPos.lat, geoPos.lon, 0.0, xyz, EarthModel.WGS84);
    }

    /**
     * Convert geodetic coordinate into cartesian XYZ coordinate with specified geodetic system.
     * @param latitude The latitude of a given pixel (in degree).
     * @param longitude The longitude of the given pixel (in degree).
     * @param altitude The altitude of the given pixel (in m)
     * @param xyz The xyz coordinates of the given pixel.
     * @param geoSystem The geodetic system.
     */
    public static void geo2xyz(final double latitude, final double longitude, final double altitude,
                               final double xyz[], final EarthModel geoSystem) {

        double a = 0.0;
        double e2 = 0.0;

        if (geoSystem == EarthModel.WGS84) {

            a = WGS84.a;
            e2 = WGS84.e2;

        } else if (geoSystem == EarthModel.GRS80) {

            a = GRS80.a;
            e2 = GRS80.e2;

        } else {
            throw new OperatorException("Incorrect geodetic system");
        }

        final double lat = latitude * org.esa.beam.util.math.MathUtils.DTOR;
        final double lon = longitude * org.esa.beam.util.math.MathUtils.DTOR;

        final double sinLat = FastMath.sin(lat);
        final double cosLat = FastMath.cos(lat);
        final double N = a / Math.sqrt(1 - e2*sinLat*sinLat);

        xyz[0] = (N + altitude) * cosLat * FastMath.cos(lon); // in m
        xyz[1] = (N + altitude) * cosLat * FastMath.sin(lon); // in m
        xyz[2] = ((1 - e2) * N + altitude) * sinLat;   // in m
    }

    /**
     * Convert geodetic coordinate into cartesian XYZ coordinate with specified geodetic system.
     * @param latitude The latitude of a given pixel (in degree).
     * @param longitude The longitude of the given pixel (in degree).
     * @param altitude The altitude of the given pixel (in m)
     * @param xyz The xyz coordinates of the given pixel.
     */
    public static void geo2xyzWGS84(final double latitude, final double longitude, final double altitude,
                                    final double xyz[]) {

        final double lat = latitude * org.esa.beam.util.math.MathUtils.DTOR;
        final double lon = longitude * org.esa.beam.util.math.MathUtils.DTOR;

        final double sinLat = FastMath.sin(lat);
        final double N = (WGS84.a / Math.sqrt(1.0 - WGS84.e2*sinLat*sinLat));
        final double NcosLat = (N + altitude) * FastMath.cos(lat);

        xyz[0] = NcosLat * FastMath.cos(lon); // in m
        xyz[1] = NcosLat * FastMath.sin(lon); // in m
        xyz[2] = (N + altitude - WGS84.e2 * N) * sinLat;
        //xyz[2] = (WGS84.e2inv * N  + altitude) * sinLat;    // in m
    }

    /**
     * Convert cartesian XYZ coordinate into geodetic coordinate (WGS84 geodetic system is used).
     * @param xyz The xyz coordinate of the given pixel.
     * @param geoPos The geodetic coordinate of the given pixel.
     */
    public static void xyz2geo(final double xyz[], final GeoPos geoPos) {
        xyz2geoWGS84(xyz, geoPos);
    }

    /**
     * Convert cartesian XYZ coordinate into geodetic coordinate with specified geodetic system.
     * @param xyz The xyz coordinate of the given pixel.
     * @param geoPos The geodetic coordinate of the given pixel.
     * @param geoSystem The geodetic system.
     */
    public static void xyz2geo(final double xyz[], final GeoPos geoPos, final EarthModel geoSystem) {

        double a = 0.0;
        double b = 0.0;
        double e2 = 0.0;
        double ep2 = 0.0;

        if (geoSystem == EarthModel.WGS84) {

            a = WGS84.a;
            b = WGS84.b;
            e2 = WGS84.e2;
            ep2 = WGS84.ep2;

        } else if (geoSystem == EarthModel.GRS80) {

            a = GRS80.a;
            b = GRS80.b;
            e2 = GRS80.e2;
            ep2 = GRS80.ep2;

        } else {
            throw new OperatorException("Incorrect geodetic system");
        }

        final double x = xyz[0];
        final double y = xyz[1];
        final double z = xyz[2];
        final double s = Math.sqrt(x*x + y*y);
        final double theta = FastMath.atan(z*a/(s*b));

        geoPos.lon = (float)(FastMath.atan(y/x) * org.esa.beam.util.math.MathUtils.RTOD);

        if (geoPos.lon < 0.0 && y >= 0.0) {
            geoPos.lon += 180.0;
        } else if (geoPos.lon > 0.0 && y < 0.0) {
            geoPos.lon -= 180.0;
        }

        geoPos.lat = (float)(FastMath.atan((z + ep2*b*FastMath.pow(FastMath.sin(theta), 3)) /
                (s - e2*a*FastMath.pow(FastMath.cos(theta), 3))) *
                org.esa.beam.util.math.MathUtils.RTOD);
    }

    /**
     * Convert cartesian XYZ coordinate into geodetic coordinate with specified geodetic system.
     * @param xyz The xyz coordinate of the given pixel.
     * @param geoPos The geodetic coordinate of the given pixel.
     */
    public static void xyz2geoWGS84(final double xyz[], final GeoPos geoPos) {

        final double x = xyz[0];
        final double y = xyz[1];
        final double z = xyz[2];
        final double s = Math.sqrt(x*x + y*y);
        final double theta = FastMath.atan(z*WGS84.a/(s*WGS84.b));

        geoPos.lon = (float)(FastMath.atan(y/x) * org.esa.beam.util.math.MathUtils.RTOD);

        if (geoPos.lon < 0.0 && y >= 0.0) {
            geoPos.lon += 180.0;
        } else if (geoPos.lon > 0.0 && y < 0.0) {
            geoPos.lon -= 180.0;
        }

        geoPos.lat = (float)(FastMath.atan((z + WGS84.ep2*WGS84.b*FastMath.pow(FastMath.sin(theta), 3)) /
                (s - WGS84.e2*WGS84.a*FastMath.pow(FastMath.cos(theta), 3))) *
                org.esa.beam.util.math.MathUtils.RTOD);
    }

    /**
     * Convert polar coordinates to Cartesian vector.
     * <p>
     * <b>Definitions:<b/>
     *  <p>Latitude: angle from XY-plane towards +Z-axis.<p/>
     *  <p>Longitude: angle in XY-plane measured from +X-axis towards +Y-axis.<p/>
     * </p>
     * <p>
     * Note: Apache's FastMath used in implementation.
     * </p>
     * @param latitude The latitude of a given pixel (in degree).
     * @param longitude The longitude of the given pixel (in degree).
     * @param radius The radius of the given point (in m)
     * @param xyz The return array vector of X, Y and Z coordinates for the input point.
     *
     * @author Petar Marikovic, PPO.labs
     */
    public static void polar2cartesian(final double latitude, final double longitude, final double radius, final double xyz[]) {

        final double latRad = latitude * DTOR;
        final double lonRad = longitude * DTOR;

        final double sinLat = FastMath.sin(latRad);
        final double cosLat = FastMath.cos(latRad);

        xyz[0] = radius * cosLat * FastMath.cos(lonRad);
        xyz[1] = radius * cosLat * FastMath.sin(lonRad);
        xyz[2] = radius * sinLat;

    }

    /**
     * Convert Cartesian to Polar coordinates.
     * <p>
     * <b>Definitions:<b/>
     *  <p>Latitude: angle from XY-plane towards +Z-axis.<p/>
     *  <p>Longitude: angle in XY-plane measured from +X-axis towards +Y-axis.<p/>
     * </p>
     * <p>
     *  Implementation Details: Unlike for rest of utility methods GeoPos class container is not used for storing polar
     *  coordinates. GeoPos fields are declared as floats and can introduced numerical errors, especially in radius/height.
     * </p>
     * <p>
     *  Note: Apache's FastMath used in implementation.
     * </p>
     * @param xyz Array of x, y, and z coordinates.
     * @param phiLamHeight Array of latitude (in radians), longitude (in radians), and radius (in meters).
     *
     * @author Petar Marikovic, PPO.labs
     */
    public static void cartesian2polar(final double[] xyz, final double[] phiLamHeight) {

        phiLamHeight[2] = Math.sqrt(xyz[0] * xyz[0] + xyz[1] * xyz[1] + xyz[2] * xyz[2]);
        phiLamHeight[1] = Math.atan2(xyz[1], xyz[0]);
        phiLamHeight[0] = Math.asin(xyz[2] / phiLamHeight[2]);

    }


    /**
     * Compute accurate target position for given orbit information using Newton's method.
     * @param data The orbit data.
     * @param xyz The xyz coordinate for the target.
     * @param time The slant range time in seconds.
     */
    public static void computeAccurateXYZ(final Orbits.OrbitData data, final double[] xyz, final double time) {

        final double a = Constants.semiMajorAxis;
        final double b = Constants.semiMinorAxis;
        final double a2 = a*a;
        final double b2 = b*b;
        final double del = 0.001;
        final int maxIter = 10;

        Matrix X = new Matrix(3, 1);
        final Matrix F = new Matrix(3, 1);
        final Matrix J = new Matrix(3, 3);

        X.set(0, 0, xyz[0]);
        X.set(1, 0, xyz[1]);
        X.set(2, 0, xyz[2]);

        J.set(0, 0, data.xVel);
        J.set(0, 1, data.yVel);
        J.set(0, 2, data.zVel);

        final double time2 = FastMath.pow(time*Constants.halfLightSpeed, 2.0);
        for (int i = 0; i < maxIter; i++) {

            final double x = X.get(0,0);
            final double y = X.get(1,0);
            final double z = X.get(2,0);

            final double dx = x - data.xPos;
            final double dy = y - data.yPos;
            final double dz = z - data.zPos;

            F.set(0, 0, data.xVel*dx + data.yVel*dy + data.zVel*dz);
            F.set(1, 0, dx*dx + dy*dy + dz*dz - time2);
            F.set(2, 0, x*x/a2 + y*y/a2 + z*z/b2 - 1);

            J.set(1, 0, 2.0*dx);
            J.set(1, 1, 2.0*dy);
            J.set(1, 2, 2.0*dz);
            J.set(2, 0, 2.0*x/a2);
            J.set(2, 1, 2.0*y/a2);
            J.set(2, 2, 2.0*z/b2);

            X = X.minus(J.inverse().times(F));

            if (Math.abs(F.get(0,0)) <= del && Math.abs(F.get(1,0)) <= del && Math.abs(F.get(2,0)) <= del)  {
                break;
            }
        }

        xyz[0] = X.get(0,0);
        xyz[1] = X.get(1,0);
        xyz[2] = X.get(2,0);
    }

    /**
     // Given starting point GLON1,GLAT1, head1 = initial heading,and distance
     // in meters, calculate destination GLON2,GLAT2, and head2=initial heading
     // from destination to starting point

     // Input:
     // lon1:	longitude
     // lat1:	latitude
     // dist:	distance in m
     // head1:	azimuth in degree measured in the diretion North east south west

     // Output:
     // GLON2:	longitude
     // GLAT2:	latitude
     // head2:	azimuth in degree measured in the direction North east south west
     //			from (GLON2,GLAT2) to (GLON1, GLAT1)
     * @param lon1
     * @param lat1
     * @param dist
     * @param head1
     * @return
     */
    public static LatLonHeading vincenty_direct(double lon1, double lat1, final double dist, final double head1) {

        final LatLonHeading pos = new LatLonHeading();

        lat1 *= org.esa.beam.util.math.MathUtils.DTOR;
        lon1 *= org.esa.beam.util.math.MathUtils.DTOR;
        final double  FAZ = head1 * org.esa.beam.util.math.MathUtils.DTOR;

        // Model WGS84:
        //    F=1/298.25722210;	// flatteing
        final double F = 0.0;  // defF

        // equatorial radius
        final double R = 1.0 - F;
        double TU = R * Math.tan(lat1);
        final double SF = Math.sin(FAZ);
        final double CF = Math.cos(FAZ);
        double BAZ = 0.0;
        if (CF != 0.0)
            BAZ = Math.atan2(TU, CF) * 2.0;
        final double CU = 1.0 / Math.sqrt(TU * TU + 1.0);
        final double SU = TU * CU;
        final double SA = CU * SF;
        final double C2A = -SA * SA + 1.0;
        double X = Math.sqrt((1.0 / R / R - 1.0) * C2A + 1.0) + 1.0;
        X = (X - 2.0) / X;
        double C = 1.0 - X;
        C = (X * X / 4.0 + 1) / C;
        double D = (0.375 * X * X - 1.0) * X;
        TU = dist / R / WGS84.a / C;
        double Y = TU;

        double SY, CY, CZ, E;
        do {
            SY = Math.sin(Y);
            CY = Math.cos(Y);
            CZ = Math.cos(BAZ + Y);
            E = CZ * CZ * 2.0 - 1.0;
            C = Y;
            X = E * CY;
            Y = E + E - 1.0;
            Y = (((SY * SY * 4.0 - 3.0) * Y * CZ * D / 6.0 + X) * D / 4.0 - CZ) * SY * D + TU;
        } while (Math.abs(Y - C) > EPS);

        BAZ = CU * CY * CF - SU * SY;
        C = R * Math.sqrt(SA * SA + BAZ * BAZ);
        D = SU * CY + CU * SY * CF;
        pos.lat = Math.atan2(D, C);
        C = CU * CY - SU * SY * CF;
        X = Math.atan2(SY * SF, C);
        C = ((-3.0 * C2A + 4.0) * F + 4.0) * C2A * F / 16.0;
        D = ((E * CY * C + CZ) * SY * C + Y) * SA;
        pos.lon = lon1 + X - (1.0 - C) * D * F;
        BAZ = Math.atan2(SA, BAZ) + Math.PI;

        pos.lon *= org.esa.beam.util.math.MathUtils.RTOD;
        pos.lat *= org.esa.beam.util.math.MathUtils.RTOD;
        pos.heading = BAZ * org.esa.beam.util.math.MathUtils.RTOD;

        while (pos.heading < 0)
            pos.heading += 360;

        return pos;
    }

    /**
     * // Given starting (GLON1,GLAT1) and end points (GLON2,GLAT2)
     * // calculate distance in meters and initial headings from start to
     * // end (return variable head1),
     * // and from end to start point (return variable head2)
     * <p/>
     * // Input:
     * // lon1:	longitude
     * // lat1:	latitude
     * // lon2:	longitude
     * // lat2:	latitude
     * <p/>
     * // Output:
     * // dist:	distance in m
     * // head1:	azimuth in degrees mesured in the direction North east south west
     * //			from (lon1,lat1) to (lon2, lat2)
     * // head2:	azimuth in degrees mesured in the direction North east south west
     * //			from (lon2,lat2) to (lon1, lat1)
     * @param lon1
     * @param lat1
     * @param lon2
     * @param lat2
     * @return
     */
    public static DistanceHeading vincenty_inverse(double lon1, double lat1, double lon2, double lat2) {

        final DistanceHeading output = new DistanceHeading();

        if ((Math.abs(lon1 - lon2) < EPS5) && (Math.abs(lat1 - lat2) < EPS5)) {
            output.distance = 0;
            output.heading1 = -1;
            output.heading2 = -1;
            return output;
        }

        lat1 *= org.esa.beam.util.math.MathUtils.DTOR;
        lat2 *= org.esa.beam.util.math.MathUtils.DTOR;
        lon1 *= org.esa.beam.util.math.MathUtils.DTOR;
        lon2 *= org.esa.beam.util.math.MathUtils.DTOR;

        // Model WGS84:
        //    F=1/298.25722210;	// flattening
        final double F = 0.0; //defF;

        final double R = 1 - F;
        double TU1 = R * Math.tan(lat1);
        double TU2 = R * Math.tan(lat2);
        final double CU1 = 1.0 / Math.sqrt(TU1 * TU1 + 1.0);
        final double SU1 = CU1 * TU1;
        final double CU2 = 1.0 / Math.sqrt(TU2 * TU2 + 1.0);
        double S = CU1 * CU2;
        double BAZ = S * TU2;
        double FAZ = BAZ * TU1;
        double X = lon2 - lon1;

        double SX, CX, SY, CY, Y, SA, C2A, CZ, E, C, D;
        do {
            SX = Math.sin(X);
            CX = Math.cos(X);
            TU1 = CU2 * SX;
            TU2 = BAZ - SU1 * CU2 * CX;
            SY = Math.sqrt(TU1 * TU1 + TU2 * TU2);
            CY = S * CX + FAZ;
            Y = Math.atan2(SY, CY);
            SA = S * SX / SY;
            C2A = -SA * SA + 1.;
            CZ = FAZ + FAZ;
            if (C2A > 0.)
                CZ = -CZ / C2A + CY;
            E = CZ * CZ * 2. - 1.;
            C = ((-3. * C2A + 4.) * F + 4.) * C2A * F / 16.;
            D = X;
            X = ((E * CY * C + CZ) * SY * C + Y) * SA;
            X = (1. - C) * X * F + lon2 - lon1;
        } while (Math.abs(D - X) > (0.01));

        FAZ = Math.atan2(TU1, TU2);
        BAZ = Math.atan2(CU1 * SX, BAZ * CX - SU1 * CU2) + Math.PI;
        X = Math.sqrt((1. / R / R - 1.) * C2A + 1.) + 1.;
        X = (X - 2.) / X;
        C = 1. - X;
        C = (X * X / 4. + 1.) / C;
        D = (0.375 * X * X - 1.) * X;
        X = E * CY;
        S = 1. - E - E;
        S = ((((SY * SY * 4. - 3.) * S * CZ * D / 6. - X) * D / 4. + CZ) * SY * D + Y) * C * WGS84.a * R;

        output.distance = S;
        output.heading1 = FAZ * org.esa.beam.util.math.MathUtils.RTOD;
        output.heading2 = BAZ * org.esa.beam.util.math.MathUtils.RTOD;

        while (output.heading1< 0)
            output.heading1 += 360;
        while (output.heading2<0)
            output.heading2+=360;

        return output;
    }

    public static class LatLonHeading {
        public double lat;
        public double lon;
        public double heading;
    }

    public static class DistanceHeading {
        public double distance;
        public double heading1;
        public double heading2;
    }

    public static interface WGS84 {
        public static final double a = 6378137.0; // m
        public static final double b = 6356752.3142451794975639665996337; //6356752.31424518; // m
        public static final double earthFlatCoef = 1.0 / ((a-b)/ a); //298.257223563;
        public static final double e2 = 2.0 / earthFlatCoef - 1.0 / (earthFlatCoef * earthFlatCoef);
        public static final double e2inv = 1 - WGS84.e2;
        public static final double ep2 = e2 / (1 - e2);
    }

    public static interface GRS80 {
        public static final double a = 6378137; // m
        public static final double b = 6356752.314140 ; // m
        public static final double earthFlatCoef = 1.0 / ((a-b)/ a); //298.257222101;
        public static final double e2 = 2.0 / earthFlatCoef - 1.0 / (earthFlatCoef * earthFlatCoef);
        public static final double ep2 = e2 / (1 - e2);
    }
}
