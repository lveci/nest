package org.esa.nest.dataio.gtopo30;

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

public class GTOPO30ElevationModel implements ElevationModel, Resampling.Raster {

    public static final int NUM_X_TILES = GTOPO30ElevationModelDescriptor.NUM_X_TILES;
    public static final int NUM_Y_TILES = GTOPO30ElevationModelDescriptor.NUM_Y_TILES;
    public static final int DEGREE_RES = GTOPO30ElevationModelDescriptor.DEGREE_RES;
    public static final int NUM_PIXELS_PER_TILE = GTOPO30ElevationModelDescriptor.PIXEL_RES_X * GTOPO30ElevationModelDescriptor.PIXEL_RES_Y;
    public static final int NO_DATA_VALUE = GTOPO30ElevationModelDescriptor.NO_DATA_VALUE;
    public static final int RASTER_WIDTH = NUM_X_TILES * NUM_PIXELS_PER_TILE;
    public static final int RASTER_HEIGHT = NUM_Y_TILES * NUM_PIXELS_PER_TILE;

    private final GTOPO30ElevationModelDescriptor _descriptor;
    private final GTOPO30ElevationTile[][] _elevationTiles;
    private final List _elevationTileCache;
    private final Resampling _resampling;
    private final Resampling.Index _resamplingIndex;
    private final Resampling.Raster _resamplingRaster;

    public GTOPO30ElevationModel(GTOPO30ElevationModelDescriptor descriptor) throws IOException {
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
        final GTOPO30ElevationTile tile = _elevationTiles[tileXIndex][tileYIndex];
        if (tile == null) {
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

    private GTOPO30ElevationTile[][] createEleveationTiles() throws IOException {
        final GTOPO30ElevationTile[][] elevationTiles = new GTOPO30ElevationTile[NUM_X_TILES][NUM_Y_TILES];
        final ProductReaderPlugIn ACEReaderPlugIn = getACEReaderPlugIn();
        for (int i = 0; i < elevationTiles.length; i++) {
            final int minLon = i * DEGREE_RES - 180;

            for (int j = 0; j < elevationTiles[i].length; j++) {
                final ProductReader productReader = ACEReaderPlugIn.createReaderInstance();
                final int minLat = j * DEGREE_RES - 90;

                final File file = _descriptor.getTileFile(minLon, minLat);
                if (file != null && file.exists() && file.isFile()) {
                    final Product product = productReader.readProductNodes(file, null);
                    elevationTiles[i][NUM_Y_TILES - 1 - j] = new GTOPO30ElevationTile(this, product);
                } else {
                    elevationTiles[i][NUM_Y_TILES - 1 - j] = null;
                }
            }
        }
        return elevationTiles;
    }

    public void updateCache(GTOPO30ElevationTile tile) {
        _elevationTileCache.remove(tile);
        _elevationTileCache.add(0, tile);
        while (_elevationTileCache.size() > 60) {
            final int index = _elevationTileCache.size() - 1;
            final GTOPO30ElevationTile lastTile = (GTOPO30ElevationTile) _elevationTileCache.get(index);
            lastTile.clearCache();
            _elevationTileCache.remove(index);
        }
    }

    private static GTOPO30ReaderPlugIn getACEReaderPlugIn() {
        final Iterator readerPlugIns = ProductIOPlugInManager.getInstance().getReaderPlugIns(
                GTOPO30ReaderPlugIn.GTOPO30_FORMAT_NAMES[0]);
        return (GTOPO30ReaderPlugIn) readerPlugIns.next();
    }
}