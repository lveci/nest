
package org.esa.nest.dataio.dem.srtm3_geotiff;

import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.dataio.ProductIOPlugInManager;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.dataio.geotiff.GeoTiffProductReaderPlugIn;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;

public class SRTM3GeoTiffElevationModel implements ElevationModel, Resampling.Raster {

    public static final int NUM_X_TILES = SRTM3GeoTiffElevationModelDescriptor.NUM_X_TILES;
    public static final int NUM_Y_TILES = SRTM3GeoTiffElevationModelDescriptor.NUM_Y_TILES;
    public static final int DEGREE_RES = SRTM3GeoTiffElevationModelDescriptor.DEGREE_RES;
    public static final int NUM_PIXELS_PER_TILE = SRTM3GeoTiffElevationModelDescriptor.PIXEL_RES;
    public static final int NO_DATA_VALUE = SRTM3GeoTiffElevationModelDescriptor.NO_DATA_VALUE;
    public static final int RASTER_WIDTH = NUM_X_TILES * NUM_PIXELS_PER_TILE;
    public static final int RASTER_HEIGHT = NUM_Y_TILES * NUM_PIXELS_PER_TILE;

    private final SRTM3GeoTiffElevationModelDescriptor _descriptor;
    private final SRTM3GeoTiffFile[][] elevationFiles;
    private final ArrayList<SRTM3GeoTiffElevationTile> _elevationTileCache = new ArrayList<SRTM3GeoTiffElevationTile>();
    private Resampling _resampling;
    private Resampling.Index _resamplingIndex;
    private final Resampling.Raster _resamplingRaster;

    final ProductReaderPlugIn productReaderPlugIn = getReaderPlugIn();

    public SRTM3GeoTiffElevationModel(SRTM3GeoTiffElevationModelDescriptor descriptor, Resampling resamplingMethod) throws IOException {
        _descriptor = descriptor;
        _resampling = resamplingMethod;
        _resamplingIndex = _resampling.createIndex();
        _resamplingRaster = this;
        elevationFiles = createElevationFiles();
    }

    /**
     * @return The resampling method used.
     * @since BEAM 4.6
     */
    public Resampling getResampling() {
        return _resampling;
    }

    public ElevationModelDescriptor getDescriptor() {
        return _descriptor;
    }

    public float getElevation(GeoPos geoPos) throws Exception {
        float pixelX = (geoPos.lon + 180.0f) / DEGREE_RES * NUM_PIXELS_PER_TILE; 
        float pixelY = RASTER_HEIGHT - (geoPos.lat + 60.0f) / DEGREE_RES * NUM_PIXELS_PER_TILE;

        _resampling.computeIndex(pixelX, pixelY,
                                 RASTER_WIDTH,
                                 RASTER_HEIGHT,
                                 _resamplingIndex);

        final float elevation = _resampling.resample(_resamplingRaster, _resamplingIndex);
        if (Float.isNaN(elevation)) {
            return _descriptor.getNoDataValue();
        }
        return elevation;
    }

    public void dispose() {
        _elevationTileCache.clear();
        for (int i = 0; i < elevationFiles.length; i++) {
            for (int j = 0; j < elevationFiles[i].length; j++) {
                elevationFiles[i][j].dispose();
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
        final SRTM3GeoTiffElevationTile tile = elevationFiles[tileXIndex][tileYIndex].getTile();
        if(tile == null) {
            return Float.NaN;
        }
        final int tileX = pixelX - tileXIndex * NUM_PIXELS_PER_TILE;
        final int tileY = pixelY - tileYIndex * NUM_PIXELS_PER_TILE;
        final float sample = tile.getSample(tileX, tileY);    
        if (sample == _descriptor.getNoDataValue()) {
            return Float.NaN;
        }
        return sample;
    }

    private SRTM3GeoTiffFile[][] createElevationFiles() throws IOException {
        final SRTM3GeoTiffFile[][] elevationFiles = new SRTM3GeoTiffFile[NUM_X_TILES][NUM_Y_TILES];
        final File demInstallDir = _descriptor.getDemInstallDir();
        for (int x = 0; x < elevationFiles.length; x++) {

            for (int y = 0; y < elevationFiles[x].length; y++) {

                final String fileName = SRTM3GeoTiffElevationModelDescriptor.createTileFilename(x+1, y+1);
                final File localFile = new File(demInstallDir, fileName);
                elevationFiles[x][y] = new SRTM3GeoTiffFile(this, localFile, productReaderPlugIn.createReaderInstance());
            }
        }             
        return elevationFiles;
    }

    public void updateCache(SRTM3GeoTiffElevationTile tile) {
        _elevationTileCache.remove(tile);
        _elevationTileCache.add(0, tile);
        while (_elevationTileCache.size() > 60) {
            final int index = _elevationTileCache.size() - 1;
            final SRTM3GeoTiffElevationTile lastTile = _elevationTileCache.get(index);
            lastTile.clearCache();
            _elevationTileCache.remove(index);
        }
    }

    private static ProductReaderPlugIn getReaderPlugIn() {
        final Iterator readerPlugIns = ProductIOPlugInManager.getInstance().getReaderPlugIns("GeoTIFF");
        return (ProductReaderPlugIn) readerPlugIns.next();
    }

}