
package org.esa.nest.dataio.dem.aster;

import org.esa.beam.framework.dataio.ProductIOPlugInManager;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.nest.dataio.dem.srtm3_geotiff.EarthGravitationalModel96;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

public final class AsterElevationModel implements ElevationModel, Resampling.Raster {

    private static final int NUM_X_TILES = AsterElevationModelDescriptor.NUM_X_TILES;
    private static final int NUM_Y_TILES = AsterElevationModelDescriptor.NUM_Y_TILES;
    private static final int DEGREE_RES = AsterElevationModelDescriptor.DEGREE_RES;
    private static final int NUM_PIXELS_PER_TILE = AsterElevationModelDescriptor.PIXEL_RES;
    private static final int RASTER_WIDTH = NUM_X_TILES * NUM_PIXELS_PER_TILE;
    private static final int RASTER_HEIGHT = NUM_Y_TILES * NUM_PIXELS_PER_TILE;

    private static final float DEGREE_RES_BY_NUM_PIXELS_PER_TILE = DEGREE_RES * (1.0f/NUM_PIXELS_PER_TILE);

    private final AsterElevationModelDescriptor _descriptor;
    private final AsterFile[][] elevationFiles;
    private Resampling _resampling;
    private Resampling.Index _resamplingIndex;
    private final Resampling.Raster _resamplingRaster;
    private final float noDataValue;

    private final List<AsterElevationTile> elevationTileCache;
    private static final ProductReaderPlugIn productReaderPlugIn = getReaderPlugIn();
    private static final EarthGravitationalModel96 egm = new EarthGravitationalModel96();

    public AsterElevationModel(AsterElevationModelDescriptor descriptor, Resampling resamplingMethod) throws IOException {
        _descriptor = descriptor;
        _resampling = resamplingMethod;
        _resamplingIndex = _resampling.createIndex();
        _resamplingRaster = this;
        elevationFiles = createElevationFiles();
        noDataValue = _descriptor.getNoDataValue();
        this.elevationTileCache = new ArrayList<AsterElevationTile>();
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

    public synchronized float getElevation(GeoPos geoPos) throws Exception {
        float pixelY = RASTER_HEIGHT - (geoPos.lat + 90.0f) / DEGREE_RES_BY_NUM_PIXELS_PER_TILE; //DEGREE_RES * NUM_PIXELS_PER_TILE;
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
        final AsterElevationTile tile = elevationFiles[tileXIndex][tileYIndex+7].getTile();
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
        final File demInstallDir = _descriptor.getDemInstallDir();
        for (int x = 0; x < NUM_X_TILES; x++) {
            final int minLon = x - 180;

            for (int y = 0; y < NUM_Y_TILES; y++) {
                final int minLat = (y+7) - 90;

                final String fileName = AsterElevationModelDescriptor.createTileFilename(minLon, minLat);
                final File localFile = new File(demInstallDir, fileName);
                elevationFiles[x][NUM_Y_TILES - 1 - y] = new AsterFile(this, localFile, productReaderPlugIn.createReaderInstance());
            }
        }
        return elevationFiles;
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