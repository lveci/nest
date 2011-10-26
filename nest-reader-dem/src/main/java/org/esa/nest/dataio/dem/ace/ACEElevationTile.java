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
package org.esa.nest.dataio.dem.ace;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.util.CachingObjectArray;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.nest.dataio.dem.ElevationTile;
import org.esa.nest.dataio.dem.srtm3_geotiff.EarthGravitationalModel96;

import java.io.IOException;

public class ACEElevationTile implements ElevationTile {

    private ACEElevationModel dem;
    private CachingObjectArray linesCache;
    private Product product;
    private final float noDataValue;
    private float[][] egmArray = null;

    public ACEElevationTile(final ACEElevationModel dem, final Product product) {
        this.dem = dem;
        this.product = product;
        noDataValue = dem.getDescriptor().getNoDataValue();

        linesCache = new CachingObjectArray(getLineFactory());
        linesCache.setCachedRange(0, product.getSceneRasterHeight());

        computeEGMArray();
    }

    public float getSample(int pixelX, int pixelY) throws IOException {
        final float[] line;
        try {
            line = (float[]) linesCache.getObject(pixelY);
        } catch (Exception e) {
            throw convertLineCacheException(e);
        }
        return line[pixelX];
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
        linesCache.clear();
    }

    private CachingObjectArray.ObjectFactory getLineFactory() {
        final Band band = product.getBandAt(0);
        final int width = product.getSceneRasterWidth();
        return new CachingObjectArray.ObjectFactory() {
            public Object createObject(int index) throws Exception {
                dem.updateCache(ACEElevationTile.this);
                final float[] line =  band.readPixels(0, index, width, 1, new float[width], ProgressMonitor.NULL);
                final int rowIdxInEGMArray = index / 30; // tile_height / numEGMSamplesInCol = 1800 / 60 = 30
                for (int i = 0; i < line.length; i++) {
                    if (line[i] != noDataValue) {
                        final int colIdxInEGMArray = i / 30; // tile_width / numEGMSamplesInRow = 1800 / 60 = 30
                        line[i] += egmArray[rowIdxInEGMArray][colIdxInEGMArray];
                    }
                }
                return line;
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

    private void computeEGMArray() {

        final int numEGMSamplesInRow = 60;
        final int numEGMSamplesInCol = 60;
        egmArray = new float[numEGMSamplesInRow][numEGMSamplesInCol]; // 15 deg / 15 min

        final GeoCoding geoCoding = product.getGeoCoding();
        if(geoCoding == null) {
            throw new OperatorException("Product does not contain a geocoding");
        }
        final GeoPos geoPosFirstNear = geoCoding.getGeoPos(new PixelPos(0,0), null);
        final double lat0  = geoPosFirstNear.getLat() + 0.125; // + half of 15 min
        final double lon0  = geoPosFirstNear.getLon() + 0.125; // + half of 15 min

        final EarthGravitationalModel96 egm = EarthGravitationalModel96.instance();
        final double delLat = 0.25; // 15 min
        final double delLon = 0.25; // 15 min
        for (int r = 0; r < numEGMSamplesInCol; r++) {
            final double lat = lat0 - delLat*r;
            for (int c = 0; c < numEGMSamplesInRow; c++) {
                egmArray[r][c] = egm.getEGM(lat, lon0 + delLon*c);
            }
        }
    }
}
