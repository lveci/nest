package org.esa.nest.dataio.hdf;

import org.esa.nest.dataio.NetCDFReaderPlugIn;

/**
 * The ReaderPlugIn for HDF products.
 *
 */
public class HDFReaderPlugIn extends NetCDFReaderPlugIn {

	private final static String[] HDF_FORMAT_NAMES = { "HDF" };
	private final static String[] HDF_FORMAT_FILE_EXTENSIONS = { "hdf", "h5", "h4", "h5eos" };
    private final static String HDF_PLUGIN_DESCRIPTION = "HDF Products";

    public HDFReaderPlugIn() {
        FORMAT_NAMES = HDF_FORMAT_NAMES;
        FORMAT_FILE_EXTENSIONS = HDF_FORMAT_FILE_EXTENSIONS;
        PLUGIN_DESCRIPTION = HDF_PLUGIN_DESCRIPTION;   
    }

}