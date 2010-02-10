package org.esa.beam.framework.datamodel;

import javax.media.jai.PixelAccessor;
import javax.media.jai.UnpackedImageData;
import java.awt.image.Raster;
import java.awt.image.DataBuffer;
import java.awt.Rectangle;

/**
*
* @author Norman Fomferra
* @author Marco Peters
* @version $Revision: 1.2 $ $Date: 2010-02-10 15:21:58 $
* @since BEAM 4.5.1
*/
class ExtremaStxOp implements StxOp {
    private double lowValue;
    private double highValue;
    private double valueSum;
    private long numValues;
    private double sqrSum;
    private double power4Sum;

    ExtremaStxOp() {
        this.lowValue = Double.MAX_VALUE;
        this.highValue = -Double.MAX_VALUE;
        this.valueSum = 0;
        this.numValues = 0;
        this.sqrSum = 0;
        this.power4Sum = 0;
    }

    public String getName() {
        return "Extrema";
    }

    public double getLowValue() {
        return lowValue;
    }

    public double getHighValue() {
        return highValue;
    }

    public double getMean() {
        return valueSum / numValues;
    }

    public long getNumValues() {
        return numValues;
    }

    public double getCoefficientOfVariation() {
        final double m4 = power4Sum / numValues;
        final double m2 = sqrSum / numValues;
        return Math.sqrt(m4 - m2*m2) / m2;
    }

    public double getEquivalentNumberOfLooks() {
        final double m4 = power4Sum / numValues;
        final double m2 = sqrSum / numValues;
        final double m2m2 = m2*m2;
        return m2m2 / (m4 - m2m2);
    }

    public void accumulateDataUByte(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor, Raster maskTile, Rectangle r) {
        double tmpLowValue = this.lowValue;
        double tmpHighValue = this.highValue;
        long tmpNumValues = this.numValues;
        double tmpValueSum = this.valueSum;
        double tmpSqrSum = this.sqrSum;
        double tmpPower4Sum = this.power4Sum;

        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_BYTE, false);
        final byte[] data = duid.getByteData(0);
        final int dataPixelStride = duid.pixelStride;
        final int dataLineStride = duid.lineStride;
        final int dataBandOffset = duid.bandOffsets[0];

        byte[] mask = null;
        int maskPixelStride = 0;
        int maskLineStride = 0;
        int maskBandOffset = 0;
        if (maskAccessor != null) {
            UnpackedImageData muid = maskAccessor.getPixels(maskTile, r, DataBuffer.TYPE_BYTE, false);
            mask = muid.getByteData(0);
            maskPixelStride = muid.pixelStride;
            maskLineStride = muid.lineStride;
            maskBandOffset = muid.bandOffsets[0];
        }

        final int width = r.width;
        final int height = r.height;

