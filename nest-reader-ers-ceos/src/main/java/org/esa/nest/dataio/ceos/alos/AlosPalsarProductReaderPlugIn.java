package org.esa.nest.dataio.ceos.alos;

import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.dataio.ceos.CEOSProductReaderPlugIn;

import java.io.File;
import java.io.FilenameFilter;

/**
 * The ReaderPlugIn for ALOS PALSAR CEOS products.
 *
 */
public class AlosPalsarProductReaderPlugIn extends CEOSProductReaderPlugIn {

    public AlosPalsarProductReaderPlugIn() {
        constants = new AlosPalsarConstants();
    }

    /**
     * Creates an instance of the actual product reader class. This method should never return <code>null</code>.
     *
     * @return a new reader instance, never <code>null</code>
     */
    @Override
    public ProductReader createReaderInstance() {
        return new AlosPalsarProductReader(this);
    }

    /**
     * Checks whether the given object is an acceptable input for this product reader and if so, the method checks if it
     * is capable of decoding the input's content.
     *
     * @param input any input object
     *
     * @return true if this product reader can decode the given input, otherwise false.
     */
    @Override
    public DecodeQualification getDecodeQualification(final Object input) {
        final File file = ReaderUtils.getFileFromInput(input);
        if (file == null) {
            return DecodeQualification.UNABLE;
        }
        if (!file.getName().toUpperCase().startsWith(constants.getVolumeFilePrefix())) {
            return DecodeQualification.UNABLE; // not the volume file
        }

        final File parentDir = file.getParentFile();
        if (file.isFile() && parentDir.isDirectory()) {
            final FilenameFilter filter = new FilenameFilter() {
                public boolean accept(final File dir, final String name) {
                    return name.toUpperCase().startsWith(constants.getVolumeFilePrefix());
                }
            };
            final File[] files = parentDir.listFiles(filter);
            if (files != null && files.length >= 0) {
                return DecodeQualification.INTENDED;//checkProductQualification(file);
            }
        }
        return DecodeQualification.UNABLE;
    }

    @Override
    protected DecodeQualification checkProductQualification(File file) {
        if(file.getName().toUpperCase().startsWith(constants.getVolumeFilePrefix())) {
            final AlosPalsarProductReader reader = new AlosPalsarProductReader(this);
            return reader.checkProductQualification(file);
        }
        return DecodeQualification.UNABLE;
    }

}