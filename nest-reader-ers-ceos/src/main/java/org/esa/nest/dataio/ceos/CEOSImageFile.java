package org.esa.nest.dataio.ceos;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.ImageRecord;
import org.esa.nest.util.Constants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


/**
 * This class represents an image file of a CEOS product.
 *
 * @version $Revision: 1.28 $ $Date: 2010-02-22 17:15:23 $
 */
public abstract class CEOSImageFile {

    protected BaseRecord _imageFDR = null;
    protected BinaryFileReader binaryReader = null;
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

    public int getBitsPerSample() {
        return _imageFDR.getAttributeInt("Number of bits per sample");
    }

    protected abstract ImageRecord createNewImageRecord(final int line) throws IOException, IllegalBinaryFormatException;

    ImageRecord getImageRecord(int line) throws IOException, IllegalBinaryFormatException {
        if(_imageRecords[line] == null) {

            binaryReader.seek(_imageFDR.getAbsolutPosition(_imageFDR.getRecordLength()));
            _imageRecords[line] = createNewImageRecord(line);
        }
        return _imageRecords[line];
    }

    public int getSlantRangeToFirstPixel(int line) {
        try {
            final ImageRecord imgRec = getImageRecord(line);
            return imgRec.getAttributeInt("Slant range to 1st pixel");
        } catch(Exception e) {
            return 0;
        }
    }

    public int getSlantRangeToMidPixel(int line) {
        try {
            final ImageRecord imgRec = getImageRecord(line);
            return imgRec.getAttributeInt("Slant range to mid-pixel");
        } catch(Exception e) {
            return 0;
        }
    }

    public int getSlantRangeToLastPixel(int line) {
        try {
            final ImageRecord imgRec = getImageRecord(line);
            return imgRec.getAttributeInt("Slant range to last pixel");
        } catch(Exception e) {
            return 0;
        }
    }

    public float[] getLatCorners() throws IOException, IllegalBinaryFormatException {
        try {
            final ImageRecord imgRec0 = getImageRecord(0);
            final ImageRecord imgRecN = getImageRecord(_imageRecords.length-1);

            final float latUL = imgRec0.getAttributeInt("First pixel latitude") / (float)Constants.oneMillion;
            final float latUR = imgRec0.getAttributeInt("Last pixel latitude") / (float)Constants.oneMillion;
            final float latLL = imgRecN.getAttributeInt("First pixel latitude") / (float)Constants.oneMillion;
            final float latLR = imgRecN.getAttributeInt("Last pixel latitude") / (float)Constants.oneMillion;
            return new float[]{latUL, latUR, latLL, latLR};
        } catch(Throwable e) {
            return null;
        }
    }

    public float[] getLonCorners() throws IOException, IllegalBinaryFormatException {
        try {
            final ImageRecord imgRec0 = getImageRecord(0);
            final ImageRecord imgRecN = getImageRecord(_imageRecords.length-1);

            final float lonUL = imgRec0.getAttributeInt("First pixel longitude") / (float)Constants.oneMillion;
            final float lonUR = imgRec0.getAttributeInt("Last pixel longitude") / (float)Constants.oneMillion;
            final float lonLL = imgRecN.getAttributeInt("First pixel longitude") / (float)Constants.oneMillion;
            final float lonLR = imgRecN.getAttributeInt("Last pixel longitude") / (float)Constants.oneMillion;
            return new float[]{lonUL, lonUR, lonLL, lonLR};
        } catch(Throwable e) {
            return null;
        }
    }

