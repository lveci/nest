package org.esa.nest.dataio.ceos.alos;

import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.nest.dataio.ceos.CEOSProductDirectory;
import org.esa.nest.dataio.ceos.CEOSProductReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;

import java.io.File;
import java.io.IOException;

/**
 * The product reader for AlosPalsar products.
 *
 */
public class AlosPalsarProductReader extends CEOSProductReader {

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    public AlosPalsarProductReader(final ProductReaderPlugIn readerPlugIn) {
       super(readerPlugIn);
    }

    protected CEOSProductDirectory createProductDirectory(File inputFile) throws IOException, IllegalCeosFormatException {
        return new AlosPalsarProductDirectory(inputFile.getParentFile());
    }

    DecodeQualification checkProductQualification(File file) {

        try {
            _dataDir = createProductDirectory(file);

            AlosPalsarProductDirectory ersDataDir = (AlosPalsarProductDirectory)_dataDir;
            if(ersDataDir.isALOS())
                return DecodeQualification.INTENDED;
            return DecodeQualification.SUITABLE;

        } catch (Exception e) {
            return DecodeQualification.UNABLE;
        }
    }

}