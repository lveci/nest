package org.esa.nest.dataio.dem.ace;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.util.CachingObjectArray;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.nest.dataio.dem.srtm3_geotiff.EarthGravitationalModel96;

import java.io.IOException;

public class ACEElevationTile {

    private EarthGravitationalModel96 _egm;
    private ACEElevationModel _dem;
    private CachingObjectArray _linesCache;
    private Product _product;
    private float _noDataValue;
    private float[][] _egmArray = null;

    public ACEElevationTile(final ACEElevationModel dem, final Product product) {
        _egm = dem.getEarthGravitationalModel96();
        _dem = dem;
        _product = product;
        _noDataValue = dem.getDescriptor().getNoDataValue();

        _linesCache = new CachingObjectArray(getLineFactory());
        _linesCache.setCachedRange(0, product.getSceneRasterHeight());

        computeEGMArray();
    }

    public float getSample(int pixelX, int pixelY) throws IOException {
        final float[] line;
        try {
            line = (float[]) _linesCache.getObject(pixelY);
        } catch (Exception e) {
            throw convertLineCacheException(e);
        }
        return line[pixelX];
    }

    public void dispose() {
        clearCache();
        _linesCache = null;
        if (_product != null) {
            _product.dispose();
            _product = null;
        }
        _dem = null;
    }

    public void clearCache() {
        _linesCache.clear();
    }

    private CachingObjectArray.ObjectFactory getLineFactory() {
        final Band band = _product.getBandAt(0);
        final int width = _product.getSceneRasterWidth();
        return new CachingObjectArray.ObjectFactory() {
            public Object createObject(int index) throws Exception {
                _dem.updateCache(ACEElevationTile.this);
                final float[] line =  band.readPixels(0, index, width, 1, new float[width], ProgressMonitor.NULL);
                final int rowIdxInEGMArray = index / 30; // tile_height / numEGMSamplesInCol = 1800 / 60 = 30
                for (int i = 0; i < line.length; i++) {
                    if (line[i] != _noDataValue) {
                        final int colIdxInEGMArray = i / 30; // tile_width / numEGMSamplesInRow = 1800 / 60 = 30
                        line[i] += _egmArray[rowIdxInEGMArray][colIdxInEGMArray];
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
        _egmArray = new float[numEGMSamplesInRow][numEGMSamplesInCol]; // 15 deg / 15 min

        final GeoCoding geoCoding = _product.getGeoCoding();
        if(geoCoding == null) {
            throw new OperatorException("Product does not contain a geocoding");
        }
        final GeoPos geoPosFirstNear = geoCoding.getGeoPos(new PixelPos(0,0), null);
        final double lat0  = geoPosFirstNear.getLat() + 0.125; // + half of 15 min
        final double lon0  = geoPosFirstNear.getLon() + 0.125; // + half of 15 min

        final double delLat = 0.25; // 15 min
        final double delLon = 0.25; // 15 min
        for (int r = 0; r < numEGMSamplesInCol; r++) {
            final double lat = lat0 - delLat*r;
            for (int c = 0; c < numEGMSamplesInRow; c++) {
                _egmArray[r][c] = _egm.getEGM(lat, lon0 + delLon*c);
            }
        }
    }
}
