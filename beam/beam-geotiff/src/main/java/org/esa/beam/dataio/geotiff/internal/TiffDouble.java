package org.esa.beam.dataio.geotiff.internal;

import org.esa.beam.framework.datamodel.ProductData;

/**
 * A TIFFValue implementation for the GeoTIFF format.
 *
 * @author Marco Peters
 * @author Sabine Embacher
 * @author Norman Fomferra
 * @version $Revision: 1.1 $ $Date: 2009-04-28 14:37:14 $
 */
class TiffDouble extends TiffValue {

    public TiffDouble(final double value) {
        setData(ProductData.createInstance(ProductData.TYPE_FLOAT64));
        getData().setElemDouble(value);
    }

    public double getValue() {
        return getData().getElemDouble();
    }
}
