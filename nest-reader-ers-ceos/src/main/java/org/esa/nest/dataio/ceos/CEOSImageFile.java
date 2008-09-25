package org.esa.nest.dataio.ceos;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.ceos.records.ImageRecord;
import org.esa.nest.dataio.ceos.records.BaseRecord;

import java.io.IOException;
import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;


/**
 * This class represents an image file of a CEOS product.
 *
 * @version $Revision: 1.7 $ $Date: 2008-09-25 03:35:41 $
 */
public abstract class CEOSImageFile {

    protected BaseRecord _imageFDR = null;
    protected CeosFileReader _ceosReader;
    protected ImageRecord[] _imageRecords = null;

    protected int _imageRecordLength = 0;
    protected long _startPosImageRecords = 0;

    public abstract String getBandName();

    public BaseRecord getImageFileDescriptor() {
        return _imageFDR;
    }

    public int getRasterWidth() {
        return _imageFDR.getAttributeInt("Number of pixels per line per SAR channel");
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
        File[] fileList = baseDir.listFiles();
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
        //ImageRecord imageRecord;

        int x = sourceOffsetX * 2;

        pm.beginTask("Reading band '" + getBandName() + "'...", sourceMaxY - sourceOffsetY);
        try {
            final short[] srcLine = new short[sourceWidth];
            final short[] destLine = new short[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                //imageRecord = getImageRecord(y);
                _ceosReader.seek(_imageRecordLength * y + _startPosImageRecords+12 + x);
                _ceosReader.readB2(srcLine);

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

        int x = sourceOffsetX * 2;

        pm.beginTask("Reading band '" + getBandName() + "'...", sourceMaxY - sourceOffsetY);
        try {
            final short[] srcLine = new short[sourceWidth * 2];
            final short[] destLine = new short[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                //ImageRecord imageRecord = getImageRecord(y);
                //_ceosReader.seek(imageRecord.getImageDataStart() + x);
                _ceosReader.seek(_imageRecordLength * y + _startPosImageRecords+12 + x);
                _ceosReader.readB2(srcLine);

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                copyLine(srcLine, destLine, sourceStepX);
                //if (oneOf2)
                //    copyLine1Of2(srcLine, destLine, sourceStepX);
                //else
                //    copyLine2Of2(srcLine, destLine, sourceStepX);

                System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }

    protected static void copyLine(final short[] srcLine, final short[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; x++, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    protected static void copyLine(final byte[] srcLine, final byte[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; x++, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    protected static void copyLine1Of2(final short[] srcLine, final short[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; x++, i += sourceStepX) {
            destLine[x] = srcLine[2 * i];
        }
    }

    protected static void copyLine1Of2(final byte[] srcLine, final byte[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; x++, i += sourceStepX) {
            destLine[x] = srcLine[2 * i];
        }
    }

    protected static void copyLine2Of2(final short[] srcLine, final short[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length-1; x++, i += sourceStepX) {
            destLine[x] = srcLine[2 * i + 1];
        }
    }

    protected static void copyLine2Of2(final byte[] srcLine, final byte[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length-1; x++, i += sourceStepX) {
            destLine[x] = srcLine[2 * i + 1];
        }
    }

    protected final ImageRecord getImageRecord(final int line) throws IOException,
                                                              IllegalCeosFormatException {
        if (_imageRecords[line] == null) {
            _ceosReader.seek(_imageRecordLength * line + _startPosImageRecords);
            _imageRecords[line] = new ImageRecord(_ceosReader, _ceosReader.getCurrentPos(), _imageRecords[0].getCeosDatabase());
        }
        return _imageRecords[line];
    }

    public void close() throws IOException {
        _ceosReader.close();
        _ceosReader = null;
    }
}