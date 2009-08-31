package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.util.CachingObjectArray;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileElevationTile {

    private CachingObjectArray _linesCache;
    private Product _product;
    private static final int maxLines = 500;
    private final List<Integer> indexList = new ArrayList<Integer>(maxLines);

    public FileElevationTile(final Product product) {
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
    }

    public void clearCache() {
        _linesCache.clear();
    }

    private CachingObjectArray.ObjectFactory getLineFactory() {
        final Band band = _product.getBandAt(0);
        final int width = _product.getSceneRasterWidth();
        return new CachingObjectArray.ObjectFactory() {
            public Object createObject(int index) throws Exception {
                updateCache(index);
                return band.readPixels(0, index, width, 1, new float[width], ProgressMonitor.NULL);
            }
        };
    }

     private void updateCache(int index) {
        indexList.remove((Object)index);
        indexList.add(0, index);
        if (indexList.size() > maxLines) {
            final int i = indexList.size() - 1;
            _linesCache.setObject(i, null);
            indexList.remove(i);
        }
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