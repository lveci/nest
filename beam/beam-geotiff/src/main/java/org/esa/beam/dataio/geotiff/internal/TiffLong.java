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
public class TiffLong extends TiffValue {

    public TiffLong(final long value) {
        TiffValueRangeChecker.checkValueTiffLong(value, "value");
        setData(ProductData.createInstance(ProductData.TYPE_UINT32));
        getData().setElemUInt(value);
    }

    public long getValue() {
        return getData().getElemUInt();
    }

}
