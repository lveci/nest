package org.esa.nest.dataio.ceos.ers;

import org.esa.nest.dataio.ceos.CEOSConstants;

/**
 * Several constants used for reading ERS products.
 */
public class ERSConstants implements CEOSConstants {

    private final static String[] FORMAT_NAMES = new String[]{"ERS CEOS"};
    private final static String[] FORMAT_FILE_EXTENSIONS = new String[]{""};
    private final static String PLUGIN_DESCRIPTION = "ERS CEOS Products";      /*I18N*/
    final static String PRODUCT_TYPE_PREFIX = "";
    private final static String VOLUME_FILE_PREFIX = "VDF";

    private final static String MISSION = "ers";

    final static String PRODUCT_DESCRIPTION_PREFIX = "ERS product ";

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

    public String getMission() {
        return MISSION;
    }
}
