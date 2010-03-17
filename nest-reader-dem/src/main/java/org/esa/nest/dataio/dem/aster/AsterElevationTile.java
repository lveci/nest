package org.esa.nest.dataio.dem.aster;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.util.CachingObjectArray;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.nest.dataio.dem.srtm3_geotiff.EarthGravitationalModel96;

import java.io.IOException;

public final class AsterElevationTile {

    private EarthGravitationalModel96 _egm;
    private CachingObjectArray _linesCache;
    private Product _product;
    private float _noDataValue;
    private float[][] _egmArray = null;

    public AsterElevationTile(final AsterElevationModel dem, final Product product) {
        _egm = dem.getEarthGravitationalModel96();
        _product = product;
        _noDataValue = dem.getDescriptor().getNoDataValue();
        _linesCache = new CachingObjectArray(getLineFactory());
        _linesCache.setCachedRange(0, product.getSceneRasterHeight());

        //System.out.println("Dem Tile "+product.getName());
        computeEGMArray();
    }

    public float getSample(int pixelX, int pixelY) throws IOException {
        try {
            final float[] line = (float[]) _linesCache.getObject(pixelY);
            return line[pixelX];
        } catch (Exception e) {
            throw convertLineCacheException(e);
        }
    }

    public void dispose() {
        clearCache();
        _linesCache = null;
        if (_product != null) {
            _product.dispose();
            _product = null;
        }
    }

    public void clearCache() {
        _linesCache.clear();
    }

    private CachingObjectArray.ObjectFactory getLineFactory() {
        final Band band = _product.getBandAt(0);
        final int width = _product.getSceneRasterWidth();
        return new CachingObjectArray.ObjectFactory() {
            public synchronized Object createObject(int index) throws Exception {
                final float[] line =  band.readPixels(0, index, width, 1, new float[width], ProgressMonitor.NULL);
                final int rowIdxInEGMArray = index / 300; // tile_height / numEGMSamplesInCol
                for (int i = 0; i < line.length; i++) {
                    if (line[i] != _noDataValue) {
                        final int colIdxInEGMArray = i/300; // tile_width / numEGMSamplesInRow = 6000 / 20 = 300
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

        final int numEGMSamplesInRow = 20;
        final int numEGMSamplesInCol = 20;
        _egmArray = new float[numEGMSamplesInRow][numEGMSamplesInCol]; // 5 deg / 15 min

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