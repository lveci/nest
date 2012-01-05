/*
 * Copyright (C) 2011 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.nest.dataio.dem.aster;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.util.CachingObjectArray;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;

import java.io.IOException;

public final class AsterElevationTile {

    private CachingObjectArray linesCache;
    private Product product;
    private final float noDataValue;
    private float[][] egmArray = null;
    private final String name;

    public AsterElevationTile(final AsterElevationModel dem, final Product product) {
        this.product = product;
        noDataValue = dem.getDescriptor().getNoDataValue();
        linesCache = new CachingObjectArray(getLineFactory());
        linesCache.setCachedRange(0, product.getSceneRasterHeight());
        name = product.getName();

        //System.out.println("Dem Tile "+name);
    }

    public String getName() {
        return name;
    }

    public float getSample(int pixelX, int pixelY) throws IOException {
        try {
            final float[] line = (float[]) linesCache.getObject(pixelY);
            return line[pixelX];
        } catch (Exception e) {
            throw convertLineCacheException(e);
        }
    }

    public void dispose() {
        clearCache();
        linesCache = null;
        if (product != null) {
            product.dispose();
            product = null;
        }
    }

    public void clearCache() {
        linesCache.clear();
    }

    private CachingObjectArray.ObjectFactory getLineFactory() {
        final Band band = product.getBandAt(0);
        final int width = product.getSceneRasterWidth();
        return new CachingObjectArray.ObjectFactory() {
            public synchronized Object createObject(int index) throws Exception {
                return band.readPixels(0, index, width, 1, new float[width], ProgressMonitor.NULL);
            }
        };
    }

    private static IOException convertLineCacheException(Exception e) {
        IOException ioe;
        if (e instanceof IOException) {
            ioe = (IOException) e;
        } else {
            ioe = new IOException();
            ioe.setStackTrace(e.getStackTrace());
        }
        return ioe;
    }
}