        int dataLineOffset = dataBandOffset;
        int maskLineOffset = maskBandOffset;
        for (int y = 0; y < height; y++) {
            int dataPixelOffset = dataLineOffset;
            int maskPixelOffset = maskLineOffset;
            for (int x = 0; x < width; x++) {
                if (mask == null || mask[maskPixelOffset] != 0) {
                    final double d = data[dataPixelOffset] & 0xff;
                    if (d < tmpLowValue) {
                        tmpLowValue = d;
                    } else if (d > tmpHighValue) {
                        tmpHighValue = d;
                    }
                    tmpNumValues++;
                    tmpValueSum += d;
                    final double d2 = d * d;
                    tmpSqrSum += d2;
                    tmpPower4Sum += d2*d2;
                }
                dataPixelOffset += dataPixelStride;
                maskPixelOffset += maskPixelStride;
            }
            dataLineOffset += dataLineStride;
            maskLineOffset += maskLineStride;
        }
        this.lowValue = tmpLowValue;
        this.highValue = tmpHighValue;
        this.numValues = tmpNumValues;
        this.valueSum = tmpValueSum;
        this.sqrSum = tmpSqrSum;
        this.power4Sum = tmpPower4Sum;
    }

    public void accumulateDataUShort(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor, Raster maskTile, Rectangle r) {
        double tmpLowValue = this.lowValue;
        double tmpHighValue = this.highValue;
        long tmpNumValues = this.numValues;
        double tmpValueSum = this.valueSum;
        double tmpSqrSum = this.sqrSum;
        double tmpPower4Sum = this.power4Sum;

        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_USHORT, false);
        final short[] data = duid.getShortData(0);
        final int dataPixelStride = duid.pixelStride;
        final int dataLineStride = duid.lineStride;
        final int dataBandOffset = duid.bandOffsets[0];

        byte[] mask = null;
        int maskPixelStride = 0;
        int maskLineStride = 0;
        int maskBandOffset = 0;
        if (maskAccessor != null) {
            UnpackedImageData muid = maskAccessor.getPixels(maskTile, r, DataBuffer.TYPE_BYTE, false);
            mask = muid.getByteData(0);
            maskPixelStride = muid.pixelStride;
            maskLineStride = muid.lineStride;
            maskBandOffset = muid.bandOffsets[0];
        }

        final int width = r.width;
        final int height = r.height;

        int dataLineOffset = dataBandOffset;
        int maskLineOffset = maskBandOffset;
        for (int y = 0; y < height; y++) {
            int dataPixelOffset = dataLineOffset;
            int maskPixelOffset = maskLineOffset;
            for (int x = 0; x < width; x++) {
                if (mask == null || mask[maskPixelOffset] != 0) {
                    final double d = data[dataPixelOffset] & 0xffff;
                    if (d < tmpLowValue) {
                        tmpLowValue = d;
                    } else if (d > tmpHighValue) {
                        tmpHighValue = d;
                    }
                    tmpNumValues++;
                    tmpValueSum += d;
                    final double d2 = d * d;
                    tmpSqrSum += d2;
                    tmpPower4Sum += d2*d2;
                }
                dataPixelOffset += dataPixelStride;
                maskPixelOffset += maskPixelStride;
            }
            dataLineOffset += dataLineStride;
            maskLineOffset += maskLineStride;
        }

        this.lowValue = tmpLowValue;
        this.highValue = tmpHighValue;
        this.numValues = tmpNumValues;
        this.valueSum = tmpValueSum;
        this.sqrSum = tmpSqrSum;
        this.power4Sum = tmpPower4Sum;
    }

    public void accumulateDataShort(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor, Raster maskTile, Rectangle r) {
        double tmpLowValue = this.lowValue;
        double tmpHighValue = this.highValue;
        long tmpNumValues = this.numValues;
        double tmpValueSum = this.valueSum;
        double tmpSqrSum = this.sqrSum;
        double tmpPower4Sum = this.power4Sum;

        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_SHORT, false);
        final short[] data = duid.getShortData(0);
        final int dataPixelStride = duid.pixelStride;
        final int dataLineStride = duid.lineStride;
        final int dataBandOffset = duid.bandOffsets[0];

        byte[] mask = null;
        int maskPixelStride = 0;
        int maskLineStride = 0;
        int maskBandOffset = 0;
        if (maskAccessor != null) {
            UnpackedImageData muid = maskAccessor.getPixels(maskTile, r, DataBuffer.TYPE_BYTE, false);
            mask = muid.getByteData(0);
            maskPixelStride = muid.pixelStride;
            maskLineStride = muid.lineStride;
            maskBandOffset = muid.bandOffsets[0];
        }

        final int width = r.width;
        final int height = r.height;

        int dataLineOffset = dataBandOffset;
        int maskLineOffset = maskBandOffset;
        for (int y = 0; y < height; y++) {
            int dataPixelOffset = dataLineOffset;
            int maskPixelOffset = maskLineOffset;
            for (int x = 0; x < width; x++) {
                if (mask == null || mask[maskPixelOffset] != 0) {
                    final double d = data[dataPixelOffset];
                    if (d < tmpLowValue) {
                        tmpLowValue = d;
                    } else if (d > tmpHighValue) {
                        tmpHighValue = d;
                    }
                    tmpNumValues++;
                    tmpValueSum += d;
                    final double d2 = d * d;
                    tmpSqrSum += d2;
                    tmpPower4Sum += d2*d2;
                }
                dataPixelOffset += dataPixelStride;
                maskPixelOffset += maskPixelStride;
            }
            dataLineOffset += dataLineStride;
            maskLineOffset += maskLineStride;
        }

        this.lowValue = tmpLowValue;
        this.highValue = tmpHighValue;
        this.valueSum = tmpValueSum;
        this.numValues = tmpNumValues;
        this.sqrSum = tmpSqrSum;
        this.power4Sum = tmpPower4Sum;
    }

    public void accumulateDataInt(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor, Raster maskTile, Rectangle r) {
        double tmpLowValue = this.lowValue;
        double tmpHighValue = this.highValue;
        long tmpNumValues = this.numValues;
        double tmpValueSum = this.valueSum;
        double tmpSqrSum = this.sqrSum;
        double tmpPower4Sum = this.power4Sum;

        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_INT, false);
        final int[] data = duid.getIntData(0);
        final int dataPixelStride = duid.pixelStride;
        final int dataLineStride = duid.lineStride;
        final int dataBandOffset = duid.bandOffsets[0];

        byte[] mask = null;
        int maskPixelStride = 0;
        int maskLineStride = 0;
        int maskBandOffset = 0;
        if (maskAccessor != null) {
            UnpackedImageData muid = maskAccessor.getPixels(maskTile, r, DataBuffer.TYPE_BYTE, false);
            mask = muid.getByteData(0);
            maskPixelStride = muid.pixelStride;
            maskLineStride = muid.lineStride;
            maskBandOffset = muid.bandOffsets[0];
        }

        final int width = r.width;
        final int height = r.height;

        int dataLineOffset = dataBandOffset;
        int maskLineOffset = maskBandOffset;
        for (int y = 0; y < height; y++) {
            int dataPixelOffset = dataLineOffset;
            int maskPixelOffset = maskLineOffset;
            for (int x = 0; x < width; x++) {
                if (mask == null || mask[maskPixelOffset] != 0) {
                    final double d = data[dataPixelOffset];
                    if (d < tmpLowValue) {
                        tmpLowValue = d;
                    } else if (d > tmpHighValue) {
                        tmpHighValue = d;
                    }
                    tmpNumValues++;
                    tmpValueSum += d;
                    final double d2 = d * d;
                    tmpSqrSum += d2;
                    tmpPower4Sum += d2*d2;
                }
                dataPixelOffset += dataPixelStride;
                maskPixelOffset += maskPixelStride;
            }
            dataLineOffset += dataLineStride;
            maskLineOffset += maskLineStride;
        }

        this.lowValue = tmpLowValue;
        this.highValue = tmpHighValue;
        this.numValues = tmpNumValues;
        this.valueSum = tmpValueSum;
        this.sqrSum = tmpSqrSum;
        this.power4Sum = tmpPower4Sum;
    }

    public void accumulateDataFloat(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor, Raster maskTile, Rectangle r) {
        double tmpLowValue = this.lowValue;
        double tmpHighValue = this.highValue;
        long tmpNumValues = this.numValues;
        double tmpValueSum = this.valueSum;
        double tmpSqrSum = this.sqrSum;
        double tmpPower4Sum = this.power4Sum;

        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_FLOAT, false);
        final float[] data = duid.getFloatData(0);
        final int dataPixelStride = duid.pixelStride;
        final int dataLineStride = duid.lineStride;
        final int dataBandOffset = duid.bandOffsets[0];

        byte[] mask = null;
        int maskPixelStride = 0;
        int maskLineStride = 0;
        int maskBandOffset = 0;
        if (maskAccessor != null) {
            UnpackedImageData muid = maskAccessor.getPixels(maskTile, r, DataBuffer.TYPE_BYTE, false);
            mask = muid.getByteData(0);
            maskPixelStride = muid.pixelStride;
            maskLineStride = muid.lineStride;
            maskBandOffset = muid.bandOffsets[0];
        }

        final int width = r.width;
        final int height = r.height;

        int dataLineOffset = dataBandOffset;
        int maskLineOffset = maskBandOffset;
        for (int y = 0; y < height; y++) {
            int dataPixelOffset = dataLineOffset;
            int maskPixelOffset = maskLineOffset;
            for (int x = 0; x < width; x++) {
                if (mask == null || mask[maskPixelOffset] != 0) {
                    final double d = data[dataPixelOffset];
                    if (d < tmpLowValue) {
                        tmpLowValue = d;
                    } else if (d > tmpHighValue) {
                        tmpHighValue = d;
                    }
                    tmpNumValues++;
                    tmpValueSum += d;
                    final double d2 = d * d;
                    tmpSqrSum += d2;
                    tmpPower4Sum += d2*d2;
                }
                dataPixelOffset += dataPixelStride;
                maskPixelOffset += maskPixelStride;
            }
            dataLineOffset += dataLineStride;
            maskLineOffset += maskLineStride;
        }

        this.lowValue = tmpLowValue;
        this.highValue = tmpHighValue;
        this.numValues = tmpNumValues;
        this.valueSum = tmpValueSum;
        this.sqrSum = tmpSqrSum;
        this.power4Sum = tmpPower4Sum;
    }

    public void accumulateDataDouble(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor, Raster maskTile, Rectangle r) {
        double tmpLowValue = this.lowValue;
        double tmpHighValue = this.highValue;
        long tmpNumValues = this.numValues;
        double tmpValueSum = this.valueSum;
        double tmpSqrSum = this.sqrSum;
        double tmpPower4Sum = this.power4Sum;

        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_DOUBLE, false);
        final double[] data = duid.getDoubleData(0);
        final int dataPixelStride = duid.pixelStride;
        final int dataLineStride = duid.lineStride;
        final int dataBandOffset = duid.bandOffsets[0];

        byte[] mask = null;
        int maskPixelStride = 0;
        int maskLineStride = 0;
        int maskBandOffset = 0;
        if (maskAccessor != null) {
            UnpackedImageData muid = maskAccessor.getPixels(maskTile, r, DataBuffer.TYPE_BYTE, false);
            mask = muid.getByteData(0);
            maskPixelStride = muid.pixelStride;
            maskLineStride = muid.lineStride;
            maskBandOffset = muid.bandOffsets[0];
        }

        final int width = r.width;
        final int height = r.height;

        int dataLineOffset = dataBandOffset;
        int maskLineOffset = maskBandOffset;
        for (int y = 0; y < height; y++) {
            int dataPixelOffset = dataLineOffset;
            int maskPixelOffset = maskLineOffset;
            for (int x = 0; x < width; x++) {
                if (mask == null || mask[maskPixelOffset] != 0) {
                    final double d = data[dataPixelOffset];
                    if (d < tmpLowValue) {
                        tmpLowValue = d;
                    } else if (d > tmpHighValue) {
                        tmpHighValue = d;
                    }
                    tmpNumValues++;
                    tmpValueSum += d;
                    final double d2 = d * d;
                    tmpSqrSum += d2;
                    tmpPower4Sum += d2*d2;
                }
                dataPixelOffset += dataPixelStride;
                maskPixelOffset += maskPixelStride;
            }
            dataLineOffset += dataLineStride;
            maskLineOffset += maskLineStride;
        }

        this.lowValue = tmpLowValue;
        this.highValue = tmpHighValue;
        this.numValues = tmpNumValues;
        this.valueSum = tmpValueSum;
        this.sqrSum = tmpSqrSum;
        this.power4Sum = tmpPower4Sum;
    }

}
