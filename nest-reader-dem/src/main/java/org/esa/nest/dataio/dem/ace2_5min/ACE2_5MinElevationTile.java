/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
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
package org.esa.nest.dataio.dem.ace2_5min;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.util.CachingObjectArray;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;

import java.io.File;
import java.io.IOException;

public class ACE2_5MinElevationTile {

    private ACE2_5MinElevationModel dem;
    private CachingObjectArray linesCache = null;
    private final File productFile;
    private Product product = null;
    private final ProductReaderPlugIn readerPlugIn;

    public ACE2_5MinElevationTile(final ACE2_5MinElevationModel dem, final File file, final ProductReaderPlugIn readPlugIn) {
        this.dem = dem;
        productFile = file;
        readerPlugIn = readPlugIn;
    }

    public float getSample(int pixelX, int pixelY) throws IOException {
        final float[] line;
        try {
            if(linesCache == null) {
                createLineCache();
            }
            line = (float[]) linesCache.getObject(pixelY);
        } catch (Exception e) {
            throw convertLineCacheException(e);
        }
        return line[pixelX];
    }

    private synchronized void createLineCache() throws IOException {
        if(linesCache == null) {
            linesCache = new CachingObjectArray(getLineFactory());
            linesCache.setCachedRange(0, product.getSceneRasterHeight());
        }
    }

    public void dispose() {
        clearCache();
        linesCache = null;
        if (product != null) {
            product.dispose();
            product = null;
        }
        dem = null;
    }

    public void clearCache() {
        if(linesCache != null)
            linesCache.clear();
    }

    private CachingObjectArray.ObjectFactory getLineFactory() throws IOException {
        if(product == null) {
            final ProductReader productReader = readerPlugIn.createReaderInstance();
            product = productReader.readProductNodes(productFile, null);
        }
        final Band band = product.getBandAt(0);
        final int width = product.getSceneRasterWidth();
        return new CachingObjectArray.ObjectFactory() {
            public synchronized Object createObject(int index) throws Exception {
                dem.updateCache(ACE2_5MinElevationTile.this);
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