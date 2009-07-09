
package org.esa.nest.dataio.dem.srtm3_geotiff;

import org.esa.beam.framework.dataio.ProductIOPlugInManager;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.resamp.Resampling;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public final class SRTM3GeoTiffElevationModel implements ElevationModel, Resampling.Raster {

    private static final int NUM_X_TILES = SRTM3GeoTiffElevationModelDescriptor.NUM_X_TILES;
    private static final int NUM_Y_TILES = SRTM3GeoTiffElevationModelDescriptor.NUM_Y_TILES;
    private static final int DEGREE_RES = SRTM3GeoTiffElevationModelDescriptor.DEGREE_RES;
    private static final int NUM_PIXELS_PER_TILE = SRTM3GeoTiffElevationModelDescriptor.PIXEL_RES;
    private static final int NO_DATA_VALUE = SRTM3GeoTiffElevationModelDescriptor.NO_DATA_VALUE;
    private static final int RASTER_WIDTH = NUM_X_TILES * NUM_PIXELS_PER_TILE;
    private static final int RASTER_HEIGHT = NUM_Y_TILES * NUM_PIXELS_PER_TILE;

    private static final float DEGREE_RES_BY_NUM_PIXELS_PER_TILE = DEGREE_RES * (1.0f/NUM_PIXELS_PER_TILE);

    private final SRTM3GeoTiffElevationModelDescriptor _descriptor;
    private final SRTM3GeoTiffFile[][] elevationFiles;
    private Resampling _resampling;
    private Resampling.Index _resamplingIndex;
    private final Resampling.Raster _resamplingRaster;
    private final float noDataValue;

    private final ProductReaderPlugIn productReaderPlugIn = getReaderPlugIn();

    public SRTM3GeoTiffElevationModel(SRTM3GeoTiffElevationModelDescriptor descriptor, Resampling resamplingMethod) throws IOException {
        _descriptor = descriptor;
        _resampling = resamplingMethod;
        _resamplingIndex = _resampling.createIndex();
        _resamplingRaster = this;
        elevationFiles = createElevationFiles();
        noDataValue = _descriptor.getNoDataValue();
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
        float pixelY = RASTER_HEIGHT - (geoPos.lat + 60.0f) / DEGREE_RES_BY_NUM_PIXELS_PER_TILE; //DEGREE_RES * NUM_PIXELS_PER_TILE;
        if(pixelY < 0)
            return noDataValue;
        float pixelX = (geoPos.lon + 180.0f) / DEGREE_RES_BY_NUM_PIXELS_PER_TILE; // DEGREE_RES * NUM_PIXELS_PER_TILE;

        _resampling.computeIndex(pixelX, pixelY,
                                 RASTER_WIDTH,
                                 RASTER_HEIGHT,
                                 _resamplingIndex);

        final float elevation = _resampling.resample(_resamplingRaster, _resamplingIndex);
        if (Float.isNaN(elevation)) {
            return noDataValue;
        }
        return elevation;
    }

    public void dispose() {
        for (SRTM3GeoTiffFile[] elevationFile : elevationFiles) {
            for (SRTM3GeoTiffFile anElevationFile : elevationFile) {
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
        final SRTM3GeoTiffElevationTile tile = elevationFiles[tileXIndex][tileYIndex].getTile();
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

    private static ProductReaderPlugIn getReaderPlugIn() {
        final Iterator readerPlugIns = ProductIOPlugInManager.getInstance().getReaderPlugIns("GeoTIFF");
        return (ProductReaderPlugIn) readerPlugIns.next();
    }

}