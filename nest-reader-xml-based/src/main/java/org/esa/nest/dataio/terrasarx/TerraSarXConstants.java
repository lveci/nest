package org.esa.nest.dataio.terrasarx;

import java.io.File;

/**
 * Several constants used for reading TerraSarX products.
 */
class TerraSarXConstants  {

    private final static String[] FORMAT_NAMES = new String[]{"TerraSarX"};
    private final static String[] FORMAT_FILE_EXTENSIONS = new String[]{""};
    private final static String PLUGIN_DESCRIPTION = "TerraSarX Products";      /*I18N*/
    final static String PRODUCT_TYPE_PREFIX = "";
    final static String PRODUCT_HEADER_PREFIX = "TSX1_SAR";

    final static String PRODUCT_DESCRIPTION_PREFIX = "TerraSarX product ";

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