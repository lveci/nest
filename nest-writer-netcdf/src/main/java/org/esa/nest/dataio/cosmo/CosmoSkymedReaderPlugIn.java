package org.esa.nest.dataio.cosmo;

import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.nest.dataio.NetCDFReaderPlugIn;

import java.io.File;

/**
 * The ReaderPlugIn for CosmoSkymed products.
 *
 */
public class CosmoSkymedReaderPlugIn extends NetCDFReaderPlugIn {

	final static String[] COSMO_FORMAT_NAMES = { "CosmoSkymed" };
	final static String[] COSMO_FORMAT_FILE_EXTENSIONS = { "h5"};
    final static String COSMO_PLUGIN_DESCRIPTION = "Cosmo-Skymed Products";
    final static String COSMO_FILE_PREFIX = "cs";

    public CosmoSkymedReaderPlugIn() {
        FORMAT_NAMES = COSMO_FORMAT_NAMES;
        FORMAT_FILE_EXTENSIONS = COSMO_FORMAT_FILE_EXTENSIONS;
        PLUGIN_DESCRIPTION = COSMO_PLUGIN_DESCRIPTION;
    }

    @Override
    protected DecodeQualification checkProductQualification(final File file) {
        final String fileName = file.getName().toLowerCase();
        for(String ext : FORMAT_FILE_EXTENSIONS) {
            if(!ext.isEmpty() && fileName.endsWith(ext) && fileName.startsWith(COSMO_FILE_PREFIX))
                return DecodeQualification.INTENDED;
        }

        return DecodeQualification.UNABLE;
    }

    /**
     * Creates an instance of the actual product reader class. This method should never return <code>null</code>.
     *
     * @return a new reader instance, never <code>null</code>
     */
    @Override
    public ProductReader createReaderInstance() {
        return new CosmoSkymedReader(this);
    }

}