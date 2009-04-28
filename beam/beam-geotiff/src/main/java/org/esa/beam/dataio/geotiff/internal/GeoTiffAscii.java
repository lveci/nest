package org.esa.beam.dataio.geotiff.internal;

/**
 * A TIFFValue implementation for the GeoTIFF format.
 *
 * @author Marco Peters
 * @author Sabine Embacher
 * @author Norman Fomferra
 * @version $Revision: 1.1 $ $Date: 2009-04-28 14:37:14 $
 */
class GeoTiffAscii extends TiffAscii {

    public GeoTiffAscii(final String ... values) {
        super(appendTerminator(values));
    }

    private static String appendTerminator(String... values) {
        final StringBuffer buffer = new StringBuffer();
        for (String value : values) {
            buffer.append(value).append("|");
        }
        return buffer.toString();
    }
}
