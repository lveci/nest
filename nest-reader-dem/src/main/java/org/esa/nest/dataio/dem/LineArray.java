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
package org.esa.nest.dataio.dem;

import org.apache.commons.math.util.FastMath;

import java.util.Arrays;

public final class LineArray {

    private final int minIndex;
    private final int maxIndex;
    private final float[][] objects;

    public LineArray(final int min, final int max) {
        if (max < min) {
            throw new IllegalArgumentException("max must be greater than or equal min");
        }
        minIndex = min;
        maxIndex = max;
        objects = new float[max - min + 1][];
    }

    public final int getMinIndex() {
        return minIndex;
    }

    public final int getMaxIndex() {
        return maxIndex;
    }

    public final float[] getObject(int i) {
        return objects[i - minIndex];
    }

    public final void setObject(int i, float[] o) {
        objects[i - minIndex] = o;
    }

    public void clear() {
        Arrays.fill(objects, 0, objects.length, null);
    }

    public void set(final LineArray array) {
        final int start = FastMath.max(minIndex, array.getMinIndex());
        final int end = FastMath.min(maxIndex, array.getMaxIndex());

        if (end < start) {
            return;
        }

        final int srcPos = start - array.getMinIndex();
        final int destPos = start - minIndex;
        System.arraycopy(array.objects, srcPos, objects, destPos, end - start + 1);
    }

    public static interface LineFactory {

        float[] createObject(final int index) throws Exception;
    }
}