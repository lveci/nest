package org.esa.nest.dataio.ceos.ers;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.dataio.ceos.records.ImageRecord;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.MetadataElement;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;

/*
 * $Id: ERSImageFile.java,v 1.8 2008-06-27 19:35:52 lveci Exp $
 *
 * Copyright (C) 2002 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */


/**
 * This class represents an image file of an ERS product.
 *
 * @version $Revision: 1.8 $ $Date: 2008-06-27 19:35:52 $
 */
class ERSImageFile {

    private final BaseRecord _imageFDR;
    private final ImageRecord[] _imageRecords;
    private CeosFileReader _ceosReader;
    private int _imageRecordLength;
    private long _startPosImageRecords;

    private static String mission = "ers";
    private static String image_DefinitionFile = "image_file.xml";
    private static String image_recordDefinition = "image_record.xml";

    public ERSImageFile(final ImageInputStream imageStream) throws IOException,
                                                                      IllegalCeosFormatException {
        _ceosReader = new CeosFileReader(imageStream);
        _imageFDR = new BaseRecord(_ceosReader, -1, mission, image_DefinitionFile);
        _ceosReader.seek(_imageFDR.getAbsolutPosition(_imageFDR.getRecordLength()));
        _imageRecords = new ImageRecord[_imageFDR.getAttributeInt("Number of lines per data set")];
        _imageRecords[0] = new ImageRecord(_ceosReader, -1, mission, image_recordDefinition);

        _imageRecordLength = _imageRecords[0].getRecordLength();
        _startPosImageRecords = _imageRecords[0].getStartPos();
    }

    public String getBandName() {
        return ERSConstants.BANDNAME_PREFIX;
    }

    public String getBandDescription() {
        return "";
    }

    public int getRasterWidth() {
        return _imageFDR.getAttributeInt("Number of pixels per line per SAR channel");//getNumImagePixelsPerLine();
    }

    public int getRasterHeight() {
        return _imageFDR.getAttributeInt("Number of lines per data set");
    }

    public static String getGeophysicalUnit() {
        return ERSConstants.GEOPHYSICAL_UNIT;
    }

    public void assignMetadataTo(MetadataElement rootElem, int count) {
        MetadataElement metadata = new MetadataElement("Image Descriptor " + count);
         _imageFDR.assignMetadataTo(metadata);
        rootElem.addElement(metadata);
    }

    /*
    public int getTotalMillisInDayOfLine(final int y) throws IOException,
                                                             IllegalCeosFormatException {
        return getImageRecord(y).getScanStartTimeMillisAtDay();
    }

    public int getMicrosecondsOfLine(final int y) throws IOException,
                                                         IllegalCeosFormatException {
        return getImageRecord(y).getScanStartTimeMicros();
    }         */

    public void readBandRasterData(final int sourceOffsetX, final int sourceOffsetY,
                                   final int sourceWidth, final int sourceHeight,
                                   final int sourceStepX, final int sourceStepY,
                                   final int destOffsetX, final int destOffsetY,
                                   final int destWidth, final int destHeight,
                                   final ProductData destBuffer, ProgressMonitor pm) throws IOException,
                                                                                            IllegalCeosFormatException
    {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        ImageRecord imageRecord;

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
                imageRecord = getImageRecord(y);
                _ceosReader.seek(imageRecord.getImageDataStart() + x);
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
        ImageRecord imageRecord;

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
                imageRecord = getImageRecord(y);
                _ceosReader.seek(imageRecord.getImageDataStart() + x);
                _ceosReader.readB2(srcLine);

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

    private ImageRecord getImageRecord(final int line) throws IOException,
                                                              IllegalCeosFormatException {
        if (_imageRecords[line] == null) {
            _ceosReader.seek(_imageRecordLength * line + _startPosImageRecords);
            _imageRecords[line] = new ImageRecord(_ceosReader, _ceosReader.getCurrentPos(), _imageRecords[0].getCeosDatabase());
        }
        return _imageRecords[line];
    }
    
    private static void copyLine(final short[] srcLine, final short[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; x++, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    private static void copyLine1Of2(final short[] srcLine, final short[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; x++, i += sourceStepX) {
            destLine[x] = srcLine[2 * i];
        }
    }

    private static void copyLine2Of2(final short[] srcLine, final short[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length-1; x++, i += sourceStepX) {
            destLine[x] = srcLine[2 * i + 1];
        }
    }

    public void close() throws IOException {
        _ceosReader.close();
        _ceosReader = null;
    }
}
