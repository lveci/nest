
package org.esa.nest.dataio.dem.ace2_5min;

import org.esa.beam.framework.dataio.ProductIOPlugInManager;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.resamp.Resampling;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ACE2_5MinElevationModel implements ElevationModel, Resampling.Raster {

    private static final int NUM_X_TILES = ACE2_5MinElevationModelDescriptor.NUM_X_TILES;
    private static final int NUM_Y_TILES = ACE2_5MinElevationModelDescriptor.NUM_Y_TILES;
    private static final int DEGREE_RES = ACE2_5MinElevationModelDescriptor.DEGREE_RES;
    private static final int NUM_PIXELS_PER_TILE = ACE2_5MinElevationModelDescriptor.PIXEL_RES;
    private static final int NO_DATA_VALUE = ACE2_5MinElevationModelDescriptor.NO_DATA_VALUE;
    private static final int RASTER_WIDTH = NUM_X_TILES * NUM_PIXELS_PER_TILE;
    private static final int RASTER_HEIGHT = NUM_Y_TILES * NUM_PIXELS_PER_TILE;

    private static final float DEGREE_RES_BY_NUM_PIXELS_PER_TILE = DEGREE_RES * (1.0f/NUM_PIXELS_PER_TILE);

    private final ACE2_5MinElevationModelDescriptor _descriptor;
    private final ACE2_5MinElevationTile[][] _elevationTiles;
    private final List _elevationTileCache;
    private final Resampling _resampling;
    private final Resampling.Index _resamplingIndex;
    private final Resampling.Raster _resamplingRaster;

    public ACE2_5MinElevationModel(ACE2_5MinElevationModelDescriptor descriptor, Resampling resamplingMethod) {
        _descriptor = descriptor;
        _resampling = resamplingMethod;
        _resamplingIndex = _resampling.createIndex();
        _resamplingRaster = this;
        _elevationTiles = createEleveationTiles();
        _elevationTileCache = new ArrayList();

        System.out.println("ACE2 DEM created");
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
        float pixelY = RASTER_HEIGHT - (geoPos.lat + 90.0f) / DEGREE_RES_BY_NUM_PIXELS_PER_TILE; // todo (nf) - consider 0.5, y = (90 - lon) / DEGREE_RES * NUM_PIXELS_PER_TILE;
        float pixelX = (geoPos.lon + 180.0f) / DEGREE_RES_BY_NUM_PIXELS_PER_TILE; // todo (nf) - consider 0.5
        _resampling.computeIndex(pixelX, pixelY,
                                 RASTER_WIDTH,
                                 RASTER_HEIGHT,
                                 _resamplingIndex);

        final float elevation = _resampling.resample(_resamplingRaster, _resamplingIndex);
        if (Float.isNaN(elevation)) {
            return NO_DATA_VALUE;
        }
        return elevation;
    }

    public void dispose() {
        _elevationTileCache.clear();
        for (int i = 0; i < _elevationTiles.length; i++) {
            for (int j = 0; j < _elevationTiles[i].length; j++) {
                _elevationTiles[i][j].dispose();
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
        final ACE2_5MinElevationTile tile = _elevationTiles[tileXIndex][tileYIndex];
        if(tile == null) {
            return Float.NaN;
        }
        final int tileX = pixelX - tileXIndex * NUM_PIXELS_PER_TILE;
        final int tileY = pixelY - tileYIndex * NUM_PIXELS_PER_TILE;
        final float sample = tile.getSample(tileX, tileY);
        if (sample == NO_DATA_VALUE) {
            return Float.NaN;
        }
        return sample;
    }

    private ACE2_5MinElevationTile[][] createEleveationTiles() {
        final ACE2_5MinElevationTile[][] elevationTiles = new ACE2_5MinElevationTile[NUM_X_TILES][NUM_Y_TILES];
        final ProductReaderPlugIn readerPlugIn = getACEReaderPlugIn();
        for (int i = 0; i < elevationTiles.length; i++) {
            final int minLon = i * DEGREE_RES - 180;

            for (int j = 0; j < elevationTiles[i].length; j++) {
                final int minLat = j * DEGREE_RES - 90;

                final File file = _descriptor.getTileFile(minLon, minLat);
                if (file != null && file.exists() && file.isFile()) {
                    elevationTiles[i][NUM_Y_TILES - 1 - j] = new ACE2_5MinElevationTile(this, file, readerPlugIn);
                } else {
                    elevationTiles[i][NUM_Y_TILES - 1 - j] = null;
                }
            }
        }
        return elevationTiles;
    }

    public void updateCache(ACE2_5MinElevationTile tile) {
        _elevationTileCache.remove(tile);
        _elevationTileCache.add(0, tile);
        while (_elevationTileCache.size() > 60) {
            final int index = _elevationTileCache.size() - 1;
            ACE2_5MinElevationTile lastTile = (ACE2_5MinElevationTile) _elevationTileCache.get(index);
            lastTile.clearCache();
            _elevationTileCache.remove(index);
        }
    }

    private static ACE2_5MinReaderPlugIn getACEReaderPlugIn() {
        final Iterator readerPlugIns = ProductIOPlugInManager.getInstance().getReaderPlugIns(
                ACE2_5MinReaderPlugIn.FORMAT_NAME);
        return (ACE2_5MinReaderPlugIn) readerPlugIns.next();
    }
}