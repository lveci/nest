package org.esa.nest.dataio.radarsat2;

import java.io.File;

/**
 * Several constants used for reading Radarsat2 products.
 */
class Radarsat2Constants {

    private final static String[] FORMAT_NAMES = new String[]{"Radarsat 2"};
    private final static String[] FORMAT_FILE_EXTENSIONS = new String[]{""};
    private final static String PLUGIN_DESCRIPTION = "Radarsat2 Products";      /*I18N*/
    final static String PRODUCT_TYPE_PREFIX = "";
    final static String PRODUCT_HEADER_PREFIX = "PRODUCT";

    final static String PRODUCT_DESCRIPTION_PREFIX = "Radarsat2 product ";

    private final static String INDICATION_KEY = "XML";

    final static Class[] VALID_INPUT_TYPES = new Class[]{File.class, String.class};

    public static String getIndicationKey() {
        return INDICATION_KEY;
    }

    public static String getPluginDescription() {
        return PLUGIN_DESCRIPTION;
    }

    public static String[] getFormatNames() {
        return FORMAT_NAMES;
    }

    public static String[] getForamtFileExtensions() {
        return FORMAT_FILE_EXTENSIONS;
    }

}