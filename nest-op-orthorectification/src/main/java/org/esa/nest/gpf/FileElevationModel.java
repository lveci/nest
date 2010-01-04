
package org.esa.nest.gpf;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.dataop.resamp.Resampling;

import java.io.File;
import java.io.IOException;

class FileElevationModel implements Resampling.Raster {

    private Resampling _resampling;
    private Resampling.Index _resamplingIndex;
    private final Resampling.Raster _resamplingRaster;
    private final GeoCoding tileGeocoding;

    private final FileElevationTile fileElevationTile;

    private final int RASTER_WIDTH;
    private final int RASTER_HEIGHT;
    private float noDataValue = 0;
    private final PixelPos pix = new PixelPos();

    public FileElevationModel(File file, Resampling resamplingMethod) throws IOException {

        final ProductReader productReader = ProductIO.getProductReaderForFile(file);
        final Product product = productReader.readProductNodes(file, null);
        RASTER_WIDTH = product.getSceneRasterWidth();
        RASTER_HEIGHT = product.getSceneRasterHeight();
        fileElevationTile = new FileElevationTile(product);
        tileGeocoding = product.getGeoCoding();
        noDataValue = (float)product.getBandAt(0).getNoDataValue();

        _resampling = resamplingMethod;
        _resamplingIndex = _resampling.createIndex();
        _resamplingRaster = this;
    }

    public FileElevationModel(File file, Resampling resamplingMethod, float demNoDataValue) throws IOException {

        this(file, resamplingMethod);

        noDataValue = demNoDataValue;
    }

    public void dispose() {
        fileElevationTile.dispose();
    }

    public void clearCache() {
        fileElevationTile.clearCache();   
    }

    public float getNoDataValue() {
        return noDataValue;
    }

    /**
     * @return The resampling method used.
     * @since BEAM 4.6
     */
    public Resampling getResampling() {
        return _resampling;
    }

    public float getElevation(GeoPos geoPos) throws Exception {

        tileGeocoding.getPixelPos(geoPos, pix);
        if(!pix.isValid() || pix.x < 0 || pix.y < 0 || pix.x > RASTER_WIDTH || pix.y > RASTER_HEIGHT)
            return noDataValue;
        
        _resampling.computeIndex(pix.x, pix.y,
                                 RASTER_WIDTH,
                                 RASTER_HEIGHT,
                                 _resamplingIndex);

        final float elevation = _resampling.resample(_resamplingRaster, _resamplingIndex);
        if (Float.isNaN(elevation)) {
            return noDataValue;
        }
        return elevation;
    }

    public float getSample(int pixelX, int pixelY) throws IOException {

        final float sample = fileElevationTile.getSample(pixelX, pixelY);
        if (sample == noDataValue) {
            return Float.NaN;
        }
        return sample;
    }

    public int getWidth() {
        return RASTER_WIDTH;
    }

    public int getHeight() {
        return RASTER_HEIGHT;
    }

}