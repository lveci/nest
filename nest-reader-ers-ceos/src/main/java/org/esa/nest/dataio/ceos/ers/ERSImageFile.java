package org.esa.nest.dataio.ceos.ers;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.dataio.ceos.ers.records.ERSImageFDR;
import org.esa.nest.dataio.ceos.records.ImageRecord;
import org.esa.beam.framework.datamodel.ProductData;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;

/*
 * $Id: ERSImageFile.java,v 1.3 2008-01-30 14:47:10 lveci Exp $
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
 * This class represents an image file of an Avnir-2 product.
 *
 * @author Marco Peters
 * @version $Revision: 1.3 $ $Date: 2008-01-30 14:47:10 $
 */
class ERSImageFile {

    public final ERSImageFDR _imageFDR;
    public final ImageRecord[] _imageRecords;
    private CeosFileReader _ceosReader;
    private final int _imageNumber;
    private int _imageRecordLength;
    private long _startPosImageRecords;

    public ERSImageFile(final ImageInputStream imageStream) throws IOException,
                                                                      IllegalCeosFormatException {
        _ceosReader = new CeosFileReader(imageStream);
        _imageFDR = new ERSImageFDR(_ceosReader);
        _imageRecords = new ImageRecord[_imageFDR.getNumLinesPerBand()];
        _imageRecords[0] = new ImageRecord(_ceosReader);
        _imageRecordLength = _imageRecords[0].getRecordLength();
        _startPosImageRecords = _imageRecords[0].getStartPos();
        _imageNumber = _imageRecords[0].getImageNumber();
    }

    public String getBandName() throws IOException,
                                       IllegalCeosFormatException {
        return ERSConstants.BANDNAME_PREFIX + getBandIndex();
    }

    public String getBandDescription() throws IOException,
                                              IllegalCeosFormatException {
        return String.format(ERSConstants.BAND_DESCRIPTION_FORMAT_STRING, new Object[]{getBandIndex()});
    }

    public int getBandIndex() throws IOException,
                                     IllegalCeosFormatException {
        return _imageNumber;
    }

    public int getRasterWidth() throws IOException,
                                       IllegalCeosFormatException {
        return _imageFDR.getNumImagePixelsPerLine();
    }

    public int getRasterHeight() throws IOException,
                                        IllegalCeosFormatException {
        return _imageFDR.getNumLinesPerBand();
    }

    public String getGeophysicalUnit() {
        return ERSConstants.GEOPHYSICAL_UNIT;
    }

    public float getSpectralWavelength() throws IOException,
                                                IllegalCeosFormatException {
        final int bandIndex = getBandIndex();

        switch (bandIndex) {
        case 1:
            return ERSConstants.WAVELENGTH_BAND_1;
        case 2:
            return ERSConstants.WAVELENGTH_BAND_2;
        case 3:
            return ERSConstants.WAVELENGTH_BAND_3;
        case 4:
            return ERSConstants.WAVELENGTH_BAND_4;
        default:
            return 0;
        }
    }

    public float getSpectralBandwidth() throws IOException,
                                               IllegalCeosFormatException {
        final int bandIndex = getBandIndex();

        switch (bandIndex) {
        case 1:
            return ERSConstants.BANDWIDTH_BAND_1;
        case 2:
            return ERSConstants.BANDWIDTH_BAND_2;
        case 3:
            return ERSConstants.BANDWIDTH_BAND_3;
        case 4:
            return ERSConstants.BANDWIDTH_BAND_4;
        default:
            return 0;
        }
    }

    public int getTotalMillisInDayOfLine(final int y) throws IOException,
                                                             IllegalCeosFormatException {
        return getImageRecord(y).getScanStartTimeMillisAtDay();
    }

    public int getMicrosecondsOfLine(final int y) throws IOException,
                                                         IllegalCeosFormatException {
        return getImageRecord(y).getScanStartTimeMicros();
    }


    public void readBandRasterData(final int sourceOffsetX, final int sourceOffsetY,
                                   final int sourceWidth, final int sourceHeight,
                                   final int sourceStepX, final int sourceStepY,
                                   final int destOffsetX, final int destOffsetY,
                                   final int destWidth, final int destHeight,
                                   final ProductData destBuffer, ProgressMonitor pm) throws IOException,
                                                                                            IllegalCeosFormatException {

        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        ImageRecord imageRecord;

        System.out.print("readBandRasterData x " + sourceOffsetX + " y " + sourceOffsetY +
                " w " + sourceWidth + " h " + sourceHeight + 
                " stepX " + sourceStepX + " stepY " + sourceStepY + "\n");

        pm.beginTask("Reading band '" + getBandName() + "'...", sourceMaxY - sourceOffsetY);
        try {
            final int[] srcLine = new int[sourceWidth];
            final int[] destLine = new int[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                imageRecord = getImageRecord(y);
                _ceosReader.seek(imageRecord.getImageDataStart() + sourceOffsetX);
                _ceosReader.readB4(srcLine);

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

    private ImageRecord getImageRecord(final int line) throws IOException,
                                                              IllegalCeosFormatException {
        if (_imageRecords[line] == null) {
            _ceosReader.seek(_imageRecordLength * line + _startPosImageRecords);
            _imageRecords[line] = new ImageRecord(_ceosReader);
        }
        return _imageRecords[line];
    }

    private void copyLine(final int[] srcLine, final int[] destLine,
                          final int sourceStepX) {
        for (int x = 0, i = 0; x < destLine.length; x++, i += sourceStepX) {
            destLine[x] = srcLine[i];
        }
    }

    public void close() throws IOException {
        _ceosReader.close();
        _ceosReader = null;
    }
}
