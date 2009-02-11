package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.dataio.dimap.FileImageInputStreamExtImpl;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.dat.dialogs.GenericBinaryDialog;

import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;

/**
 * The product reader for ImageIO products.
 *
 */
public class GenericReader extends AbstractProductReader {

    private int rasterWidth = 0;
    private int rasterHeight = 0;
    private int numBands = 1;
    private int dataType = ProductData.TYPE_INT16;

    private int _imageRecordLength = rasterWidth;
    private int _startPosImageRecords = 0;
    private int _imageHeaderLength = 0;
    
    private BinaryFileReader binaryReader = null;

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
                _imageRecordLength = rasterWidth;
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

        final ImageInputStream imageStream = FileImageInputStreamExtImpl.createInputStream(inputFile);
        binaryReader = new BinaryFileReader(imageStream);

        return product;
    }

    @Override
    public void close() throws IOException {
        super.close();

        binaryReader.close();
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
    protected void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight,
                                          int sourceStepX, int sourceStepY, Band destBand, int destOffsetX,
                                          int destOffsetY, int destWidth, int destHeight, ProductData destBuffer,
                                          ProgressMonitor pm) throws IOException {

        try {
            switch(dataType) {
            case ProductData.TYPE_INT8:
            case ProductData.TYPE_UINT8:
                readBandRasterDataByte(sourceOffsetX, sourceOffsetY,
                                sourceWidth, sourceHeight, sourceStepX, sourceStepY,
                                _startPosImageRecords +_imageHeaderLength, _imageRecordLength,
                                destWidth, destBuffer, binaryReader, pm);
                break;
            case ProductData.TYPE_INT16:
            case ProductData.TYPE_UINT16:
                readBandRasterDataShort(sourceOffsetX, sourceOffsetY,
                                sourceWidth, sourceHeight, sourceStepX, sourceStepY,
                                _startPosImageRecords +_imageHeaderLength, _imageRecordLength,
                                destWidth, destBuffer, binaryReader, pm);
                break;
            case ProductData.TYPE_INT32:
            case ProductData.TYPE_UINT32:
                readBandRasterDataInt(sourceOffsetX, sourceOffsetY,
                                sourceWidth, sourceHeight, sourceStepX, sourceStepY,
                                _startPosImageRecords +_imageHeaderLength, _imageRecordLength,
                                destWidth, destBuffer, binaryReader, pm);
                break;
            case ProductData.TYPE_FLOAT32:
                readBandRasterDataFloat(sourceOffsetX, sourceOffsetY,
                                sourceWidth, sourceHeight, sourceStepX, sourceStepY,
                                _startPosImageRecords +_imageHeaderLength, _imageRecordLength,
                                destWidth, destBuffer, binaryReader, pm);
                break;
            case ProductData.TYPE_FLOAT64:
                readBandRasterDataDouble(sourceOffsetX, sourceOffsetY,
                                sourceWidth, sourceHeight, sourceStepX, sourceStepY,
                                _startPosImageRecords +_imageHeaderLength, _imageRecordLength,
                                destWidth, destBuffer, binaryReader, pm);
                break;
            default:
                throw new IOException("Undandled type "+ ProductData.getTypeString(dataType));
            }
        } catch(Exception e) {
            throw new IOException(e);
        }
    }


    public static void readBandRasterDataShort(final int sourceOffsetX, final int sourceOffsetY,
                                        final int sourceWidth, final int sourceHeight,
                                        final int sourceStepX, final int sourceStepY,
                                        final int imageStartOffset, int imageRecordLength,
                                        final int destWidth, final ProductData destBuffer,
                                        final BinaryFileReader binaryReader, final ProgressMonitor pm)
                                        throws IOException, IllegalBinaryFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 2;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final short[] srcLine = new short[sourceWidth];
            short[] destLine = null;
            if (sourceStepX != 1)
                destLine = new short[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
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

    public static void readBandRasterDataByte(final int sourceOffsetX, final int sourceOffsetY,
                                       final int sourceWidth, final int sourceHeight,
                                       final int sourceStepX, final int sourceStepY,
                                       final int imageStartOffset, int imageRecordLength,
                                       final int destWidth, final ProductData destBuffer,
                                       final BinaryFileReader binaryReader, final ProgressMonitor pm)
                                       throws IOException, IllegalBinaryFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 1;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final byte[] srcLine = new byte[sourceWidth];
            byte[] destLine = null;
            if (sourceStepX != 1)
                destLine = new byte[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
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

    public static void readBandRasterDataInt(final int sourceOffsetX, final int sourceOffsetY,
                                             final int sourceWidth, final int sourceHeight,
                                             final int sourceStepX, final int sourceStepY,
                                             final int imageStartOffset, int imageRecordLength,
                                             final int destWidth, final ProductData destBuffer,
                                             final BinaryFileReader binaryReader, final ProgressMonitor pm)
                                             throws IOException, IllegalBinaryFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 4;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final int[] srcLine = new int[sourceWidth];
            int[] destLine = null;
            if (sourceStepX != 1)
                destLine = new int[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
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

    public static void readBandRasterDataFloat(final int sourceOffsetX, final int sourceOffsetY,
                                             final int sourceWidth, final int sourceHeight,
                                             final int sourceStepX, final int sourceStepY,
                                             final int imageStartOffset, int imageRecordLength,
                                             final int destWidth, final ProductData destBuffer,
                                             final BinaryFileReader binaryReader, final ProgressMonitor pm)
                                             throws IOException, IllegalBinaryFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 4;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final float[] srcLine = new float[sourceWidth];
            float[] destLine = null;
            if (sourceStepX != 1)
                destLine = new float[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
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

    public static void readBandRasterDataLong(final int sourceOffsetX, final int sourceOffsetY,
                                             final int sourceWidth, final int sourceHeight,
                                             final int sourceStepX, final int sourceStepY,
                                             final int imageStartOffset, int imageRecordLength,
                                             final int destWidth, final ProductData destBuffer,
                                             final BinaryFileReader binaryReader, final ProgressMonitor pm)
                                             throws IOException, IllegalBinaryFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 8;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final long[] srcLine = new long[sourceWidth];
            long[] destLine = null;
            if (sourceStepX != 1)
                destLine = new long[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
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

    public static void readBandRasterDataDouble(final int sourceOffsetX, final int sourceOffsetY,
                                             final int sourceWidth, final int sourceHeight,
                                             final int sourceStepX, final int sourceStepY,
                                             final int imageStartOffset, int imageRecordLength,
                                             final int destWidth, final ProductData destBuffer,
                                             final BinaryFileReader binaryReader, final ProgressMonitor pm)
                                             throws IOException, IllegalBinaryFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 8;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final double[] srcLine = new double[sourceWidth];
            double[] destLine = null;
            if (sourceStepX != 1)
                destLine = new double[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
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

    public static void readBandRasterDataSLCShort(final int sourceOffsetX, final int sourceOffsetY,
                                      final int sourceWidth, final int sourceHeight,
                                      final int sourceStepX, final int sourceStepY,
                                      final int imageStartOffset, int imageRecordLength,
                                      final int destWidth, final ProductData destBuffer, boolean oneOf2,
                                      final BinaryFileReader binaryReader, final ProgressMonitor pm)
                                        throws IOException, IllegalBinaryFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 4;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final short[] srcLine = new short[sourceWidth * 2];
            final short[] destLine = new short[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (oneOf2)
                    copyLine1Of2(srcLine, destLine, sourceStepX);
                else
                    copyLine2Of2(srcLine, destLine, sourceStepX);

                System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }

    public static void readBandRasterDataSLCFloat(final int sourceOffsetX, final int sourceOffsetY,
                                           final int sourceWidth, final int sourceHeight,
                                           final int sourceStepX, final int sourceStepY,
                                           final int imageStartOffset, int imageRecordLength,
                                           final int destWidth, final ProductData destBuffer, boolean oneOf2,
                                           final BinaryFileReader binaryReader, final ProgressMonitor pm)
                                            throws IOException, IllegalBinaryFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 8;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final float[] srcLine = new float[sourceWidth * 2];
            final float[] destLine = new float[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (oneOf2)
                    copyLine1Of2(srcLine, destLine, sourceStepX);
                else
                    copyLine2Of2(srcLine, destLine, sourceStepX);

                System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }

    public static void readBandRasterDataSLCByte(final int sourceOffsetX, final int sourceOffsetY,
                                          final int sourceWidth, final int sourceHeight,
                                          final int sourceStepX, final int sourceStepY,
                                          final int imageStartOffset, int imageRecordLength,
                                          final int destWidth, final ProductData destBuffer, boolean oneOf2,
                                          final BinaryFileReader binaryReader, final ProgressMonitor pm)
                                          throws IOException, IllegalBinaryFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 2;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final byte[] srcLine = new byte[sourceWidth * 2];
            final byte[] destLine = new byte[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (oneOf2)
                    copyLine1Of2(srcLine, destLine, sourceStepX);
                else
                    copyLine2Of2(srcLine, destLine, sourceStepX);

                System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }

    private static void copyLine(final short[] srcLine, final short[] destLine, final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    private static void copyLine(final byte[] srcLine, final byte[] destLine, final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    private static void copyLine(final int[] srcLine, final int[] destLine, final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    private static void copyLine(final long[] srcLine, final long[] destLine, final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    private static void copyLine(final float[] srcLine, final float[] destLine, final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    private static void copyLine(final double[] srcLine, final double[] destLine, final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    private static void copyLine1Of2(final short[] srcLine, final short[] destLine, final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i << 1];
        }
    }

    private static void copyLine1Of2(final byte[] srcLine, final byte[] destLine, final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i << 1];
        }
    }

    private static void copyLine1Of2(final float[] srcLine, final float[] destLine, final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = (int)srcLine[i << 1];
        }
    }

    private static void copyLine2Of2(final short[] srcLine, final short[] destLine, final int sourceStepX) {
        final int length = destLine.length;
        for (int x = 0, i = 0; x < length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[(i << 1) + 1];
        }
    }

    private static void copyLine2Of2(final byte[] srcLine, final byte[] destLine, final int sourceStepX) {
        final int length = destLine.length;
        for (int x = 0, i = 0; x < length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[(i << 1) + 1];
        }
    }

    private static void copyLine2Of2(final float[] srcLine, final float[] destLine, final int sourceStepX) {
        final int length = destLine.length;
        for (int x = 0, i = 0; x < length; ++x, i += sourceStepX) {
            destLine[x] = (int)srcLine[(i << 1) + 1];
        }
    }
}