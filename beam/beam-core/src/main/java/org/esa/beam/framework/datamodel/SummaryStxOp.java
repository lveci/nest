/*
 * Copyright (C) 2011 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.framework.datamodel;

import javax.media.jai.PixelAccessor;
import javax.media.jai.UnpackedImageData;
import java.awt.Rectangle;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;

/**
 * Utility class for calculating minimum, maximum, mean and standard deviation. Uses
 * a one-pass algorithm for computing mean and variance.
 *
 * @author Norman Fomferra
 * @author Marco Peters
 * @author Ralf Quast

 * @since BEAM 4.5.1
 */
class SummaryStxOp implements StxOp {

    private double minimum;
    private double maximum;
    private double mean;
    private double m2;
    private long sampleCount;
    private boolean hasValidValues;
    private double valueSum;
    private double sqrSum;
    private double power4Sum;
    private String unit;
    SummaryStxOp() {
        this.minimum = Double.POSITIVE_INFINITY;
        this.maximum = Double.NEGATIVE_INFINITY;
        this.valueSum = 0;
        this.sampleCount = 0;
        this.sqrSum = 0;
        this.power4Sum = 0;
        this.unit = "";    }

    @Override
    public String getName() {
        return "Summary";
    }

    final double getMinimum() {
        return hasValidValues ? minimum : Double.NaN;
    }

    final double getMaximum() {
        return hasValidValues ? maximum : Double.NaN;
    }

    final double getMean() {
        return hasValidValues ? mean : Double.NaN;
    }

    final double getStdDev() {
        return hasValidValues ? Math.sqrt(getVariance()) : Double.NaN;
    }

    final double getVariance() {
        return m2 / (sampleCount - 1);
    }

    public double getCoefficientOfVariation() {
        double cv = 0.0;
        if (unit != null && unit.contains("intensity")) {
            final double m = valueSum / sampleCount;
            final double m2 = sqrSum / sampleCount;
            cv = Math.sqrt(m2 - m*m) / m;
        } else {
            final double m4 = power4Sum / sampleCount;
            final double m2 = sqrSum / sampleCount;
            cv = Math.sqrt(m4 - m2*m2) / m2;
        }
        return cv;
    }

    public double getEquivalentNumberOfLooks() {
        double enl = 0.0;
        if (unit != null && unit.contains("intensity")) {
            final double m = valueSum / sampleCount;
            final double m2 = sqrSum / sampleCount;
            final double mm = m*m;
            enl = mm / (m2 - mm);
        } else {
            final double m4 = power4Sum / sampleCount;
            final double m2 = sqrSum / sampleCount;
            final double m2m2 = m2*m2;
            enl = m2m2 / (m4 - m2m2);
        }
        return enl;
    }

