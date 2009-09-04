package org.esa.nest.dataio.ceos.alos;

import org.esa.nest.dataio.ceos.CEOSConstants;

/**
 * Several constants used for reading Palsar products.
 */
public class AlosPalsarConstants implements CEOSConstants {

    final static String[] FORMAT_NAMES = new String[]{"ALOS PALSAR"};
    final static String[] FORMAT_FILE_EXTENSIONS = new String[]{""};
    final static String PLUGIN_DESCRIPTION = "ALOS PALSAR CEOS Products";      /*I18N*/
    final static String PRODUCT_TYPE_PREFIX = "";
    final static String VOLUME_FILE_PREFIX = "VOL";

    final static String MISSION = "alos";

    final static String PRODUCT_DESCRIPTION_PREFIX = "ALOS PALSAR product ";

    final static String SUMMARY_FILE_NAME = "summary.txt";
    final static String WORKREPORT_FILE_NAME = "workreport";

    final static int LEVEL1_0 = 0;
    final static int LEVEL1_1 = 1;
    final static int LEVEL1_5 = 3;
    final static int LEVEL4_1 = 4;
    final static int LEVEL4_2 = 5;

    /**
     * Taken from <a href="http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm">http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm</a>
     */
    final static float WAVELENGTH_BAND_1 = 420.0F;
    /**
     * Taken from <a href="http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm">http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm</a>
     */
    final static float WAVELENGTH_BAND_2 = 520.0F;
    /**
     * Taken from <a href="http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm">http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm</a>
     */
    final static float WAVELENGTH_BAND_3 = 610.0F;
    /**
     * Taken from <a href="http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm">http://www.eorc.jaxa.jp/ALOS/about/avnir2.htm</a>
     */
    final static float WAVELENGTH_BAND_4 = 760.0F;

    final static float BANDWIDTH_BAND_1 = 80.0F;
    final static float BANDWIDTH_BAND_2 = BANDWIDTH_BAND_1;
    final static float BANDWIDTH_BAND_3 = BANDWIDTH_BAND_1;
    final static float BANDWIDTH_BAND_4 = 130.0F;

    private final static String INDICATION_KEY = "001";
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