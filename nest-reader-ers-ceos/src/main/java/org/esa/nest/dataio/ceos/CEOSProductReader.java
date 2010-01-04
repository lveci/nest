package org.esa.nest.dataio.ceos;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.util.Debug;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.datamodel.Unit;

import java.io.File;
import java.io.IOException;

/**
 * The product reader for Radarsat products.
 *
 */
public abstract class CEOSProductReader extends AbstractProductReader {

    protected CEOSProductDirectory _dataDir = null;

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    protected CEOSProductReader(final ProductReaderPlugIn readerPlugIn) {
       super(readerPlugIn);
    }
 
    protected abstract CEOSProductDirectory createProductDirectory(File inputFile)
            throws IOException, IllegalBinaryFormatException;

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
     * Provides an implementation of the <code>readProductNodes</code> interface method. Clients implementing this
     * method can be sure that the input object and eventually the subset information has already been set.
     * <p/>
     * <p>This method is called as a last step in the <code>readProductNodes(input, subsetInfo)</code> method.
     *
     * @throws java.io.IOException if an I/O error occurs
     */
    @Override
    protected Product readProductNodesImpl() throws IOException {
        //final ProductReaderPlugIn readerPlugIn = getReaderPlugIn();
        final Object input = getInput();

        final File fileFromInput = ReaderUtils.getFileFromInput(input);
        Product product;
        try {
            _dataDir = createProductDirectory(fileFromInput);
            _dataDir.readProductDirectory();
            product = _dataDir.createProduct();
            product.setFileLocation(fileFromInput);
        } catch (Exception e) {
            Debug.trace(e.toString());
            final IOException ioException = new IOException(e.getMessage());
            ioException.initCause(e);
            throw ioException;
        }
        product.setProductReader(this);
        product.setModified(false);

        return product;
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
            if(imageFile.getBitsPerSample() == 8) {
                if(_dataDir.isSLC()) {
                    boolean oneOf2 = destBand.getUnit().equals(Unit.REAL) || !destBand.getName().startsWith("q");

                    imageFile.readBandRasterDataSLCByte(sourceOffsetX, sourceOffsetY,
                                             sourceWidth, sourceHeight,
                                             sourceStepX, sourceStepY,
                                             destWidth,
                                             destBuffer, oneOf2, pm);

                } else {
                    imageFile.readBandRasterDataByte(sourceOffsetX, sourceOffsetY,
                                             sourceWidth, sourceHeight,
                                             sourceStepX, sourceStepY,
                                             destWidth,
                                             destBuffer, pm);
                }
            } else {
                if(_dataDir.isSLC()) {
                    boolean oneOf2 = destBand.getUnit().equals(Unit.REAL) || !destBand.getName().startsWith("q");

                    imageFile.readBandRasterDataSLC(sourceOffsetX, sourceOffsetY,
                                                    sourceWidth, sourceHeight,
                                                    sourceStepX, sourceStepY,
                                                    destWidth,
                                                    destBuffer, oneOf2, pm);

                } else {
                    imageFile.readBandRasterDataShort(sourceOffsetX, sourceOffsetY,
                                                    sourceWidth, sourceHeight,
                                                    sourceStepX, sourceStepY,
                                                    destWidth,
                                                    destBuffer, pm);
                }
            }

        } catch (Exception e) {
            final IOException ioException = new IOException(e.getMessage());
            ioException.initCause(e);
            throw ioException;
        }

    }
}