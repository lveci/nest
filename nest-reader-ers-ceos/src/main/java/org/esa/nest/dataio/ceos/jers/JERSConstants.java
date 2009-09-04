package org.esa.nest.dataio.ceos.jers;

import org.esa.nest.dataio.ceos.CEOSConstants;

/**
 * Several constants used for reading JERS products.
 */
public class JERSConstants implements CEOSConstants {

    final static String[] FORMAT_NAMES = new String[]{"JERS CEOS"};
    final static String[] FORMAT_FILE_EXTENSIONS = new String[]{""};
    final static String PLUGIN_DESCRIPTION = "JERS CEOS Products";      /*I18N*/
    final static String PRODUCT_TYPE_PREFIX = "";
    final static String VOLUME_FILE_PREFIX = "VDF";

    final static String PRODUCT_DESCRIPTION_PREFIX = "JERS product ";

    final static String SUMMARY_FILE_NAME = "summary.txt";

    private final static String INDICATION_KEY = ".001";
    private final static int MINIMUM_FILES = 4;    // 4 image files + leader file + volume file + trailer file

    public String getVolumeFilePrefix() {
        return VOLUME_FILE_PREFIX;
    }

    public String getIndicationKey() {
        return INDICATION_KEY;
    }

    public int getMinimumNumFiles() {
        return MINIMUM_FILES;
    }

    public String getPluginDescription() {
        return PLUGIN_DESCRIPTION;
    }

    public String[] getFormatNames() {
        return FORMAT_NAMES;
    }

    public String[] getForamtFileExtensions() {
        return FORMAT_FILE_EXTENSIONS;
    }
}