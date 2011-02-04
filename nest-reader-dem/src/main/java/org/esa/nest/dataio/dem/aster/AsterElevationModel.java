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
package org.esa.nest.dataio.dem.aster;

import org.esa.beam.framework.dataio.ProductIOPlugInManager;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dataio.dem.srtm3_geotiff.EarthGravitationalModel96;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.awt.*;

import com.bc.io.FileUnpacker;

public final class AsterElevationModel implements ElevationModel, Resampling.Raster {

    private static final int NUM_X_TILES = AsterElevationModelDescriptor.NUM_X_TILES;
    private static final int NUM_Y_TILES = AsterElevationModelDescriptor.NUM_Y_TILES;
    private static final int DEGREE_RES = AsterElevationModelDescriptor.DEGREE_RES;
    private static final int NUM_PIXELS_PER_TILE = AsterElevationModelDescriptor.PIXEL_RES;
    private static final int RASTER_WIDTH = NUM_X_TILES * NUM_PIXELS_PER_TILE;
    private static final int RASTER_HEIGHT = NUM_Y_TILES * NUM_PIXELS_PER_TILE;

    private static final float DEGREE_RES_BY_NUM_PIXELS_PER_TILE = DEGREE_RES * (1.0f/NUM_PIXELS_PER_TILE);

    private final AsterElevationModelDescriptor descriptor;
    private final AsterFile[][] elevationFiles;
    private Resampling resampling;
    private Resampling.Index resamplingIndex;
    private final Resampling.Raster resamplingRaster;
    private final float noDataValue;

    private final List<AsterElevationTile> elevationTileCache;
    private static final ProductReaderPlugIn productReaderPlugIn = getReaderPlugIn();
    private static final EarthGravitationalModel96 egm = new EarthGravitationalModel96();

    public AsterElevationModel(AsterElevationModelDescriptor descriptor, Resampling resamplingMethod) throws IOException {
        this.descriptor = descriptor;
        resampling = resamplingMethod;
        resamplingIndex = resampling.createIndex();
        resamplingRaster = this;
        elevationFiles = createElevationFiles();
        noDataValue = this.descriptor.getNoDataValue();
        this.elevationTileCache = new ArrayList<AsterElevationTile>();
        unpackTileBundles();
    }

    /**
     * @return The resampling method used.
     * @since BEAM 4.6
     */
    public Resampling getResampling() {
        return resampling;
    }

    public ElevationModelDescriptor getDescriptor() {
        return descriptor;
    }

    public synchronized float getElevation(GeoPos geoPos) throws Exception {
        float pixelY = RASTER_HEIGHT - (geoPos.lat + 83.0f) / DEGREE_RES_BY_NUM_PIXELS_PER_TILE; //DEGREE_RES * NUM_PIXELS_PER_TILE;
        if(pixelY < 0)
            return noDataValue;
        float pixelX = (geoPos.lon + 180.0f) / DEGREE_RES_BY_NUM_PIXELS_PER_TILE; // DEGREE_RES * NUM_PIXELS_PER_TILE;

        resampling.computeIndex(pixelX, pixelY,
                                 RASTER_WIDTH,
                                 RASTER_HEIGHT,
                resamplingIndex);

        final float elevation = resampling.resample(resamplingRaster, resamplingIndex);
        if (Float.isNaN(elevation)) {
            return noDataValue;
        }
        return elevation;
    }

    public void dispose() {
        for(AsterElevationTile tile : elevationTileCache) {
            tile.dispose();
        }
        elevationTileCache.clear();
        for (AsterFile[] elevationFile : elevationFiles) {
            for (AsterFile anElevationFile : elevationFile) {
                anElevationFile.dispose();
            }
        }
    }

    public int getWidth() {
        return RASTER_WIDTH;
    }

    public int getHeight() {
        return RASTER_HEIGHT;
    }

    public float getSample(int pixelX, int pixelY) throws IOException {
        final int tileXIndex = pixelX / NUM_PIXELS_PER_TILE;
        final int tileYIndex = pixelY / NUM_PIXELS_PER_TILE;
        final AsterElevationTile tile = elevationFiles[tileXIndex][tileYIndex].getTile();
        if(tile == null) {
            return Float.NaN;
        }
        final int tileX = pixelX - tileXIndex * NUM_PIXELS_PER_TILE;
        final int tileY = pixelY - tileYIndex * NUM_PIXELS_PER_TILE;
        final float sample = tile.getSample(tileX, tileY);
        if (sample == noDataValue) {
            return Float.NaN;
        }
        return sample;
    }

    private AsterFile[][] createElevationFiles() throws IOException {
        final AsterFile[][] elevationFiles = new AsterFile[NUM_X_TILES][NUM_Y_TILES];
        final File demInstallDir = descriptor.getDemInstallDir();
        for (int x = 0; x < NUM_X_TILES; x++) {
            final int minLon = x - 180;

            for (int y = 0; y < NUM_Y_TILES; y++) {
                final int minLat = (y+7) - 90;

                final String fileName = AsterElevationModelDescriptor.createTileFilename(minLon, minLat);
                final File localFile = new File(demInstallDir, fileName);
                elevationFiles[x][NUM_Y_TILES - 1 - y] = new AsterFile(this, localFile, productReaderPlugIn.createReaderInstance());

                //int cy = NUM_Y_TILES - 1 - y;
                //System.out.println("["+x+"]["+cy+"]="+ fileName);
            }
        }
        return elevationFiles;
    }

    private void unpackTileBundles() {

        final File parentFolder = descriptor.getDemInstallDir();
        final File[] files = parentFolder.listFiles();

        try {
            for(File f : files) {
                if(f.getName().startsWith("Tiles_") && f.getName().endsWith(".zip")) {
                    Component component = null;
                    if(VisatApp.getApp() != null) {
                        component = VisatApp.getApp().getApplicationWindow();
                    }
                    FileUnpacker.unpackZip(f, parentFolder, component);
                    f.delete();
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void updateCache(AsterElevationTile tile) {
        elevationTileCache.remove(tile);
        elevationTileCache.add(0, tile);
        while (elevationTileCache.size() > 6) {
            final int index = elevationTileCache.size() - 1;
            final AsterElevationTile lastTile = elevationTileCache.get(index);
            lastTile.clearCache();
            elevationTileCache.remove(index);
        }
    }

    private static ProductReaderPlugIn getReaderPlugIn() {
        final Iterator readerPlugIns = ProductIOPlugInManager.getInstance().getReaderPlugIns("GeoTIFF");
        return (ProductReaderPlugIn) readerPlugIns.next();
    }

    public EarthGravitationalModel96 getEarthGravitationalModel96() {
        return egm;
    }
}