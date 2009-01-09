package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.dat.dialogs.GenericBinaryDialog;

import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;

/**
 * The product reader for ImageIO products.
 *
 */
public class GenericReader extends AbstractProductReader {

    private int rasterWidth = 100;
    private int rasterHeight = 100;
    private int numBands = 1;
    private int dataType = ProductData.TYPE_INT16;

    private int _imageRecordLength = rasterWidth;
    private int _startPosImageRecords = 0;
    private int _imageHeaderLength = 0;
    
    private BinaryFileReader binaryReader;

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    public GenericReader(final ProductReaderPlugIn readerPlugIn) {
       super(readerPlugIn);
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
        if(VisatApp.getApp() != null) {
            //if in DAT then open options dialog
            final GenericBinaryDialog dialog = new GenericBinaryDialog(VisatApp.getApp().getMainFrame(), "importGenericBinary");
            if (dialog.show() == ModalDialog.ID_OK) {
                rasterWidth = dialog.getRasterWidth();
                rasterHeight = dialog.getRasterHeight();
                numBands = dialog.getNumBands();
                dataType = dialog.getDataType();
                _imageHeaderLength = dialog.getHeaderBytes();
            } else {
                throw new IOException("Import Canceled");
            }
        }


        final File inputFile = ReaderUtils.getFileFromInput(getInput());

        final Product product = new Product(inputFile.getName(),
                                            "Generic",
                                            rasterWidth, rasterHeight);

        int bandCnt = 1;
        for(int b=0; b < numBands; ++b) {
            final Band band = new Band("band"+ bandCnt++, dataType, rasterWidth, rasterHeight);
            product.addBand(band);
        }

        addMetaData(product, inputFile);

        product.setProductReader(this);
        product.setModified(false);
        product.setFileLocation(inputFile);


        ImageInputStream imageStream = new FileImageInputStream(inputFile);
        binaryReader = new BinaryFileReader(imageStream);

        return product;
    }

    public void close() throws IOException {
        super.close();


    }

    static DecodeQualification checkProductQualification(File file) {
        return DecodeQualification.SUITABLE;
    }

    private void addMetaData(final Product product, final File inputFile) throws IOException {
        final MetadataElement root = product.getMetadataRoot();
        root.addElement(new MetadataElement(Product.ABSTRACTED_METADATA_ROOT_NAME));

        AbstractMetadata.addAbstractedMetadataHeader(root);

        final MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);

        //AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, imgIOFile.getName());
        //AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, productType);

        AbstractMetadata.loadExternalMetadata(product, absRoot, inputFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight,
                                          int sourceStepX, int sourceStepY, Band destBand, int destOffsetX,
                                          int destOffsetY, int destWidth, int destHeight, ProductData destBuffer,
                                          ProgressMonitor pm) throws IOException {

        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX;// * 2;
        final long xpos = _startPosImageRecords +_imageHeaderLength + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final short[] srcLine = new short[sourceWidth];
            final short[] destLine = new short[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(_imageRecordLength * y + xpos);
                    binaryReader.readB2(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (sourceStepX == 1) {

                    System.arraycopy(srcLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                } else {
                    copyLine(srcLine, destLine, sourceStepX);

                    System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                }

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }


    protected static void copyLine(final short[] srcLine, final short[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    protected static void copyLine(final byte[] srcLine, final byte[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }
}