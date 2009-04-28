package org.esa.beam.dataio.geotiff.internal;

import org.esa.beam.util.Guardian;

/**
 * This class provides static methods for checking values if they are within the value range
 * of a TIFF type.
 *
 * @author Marco Peters
 * @author Sabine Embacher
 * @author Norman Fomferra
 * @version $Revision: 1.1 $ $Date: 2009-04-28 14:37:14 $
 */
class TiffValueRangeChecker {

    private static final long UNSIGNED_INT_MAX = 0xffffffffL;
    private static final int UNSIGNED_SHORT_MAX = 0xffff;

    public static void checkValueTiffRational(final long value, final String name) {
        checkValue(value, name, 1, UNSIGNED_INT_MAX);
    }

    public static void checkValueTiffLong(final long value, final String name) {
        checkValue(value, name, 0, UNSIGNED_INT_MAX);
    }

    public static void checkValueTiffShort(final int value, final String name) {
        checkValue(value, name, 0, UNSIGNED_SHORT_MAX);
    }

    private static void checkValue(final long value, final String name, final long min, final long max) {
        Guardian.assertWithinRange(name, value, min, max);
    }
}
