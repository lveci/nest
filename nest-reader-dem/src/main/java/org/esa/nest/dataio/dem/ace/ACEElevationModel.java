
package org.esa.nest.dataio.dem.ace;

import org.esa.beam.framework.dataio.ProductIOPlugInManager;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.resamp.Resampling;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ACEElevationModel implements ElevationModel, Resampling.Raster {

    public static final int NUM_X_TILES = ACEElevationModelDescriptor.NUM_X_TILES;
    public static final int NUM_Y_TILES = ACEElevationModelDescriptor.NUM_Y_TILES;
    public static final int DEGREE_RES = ACEElevationModelDescriptor.DEGREE_RES;
    public static final int NUM_PIXELS_PER_TILE = ACEElevationModelDescriptor.PIXEL_RES;
    public static final int NO_DATA_VALUE = ACEElevationModelDescriptor.NO_DATA_VALUE;
    public static final int RASTER_WIDTH = NUM_X_TILES * NUM_PIXELS_PER_TILE;
    public static final int RASTER_HEIGHT = NUM_Y_TILES * NUM_PIXELS_PER_TILE;

    private final ACEElevationModelDescriptor _descriptor;
    private final ACEElevationTile[][] _elevationTiles;
    private final List _elevationTileCache;
    private final Resampling _resampling;
    private final Resampling.Index _resamplingIndex;
    private final Resampling.Raster _resamplingRaster;

    public ACEElevationModel(ACEElevationModelDescriptor descriptor) throws IOException {
        _descriptor = descriptor;
        _resampling = Resampling.BILINEAR_INTERPOLATION;
        _resamplingIndex = _resampling.createIndex();
        _resamplingRaster = this;
        _elevationTiles = createEleveationTiles();
        _elevationTileCache = new ArrayList();
    }

    public ElevationModelDescriptor getDescriptor() {
        return _descriptor;
    }

    public float getElevation(GeoPos geoPos) throws Exception {
        float pixelX = (geoPos.lon + 180.0f) / DEGREE_RES * NUM_PIXELS_PER_TILE; // todo (nf) - consider 0.5
        float pixelY = RASTER_HEIGHT - (geoPos.lat + 90.0f) / DEGREE_RES * NUM_PIXELS_PER_TILE; // todo (nf) - consider 0.5, y = (90 - lon) / DEGREE_RES * NUM_PIXELS_PER_TILE;
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
        final ACEElevationTile tile = _elevationTiles[tileXIndex][tileYIndex];
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

    private ACEElevationTile[][] createEleveationTiles() throws IOException {
        final ACEElevationTile[][] elevationTiles = new ACEElevationTile[NUM_X_TILES][NUM_Y_TILES];
        final ProductReaderPlugIn ACEReaderPlugIn = getACEReaderPlugIn();
        for (int i = 0; i < elevationTiles.length; i++) {
            final int minLon = i * DEGREE_RES - 180;

            for (int j = 0; j < elevationTiles[i].length; j++) {
                final ProductReader productReader = ACEReaderPlugIn.createReaderInstance();
                final int minLat = j * DEGREE_RES - 90;

                File file = _descriptor.getTileFile(minLon, minLat);
                if (file != null && file.exists() && file.isFile()) {
                    final Product product = productReader.readProductNodes(file, null);
                    elevationTiles[i][NUM_Y_TILES - 1 - j] = new ACEElevationTile(this, product);
                } else {
                    elevationTiles[i][NUM_Y_TILES - 1 - j] = null;
                }
            }
        }
        return elevationTiles;
    }

    public void updateCache(ACEElevationTile tile) {
        _elevationTileCache.remove(tile);
        _elevationTileCache.add(0, tile);
        while (_elevationTileCache.size() > 60) {
            final int index = _elevationTileCache.size() - 1;
            ACEElevationTile lastTile = (ACEElevationTile) _elevationTileCache.get(index);
            lastTile.clearCache();
            _elevationTileCache.remove(index);
        }
    }

    private static ACEReaderPlugIn getACEReaderPlugIn() {
        final Iterator readerPlugIns = ProductIOPlugInManager.getInstance().getReaderPlugIns(
                ACEReaderPlugIn.FORMAT_NAME);
        return (ACEReaderPlugIn) readerPlugIns.next();
    }
}