    public void assignMetadataTo(MetadataElement rootElem, int count) {
        final MetadataElement imgDescElem = new MetadataElement("Image Descriptor " + count);
        _imageFDR.assignMetadataTo(imgDescElem);
        rootElem.addElement(imgDescElem);

        if(_imageRecords[0] != null) {
            final MetadataElement imgRecElem = new MetadataElement("Image Record ");
            _imageRecords[0].assignMetadataTo(imgRecElem);
            imgDescElem.addElement(imgRecElem);
        }
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

    public void readBandRasterDataShort(final int sourceOffsetX, final int sourceOffsetY,
                                        final int sourceWidth, final int sourceHeight,
                                        final int sourceStepX, final int sourceStepY,
                                        final int destWidth, final ProductData destBuffer, ProgressMonitor pm)
                                        throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * ProductData.getElemSize(destBuffer.getType());
        final long xpos = _startPosImageRecords +_imageHeaderLength + x;

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
                    binaryReader.seek(_imageRecordLength * y + xpos);
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

    public void readBandRasterDataInt(final int sourceOffsetX, final int sourceOffsetY,
                                             final int sourceWidth, final int sourceHeight,
                                             final int sourceStepX, final int sourceStepY,
                                             final int destWidth, final ProductData destBuffer,
                                             final ProgressMonitor pm)
                                             throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * ProductData.getElemSize(destBuffer.getType());
        final long xpos = _startPosImageRecords +_imageHeaderLength + x;

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
                    binaryReader.seek(_imageRecordLength * y + xpos);
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

    public void readBandRasterDataFloat(final int sourceOffsetX, final int sourceOffsetY,
                                             final int sourceWidth, final int sourceHeight,
                                             final int sourceStepX, final int sourceStepY,
                                             final int destWidth, final ProductData destBuffer,
                                             final ProgressMonitor pm)
                                             throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * ProductData.getElemSize(destBuffer.getType());
        final long xpos = _startPosImageRecords +_imageHeaderLength + x;

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
                    binaryReader.seek(_imageRecordLength * y + xpos);
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

    public void readBandRasterDataByte(final int sourceOffsetX, final int sourceOffsetY,
                                       final int sourceWidth, final int sourceHeight,
                                       final int sourceStepX, final int sourceStepY,
                                       final int destWidth, final ProductData destBuffer, ProgressMonitor pm)
                                        throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * ProductData.getElemSize(destBuffer.getType());
        final long xpos = _startPosImageRecords +_imageHeaderLength + x;

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
                    binaryReader.seek(_imageRecordLength * y + xpos);
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

    public void readBandRasterDataSLC(final int sourceOffsetX, final int sourceOffsetY,
                                      final int sourceWidth, final int sourceHeight,
                                      final int sourceStepX, final int sourceStepY,
                                      final int destWidth, final ProductData destBuffer, boolean oneOf2,
                                      ProgressMonitor pm) throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 4;
        final long xpos = _startPosImageRecords +_imageHeaderLength + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            //short[] srcLine;
            final short[] srcLine = new short[sourceWidth * 2];
            final short[] destLine = new short[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                /*
                synchronized (binaryReader) {
                    Integer cacheIndex = indexMap.get(y);
                    if(cacheIndex == null) {
                            if (cachePos >= cacheSize) {
                                cachePos = 0;
                            }

                            srcLine = new short[sourceWidth * 2];
                            binaryReader.seek(_imageRecordLength * y + xpos);
                            binaryReader.read(srcLine);

                            if(linesCache[cachePos] != null) {
                                indexMap.put(indexList[cachePos], null);
                            }
                            linesCache[cachePos] = srcLine;
                            indexList[cachePos] = y;
                            indexMap.put(y, cachePos);
                            ++cachePos;
                    } else {
                        srcLine = (short[]) linesCache[cacheIndex];
                    }
                }   */

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(_imageRecordLength * y + xpos);
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

    public void readBandRasterDataSLCFloat(final int sourceOffsetX, final int sourceOffsetY,
                                           final int sourceWidth, final int sourceHeight,
                                           final int sourceStepX, final int sourceStepY,
                                           final int destWidth, final ProductData destBuffer, boolean oneOf2,
                                           ProgressMonitor pm) throws IOException {
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
                synchronized (binaryReader) {
                    binaryReader.seek(_imageRecordLength * y + xpos);
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

    public void readBandRasterDataSLCByte(final int sourceOffsetX, final int sourceOffsetY,
                                          final int sourceWidth, final int sourceHeight,
                                          final int sourceStepX, final int sourceStepY,
                                          final int destWidth, final ProductData destBuffer, boolean oneOf2,
                                          ProgressMonitor pm) throws IOException {
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
                synchronized (binaryReader) {
                    binaryReader.seek(_imageRecordLength * y + xpos);
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

    private static void copyLine(final short[] srcLine, final short[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    private static void copyLine(final byte[] srcLine, final byte[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    private static void copyLine(final int[] srcLine, final int[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    private static void copyLine(final float[] srcLine, final float[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    private static void copyLine1Of2(final short[] srcLine, final short[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i << 1];
        }
    }

    private static void copyLine1Of2(final byte[] srcLine, final byte[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[i << 1];
        }
    }

    private static void copyLine1Of2(final float[] srcLine, final float[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; ++x, i += sourceStepX) {
            destLine[x] = (int)srcLine[i << 1];
        }
    }

    private static void copyLine2Of2(final short[] srcLine, final short[] destLine,
                          final int sourceStepX) {
        final int length = destLine.length;
        for (int x = 0, i = 0; x < length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[(i << 1) + 1];
        }
    }

    private static void copyLine2Of2(final byte[] srcLine, final byte[] destLine,
                          final int sourceStepX) {
        final int length = destLine.length;
        for (int x = 0, i = 0; x < length; ++x, i += sourceStepX) {
            destLine[x] = srcLine[(i << 1) + 1];
        }
    }

    private static void copyLine2Of2(final float[] srcLine, final float[] destLine,
                          final int sourceStepX) {
        final int length = destLine.length;
        for (int x = 0, i = 0; x < length; ++x, i += sourceStepX) {
            destLine[x] = (int)srcLine[(i << 1) + 1];
        }
    }

    public void close() throws IOException {
        binaryReader.close();
        binaryReader = null;
    }
}