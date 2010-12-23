/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.beam.dataio.geotiff.internal;

import org.esa.beam.util.Guardian;

/**
 * This class provides static methods for checking values if they are within the value range
 * of a TIFF type.
 *
 * @author Marco Peters
 * @author Sabine Embacher
 * @author Norman Fomferra
 * @version $Revision: 1.3 $ $Date: 2010-12-23 14:35:09 $
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
