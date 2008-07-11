package org.esa.nest.dataio.ceos.ers;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.IllegalFileFormatException;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;

import java.io.File;
import java.io.IOException;

/**
 * The product reader for ERS products.
 *
 */
public class ERSProductReader extends AbstractProductReader {

    private ERSProductDirectory _dataDir;

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    public ERSProductReader(final ProductReaderPlugIn readerPlugIn) {
       super(readerPlugIn);
    }

    /**
     * Closes the access to all currently opened resources such as file input streams and all resources of this children
     * directly owned by this reader. Its primary use is to allow the garbage collector to perform a vanilla job.
     * <p/>
     * <p>This method should be called only if it is for sure that this object instance will never be used again. The
     * results of referencing an instance of this class after a call to <code>close()</code> are undefined.
     * <p/>
     * <p>Overrides of this method should always call <code>super.close();</code> after disposing this instance.
     *
     * @throws java.io.IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        if (_dataDir != null) {
            _dataDir.close();
            _dataDir = null;
        }
        super.close();
    }

    /**
     * Returns a <code>File</code> if the given input is a <code>String</code> or <code>File</code>,
     * otherwise it returns null;
     *
     * @param input an input object of unknown type
     *
     * @return a <code>File</code> or <code>null</code> it the input can not be resolved to a <code>File</code>.
     */
    public static File getFileFromInput(final Object input) {
        if (input instanceof String) {
            return new File((String) input);
        } else if (input instanceof File) {
            return (File) input;
        }
        return null;
    }

    /**
     * Provides an implementation of the <code>readProductNodes</code> interface method. Clients implementing this
     * method can be sure that the input object and eventually the subset information has already been set.
     * <p/>
     * <p>This method is called as a last step in the <code>readProductNodes(input, subsetInfo)</code> method.
     *
     * @throws java.io.IOException if an I/O error occurs
     */
    @Override
    protected Product readProductNodesImpl() throws IOException {
        final ProductReaderPlugIn readerPlugIn = getReaderPlugIn();
        final Object input = getInput();
        if (readerPlugIn.getDecodeQualification(input) == DecodeQualification.UNABLE) {
            throw new IOException("Unsupported product format."); /*I18N*/
        }
        final File fileFromInput = getFileFromInput(getInput());
        Product product;
        try {
            _dataDir = new ERSProductDirectory(fileFromInput.getParentFile());
            product = _dataDir.createProduct();
            product.setFileLocation(fileFromInput);
        } catch (IllegalCeosFormatException e) {
            final IOException ioException = new IOException(e.getMessage());
            ioException.initCause(e);
            throw ioException;
        }
        product.setProductReader(this);
        product.setModified(false);

        return product;
    }

    DecodeQualification checkProductQualification(File file) {
        try {
            _dataDir = new ERSProductDirectory(file.getParentFile());
        } catch (Exception e) {
            return DecodeQualification.UNABLE;
        }
        if(_dataDir.isERS())
            return DecodeQualification.INTENDED;
        return DecodeQualification.SUITABLE;
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
            final ERSImageFile imageFile = _dataDir.getImageFile(destBand);
            if(_dataDir.isSLC()) {
                imageFile.readBandRasterDataSLC(sourceOffsetX, sourceOffsetY,
                                         sourceWidth, sourceHeight,
                                         sourceStepX, sourceStepY,
                                         destOffsetX, destOffsetY,
                                         destWidth, destHeight,
                                         destBuffer, !destBand.getName().startsWith("q"), pm);

            } else {
                imageFile.readBandRasterData(sourceOffsetX, sourceOffsetY,
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
