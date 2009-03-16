package org.esa.nest.dataio.dem.srtm3_geotiff;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.util.CachingObjectArray;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;

import java.io.IOException;

public class SRTM3GeoTiffElevationTile {

    private SRTM3GeoTiffElevationModel _dem;
    private CachingObjectArray _linesCache;
    private Product _product;

    public SRTM3GeoTiffElevationTile(final SRTM3GeoTiffElevationModel dem, final Product product) {
        _dem = dem;
        _product = product;

        _linesCache = new CachingObjectArray(getLineFactory());
        _linesCache.setCachedRange(0, product.getSceneRasterHeight());
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
                _dem.updateCache(SRTM3GeoTiffElevationTile.this);
                return band.readPixels(0, index, width, 1, new float[width], ProgressMonitor.NULL);
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
}