package org.esa.nest.dataio.ceos;

import java.io.File;

/**
 * Several constants used for reading CEOS products.
 */
public interface CEOSConstants {

    Class[] VALID_INPUT_TYPES = new Class[]{File.class, String.class};

    public String getVolumeFilePrefix();
    public String getIndicationKey();
    public int getMinimumNumFiles();

    public String getPluginDescription();
    public String[] getFormatNames();
    public String[] getForamtFileExtensions();
}