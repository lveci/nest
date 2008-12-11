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

import shared.array.RealArray;
import shared.array.ComplexArray;
import shared.array.AbstractArray;
import shared.util.Control;

/**
 * Oversample
 */

@OperatorMetadata(alias="Oversample", description="Oversample the datset")
public class OversamplingOp extends Operator {

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
    private float[] tmp;

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

            //System.loadLibrary("sharedx");
            
            //Control.checkTrue(AbstractArray.OpKernel.useNative() && AbstractArray.FFTService.useProvider(),
            //                    "Could not link native layer");

            //AbstractArray.FFTService.setHint("mode", "estimate");

            abs = OperatorUtils.getAbstractedMetadata(sourceProduct);

            getSrcImagePixelSpacings();

            getSampleType();

            getProductType();

            if (!isDetectedSampleType) {
                prf = abs.getAttributeDouble(AbstractMetadata.pulse_repetition_frequency);
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

        int sourceImageTileWidth;
        int sourceImageTileHeight;
        if (isDetectedSampleType) {
            sourceImageTileWidth = 256;
            sourceImageTileHeight = 256;
        } else {
            sourceImageTileWidth = 256;//sourceImageWidth;
            sourceImageTileHeight = (int)(prf+0.5);
        }
        
        int targetImageTileWidth = (int)(sourceImageTileWidth * widthRatio + 0.5f);
        int targetImageTileHeight = (int)(sourceImageTileHeight * heightRatio + 0.5f);

        targetProduct.setPreferredTileSize(targetImageTileWidth, targetImageTileHeight);
        //targetProduct.setPreferredTileSize(JAI.getDefaultTileSize());
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

        final Rectangle targetTileRectangle = targetTile.getRectangle();

        final Rectangle sourceTileRectangle = getSourceTileRectangle(targetTileRectangle);

        final int sourceTileWidth = sourceTileRectangle.width;
        final int sourceTileHeight = sourceTileRectangle.height;
        final int targetTileWidth = targetTileRectangle.width;
        final int targetTileHeight = targetTileRectangle.height;

        final double[] srcImage = getRealImageSourceTileArray(sourceTileRectangle, targetBandName, pm);

        final RealArray srcImageArray = new RealArray(srcImage, sourceTileHeight, sourceTileWidth);

        
        final double[] spectrum = srcImageArray.tocRe().fft().values();

        final double[] zeroPaddedSpec = new double[targetTileWidth*targetTileHeight*2];

        zeroPaddingRealImageSpectrum(sourceTileHeight,
                                     sourceTileWidth,
                                     targetTileHeight,
                                     targetTileWidth,
                                     spectrum,
                                     zeroPaddedSpec);

        final ComplexArray zeroPaddedSpecArray = new ComplexArray(zeroPaddedSpec, targetTileHeight, targetTileWidth, 2);

        final double[] overSampledImage = zeroPaddedSpecArray.ifft().torAbs().values();

        saveOverSampledRealImage(targetTile, overSampledImage);
    }

    private Rectangle getSourceTileRectangle(Rectangle targetTileRectangle) {

        final int sx0 = (int)(targetTileRectangle.x / widthRatio + 0.5f);
        final int sy0 = (int)(targetTileRectangle.y / heightRatio + 0.5f);
        final int sw  = (int)(targetTileRectangle.width / widthRatio + 0.5f);
        final int sh  = (int)(targetTileRectangle.height / heightRatio + 0.5f);
        System.out.println("x0 = " + targetTileRectangle.x + ", y0 = " + targetTileRectangle.y +
                ", w = " + targetTileRectangle.width + ", h = " + targetTileRectangle.height);

        return new Rectangle(sx0, sy0, sw, sh);
    }

