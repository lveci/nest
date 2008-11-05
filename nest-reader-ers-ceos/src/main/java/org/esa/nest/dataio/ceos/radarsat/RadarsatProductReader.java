package org.esa.nest.dataio.ceos.radarsat;

import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.dataio.ceos.CEOSProductDirectory;
import org.esa.nest.dataio.ceos.CEOSProductReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.dataio.ceos.CEOSImageFile;

import java.io.File;
import java.io.IOException;

import com.bc.ceres.core.ProgressMonitor;

/**
 * The product reader for Radarsat products.
 *
 */
public class RadarsatProductReader extends CEOSProductReader {

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    public RadarsatProductReader(final ProductReaderPlugIn readerPlugIn) {
       super(readerPlugIn);
    }

    protected CEOSProductDirectory createProductDirectory(File inputFile) throws IOException, IllegalCeosFormatException {
        return new RadarsatProductDirectory(inputFile.getParentFile());
    }

    DecodeQualification checkProductQualification(File file) {

        try {
            _dataDir = createProductDirectory(file);

            RadarsatProductDirectory dataDir = (RadarsatProductDirectory)_dataDir;
            if(dataDir.isRadarsat())
                return DecodeQualification.INTENDED;
            return DecodeQualification.SUITABLE;

        } catch (Exception e) {
            return DecodeQualification.UNABLE;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight,
                                          int sourceStepX, int sourceStepY, Band destBand, int destOffsetX,
                                          int destOffsetY, int destWidth, int destHeight, ProductData destBuffer,
                                          ProgressMonitor pm) throws IOException {
        try {
            final CEOSImageFile imageFile = _dataDir.getImageFile(destBand);
            if(_dataDir.isSLC()) {
                boolean oneOf2 = !destBand.getName().startsWith("q");

                imageFile.readBandRasterDataSLCByte(sourceOffsetX, sourceOffsetY,
                                         sourceWidth, sourceHeight,
                                         sourceStepX, sourceStepY,
                                         destOffsetX, destOffsetY,
                                         destWidth, destHeight,
                                         destBuffer, oneOf2, pm);

            } else {
                imageFile.readBandRasterDataByte(sourceOffsetX, sourceOffsetY,
                                         sourceWidth, sourceHeight,
                                         sourceStepX, sourceStepY,
                                         destOffsetX, destOffsetY,
                                         destWidth, destHeight,
                                         destBuffer, pm);
            }

        } catch (IllegalCeosFormatException e) {
            final IOException ioException = new IOException(e.getMessage());
            ioException.initCause(e);
            throw ioException;
        }

    }
}