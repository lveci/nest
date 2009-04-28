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
class TiffAscii extends TiffValue {

    public TiffAscii(final String... values) {
        final StringBuffer buffer = new StringBuffer();
        for (String value : values) {
            buffer.append(value).append('\u0000');
        }
        setData(ProductData.createInstance(buffer.toString()));
    }

    public String getValue() {
        return getData().getElemString();
    }
}