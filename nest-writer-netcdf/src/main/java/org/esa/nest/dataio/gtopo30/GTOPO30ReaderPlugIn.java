package org.esa.nest.dataio.gtopo30;

import org.esa.nest.dataio.NetCDFReaderPlugIn;

/**
 * The ReaderPlugIn for GTOPO30 tiles.
 */
public class GTOPO30ReaderPlugIn extends NetCDFReaderPlugIn {

    final static String[] GTOPO30_FORMAT_NAMES = {"GTOPO30"};
    final static String[] GTOPO30_FORMAT_FILE_EXTENSIONS = {"dem"};
    final static String GTOPO30_PLUGIN_DESCRIPTION = "GTOPO30 DEM Tiles";

    public GTOPO30ReaderPlugIn() {
        FORMAT_NAMES = GTOPO30_FORMAT_NAMES;
        FORMAT_FILE_EXTENSIONS = GTOPO30_FORMAT_FILE_EXTENSIONS;
        PLUGIN_DESCRIPTION = GTOPO30_PLUGIN_DESCRIPTION;
    }

}