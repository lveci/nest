/*
 * Copyright (C) 2002-2007 by ?
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
package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import java.awt.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Arrays;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

/**
 * Oversample
 */

@OperatorMetadata(alias="Oversample2", description="Oversample the datset")
public class OversamplingOp2 extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames;

    @Parameter(valueSet = {UndersamplingOp.IMAGE_SIZE, UndersamplingOp.RATIO, UndersamplingOp.PIXEL_SPACING},
            defaultValue = UndersamplingOp.IMAGE_SIZE, label="Output Image By:")
    private String outputImageBy = UndersamplingOp.RATIO;

    @Parameter(description = "The row dimension of the output image", defaultValue = "1000", label="Output Image Rows")
    private int targetImageHeight = 1000;
    @Parameter(description = "The col dimension of the output image", defaultValue = "1000", label="Output Image Columns")
    private int targetImageWidth = 1000;

    @Parameter(description = "The width ratio of the output/input images", defaultValue = "2.0", label="Width Ratio")
    private float widthRatio = 2.0f;
    @Parameter(description = "The height ratio of the output/input images", defaultValue = "2.0", label="Height Ratio")
    private float heightRatio = 2.0f;

    @Parameter(description = "The range pixel spacing", defaultValue = "12.5", label="Range Spacing")
    private float rangeSpacing = 12.5f;
    @Parameter(description = "The azimuth pixel spacing", defaultValue = "12.5", label="Azimuth Spacing")
    private float azimuthSpacing = 12.5f;

    private Band sourceBand1;
    private Band sourceBand2;

    private MetadataElement abs; // root of the abstracted metadata
    private String sampleType;
    private String productType;

    private boolean isDetectedSampleType = false;
    private boolean isCEOSFormat = false;

    private int sourceImageWidth;
    private int sourceImageHeight;

    private float srcRangeSpacing; // range pixel spacing of source image
    private float srcAzimuthSpacing; // azimuth pixel spacing of source image
    private float[][] tmpI;
    private float[][] tmpQ;

    private double prf; // pulse repetition frequency in Hz
    private double samplingRate; // range sampling rate in Hz
    private double[] dopplerCentroidFreq; // Doppler centroid frequencies for all columns in a range line

    private static final double nsTOs = 1.0 / 1000000000.0; // ns to s

    /**
     * Initializes this operator and sets the one and only target product.
     * <p>The target product can be either defined by a field of type {@link org.esa.beam.framework.datamodel.Product} annotated with the
     * {@link org.esa.beam.framework.gpf.annotations.TargetProduct TargetProduct} annotation or
     * by calling {@link #setTargetProduct} method.</p>
     * <p>The framework calls this method after it has created this operator.
     * Any client code that must be performed before computation of tile data
     * should be placed here.</p>
     *
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during operator initialisation.
     * @see #getTargetProduct()
     */
    @Override
    public void initialize() throws OperatorException {

        try {
            sourceImageWidth = sourceProduct.getSceneRasterWidth();
            sourceImageHeight = sourceProduct.getSceneRasterHeight();

            abs = OperatorUtils.getAbstractedMetadata(sourceProduct);

            getSrcImagePixelSpacings();

            getSampleType();

            getProductType();

            prf = abs.getAttributeDouble(AbstractMetadata.pulse_repetition_frequency);
            if (!isDetectedSampleType) {
                samplingRate = abs.getAttributeDouble(AbstractMetadata.range_sampling_rate);
                computeDopplerCentroidFrequencies();
            }

            computeTargetImageSizeAndPixelSpacings();

            createTargetProduct();

        } catch(Exception e) {
            throw new OperatorException(e.getMessage());
        }
    }

    /**
     * Get the range and azimuth spacings (in meter).
     * @throws Exception when metadata not found
     */
    private void getSrcImagePixelSpacings() throws Exception {

        srcRangeSpacing = (float)abs.getAttributeDouble(AbstractMetadata.range_spacing);
        //System.out.println("Range spacing is " + srcRangeSpacing);

        srcAzimuthSpacing = (float)abs.getAttributeDouble(AbstractMetadata.azimuth_spacing);
        //System.out.println("Azimuth spacing is " + srcAzimuthSpacing);
    }

    /**
     * Get the sample type.
     * @throws Exception when metadata not found
     */
    void getSampleType() throws Exception {

        sampleType = abs.getAttributeString(AbstractMetadata.SAMPLE_TYPE);
        //System.out.println("Sample type is " + sampleType);
        isDetectedSampleType = sampleType.contains("DETECTED");
    }
    /**
     * Get Product type.
     * @throws Exception when metadata not found
     */
    private void getProductType() throws Exception {

        productType = abs.getAttributeString(AbstractMetadata.PRODUCT_TYPE);

        if (productType.contains("ERS")) {
            isCEOSFormat = true;
        } else if (productType.contains("ASA") || productType.contains("SAR")) {
            isCEOSFormat = false;
        } else {
            throw new OperatorException("Invalid product type: " + productType);
        }
        //System.out.println("product type is " + productType);
    }

    /**
     * Compute Doppler centroid frequency for all columns in a range line.
     */
    private void computeDopplerCentroidFrequencies() {

        if (isCEOSFormat) { // CEOS
            computeDopplerCentroidFreqForERSProd();
        } else { // ENVISAT
            computeDopplerCentroidFreqForENVISATProd();
        }
    }

    /**
     * Compute Doppler centroid frequency for all columns for ERS product.
     */
    private void computeDopplerCentroidFreqForERSProd() {

        // Get coefficients of Doppler frequency polynomial from
        // fields 105, 106 and 107 in PRI Data Set Summary Record
        final MetadataElement facility = sourceProduct.getMetadataRoot().getElement("Leader").getElement("Scene Parameters");
        if (facility == null) {
            throw new OperatorException("Scene Parameters not found");
        }

        MetadataAttribute attr = facility.getAttribute("Cross track Doppler frequency centroid constant term");
        if (attr == null) {
            throw new OperatorException("Cross track Doppler frequency centroid constant term not found");
        }
        double a0 = attr.getData().getElemDouble();

        attr = facility.getAttribute("Cross track Doppler frequency centroid linear term");
        if (attr == null) {
            throw new OperatorException("Cross track Doppler frequency centroid linear term not found");
        }
        double a1 = attr.getData().getElemDouble();

        attr = facility.getAttribute("Cross track Doppler frequency centroid quadratic term");
        if (attr == null) {
            throw new OperatorException("Cross track Doppler frequency centroid quadratic term not found");
        }
        double a2 = attr.getData().getElemDouble();
        System.out.println("Doppler frequency polynomial coefficients are " + a0 + ", " + a1 + ", " + a2);

        // compute Doppler centroid frequencies
        dopplerCentroidFreq = new double[sourceImageWidth];
        for (int c = 0; c < sourceImageWidth; c++) {
            final double dt = (c - sourceImageWidth*0.5) / samplingRate;
            dopplerCentroidFreq[c] = a0 + a1*dt + a2*dt*dt;
        }
    }

    /**
     * Compute Doppler centroid frequency for all columns for ENVISAT product.
     */
    private void computeDopplerCentroidFreqForENVISATProd() {

        // get slant range time origin in second
        final MetadataElement dsd = sourceProduct.getMetadataRoot().getElement("DOP_CENTROID_COEFFS_ADS");
        if (dsd == null) {
            throw new OperatorException("DOP_CENTROID_COEFFS_ADS not found");
        }

        final MetadataAttribute srtAttr = dsd.getAttribute("slant_range_time");
        if (srtAttr == null) {
            throw new OperatorException("slant_range_time not found");
        }

        final double t0 = srtAttr.getData().getElemFloat() * nsTOs;

        // get Doppler centroid coefficients: d0, d1, d2, d3 and d4
        final MetadataAttribute coefAttr = dsd.getAttribute("dop_coef");
        if (coefAttr == null) {
            throw new OperatorException("dop_coef not found");
        }

        final double d0 = coefAttr.getData().getElemFloatAt(0);
        final double d1 = coefAttr.getData().getElemFloatAt(1);
        final double d2 = coefAttr.getData().getElemFloatAt(2);
        final double d3 = coefAttr.getData().getElemFloatAt(3);
        final double d4 = coefAttr.getData().getElemFloatAt(4);

        // compute Doppler centroid frequencies for all columns in a range line
        final TiePointGrid slantRangeTime = OperatorUtils.getSlantRangeTime(sourceProduct);
        dopplerCentroidFreq = new double[sourceImageWidth];
        for (int c = 0; c < sourceImageWidth; c++) {
            final double tSR = slantRangeTime.getPixelDouble(c, 0) * nsTOs;
            final double dt = tSR - t0;
            dopplerCentroidFreq[c] = d0 + d1*dt + d2*Math.pow(dt, 2.0) + d3*Math.pow(dt, 3.0) + d4*Math.pow(dt, 4.0);
        }
        /*
        for (double v:dopplerCentroidFreq) {
            System.out.print(v + ",");
        }
        System.out.println();
        */
    }

    /**
     * Compute target image size and range/azimuth spacings.
     *
     * @throws OperatorException The exceptions.
     */
    private void computeTargetImageSizeAndPixelSpacings() throws OperatorException {

        if (outputImageBy.equals(UndersamplingOp.IMAGE_SIZE)) {

            if (targetImageHeight <= sourceImageHeight || targetImageWidth <= sourceImageWidth) {
                throw new OperatorException("Output image size must be greater than the source image size");
            }

            widthRatio = (float)targetImageWidth / (float)sourceImageWidth;
            heightRatio = (float)targetImageHeight / (float)sourceImageHeight;

            rangeSpacing = srcRangeSpacing / widthRatio;
            azimuthSpacing = srcAzimuthSpacing / heightRatio;

        } else if (outputImageBy.equals(UndersamplingOp.RATIO)) {

            if (widthRatio <= 1 || heightRatio <= 1) {
                throw new OperatorException("The width or height ratio must be greater than 1");
            }

            targetImageHeight = (int)(heightRatio * sourceImageHeight + 0.5f);
            targetImageWidth = (int)(widthRatio * sourceImageWidth + 0.5f);

            rangeSpacing = srcRangeSpacing / widthRatio;
            azimuthSpacing = srcAzimuthSpacing / heightRatio;

        } else if (outputImageBy.equals(UndersamplingOp.PIXEL_SPACING)) {

            if (rangeSpacing <= 0.0f || rangeSpacing >= srcRangeSpacing ||
                azimuthSpacing <= 0.0f || azimuthSpacing >= srcAzimuthSpacing) {
                throw new OperatorException("The azimuth or range spacing must be positive and smaller than the source spacing");
            }

            widthRatio = srcRangeSpacing / rangeSpacing;
            heightRatio = srcAzimuthSpacing / azimuthSpacing;

            targetImageHeight = (int)(widthRatio * sourceImageHeight + 0.5);
            targetImageWidth = (int)(heightRatio * sourceImageWidth + 0.5);

        } else {
            throw new OperatorException("Please specify output image size, or row and column ratios, or pixel spacings");
        }
    }

    private void createTargetProduct() throws Exception {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    targetImageWidth,
                                    targetImageHeight);

        addSelectedBands();

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        updateTargetProductMetadata();

        int sourceImageTileWidth = sourceImageWidth;
        int sourceImageTileHeight = Math.min((int)(prf+0.5), sourceImageHeight);

        int targetImageTileWidth = (int)(sourceImageTileWidth * widthRatio + 0.5f);
        int targetImageTileHeight = (int)(sourceImageTileHeight * heightRatio + 0.5f);

        targetProduct.setPreferredTileSize(targetImageTileWidth, targetImageTileHeight);
    }

    private void addSelectedBands() {
        if (sourceBandNames == null || sourceBandNames.length == 0) {
            Band[] bands = sourceProduct.getBands();
            ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        Band[] sourceBands = new Band[sourceBandNames.length];
        for (int i = 0; i < sourceBandNames.length; i++) {
            String sourceBandName = sourceBandNames[i];
            Band sourceBand = sourceProduct.getBand(sourceBandName);
            if (sourceBand == null) {
                throw new OperatorException("Source band not found: " + sourceBandName);
            }
            sourceBands[i] = sourceBand;
        }

        for(Band srcBand : sourceBands) {

            final String unit = srcBand.getUnit();
            if(unit == null) {
                throw new OperatorException("band "+srcBand.getName()+" requires a unit");
            }

            if (unit.contains("phase")) {
                continue;
            }

            Band targetBand = new Band(srcBand.getName(),
                                       ProductData.TYPE_FLOAT64,
                                       targetImageWidth,
                                       targetImageHeight);

            targetBand.setUnit(unit);
            targetProduct.addBand(targetBand);
        }
    }

    private void updateTargetProductMetadata() throws Exception {

        final MetadataElement absTgt = OperatorUtils.getAbstractedMetadata(targetProduct);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.azimuth_spacing, azimuthSpacing);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.range_spacing, rangeSpacing);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.num_samples_per_line, targetImageWidth);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.num_output_lines, targetImageHeight);

        final float oldLineTimeInterval = (float)absTgt.getAttributeDouble(AbstractMetadata.line_time_interval);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.line_time_interval, oldLineTimeInterval/heightRatio);
    }

    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTileMap The target tiles associated with all target bands to be computed.
     * @param targetRectangle The rectangle of target tile.
     * @param pm A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the target raster.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTileMap, Rectangle targetRectangle, ProgressMonitor pm)
            throws OperatorException {

        try {

            final Band[] targetBands = targetProduct.getBands();
            for (int i = 0; i < targetBands.length; i++) {

                checkForCancelation(pm);

                if (targetBands[i].getUnit().contains("real")) {

                    if (i+1 >= targetBands.length) {
                        throw new OperatorException("q band is missing from target product");
                    }

                    computeOverSampledTileForComplexImage(targetBands[i].getName(),
                                                          targetBands[i+1].getName(),
                                                          targetTileMap.get(targetBands[i]),
                                                          targetTileMap.get(targetBands[i+1]),
                                                          pm);
                    i++;

                } else {

                    computeOverSampledTileForRealImage(targetBands[i].getName(),
                                                       targetTileMap.get(targetBands[i]),
                                                       pm);
                }
            }

        } catch (Exception e){
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }

    private void computeOverSampledTileForRealImage(String targetBandName, Tile targetTile, ProgressMonitor pm)
                 throws Exception {

        final ProductData tgtData = targetTile.getDataBuffer();

        Rectangle targetTileRectangle = targetTile.getRectangle();
        int tx0 = targetTileRectangle.x;
        int ty0 = targetTileRectangle.y;
        int targetTileWidth = targetTileRectangle.width;
        int targetTileHeight = targetTileRectangle.height;

        Rectangle sourceTileRectangle = getSourceTileRectangle(targetTileRectangle);
        int sx0 = sourceTileRectangle.x;
        int sy0 = sourceTileRectangle.y;
        int sourceTileWidth = sourceTileRectangle.width;
        int sourceTileHeight = sourceTileRectangle.height;

        tmpI = new float[targetTileHeight][sourceTileWidth];
        tmpQ = new float[targetTileHeight][sourceTileWidth];

        final Band srcBand = sourceProduct.getBand(targetBandName);
        final Tile srcRaster = getSourceTile(srcBand, sourceTileRectangle, pm);
        final ProductData srcData = srcRaster.getDataBuffer();

        // perform 1-D FFT on each row
        DoubleFFT_1D src_row_fft = new DoubleFFT_1D(sourceTileWidth);
        for (int y = 0; y < sourceTileHeight; y++) {
            double[] row = getRowData(sy0 + y, sx0, sourceTileWidth, srcData, srcRaster);
            src_row_fft.complexForward(row);
            saveRowSpec(row, y, sourceTileWidth);
        }

        // perform 1-D FFT, zero padding and IFFT on each column
        DoubleFFT_1D src_col_fft = new DoubleFFT_1D(sourceTileHeight);
        DoubleFFT_1D tgt_col_fft = new DoubleFFT_1D(targetTileHeight);
        for (int x = 0; x < sourceTileWidth; x++) {
            double[] col = getColData(x, sourceTileHeight);
            src_col_fft.complexForward(col);
            double[] zeroPaddedColSpec = paddingZeros(col, sourceTileHeight, targetTileHeight);
            tgt_col_fft.complexInverse(zeroPaddedColSpec, true);
            saveOverSampledCol(zeroPaddedColSpec, x, targetTileHeight);
        }

        // perform 1-D IFFT on each row
        DoubleFFT_1D tgt_row_fft = new DoubleFFT_1D(targetTileWidth);
        for (int y = 0; y < targetTileHeight; y++) {
            double[] row = getRowData(y, sourceTileWidth, targetTileWidth);
            tgt_row_fft.complexInverse(row, true);
            saveOverSampledComplexImage(row, ty0 + y, tx0, targetTileWidth, tgtData, targetTile);
        }
    }

    private Rectangle getSourceTileRectangle(Rectangle targetTileRectangle) {

        int sx0 = (int)(targetTileRectangle.x / widthRatio + 0.5f);
        int sy0 = (int)(targetTileRectangle.y / heightRatio + 0.5f);
        int sw  = (int)(targetTileRectangle.width / widthRatio + 0.5f);
        int sh  = (int)(targetTileRectangle.height / heightRatio + 0.5f);
        System.out.println("x0 = " + targetTileRectangle.x + ", y0 = " + targetTileRectangle.y +
                ", w = " + targetTileRectangle.width + ", h = " + targetTileRectangle.height);

        return new Rectangle(sx0, sy0, sw, sh);
    }

    //==================================================================================================================

    private void computeOverSampledTileForComplexImage(
            String iBandName, String qBandName, Tile iTargetTile, Tile qTargetTile, ProgressMonitor pm)
        throws Exception {

        final ProductData iTgtData = iTargetTile.getDataBuffer();
        final ProductData qTgtData = qTargetTile.getDataBuffer();

        Rectangle targetTileRectangle = iTargetTile.getRectangle();
        int tx0 = targetTileRectangle.x;
        int ty0 = targetTileRectangle.y;
        int targetTileWidth = targetTileRectangle.width;
        int targetTileHeight = targetTileRectangle.height;

        Rectangle sourceTileRectangle = getSourceTileRectangle(targetTileRectangle);
        int sx0 = sourceTileRectangle.x;
        int sy0 = sourceTileRectangle.y;
        int sourceTileWidth = sourceTileRectangle.width;
        int sourceTileHeight = sourceTileRectangle.height;

        tmpI = new float[targetTileHeight][sourceTileWidth];
        tmpQ = new float[targetTileHeight][sourceTileWidth];

        final Band iBand = sourceProduct.getBand(iBandName);
        final Band qBand = sourceProduct.getBand(qBandName);

        final Tile iRaster = getSourceTile(iBand, sourceTileRectangle, pm);
        final Tile qRaster = getSourceTile(qBand, sourceTileRectangle, pm);

        final ProductData iSrcData = iRaster.getDataBuffer();
        final ProductData qSrcData = qRaster.getDataBuffer();

        // perform 1-D FFT on each row
        DoubleFFT_1D src_row_fft = new DoubleFFT_1D(sourceTileWidth);
        for (int y = 0; y < sourceTileHeight; y++) {
            double[] row = getRowData(sy0 + y, sx0, sourceTileWidth, iSrcData, qSrcData, iRaster);
            src_row_fft.complexForward(row);
            saveRowSpec(row, y, sourceTileWidth);
        }

        // perform 1-D FFT, zero padding and IFFT on each column
        DoubleFFT_1D src_col_fft = new DoubleFFT_1D(sourceTileHeight);
        DoubleFFT_1D tgt_col_fft = new DoubleFFT_1D(targetTileHeight);
        for (int x = 0; x < sourceTileWidth; x++) {
            double[] col = getColData(x, sourceTileHeight);
            src_col_fft.complexForward(col);
            double[] zeroPaddedColSpec = paddingZeros(col, sx0 + x, sourceTileHeight, targetTileHeight);
            tgt_col_fft.complexInverse(zeroPaddedColSpec, true);
            saveOverSampledCol(zeroPaddedColSpec, x, targetTileHeight);
        }

        // perform 1-D IFFT on each row
        DoubleFFT_1D tgt_row_fft = new DoubleFFT_1D(targetTileWidth);
        for (int y = 0; y < targetTileHeight; y++) {
            double[] row = getRowData(y, sourceTileWidth, targetTileWidth);
            tgt_row_fft.complexInverse(row, true);
            saveOverSampledComplexImage(row, ty0 + y, tx0, targetTileWidth, iTgtData, qTgtData, iTargetTile);
        }
    }

    private double[] getRowData(int sy, int sx0, int sw, ProductData srcData, Tile srcRaster) {

        int index;
        int k = 0;
        final double[] array = new double[sw*2];

        for (int sx = sx0; sx < sx0 + sw; ++sx) {
            index = srcRaster.getDataBufferIndex(sx, sy);
            array[k++] = srcData.getElemDoubleAt(index);
            array[k++] = 0.0;
        }
        return array;
    }

    private double[] getRowData(
            int sy, int sx0, int sw, ProductData iData, ProductData qData, Tile iRaster) {

        int index;
        int k = 0;
        final double[] array = new double[sw*2];

        for (int sx = sx0; sx < sx0 + sw; ++sx) {
            index = iRaster.getDataBufferIndex(sx, sy);
            array[k++] = iData.getElemDoubleAt(index);
            array[k++] = qData.getElemDoubleAt(index);
        }
        return array;
    }

    private void saveRowSpec(double[] row, int y, int sourceTileWidth) {

        for (int x = 0; x < sourceTileWidth; x++) {
            tmpI[y][x] = (float)row[2*x];
            tmpQ[y][x] = (float)row[2*x+1];
        }
    }

    private double[] getColData(int x, int sourceTileHeight) {

        double[] array = new double[2*sourceTileHeight];
        int k = 0;
        for (int y = 0; y < sourceTileHeight; y++) {
            array[k++] = (double)tmpI[y][x];
            array[k++] = (double)tmpQ[y][x];
        }
        return array;
    }

    private double[] paddingZeros(double[] colSpec, int sourceTileHeight, int targetTileHeight) {

        double[] array = new double[2*targetTileHeight];
        Arrays.fill(array, 0.0);
        int d = (int)(sourceTileHeight/2 + 0.5);
        int s1 = 0;
        int s2 = d*2;
        int S1 = 0;
        int S2 = 2*(targetTileHeight - sourceTileHeight + d);
        System.arraycopy(colSpec, s1, array, S1, d*2);
        System.arraycopy(colSpec, s2, array, S2, (sourceTileHeight - d)*2);

        return array;
    }

    private double[] paddingZeros(double[] colSpec, int x, int sourceTileHeight, int targetTileHeight) {

        double[] array = new double[2*targetTileHeight];
        Arrays.fill(array, 0.0);
        int d = computeDopplerCentroidFreqIndex(x, sourceTileHeight);
        int s1 = 0;
        int s2 = d*2;
        int S1 = 0;
        int S2 = 2*(targetTileHeight - sourceTileHeight + d);
        System.arraycopy(colSpec, s1, array, S1, d*2);
        System.arraycopy(colSpec, s2, array, S2, (sourceTileHeight - d)*2);

        return array;
    }

    private void saveOverSampledCol(double[] overSampledCol, int x, int targetTileHeight) {

        int k = 0;
        for (int y = 0; y < targetTileHeight; y++) {
            tmpI[y][x] = (float)overSampledCol[k++];
            tmpQ[y][x] = (float)overSampledCol[k++];
        }
    }

    private double[] getRowData(int y, int sourceTileWidth, int targetTileWidth) {

        double[] array = new double[targetTileWidth*2];
        Arrays.fill(array, 0.0);

        int firstHalfSourceTileWidth = (int)(sourceTileWidth/2 + 0.5);
        int k = 0;
        for (int x = 0; x < firstHalfSourceTileWidth; x++) {
            array[k++] = tmpI[y][x];
            array[k++] = tmpQ[y][x];
        }

        int secondHalfSourceTileWidth = sourceTileWidth - firstHalfSourceTileWidth;
        k = 2*(targetTileWidth - secondHalfSourceTileWidth);
        for (int x = firstHalfSourceTileWidth; x < sourceTileWidth; x++) {
            array[k++] = tmpI[y][x];
            array[k++] = tmpQ[y][x];
        }

        return array;
    }

    private void saveOverSampledComplexImage(
        double[] overSampledRow, int ty, int tx0, int tw, ProductData tgtData, Tile targetTile) {

        int k = 0;
        double c = widthRatio*heightRatio;
        for (int tx = tx0; tx < tx0 + tw; tx++) {
            int index = targetTile.getDataBufferIndex(tx, ty);
            double i = overSampledRow[k++];
            double q = overSampledRow[k++];
            tgtData.setElemDoubleAt(index, c*Math.sqrt(i*i + q*q));
        }
    }

    private void saveOverSampledComplexImage(
        double[] overSampledRow, int ty, int tx0, int tw, ProductData iData, ProductData qData, Tile iTargetTile) {

        int k = 0;
        double c = widthRatio*heightRatio;
        for (int tx = tx0; tx < tx0 + tw; tx++) {
            int index = iTargetTile.getDataBufferIndex(tx, ty);
            iData.setElemDoubleAt(index, c*overSampledRow[k++]);
            qData.setElemDoubleAt(index, c*overSampledRow[k++]);
        }
    }

    /**
     * Compute index for Doppler centroid frequency for given column.
     * @param c The column index in the full image.
     * @param h The source tile height.
     * @return  The index of Doppler centroid frequency.
     */
    private int computeDopplerCentroidFreqIndex(int c, int h) {

        final double fdc = dopplerCentroidFreq[c];
        final int idxFdc = (int)(fdc * h / prf + 0.5);
        return (idxFdc + h/2) % h;
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator()
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(OversamplingOp2.class);
            setOperatorUI(OversamplingOpUI.class);
        }
    }
}