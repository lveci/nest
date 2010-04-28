package org.esa.nest.dataio.gtopo30;

import org.esa.nest.dataio.NetCDFReaderPlugIn;
import org.esa.beam.framework.dataio.DecodeQualification;

/**
 * The ReaderPlugIn for GTOPO30 tiles.
 */
public class GTOPO30ReaderPlugIn extends NetCDFReaderPlugIn {

    private final static String[] GTOPO30_FORMAT_NAMES = {"GTOPO30"};
    private final static String[] GTOPO30_FORMAT_FILE_EXTENSIONS = {"dem"};
    private final static String GTOPO30_PLUGIN_DESCRIPTION = "GTOPO30 DEM Tiles";

    public GTOPO30ReaderPlugIn() {
        FORMAT_NAMES = GTOPO30_FORMAT_NAMES;
        FORMAT_FILE_EXTENSIONS = GTOPO30_FORMAT_FILE_EXTENSIONS;
        PLUGIN_DESCRIPTION = GTOPO30_PLUGIN_DESCRIPTION;
    }

    protected DecodeQualification isIntended(final String extension) {
        return DecodeQualification.SUITABLE;
    }
}