    private double[] getRealImageSourceTileArray(
            Rectangle sourceTileRectangle, String srcBandNames, ProgressMonitor pm) {

        final int sx0 = sourceTileRectangle.x;
        final int sy0 = sourceTileRectangle.y;
        final int sw = sourceTileRectangle.width;
        final int sh = sourceTileRectangle.height;

        final Band sourceBand = sourceProduct.getBand(srcBandNames);
        final Tile sourceRaster = getSourceTile(sourceBand, sourceTileRectangle, pm);
        final ProductData srcData = sourceRaster.getDataBuffer();

        int k = 0;
        final int maxY = sy0 + sh;
        final int maxX = sx0 + sw;
        final double[] array = new double[sw*sh];

        for (int y = sy0; y < maxY; ++y) {
            for (int x = sx0; x < maxX; ++x) {
                array[k++] = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x, y));
            }
        }

        return array;
    }

    private void zeroPaddingRealImageSpectrum(int sourceTileHeight,
                                              int sourceTileWidth,
                                              int targetTileHeight,
                                              int targetTileWidth,
                                              double[] spectrum,
                                              double[] zeroPaddedSpectrum) {

        // Both spectrum and zeroPaddedSpectrum are complex
        if (spectrum.length != 2*sourceTileHeight*sourceTileWidth) {
            throw new OperatorException("Incorrect spectrum size");
        }

        final int firstHalfSourceTileWidth = (int)(sourceTileWidth*0.5 + 0.5);
        final int firstHalfSourceTileHeight = (int)(sourceTileHeight*0.5 + 0.5);
        final int secondHalfSourceTileWidth = sourceTileWidth - firstHalfSourceTileWidth;

        final int sourceTileWidth2 = sourceTileWidth*2;
        final int firstHalfSourceTileWidth2 = firstHalfSourceTileWidth*2;
        final int targetTileWidth2 = targetTileWidth*2;
        final int secondHalfSourceTileWidth2 = secondHalfSourceTileWidth*2;
        final int a = 2*(targetTileWidth - secondHalfSourceTileWidth);

        Arrays.fill(zeroPaddedSpectrum, 0.0);

        int R; // row index in zeroPaddedSpectrum
        for (int r = 0; r < sourceTileHeight; r++) {

            if (r < firstHalfSourceTileHeight) {
                R = r;
            } else {
                R = r + targetTileHeight - sourceTileHeight;
            }

            final int s1 = r*sourceTileWidth2;              // multiply by 2 because the data is complex
            final int s2 = s1 + firstHalfSourceTileWidth2;
            final int S1 = R*targetTileWidth2;
            final int S2 = S1 + a;

            System.arraycopy(spectrum, s1, zeroPaddedSpectrum, S1, firstHalfSourceTileWidth2);
            System.arraycopy(spectrum, s2, zeroPaddedSpectrum, S2, secondHalfSourceTileWidth2);
        }
    }

    private void saveOverSampledRealImage(Tile targetTile, double[] image) {

        final ProductData trgData = targetTile.getDataBuffer();
        final Rectangle targetTileRectangle = targetTile.getRectangle();
        final int tx0 = targetTileRectangle.x;
        final int ty0 = targetTileRectangle.y;
        final int tw = targetTileRectangle.width;
        final int th = targetTileRectangle.height;

        int k = 0;
        final double c = widthRatio*heightRatio;
        for (int ty = ty0; ty < ty0 + th; ty++) {
            for (int tx = tx0; tx < tx0 + tw; tx++) {
                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(tx, ty), c*image[k]);
                k++;
            }
        }
    }

    private void computeOverSampledTileForComplexImage(
            String iBandName, String qBandName, Tile iTargetTile, Tile qTargetTile, ProgressMonitor pm)
        throws Exception {

        final Rectangle targetTileRectangle = iTargetTile.getRectangle();

        final Rectangle sourceTileRectangle = getSourceTileRectangle(targetTileRectangle);

        final int sx0 = sourceTileRectangle.x;
        final int sy0 = sourceTileRectangle.y;
        final int sourceTileWidth = sourceTileRectangle.width;
        final int sourceTileHeight = sourceTileRectangle.height;
        final int targetTileWidth = targetTileRectangle.width;
        final int targetTileHeight = targetTileRectangle.height;

        final double[] srcImage = getComplexImageSourceTileArray(sourceTileRectangle, iBandName, qBandName, pm);

        final ComplexArray srcImageArray = new ComplexArray(srcImage, sourceTileHeight, sourceTileWidth, 2);

        final ComplexArray spectrum = srcImageArray.fft();

        final ComplexArray zeroPaddedSpecArray = zeroPaddingComplexImageSpectrum(
                sx0, sy0, sourceTileHeight, sourceTileWidth, targetTileHeight, targetTileWidth, spectrum);

        final double[] overSampledImage = zeroPaddedSpecArray.ifft().values();
        
        saveOverSampledComplexImage(iTargetTile, qTargetTile, overSampledImage);
    }

    private double[] getComplexImageSourceTileArray(
            Rectangle sourceTileRectangle, String iBandName, String qBandName, ProgressMonitor pm) {

        final int sx0 = sourceTileRectangle.x;
        final int sy0 = sourceTileRectangle.y;
        final int sw = sourceTileRectangle.width;
        final int sh = sourceTileRectangle.height;

        final Band iBand = sourceProduct.getBand(iBandName);
        final Band qBand = sourceProduct.getBand(qBandName);

        final Tile iRaster = getSourceTile(iBand, sourceTileRectangle, pm);
        final Tile qRaster = getSourceTile(qBand, sourceTileRectangle, pm);

        final ProductData iData = iRaster.getDataBuffer();
        final ProductData qData = qRaster.getDataBuffer();

        int index;
        int k = 0;
        final int maxY = sy0 + sh;
        final int maxX = sx0 + sw;
        final double[] array = new double[sw*sh*2];

        for (int y = sy0; y < maxY; ++y) {
            for (int x = sx0; x < maxX; ++x) {
                index = iRaster.getDataBufferIndex(x, y);
                array[k++] = iData.getElemDoubleAt(index);
                array[k++] = qData.getElemDoubleAt(index);
            }
        }
        return array;
    }

    private ComplexArray zeroPaddingComplexImageSpectrum(int sx0,
                                                         int sy0,
                                                         int sourceTileHeight,
                                                         int sourceTileWidth,
                                                         int targetTileHeight,
                                                         int targetTileWidth,
                                                         ComplexArray spectrum) {

        // Both spectrum and zeroPaddedSpectrum are complex
        final double[] tranSpec = spectrum.transpose(1, 0, 2).values();
        final double[] paddedSpec = new double[2*targetTileHeight*targetTileWidth];
        Arrays.fill(paddedSpec, 0.0);

        final int firstHalfSourceTileWidth = (int)(sourceTileWidth*0.5 + 0.5);
        final int sourceTileHeight2 = sourceTileHeight*2;
        final int targetTileHeight2 = targetTileHeight*2;
        final int a = targetTileHeight - sourceTileHeight;
        
        int C; // col index in zeroPaddedSpectrum
        for (int c = 0; c < sourceTileWidth; c++) {

            final int d = computeDopplerCentroidFreqIndex(c + sx0, sourceTileHeight);
            final int d2 = d * 2;

            if (c < firstHalfSourceTileWidth) {
                C = c;
            } else {
                C = c + targetTileWidth - sourceTileWidth;
            }

            final int s1 = c*sourceTileHeight2; // multiply by 2 because the data is complex
            final int s2 = s1 + d2;
            final int S1 = C*targetTileHeight2;
            final int S2 = S1 + 2*(a + d);

            System.arraycopy(tranSpec, s1, paddedSpec, S1, d2);
            System.arraycopy(tranSpec, s2, paddedSpec, S2, (sourceTileHeight - d)*2);
        }

        ComplexArray zeroPaddedSpectrum = new ComplexArray(paddedSpec, targetTileWidth, targetTileHeight, 2);
        return zeroPaddedSpectrum.transpose(1, 0, 2);
    }

    private void saveOverSampledComplexImage(Tile iTargetTile, Tile qTargetTile, double[] image) {

        final ProductData iData = iTargetTile.getDataBuffer();
        final ProductData qData = qTargetTile.getDataBuffer();

        final Rectangle targetTileRectangle = iTargetTile.getRectangle();
        final int tx0 = targetTileRectangle.x;
        final int ty0 = targetTileRectangle.y;
        final int tw = targetTileRectangle.width;
        final int th = targetTileRectangle.height;

        int k = 0;
        final double c = widthRatio*heightRatio;
        for (int ty = ty0; ty < ty0 + th; ty++) {
            for (int tx = tx0; tx < tx0 + tw; tx++) {
                final int index = iTargetTile.getDataBufferIndex(tx, ty);
                iData.setElemDoubleAt(index, c*image[k++]);
                qData.setElemDoubleAt(index, c*image[k++]);
            }
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
            super(OversamplingOp.class);
            setOperatorUI(OversamplingOpUI.class);
        }
    }
}