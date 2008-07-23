package org.esa.nest.dataio.ceos;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.dataio.ceos.records.ImageRecord;

import java.io.IOException;

/*
 * $Id: CEOSImageFile.java,v 1.1 2008-07-23 19:47:17 lveci Exp $
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
 * @version $Revision: 1.1 $ $Date: 2008-07-23 19:47:17 $
 */
public abstract class CEOSImageFile {

    protected CeosFileReader _ceosReader;
    protected ImageRecord[] _imageRecords = null;

    protected int _imageRecordLength = 0;
    protected long _startPosImageRecords = 0;

    public abstract String getBandName();

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