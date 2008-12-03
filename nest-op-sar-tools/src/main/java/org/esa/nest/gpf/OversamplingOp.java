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
import org.esa.nest.dataio.OperatorUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Arrays;

import shared.array.RealArray;
import shared.array.ComplexArray;

/**
 * Oversample
 */

@OperatorMetadata(alias="Oversample", internal=true)
public class OversamplingOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames;

    @Parameter(description = "The row dimension of the output image", defaultValue = "0", label="Output Image Rows")
    private int targetImageHeight;
    @Parameter(description = "The col dimension of the output image", defaultValue = "0", label="Output Image Columns")
    private int targetImageWidth;

    @Parameter(description = "The width ratio of the output/input images", defaultValue = "0.0", label="Width Ratio")
    private float widthRatio;
    @Parameter(description = "The height ratio of the output/input images", defaultValue = "0.0", label="Height Ratio")
    private float heightRatio;

    @Parameter(description = "The range pixel spacing", defaultValue = "0.0", label="Range Spacing")
    private float rangeSpacing;
    @Parameter(description = "The azimuth pixel spacing", defaultValue = "0.0", label="Azimuth Spacing")
    private float azimuthSpacing;

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

        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();

        abs = OperatorUtils.getAbstractedMetadata(sourceProduct);

        getSrcImagePixelSpacings();

        getSampleType();

        getProductType();

        if (!isDetectedSampleType) {
            getPulseRepetitionFrequency();
            getRangeSamplingRate();
            computeDopplerCentroidFrequencies();
        }

        computeTargetImageSizeAndPixelSpacings();

        createTargetProduct();
    }

    /**
     * Get the range and azimuth spacings (in meter).
     */
    private void getSrcImagePixelSpacings() {

        MetadataAttribute rangeSpacingAttr = abs.getAttribute(AbstractMetadata.range_spacing);
        if (rangeSpacingAttr == null) {
            throw new OperatorException(AbstractMetadata.range_spacing + " not found");
        }

        srcRangeSpacing = rangeSpacingAttr.getData().getElemFloat();
        //System.out.println("Range spacing is " + srcRangeSpacing);

        MetadataAttribute azimuthSpacingAttr = abs.getAttribute(AbstractMetadata.azimuth_spacing);
        if (azimuthSpacingAttr == null) {
            throw new OperatorException(AbstractMetadata.azimuth_spacing + " not found");
        }

        srcAzimuthSpacing = azimuthSpacingAttr.getData().getElemFloat();
        //System.out.println("Azimuth spacing is " + srcAzimuthSpacing);
    }

    /**
     * Get the sample type.
     */
    void getSampleType() {

        MetadataAttribute sampleTypeAttr = abs.getAttribute(AbstractMetadata.SAMPLE_TYPE);
        if (sampleTypeAttr == null) {
            throw new OperatorException(AbstractMetadata.SAMPLE_TYPE + " not found");
        }

        sampleType = sampleTypeAttr.getData().getElemString();
        //System.out.println("Sample type is " + sampleType);
        isDetectedSampleType = sampleType.contains("DETECTED");
    }
    /**
     * Get Product type.
     */
    private void getProductType() {

        final MetadataAttribute productTypeAttr = abs.getAttribute(AbstractMetadata.PRODUCT_TYPE);
        if (productTypeAttr == null) {
            throw new OperatorException(AbstractMetadata.PRODUCT_TYPE + " not found");
        }

        productType = productTypeAttr.getData().getElemString();

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
     * Get pulse repetition frequency (in Hz).
     */
    private void getPulseRepetitionFrequency() {

        MetadataAttribute prfAttr = abs.getAttribute(AbstractMetadata.pulse_repetition_frequency);
        if (prfAttr == null) {
            throw new OperatorException(AbstractMetadata.pulse_repetition_frequency + " not found");
        }

        prf = prfAttr.getData().getElemDouble();
    }

    /**
     * Get range sampling rate (in Hz).
     */
    private void getRangeSamplingRate() {

        MetadataAttribute rateAttr = abs.getAttribute(AbstractMetadata.range_sampling_rate);
        if (rateAttr == null) {
            throw new OperatorException(AbstractMetadata.range_sampling_rate + " not found");
        }
        // the conversion below is temporary and should be removed once the sampling rate is fixed in the abs
        if (isCEOSFormat) { // CEOS
            samplingRate = rateAttr.getData().getElemDouble()*1000000; // MHz to Hz
        } else {
            samplingRate = rateAttr.getData().getElemDouble();
        }
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
            double dt = (c - sourceImageWidth*0.5) / samplingRate;
            dopplerCentroidFreq[c] = a0 + a1*dt + a2*dt*dt;
        }
    }

    /**
     * Compute Doppler centroid frequency for all columns for ENVISAT product.
     */
    private void computeDopplerCentroidFreqForENVISATProd() {

        // get slant range time origin in second
        MetadataElement dsd = sourceProduct.getMetadataRoot().getElement("DOP_CENTROID_COEFFS_ADS");
        if (dsd == null) {
            throw new OperatorException("DOP_CENTROID_COEFFS_ADS not found");
        }

        MetadataAttribute srtAttr = dsd.getAttribute("slant_range_time");
        if (srtAttr == null) {
            throw new OperatorException("slant_range_time not found");
        }

        double t0 = srtAttr.getData().getElemFloat() * nsTOs;

        // get Doppler centroid coefficients: d0, d1, d2, d3 and d4
        MetadataAttribute coefAttr = dsd.getAttribute("dop_coef");
        if (coefAttr == null) {
            throw new OperatorException("dop_coef not found");
        }

        double d0 = coefAttr.getData().getElemFloatAt(0);
        double d1 = coefAttr.getData().getElemFloatAt(1);
        double d2 = coefAttr.getData().getElemFloatAt(2);
        double d3 = coefAttr.getData().getElemFloatAt(3);
        double d4 = coefAttr.getData().getElemFloatAt(4);

        // compute Doppler centroid frequencies for all columns in a range line
        TiePointGrid slantRangeTime = getSlantRangeTime(sourceProduct);
        dopplerCentroidFreq = new double[sourceImageWidth];
        for (int c = 0; c < sourceImageWidth; c++) {
            double tSR = slantRangeTime.getPixelDouble(c, 0) * nsTOs;
            double dt = tSR - t0;
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
     * Get slant range time tie point grid.
     * @param sourceProduct the source
     * @return srcTPG The slant range time tie point grid.
     */
    public static TiePointGrid getSlantRangeTime(Product sourceProduct) {

        for (int i = 0; i < sourceProduct.getNumTiePointGrids(); i++) {
            final TiePointGrid srcTPG = sourceProduct.getTiePointGridAt(i);
            if (srcTPG.getName().equals("slant_range_time")) {
                return srcTPG;
            }
        }

        return null;
    }

    /**
     * Compute target image size and range/azimuth spacings.
     *
     * @throws OperatorException The exceptions.
     */
    private void computeTargetImageSizeAndPixelSpacings() throws OperatorException {

        if (targetImageWidth != 0 && targetImageHeight != 0) {

            if (targetImageHeight <= sourceImageHeight || targetImageWidth <= sourceImageWidth) {
                throw new OperatorException("Output image size must be greater than the source image size");
            }

            widthRatio = (float)targetImageWidth / (float)sourceImageWidth;
            heightRatio = (float)targetImageHeight / (float)sourceImageHeight;

            rangeSpacing = srcRangeSpacing / widthRatio;
            azimuthSpacing = srcAzimuthSpacing / heightRatio;

        } else if (Float.compare(widthRatio, 0.0f) != 0 && Float.compare(heightRatio, 0.0f) != 0) {

            if (widthRatio <= 1 || heightRatio <= 1) {
                throw new OperatorException("The width or height ratio must be greater than 1");
            }

            targetImageHeight = (int)(heightRatio * sourceImageHeight + 0.5f);
            targetImageWidth = (int)(widthRatio * sourceImageWidth + 0.5f);

            rangeSpacing = srcRangeSpacing / widthRatio;
            azimuthSpacing = srcAzimuthSpacing / heightRatio;

        } else if (Float.compare(rangeSpacing, 0.0f) != 0 && Float.compare(azimuthSpacing, 0.0f) != 0) {

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

    private void createTargetProduct() {

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

    private void updateTargetProductMetadata() {

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

            Band[] targetBands = targetProduct.getBands();
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

        } catch (OperatorException e){
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }

    private void computeOverSampledTileForRealImage(String targetBandName, Tile targetTile, ProgressMonitor pm)
                 throws OperatorException {

        Rectangle targetTileRectangle = targetTile.getRectangle();

        Rectangle sourceTileRectangle = getSourceTileRectangle(targetTileRectangle);

        int sourceTileWidth = sourceTileRectangle.width;
        int sourceTileHeight = sourceTileRectangle.height;
        int targetTileWidth = targetTileRectangle.width;
        int targetTileHeight = targetTileRectangle.height;

        double[] srcImage = getRealImageSourceTileArray(sourceTileRectangle, targetBandName, pm);

        RealArray srcImageArray = new RealArray(srcImage, sourceTileHeight, sourceTileWidth);

        double[] spectrum = srcImageArray.tocRe().fft().values();

        double[] zeroPaddedSpec = new double[targetTileWidth*targetTileHeight*2];

        zeroPaddingRealImageSpectrum(sourceTileHeight,
                                     sourceTileWidth,
                                     targetTileHeight,
                                     targetTileWidth,
                                     spectrum,
                                     zeroPaddedSpec);

        ComplexArray zeroPaddedSpecArray = new ComplexArray(zeroPaddedSpec, targetTileHeight, targetTileWidth, 2);

        double[] overSampledImage = zeroPaddedSpecArray.ifft().torAbs().values();

        saveOverSampledRealImage(targetTile, overSampledImage);
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

    private double[] getRealImageSourceTileArray(
            Rectangle sourceTileRectangle, String srcBandNames, ProgressMonitor pm) {

        final int sx0 = sourceTileRectangle.x;
        final int sy0 = sourceTileRectangle.y;
        final int sw = sourceTileRectangle.width;
        final int sh = sourceTileRectangle.height;

        final Band sourceBand = sourceProduct.getBand(srcBandNames);
        final Tile sourceRaster = getSourceTile(sourceBand, sourceTileRectangle, pm);
        final ProductData srcData = sourceRaster.getDataBuffer();

        int index;
        int k = 0;
        final int maxY = sy0 + sh;
        final int maxX = sx0 + sw;
        final double[] array = new double[sw*sh];

        for (int y = sy0; y < maxY; ++y) {
            for (int x = sx0; x < maxX; ++x) {
                index = sourceRaster.getDataBufferIndex(x, y);
                array[k++] = srcData.getElemDoubleAt(index);
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

        int firstHalfSourceTileWidth = (int)(sourceTileWidth*0.5 + 0.5);
        int firstHalfSourceTileHeight = (int)(sourceTileHeight*0.5 + 0.5);
        int secondHalfSourceTileWidth = sourceTileWidth - firstHalfSourceTileWidth;

        Arrays.fill(zeroPaddedSpectrum, 0.0);

        int R; // row index in zeroPaddedSpectrum
        for (int r = 0; r < sourceTileHeight; r++) {

            if (r < firstHalfSourceTileHeight) {
                R = r;
            } else {
                R = r + targetTileHeight - sourceTileHeight;
            }

            int s1 = r*sourceTileWidth*2; // multiply by 2 because the data is complex
            int s2 = s1 + firstHalfSourceTileWidth*2;
            int S1 = R*targetTileWidth*2;
            int S2 = S1 + 2*(targetTileWidth - secondHalfSourceTileWidth);

            System.arraycopy(spectrum, s1, zeroPaddedSpectrum, S1, firstHalfSourceTileWidth*2);
            System.arraycopy(spectrum, s2, zeroPaddedSpectrum, S2, secondHalfSourceTileWidth*2);
        }
    }

    private void saveOverSampledRealImage(Tile targetTile, double[] image) {

        final ProductData trgData = targetTile.getDataBuffer();
        Rectangle targetTileRectangle = targetTile.getRectangle();
        int tx0 = targetTileRectangle.x;
        int ty0 = targetTileRectangle.y;
        int tw = targetTileRectangle.width;
        int th = targetTileRectangle.height;

        int k = 0;
        double c = widthRatio*heightRatio;
        for (int ty = ty0; ty < ty0 + th; ty++) {
            for (int tx = tx0; tx < tx0 + tw; tx++) {
                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(tx, ty), c*image[k]);
                k++;
            }
        }
    }

    private void computeOverSampledTileForComplexImage(
            String iBandName, String qBandName, Tile iTargetTile, Tile qTargetTile, ProgressMonitor pm)
        throws OperatorException {

        Rectangle targetTileRectangle = iTargetTile.getRectangle();

        Rectangle sourceTileRectangle = getSourceTileRectangle(targetTileRectangle);

        int sx0 = sourceTileRectangle.x;
        int sy0 = sourceTileRectangle.y;
        int sourceTileWidth = sourceTileRectangle.width;
        int sourceTileHeight = sourceTileRectangle.height;
        int targetTileWidth = targetTileRectangle.width;
        int targetTileHeight = targetTileRectangle.height;

        double[] srcImage = getComplexImageSourceTileArray(sourceTileRectangle, iBandName, qBandName, pm);

        ComplexArray srcImageArray = new ComplexArray(srcImage, sourceTileHeight, sourceTileWidth, 2);

        ComplexArray spectrum = srcImageArray.fft();

        ComplexArray zeroPaddedSpecArray = zeroPaddingComplexImageSpectrum(
                sx0, sy0, sourceTileHeight, sourceTileWidth, targetTileHeight, targetTileWidth, spectrum);

        double[] overSampledImage = zeroPaddedSpecArray.ifft().values();
        
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
        double[] tranSpec = spectrum.transpose(1, 0, 2).values();
        double[] paddedSpec = new double[2*targetTileHeight*targetTileWidth];
        Arrays.fill(paddedSpec, 0.0);

        int firstHalfSourceTileWidth = (int)(sourceTileWidth*0.5 + 0.5);

        int C; // col index in zeroPaddedSpectrum
        for (int c = 0; c < sourceTileWidth; c++) {

            int d = computeDopplerCentroidFreqIndex(c + sx0, sourceTileHeight);

            if (c < firstHalfSourceTileWidth) {
                C = c;
            } else {
                C = c + targetTileWidth - sourceTileWidth;
            }

            int s1 = c*sourceTileHeight*2; // multiply by 2 because the data is complex
            int s2 = s1 + d*2;
            int S1 = C*targetTileHeight*2;
            int S2 = S1 + 2*(targetTileHeight - sourceTileHeight + d);

            System.arraycopy(tranSpec, s1, paddedSpec, S1, d*2);
            System.arraycopy(tranSpec, s2, paddedSpec, S2, (sourceTileHeight - d)*2);
        }

        ComplexArray zeroPaddedSpectrum = new ComplexArray(paddedSpec, targetTileWidth, targetTileHeight, 2);
        return zeroPaddedSpectrum.transpose(1, 0, 2);
    }

    private void saveOverSampledComplexImage(Tile iTargetTile, Tile qTargetTile, double[] image) {

        final ProductData iData = iTargetTile.getDataBuffer();
        final ProductData qData = qTargetTile.getDataBuffer();

        Rectangle targetTileRectangle = iTargetTile.getRectangle();
        int tx0 = targetTileRectangle.x;
        int ty0 = targetTileRectangle.y;
        int tw = targetTileRectangle.width;
        int th = targetTileRectangle.height;

        int k = 0;
        double c = widthRatio*heightRatio;
        for (int ty = ty0; ty < ty0 + th; ty++) {
            for (int tx = tx0; tx < tx0 + tw; tx++) {
                int index = iTargetTile.getDataBufferIndex(tx, ty);
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

        double fdc = dopplerCentroidFreq[c];
        int idxFdc = (int)(fdc * h / prf + 0.5);
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
        }
    }
}