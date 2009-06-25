package org.esa.nest.dataio.geotiff;

import org.esa.beam.dataio.geotiff.GeoTiffProductReaderPlugIn;
import org.esa.beam.framework.dataio.ProductReader;

public class NestGeoTiffProductReaderPlugIn extends GeoTiffProductReaderPlugIn {

    @Override
    public ProductReader createReaderInstance() {
        return new NestGeoTiffProductReader(this);
    }
}