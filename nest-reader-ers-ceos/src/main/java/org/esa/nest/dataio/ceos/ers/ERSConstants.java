package org.esa.nest.dataio.ceos.ers;

import java.io.File;

/**
 * Several constants used for reading Palsar products.
 */
public interface ERSConstants {

    Class[] VALID_INPUT_TYPES = new Class[]{File.class, String.class};
    String[] FORMAT_NAMES = new String[]{"ERS"};
    String[] FORMAT_FILE_EXTENSIONS = new String[]{""};
    String PLUGIN_DESCRIPTION = "ERS CEOS Products";      /*I18N*/
    String PRODUCT_TYPE_PREFIX = "";
    String PRODUCT_LEVEL_1B2 = "1B2";
    String VOLUME_FILE_PREFIX = "VDF";

    String SUMMARY_FILE_NAME = "summary.txt";


    /**
     * Taken from <a href="http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm">http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm</a>
     */
    float WAVELENGTH_BAND_1 = 420.0F;
    /**
     * Taken from <a href="http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm">http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm</a>
     */
    float WAVELENGTH_BAND_2 = 520.0F;
    /**
     * Taken from <a href="http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm">http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm</a>
     */
    float WAVELENGTH_BAND_3 = 610.0F;
    /**
     * Taken from <a href="http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm">http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm</a>
     */
    float WAVELENGTH_BAND_4 = 760.0F;

    float BANDWIDTH_BAND_1 = 80.0F;
    float BANDWIDTH_BAND_2 = BANDWIDTH_BAND_1;
    float BANDWIDTH_BAND_3 = BANDWIDTH_BAND_1;
    float BANDWIDTH_BAND_4 = 130.0F;

    String GEOPHYSICAL_UNIT = "mw / (m^2*sr*nm)";
    String BANDNAME_PREFIX = "radiance_";
    String BAND_DESCRIPTION_FORMAT_STRING = "Radiance, Band %d";    /*I18N*/
    String PRODUCT_DESCRIPTION_PREFIX = "ERS product Level ";

    String MAP_PROJECTION_RAW = "NNNNN";
    String MAP_PROJECTION_UTM = "YNNNN";
    String MAP_PROJECTION_PS = "NNNNY";
}
