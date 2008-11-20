package org.esa.nest.dataio.ceos;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.ceos.records.ImageRecord;
import org.esa.nest.dataio.ceos.records.BaseRecord;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;


/**
 * This class represents an image file of a CEOS product.
 *
 * @version $Revision: 1.18 $ $Date: 2008-11-20 17:41:45 $
 */
public abstract class CEOSImageFile {

    protected BaseRecord _imageFDR = null;
    protected CeosFileReader _ceosReader = null;
    protected ImageRecord[] _imageRecords = null;

    protected int _imageRecordLength = 0;
    protected long _startPosImageRecords = 0;
    protected int _imageHeaderLength = 0;

    public BaseRecord getImageFileDescriptor() {
        return _imageFDR;
    }

    public int getRasterWidth() {
        int width = _imageFDR.getAttributeInt("Number of pixels per line per SAR channel");
        if(width==0)
            width = _imageFDR.getAttributeInt("SAR DATA record length");
        return width;
    }

    public int getRasterHeight() {
        return _imageFDR.getAttributeInt("Number of lines per data set");
    }

    public void assignMetadataTo(MetadataElement rootElem, int count) {
        MetadataElement metadata = new MetadataElement("Image Descriptor " + count);
         _imageFDR.assignMetadataTo(metadata);
        rootElem.addElement(metadata);
    }

    public static String[] getImageFileNames(File baseDir, String prefix) {
        final ArrayList<String> list = new ArrayList<String>(2);
        final File[] fileList = baseDir.listFiles();
        for (File file : fileList) {
            if (file.getName().toUpperCase().startsWith(prefix)) {
                list.add(file.getName());
            }
        }
        return list.toArray(new String[list.size()]);
    }

    public void readBandRasterData(final int sourceOffsetX, final int sourceOffsetY,
                                   final int sourceWidth, final int sourceHeight,
                                   final int sourceStepX, final int sourceStepY,
                                   final int destOffsetX, final int destOffsetY,
                                   final int destWidth, final int destHeight,
                                   final ProductData destBuffer, ProgressMonitor pm) throws IOException,
                                                                                            IllegalCeosFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 2;
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
                synchronized (_ceosReader) {
                    _ceosReader.seek(_imageRecordLength * y + xpos);
                    _ceosReader.readB2(srcLine);
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

    public void readBandRasterDataByte(final int sourceOffsetX, final int sourceOffsetY,
                                   final int sourceWidth, final int sourceHeight,
                                   final int sourceStepX, final int sourceStepY,
                                   final int destOffsetX, final int destOffsetY,
                                   final int destWidth, final int destHeight,
                                   final ProductData destBuffer, ProgressMonitor pm) throws IOException,
                                                                                            IllegalCeosFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 1;
        final long xpos = _startPosImageRecords +_imageHeaderLength + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final byte[] srcLine = new byte[sourceWidth];
            final byte[] destLine = new byte[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (_ceosReader) {
                    _ceosReader.seek(_imageRecordLength * y + xpos);
                    _ceosReader.readB1(srcLine);
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

    public void readBandRasterDataSLC(final int sourceOffsetX, final int sourceOffsetY,
                                   final int sourceWidth, final int sourceHeight,
                                   final int sourceStepX, final int sourceStepY,
                                   final int destOffsetX, final int destOffsetY,
                                   final int destWidth, final int destHeight,
                                   final ProductData destBuffer, boolean oneOf2,
                                   ProgressMonitor pm) throws IOException, IllegalCeosFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 4;
        final long xpos = _startPosImageRecords +_imageHeaderLength + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final short[] srcLine = new short[sourceWidth * 2];
            final short[] destLine = new short[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (_ceosReader) {
                    _ceosReader.seek(_imageRecordLength * y + xpos);
                    _ceosReader.readB2(srcLine);
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

    public void readBandRasterDataSLCFloat(final int sourceOffsetX, final int sourceOffsetY,
                                   final int sourceWidth, final int sourceHeight,
                                   final int sourceStepX, final int sourceStepY,
                                   final int destOffsetX, final int destOffsetY,
                                   final int destWidth, final int destHeight,
                                   final ProductData destBuffer, boolean oneOf2,
                                   ProgressMonitor pm) throws IOException, IllegalCeosFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 8;
        final long xpos = _startPosImageRecords +_imageHeaderLength + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final float[] srcLine = new float[sourceWidth * 2];
            final float[] destLine = new float[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (_ceosReader) {
                    _ceosReader.seek(_imageRecordLength * y + xpos);
                    _ceosReader.readF(srcLine);
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

    public void readBandRasterDataSLCByte(final int sourceOffsetX, final int sourceOffsetY,
                                   final int sourceWidth, final int sourceHeight,
                                   final int sourceStepX, final int sourceStepY,
                                   final int destOffsetX, final int destOffsetY,
                                   final int destWidth, final int destHeight,
                                   final ProductData destBuffer, boolean oneOf2,
                                   ProgressMonitor pm) throws IOException, IllegalCeosFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 2;
        final long xpos = _startPosImageRecords +_imageHeaderLength + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final byte[] srcLine = new byte[sourceWidth * 2];
            final byte[] destLine = new byte[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (_ceosReader) {
                    _ceosReader.seek(_imageRecordLength * y + xpos);
                    _ceosReader.readB1(srcLine);
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

    protected static void copyLine1Of2(final short[] srcLine, final short[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i << 1];
        }
    }

    protected static void copyLine1Of2(final byte[] srcLine, final byte[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i << 1];
        }
    }

    protected static void copyLine1Of2(final float[] srcLine, final float[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = (int)srcLine[i << 1];
        }
    }

    protected static void copyLine2Of2(final short[] srcLine, final short[] destLine,
                          final int sourceStepX) {
        final int length = destLine.length;
        for (int x = 0, i = 0; x < length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[(i << 1) + 1];
        }
    }

    protected static void copyLine2Of2(final byte[] srcLine, final byte[] destLine,
                          final int sourceStepX) {
        final int length = destLine.length;
        for (int x = 0, i = 0; x < length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[(i << 1) + 1];
        }
    }

    protected static void copyLine2Of2(final float[] srcLine, final float[] destLine,
                          final int sourceStepX) {
        final int length = destLine.length;
        for (int x = 0, i = 0; x < length; ++x, i += sourceStepX) {
            destLine[x] = (int)srcLine[(i << 1) + 1];
        }
    }

    public void close() throws IOException {
        _ceosReader.close();
        _ceosReader = null;
    }
}