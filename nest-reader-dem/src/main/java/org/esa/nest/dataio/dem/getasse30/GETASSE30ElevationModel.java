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
package org.esa.nest.dataio.dem.getasse30;

import com.bc.ceres.core.Assert;
import org.esa.beam.framework.dataio.ProductIOPlugInManager;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.nest.dataio.dem.ElevationTile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GETASSE30ElevationModel implements ElevationModel, Resampling.Raster {

    private static final int NUM_X_TILES = GETASSE30ElevationModelDescriptor.NUM_X_TILES;
    private static final int NUM_Y_TILES = GETASSE30ElevationModelDescriptor.NUM_Y_TILES;
    private static final int DEGREE_RES = GETASSE30ElevationModelDescriptor.DEGREE_RES;
    private static final int NUM_PIXELS_PER_TILE = GETASSE30ElevationModelDescriptor.PIXEL_RES;
    private static final int NO_DATA_VALUE = GETASSE30ElevationModelDescriptor.NO_DATA_VALUE;
    private static final int RASTER_WIDTH = NUM_X_TILES * NUM_PIXELS_PER_TILE;
    private static final int RASTER_HEIGHT = NUM_Y_TILES * NUM_PIXELS_PER_TILE;

    private static final float DEGREE_RES_BY_NUM_PIXELS_PER_TILE = DEGREE_RES * (1.0f/NUM_PIXELS_PER_TILE);

    private final GETASSE30ElevationModelDescriptor descriptor;
    private final GETASSE30File[][] elevationFiles;
    private final List<GETASSE30ElevationTile> elevationTileCache;
    private final Resampling resampling;
    private final Resampling.Index resamplingIndex;
    private final Resampling.Raster resamplingRaster;
    private static final ProductReaderPlugIn productReaderPlugIn = getGETASSE30ReaderPlugIn();

    public GETASSE30ElevationModel(GETASSE30ElevationModelDescriptor descriptor, Resampling resampling) throws IOException {
        Assert.notNull(descriptor, "descriptor");
        Assert.notNull(resampling, "resampling");
        this.descriptor = descriptor;
        this.resampling = resampling;
        this.resamplingIndex = resampling.createIndex();
        this.resamplingRaster = this;
        this.elevationFiles = createElevationFiles();
        this.elevationTileCache = new ArrayList<GETASSE30ElevationTile>();
    }

    @Override
    public ElevationModelDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public float getElevation(GeoPos geoPos) throws Exception {
        float pixelX = (geoPos.lon + 180.0f) / DEGREE_RES_BY_NUM_PIXELS_PER_TILE; // todo (nf) - consider 0.5
        float pixelY = RASTER_HEIGHT - (geoPos.lat + 90.0f) / DEGREE_RES_BY_NUM_PIXELS_PER_TILE; // todo (nf) - consider 0.5, y = (90 - lon) / DEGREE_RES * NUM_PIXELS_PER_TILE;
        final float elevation;
        synchronized (resampling) {
            resampling.computeIndex(pixelX, pixelY,
                                     RASTER_WIDTH,
                                     RASTER_HEIGHT,
                                     resamplingIndex);
            elevation = resampling.resample(resamplingRaster, resamplingIndex);
        }
        if (Float.isNaN(elevation)) {
            return NO_DATA_VALUE;
        }
        return elevation;
    }

    @Override
    public PixelPos getIndex(GeoPos geoPos) throws Exception {
        float pixelY = RASTER_HEIGHT - (geoPos.lat + 90.0f) / DEGREE_RES * NUM_PIXELS_PER_TILE;
        float pixelX = (geoPos.lon + 180.0f) / DEGREE_RES * NUM_PIXELS_PER_TILE;
        return new PixelPos(pixelX, pixelY);
    }

    @Override
    public synchronized GeoPos getGeoPos(PixelPos pixelPos) throws Exception {
        float pixelLat = (RASTER_HEIGHT - pixelPos.y) / (DEGREE_RES * NUM_PIXELS_PER_TILE) - 90.0f;
        float pixelLon = pixelPos.x / (DEGREE_RES * NUM_PIXELS_PER_TILE) - 180.0f;
        return new GeoPos(pixelLat, pixelLon);
    }

    @Override
    public Resampling getResampling() {
        return resampling;
    }

    @Override
    public void dispose() {
        for(GETASSE30ElevationTile tile : elevationTileCache) {
            tile.dispose();
        }
        elevationTileCache.clear();
        for (GETASSE30File[] elevationFile : elevationFiles) {
            for (GETASSE30File file : elevationFile) {
                file.dispose();
            }
        }
    }

    @Override
    public int getWidth() {
        return RASTER_WIDTH;
    }

    @Override
    public int getHeight() {
        return RASTER_HEIGHT;
    }

    @Override
    public float getSample(int pixelX, int pixelY) throws IOException {
        final int tileXIndex = pixelX / NUM_PIXELS_PER_TILE;
        final int tileYIndex = pixelY / NUM_PIXELS_PER_TILE;
        final ElevationTile tile = elevationFiles[tileXIndex][tileYIndex].getTile();
        if(tile == null) {
            return Float.NaN;
        }
        final int tileX = pixelX - tileXIndex * NUM_PIXELS_PER_TILE;
        final int tileY = pixelY - tileYIndex * NUM_PIXELS_PER_TILE;
        final float sample = tile.getSample(tileX, tileY);
        if (sample == NO_DATA_VALUE)
            return Float.NaN;
        return sample;
    }

    private GETASSE30File[][] createElevationFiles() throws IOException {
        final GETASSE30File[][] elevationFiles = new GETASSE30File[NUM_X_TILES][NUM_Y_TILES];

        final File demInstallDir = descriptor.getDemInstallDir();
        for (int x = 0; x < elevationFiles.length; x++) {
            final int minLon = x * DEGREE_RES - 180;
            for (int y = 0; y < elevationFiles[x].length; y++) {
                final int minLat = y * DEGREE_RES - 90;
                final String fileName = GETASSE30ElevationModelDescriptor.createTileFilename(minLat, minLon);
                final File localFile = new File(demInstallDir, fileName);
                elevationFiles[x][NUM_Y_TILES - 1 - y] = new GETASSE30File(this, localFile, productReaderPlugIn.createReaderInstance());
            }
        }
        return elevationFiles;
    }

    public void updateCache(GETASSE30ElevationTile tile) {
        elevationTileCache.remove(tile);
        elevationTileCache.add(0, tile);
        while (elevationTileCache.size() > 60) {
            final int index = elevationTileCache.size() - 1;
            GETASSE30ElevationTile lastTile = elevationTileCache.get(index);
            lastTile.clearCache();
            elevationTileCache.remove(index);
        }
    }

    private static GETASSE30ReaderPlugIn getGETASSE30ReaderPlugIn() {
        final Iterator readerPlugIns = ProductIOPlugInManager.getInstance().getReaderPlugIns(
                GETASSE30ReaderPlugIn.FORMAT_NAME);
        return (GETASSE30ReaderPlugIn) readerPlugIns.next();
    }
}