    @Override
    public void accumulateDataUByte(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor,
                                    Raster maskTile, Rectangle r, String unit) {
        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_BYTE, false);
        ValueConverter valueConverter = new UByteValueConverter(duid.getByteData(0));
        accumulateData(duid, valueConverter, maskAccessor, maskTile, r, unit);

    }

    @Override
    public void accumulateDataByte(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor,
                                   Raster maskTile, Rectangle r, String unit) {
        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_BYTE, false);
        ValueConverter valueConverter = new ByteValueConverter(duid.getByteData(0));
        accumulateData(duid, valueConverter, maskAccessor, maskTile, r, unit);
    }

    @Override
    public void accumulateDataUShort(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor,
                                     Raster maskTile, Rectangle r, String unit) {
        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_USHORT, false);
        ValueConverter valueConverter = new UShortValueConverter(duid.getShortData(0));
        accumulateData(duid, valueConverter, maskAccessor, maskTile, r, unit);
    }

    @Override
    public void accumulateDataShort(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor,
                                    Raster maskTile, Rectangle r, String unit) {
        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_SHORT, false);
        ValueConverter valueConverter = new ShortValueConverter(duid.getShortData(0));
        accumulateData(duid, valueConverter, maskAccessor, maskTile, r, unit);

    }

    @Override
    public void accumulateDataInt(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor,
                                  Raster maskTile, Rectangle r, String unit) {
        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_INT, false);
        ValueConverter valueConverter = new IntValueConverter(duid.getIntData(0));
        accumulateData(duid, valueConverter, maskAccessor, maskTile, r, unit);
    }

    @Override
    public void accumulateDataUInt(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor,
                                   Raster maskTile, Rectangle r, String unit) {
        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_INT, false);
        ValueConverter valueConverter = new UIntValueConverter(duid.getIntData(0));
        accumulateData(duid, valueConverter, maskAccessor, maskTile, r, unit);
    }

    @Override
    public void accumulateDataFloat(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor,
                                    Raster maskTile, Rectangle r, String unit) {
        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_FLOAT, false);
        ValueConverter valueConverter = new FloatValueConverter(duid.getFloatData(0));
        accumulateData(duid, valueConverter, maskAccessor, maskTile, r, unit);
    }

    @Override
    public void accumulateDataDouble(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor,
                                     Raster maskTile, Rectangle r, String unit) {
        final UnpackedImageData duid = dataAccessor.getPixels(dataTile, r, DataBuffer.TYPE_DOUBLE, false);
        ValueConverter valueConverter = new DoubleValueConverter(duid.getDoubleData(0));
        accumulateData(duid, valueConverter, maskAccessor, maskTile, r, unit);
    }

    private void accumulateData(UnpackedImageData duid, ValueConverter valueConverter, PixelAccessor maskAccessor,
                                Raster maskTile, Rectangle r, String unit) {
        final int dataPixelStride = duid.pixelStride;
        final int dataLineStride = duid.lineStride;
        final int dataBandOffset = duid.bandOffsets[0];

        double tileMinimum = this.minimum;
        double tileMaximum = this.maximum;
        long tileSampleCount = this.sampleCount;
        double tileMean = this.mean;
        double tileM2 = this.m2;

        double tmpValueSum = this.valueSum;
        double tmpSqrSum = this.sqrSum;
        double tmpPower4Sum = this.power4Sum;
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
                    hasValidValues = true;
                    final double d = valueConverter.getValue(dataPixelOffset);
                    if (d < tileMinimum) {
                        tileMinimum = d;
                    }
                    if (d > tileMaximum) {
                        tileMaximum = d;
                    }
                    tileSampleCount++;
                    final double delta = d - tileMean;
                    if (delta != 0.0) { // if delta is zero, tileMean would become NaN
                        tileMean += delta / tileSampleCount;
                    }
                    tileM2 += delta * (d - tileMean);

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

        this.minimum = tileMinimum;
        this.maximum = tileMaximum;
        this.sampleCount = tileSampleCount;
        this.mean = tileMean;
        this.m2 = tileM2;

        this.valueSum = tmpValueSum;
        this.sqrSum = tmpSqrSum;
        this.power4Sum = tmpPower4Sum;
        this.unit = unit;
    }


    private interface ValueConverter {

        double getValue(int index);
    }

    private static class ByteValueConverter implements ValueConverter {

        private byte[] data;

        ByteValueConverter(byte[] data) {
            this.data = data.clone();
        }

//        @Override
        public double getValue(int index) {
            return data[index];
        }
    }

    private static class UByteValueConverter implements ValueConverter {

        private byte[] data;

        UByteValueConverter(byte[] data) {
            this.data = data.clone();
        }

//        @Override
        public double getValue(int index) {
            return data[index] & 0xff;
        }
    }

    private static class ShortValueConverter implements ValueConverter {

        private short[] data;

        ShortValueConverter(short[] data) {
            this.data = data.clone();
        }

//        @Override
        public double getValue(int index) {
            return data[index];
        }
    }

    private static class UShortValueConverter implements ValueConverter {

        private short[] data;

        UShortValueConverter(short[] data) {
            this.data = data.clone();
        }

//        @Override
        public double getValue(int index) {
            return data[index] & 0xffff;
        }
    }

    private static class IntValueConverter implements ValueConverter {

        private int[] data;

        IntValueConverter(int[] data) {
            this.data = data.clone();
        }

//        @Override
        public double getValue(int index) {
            return data[index];
        }
    }

    private static class UIntValueConverter implements ValueConverter {

        private int[] data;

        UIntValueConverter(int[] data) {
            this.data = data.clone();
        }

//        @Override
        public double getValue(int index) {
            return data[index] & 0xffffffffL;
        }
    }

    private static class FloatValueConverter implements ValueConverter {

        private float[] data;

        FloatValueConverter(float[] data) {
            this.data = data.clone();
        }

//        @Override
        public double getValue(int index) {
            return data[index];
        }
    }

    private static class DoubleValueConverter implements ValueConverter {

        private double[] data;

        DoubleValueConverter(double[] data) {
            this.data = data.clone();
        }

//        @Override
        public double getValue(int index) {
            return data[index];
        }
    }
}
