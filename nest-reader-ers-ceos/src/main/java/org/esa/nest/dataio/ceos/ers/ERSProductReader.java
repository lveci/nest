package org.esa.nest.dataio.ceos.ers;

import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.CEOSProductDirectory;
import org.esa.nest.dataio.ceos.CEOSProductReader;

import java.io.File;
import java.io.IOException;

/**
 * The product reader for ERS products.
 *
 */
public class ERSProductReader extends CEOSProductReader {

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    public ERSProductReader(final ProductReaderPlugIn readerPlugIn) {
       super(readerPlugIn);
    }

    @Override
    protected CEOSProductDirectory createProductDirectory(File inputFile) throws IOException, IllegalBinaryFormatException {
        return new ERSProductDirectory(inputFile.getParentFile());
    }

    DecodeQualification checkProductQualification(File file) {

        try {
            _dataDir = createProductDirectory(file);

            ERSProductDirectory ersDataDir = (ERSProductDirectory)_dataDir;
            if(ersDataDir.isERS())
                return DecodeQualification.INTENDED;
            return DecodeQualification.SUITABLE;
            
        } catch (Exception e) {
            return DecodeQualification.UNABLE;
        }
    }

}
