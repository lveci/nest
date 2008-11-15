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
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductSubsetBuilder;
import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.HashMap;

/**
 * Undersample
 */

@OperatorMetadata(alias="Undersample")
public class UndersamplingOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames;

    @Parameter(valueSet = {SUB_SAMPLING, KERNEL_FILTERING}, defaultValue = SUB_SAMPLING, label="Under-Sampling Methods")
    private String method;

    @Parameter(valueSet = {SUMMARY, EDGE_DETECT, EDGE_ENHANCEMENT, LOSS_PASS, HIGH_PASS, HORIZONTAL, VERTICAL},
               defaultValue = SUMMARY, label="Filter Type")
    private String filterType;

    @Parameter(valueSet = {FILTER_SIZE_3x3, FILTER_SIZE_5x5, FILTER_SIZE_7x7},
               defaultValue = FILTER_SIZE_3x3, label="Filter Size")
    private String filterSize;

    @Parameter(description = "The kernel file", label="Kernel File")
    private File kernelFile;

    @Parameter(defaultValue = "2", label=" Sub-Sampling in X")
    private int subSamplingX;
    @Parameter(defaultValue = "2", label=" Sub-Sampling in Y")
    private int subSamplingY;

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
    private ProductReader subsetReader;

    private int filterWidth;
    private int filterHeight;
    private int sourceImageWidth;
    private int sourceImageHeight;

    private float stepRange; // step size in range direction for moving window filtering
    private float stepAzimuth; // step size in azimuth direction for moving window filtering
    private float srcRangeSpacing; // range pixel spacing of source image
    private float srcAzimuthSpacing; // azimuth pixel spacing of source image
    private float[][] kernel; // kernel for filtering
    private HashMap<String, String[]> targetBandNameToSourceBandName;

    private static final String SUB_SAMPLING = "Sub-Sampling";
    private static final String KERNEL_FILTERING = "Kernel Filtering";
    private static final String SUMMARY = "Summary";
    private static final String EDGE_DETECT = "Edge Detect";
    private static final String EDGE_ENHANCEMENT = "Edge Enhancement";
    private static final String LOSS_PASS = "Loss Pass";
    private static final String HIGH_PASS = "High Pass";
    private static final String HORIZONTAL = "Horizontal";
    private static final String VERTICAL = "Vertical";
    private static final String FILTER_SIZE_3x3 = "3x3";
    private static final String FILTER_SIZE_5x5 = "5x5";
    private static final String FILTER_SIZE_7x7 = "7x7";

    @Override
    public void initialize() throws OperatorException {

        if (method.contains(SUB_SAMPLING)) {
            initializeForSubSampling();
        } else if (method.contains(KERNEL_FILTERING)) {
            initializeForKernelFiltering();
        } else {
            throw new OperatorException("Unknown undersampling method: " + method);
        }
    }

    /**
     * Initialization for sub-sampling.
     *
     * @throws OperatorException The exceptions.
     */
    private void initializeForSubSampling() throws OperatorException {

        if (sourceBandNames == null || sourceBandNames.length == 0) {
            Band[] bands = sourceProduct.getBands();
            ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        subsetReader = new ProductSubsetBuilder();
        ProductSubsetDef subsetDef = new ProductSubsetDef();

        subsetDef.addNodeNames(sourceProduct.getTiePointGridNames());
        subsetDef.addNodeNames(sourceBandNames);
        subsetDef.setSubSampling(subSamplingX, subSamplingY);
        subsetDef.setIgnoreMetadata(false);

        try {
            targetProduct = subsetReader.readProductNodes(sourceProduct, subsetDef);
        } catch (Throwable t) {
            throw new OperatorException(t);
        }
    }

    /**
     * Initialization for kernel filtering.
     *
     * @throws OperatorException The exceptions.
     */
    private void initializeForKernelFiltering() throws OperatorException {

        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();

        getFilterDimension();
        
        getSrcImagePixelSpacings();

        computeTargetImageSizeAndPixelSpacings();

        computeRangeAzimuthStepSizes();

        getKernelFile();

        createTargetProduct();
    }

    private void getFilterDimension() {

        if (filterSize.contains("3x3")) {
            filterWidth = 3;
            filterHeight = 3;
        } else if (filterSize.contains("5x5")) {
            filterWidth = 5;
            filterHeight = 5;
        } else if (filterSize.contains("7x7")) {
            filterWidth = 7;
            filterHeight = 7;
        } else {
            throw new OperatorException("Unknown filter size: " + filterSize);
        }
    }

    /**
     * Get the range and azimuth spacings (in meter).
     */
    void getSrcImagePixelSpacings() {

        MetadataElement abs = sourceProduct.getMetadataRoot().getElement("Abstracted Metadata");
        if (abs == null) {
            throw new OperatorException("Abstracted Metadata not found");
        }

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
     * Compute target image size and range/azimuth spacings.
     *
     * @throws OperatorException The exceptions.
     */
    private void computeTargetImageSizeAndPixelSpacings() throws OperatorException {

        if (targetImageWidth != 0 && targetImageHeight != 0) {

            if (targetImageHeight <= 0 || targetImageHeight >= sourceImageHeight ||
                targetImageWidth <= 0 || targetImageWidth >= sourceImageWidth) {
                throw new OperatorException("Output image size must be positive and smaller than the source image size");
            }

            rangeSpacing = srcRangeSpacing * sourceImageWidth / targetImageWidth;
            azimuthSpacing = srcAzimuthSpacing * sourceImageHeight / targetImageHeight;

        } else if (Float.compare(widthRatio, 0.0f) != 0 && Float.compare(heightRatio, 0.0f) != 0) {

            if (widthRatio <= 0 || widthRatio >= 1 || heightRatio <= 0 || heightRatio > 1) {
                throw new OperatorException("The width or height ratio must be within range (0, 1)");
            }

            targetImageHeight = (int)(heightRatio * sourceImageHeight + 0.5f);
            targetImageWidth = (int)(widthRatio * sourceImageWidth + 0.5f);

            rangeSpacing = srcRangeSpacing / widthRatio;
            azimuthSpacing = srcAzimuthSpacing / heightRatio;

        } else if (Float.compare(rangeSpacing, 0.0f) != 0 && Float.compare(azimuthSpacing, 0.0f) != 0) {

            if (rangeSpacing <= 0.0f || rangeSpacing >= srcRangeSpacing ||
                azimuthSpacing <= 0.0f || azimuthSpacing >= srcAzimuthSpacing) {
                throw new OperatorException("The azimuth or range spacing must be positive and smaller than the source spscing");
            }

            targetImageHeight = (int)(rangeSpacing / srcRangeSpacing * sourceImageHeight + 0.5);
            targetImageWidth = (int)(azimuthSpacing / srcAzimuthSpacing * sourceImageWidth + 0.5);

        } else {
            throw new OperatorException("Please specify output image size, or row and column ratios, or pixel spacings");
        }
    }

    /**
     * Compute range and azimuth step size for kernel filtering.
     */
    private void computeRangeAzimuthStepSizes() {

        stepAzimuth = (float)(sourceImageHeight - filterHeight) / (float)(targetImageHeight - 1);
        stepRange = (float)(sourceImageWidth - filterWidth) / (float)(targetImageWidth - 1);
    }

    /**
     * Read pre-defined or user defined kernel file.
     */
    private void getKernelFile() {

        String fileName = "";
        boolean isPreDefinedKernel;

        if (kernelFile != null) { // user defined kernel file

            isPreDefinedKernel = false;
            fileName = kernelFile.getName();
            
        } else { // pre-defined kernel file with user specified filter diemnsion

            isPreDefinedKernel = true;

            if(filterType.contains(SUMMARY)) {
                fileName = "sum_" + filterHeight + "_" + filterWidth + ".ker";
            } else if (filterType.contains(EDGE_DETECT)) {
                fileName = "edd_" + filterHeight + "_" + filterWidth + ".ker";
            } else if (filterType.contains(EDGE_ENHANCEMENT)) {
                fileName = "ede_" + filterHeight + "_" + filterWidth + ".ker";
            } else if (filterType.contains(LOSS_PASS)) {
                fileName = "lop_" + filterHeight + "_" + filterWidth + ".ker";
            } else if (filterType.contains(HIGH_PASS)) {
                fileName = "hip_" + filterHeight + "_" + filterWidth + ".ker";
            } else if (filterType.contains(HORIZONTAL)) {
                fileName = "hor_" + filterHeight + "_" + filterWidth + ".ker";
            } else if (filterType.contains(VERTICAL)) {
                fileName = "ver_" + filterHeight + "_" + filterWidth + ".ker";
            } else {
                throw new OperatorException("Incorrect filter type: " + filterType);
            }
        }

        final File file = getResFile(fileName);
        kernel = readFile(file.getAbsolutePath(), isPreDefinedKernel);
    }

    private static File getResFile(String fileName) {
        final String homeUrl = System.getProperty("nest.home", ".");
        final String path = homeUrl + File.separator + "res" + File.separator + fileName;
        return new File(path);
    }
    
    /**
     * Read data from kernel file and save them in a 2D array.
     *
     * @param fileName The kernel file name
     * @return array The 2D array holding kernel data
     */
    private float[][] readFile(String fileName, boolean isPreDefinedKernel) {

        // get reader
        FileInputStream stream;
        try {
            stream = new FileInputStream(fileName);
        } catch(FileNotFoundException e) {
            throw new OperatorException("File not found: " + fileName);
        }

        final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        // read data from file and save them in 2-D array
        String line = "";
        StringTokenizer st;
        float[][] array;
        int rowIdx = 0;

        try {
            // get the 1st line
            if ((line = reader.readLine()) == null) {
                throw new OperatorException("Empty file: " + fileName);
            }

            st = new StringTokenizer(line);
            if (st.countTokens() != 2) {
                throw new OperatorException("Incorrect file format: " + fileName);
            }

            final int numRows = Integer.parseInt(st.nextToken());
            final int numCols = Integer.parseInt(st.nextToken());
            array = new float[numRows][numCols];

            // get the rest numRows lines
            while((line = reader.readLine()) != null) {

                st = new StringTokenizer(line);
                if (st.countTokens() != numCols) {
                    throw new OperatorException("Incorrect file format: " + fileName);
                }

                for (int j = 0; j < numCols; j++) {
                    array[rowIdx][j] = Float.parseFloat(st.nextToken());
                }
                rowIdx++;
            }

            if (numRows != rowIdx) {
                throw new OperatorException("Incorrect number of lines in file: " + fileName);
            }

            reader.close();
            stream.close();

            if (isPreDefinedKernel) {
                if (filterHeight != numRows || filterWidth != numCols) {
                    throw new OperatorException("Kernel size does not match given filter size");
                }
            } else { // user defined kernel
                filterHeight = numRows;
                filterWidth = numCols;
            }

        } catch (IOException e) {
            throw new OperatorException(e);
        }
        return array;
    }

    /**
     * Create target product.
     */
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
    }

    /**
     * Add user selected bands to the target product.
     */
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

        String targetBandName;
        targetBandNameToSourceBandName = new HashMap<String, String[]>();
        for (int i = 0; i < sourceBands.length; i++) {

            final Band srcBand = sourceBands[i];
            final String unit = srcBand.getUnit();
            if(unit == null) {
                throw new OperatorException("band "+srcBand.getName()+" requires a unit");
            }

            String targetUnit = "";

            if (unit.contains("phase")) {

                continue;

            } else if (unit.contains("imaginary")) {

                throw new OperatorException("Real and imaginary bands should be selected in pairs");

            } else if (unit.contains("real")) {

                if (i == sourceBands.length - 1) {
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");
                }
                final String nextUnit = sourceBands[i+1].getUnit();
                if (nextUnit == null || !nextUnit.contains("imaginary")) {
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");
                }
                final String[] srcBandNames = new String[2];
                srcBandNames[0] = srcBand.getName();
                srcBandNames[1] = sourceBands[i+1].getName();
                final String pol = MultilookOp.getPolarizationFromBandName(srcBandNames[0]);
                if (pol != null) {
                    targetBandName = "Amplitude_" + pol.toUpperCase();
                } else {
                    targetBandName = "Amplitude";
                }
                ++i;
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                    targetUnit = "amplitude";
                }

            } else {

                final String[] srcBandNames = {srcBand.getName()};
                targetBandName = srcBand.getName();
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                    targetUnit = unit;
                }
            }

            if(targetProduct.getBand(targetBandName) == null) {

                final Band targetBand = new Band(targetBandName,
                                           ProductData.TYPE_FLOAT64,
                                           targetImageWidth,
                                           targetImageHeight);

                targetBand.setUnit(targetUnit);
                targetProduct.addBand(targetBand);
            }
        }

    }

    /**
     * Update metadata in the target product.
     */
    private void updateTargetProductMetadata() {

        MetadataElement abs = targetProduct.getMetadataRoot().getElement("Abstracted Metadata");
        if (abs == null) {
            throw new OperatorException("Abstracted Metadata not found");
        }

        MetadataAttribute azimuthSpacingAttr = abs.getAttribute(AbstractMetadata.azimuth_spacing);
        if (azimuthSpacingAttr == null) {
            throw new OperatorException(AbstractMetadata.azimuth_spacing + " not found");
        }
        azimuthSpacingAttr.getData().setElemFloat((float)(azimuthSpacing));

        MetadataAttribute rangeSpacingAttr = abs.getAttribute(AbstractMetadata.range_spacing);
        if (rangeSpacingAttr == null) {
            throw new OperatorException(AbstractMetadata.range_spacing + " not found");
        }
        rangeSpacingAttr.getData().setElemFloat((float)(rangeSpacing));

        MetadataAttribute lineTimeIntervalAttr = abs.getAttribute(AbstractMetadata.line_time_interval);
        if (lineTimeIntervalAttr == null) {
            throw new OperatorException(AbstractMetadata.line_time_interval + " not found");
        }
        float oldLineTimeInterval = lineTimeIntervalAttr.getData().getElemFloat();
        lineTimeIntervalAttr.getData().setElemFloat(oldLineTimeInterval*stepAzimuth);

        MetadataAttribute firstLineTimeAttr = abs.getAttribute(AbstractMetadata.first_line_time);
        if (firstLineTimeAttr == null) {
            throw new OperatorException(AbstractMetadata.first_line_time + " not found");
        }
        String oldFirstLineTime = firstLineTimeAttr.getData().getElemString();
        int idx = oldFirstLineTime.lastIndexOf(':') + 1;
        String oldSecondsStr = oldFirstLineTime.substring(idx);
        double oldSeconds = Double.parseDouble(oldSecondsStr);
        double newSeconds = oldSeconds + oldLineTimeInterval*((filterHeight + stepAzimuth)/2.0);
        String newFirstLineTime = String.valueOf(oldFirstLineTime.subSequence(0, idx)) + newSeconds + "000000";
        abs.removeAttribute(firstLineTimeAttr);
        abs.addAttribute(new MetadataAttribute(
                AbstractMetadata.first_line_time, ProductData.createInstance(newFirstLineTime.substring(0,27)), false));
    }


    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        if (method.contains(SUB_SAMPLING)) {
            computeTileUsingSubSampling(targetBand, targetTile, pm);
        } else if (method.contains(KERNEL_FILTERING)) {
            computeTileUsingKernelFiltering(targetBand, targetTile, pm);
        } else {
            throw new OperatorException("Unknown undersampling method: " + method);
        }
    }

    private void computeTileUsingSubSampling(Band targetBand, Tile targetTile, ProgressMonitor pm) {

        ProductData destBuffer = targetTile.getRawSamples();
        Rectangle rectangle = targetTile.getRectangle();
        try {
            subsetReader.readBandRasterData(targetBand,
                                            rectangle.x,
                                            rectangle.y,
                                            rectangle.width,
                                            rectangle.height,
                                            destBuffer, pm);
            targetTile.setRawSamples(destBuffer);
        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }

    private void computeTileUsingKernelFiltering(Band targetBand, Tile targetTile, ProgressMonitor pm) {

        ProductData trgData = targetTile.getDataBuffer();
        Rectangle targetTileRectangle = targetTile.getRectangle();
        String bandName = targetBand.getName();

        final int tx0 = targetTileRectangle.x;
        final int ty0 = targetTileRectangle.y;
        final int tw  = targetTileRectangle.width;
        final int th  = targetTileRectangle.height;

        double filteredValue;
        final int maxy = ty0 + th;
        final int maxx = tx0 + tw;
        for (int ty = ty0; ty < maxy; ty++) {
            for (int tx = tx0; tx < maxx; tx++) {
                filteredValue = getFilteredValue(tx, ty, bandName, pm);
                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(tx, ty), filteredValue);
            }
        }
    }

    private double getFilteredValue(int tx, int ty, String bandName, ProgressMonitor pm) {

        final int x0 = (int)(tx * stepRange + 0.5);
        final int y0 = (int)(ty * stepAzimuth + 0.5);
        final int maxY = y0 + filterHeight;
        final int maxX = x0 + filterWidth;

        Rectangle sourceTileRectangle = new Rectangle(x0, y0, filterWidth, filterHeight);

        Tile sourceRaster1 = null;
        Tile sourceRaster2 = null;
        ProductData srcData1 = null;
        ProductData srcData2 = null;

        final String[] srcBandNames = targetBandNameToSourceBandName.get(bandName);
        if (srcBandNames.length == 1) {
            sourceBand1 = sourceProduct.getBand(srcBandNames[0]);
            sourceRaster1 = getSourceTile(sourceBand1, sourceTileRectangle, pm);
            srcData1 = sourceRaster1.getDataBuffer();
        } else {
            sourceBand1 = sourceProduct.getBand(srcBandNames[0]);
            sourceBand2 = sourceProduct.getBand(srcBandNames[1]);
            sourceRaster1 = getSourceTile(sourceBand1, sourceTileRectangle, pm);
            sourceRaster2 = getSourceTile(sourceBand2, sourceTileRectangle, pm);
            srcData1 = sourceRaster1.getDataBuffer();
            srcData2 = sourceRaster2.getDataBuffer();
        }

        final int bandUnit = MultilookOp.getSourceBandUnit(sourceBand1);

        double filteredValue = 0.0;
        for (int y = y0; y < maxY; y++) {
            for (int x = x0; x < maxX; x++) {

                final int index = sourceRaster1.getDataBufferIndex(x, y);
                final float weight = kernel[maxY - 1 - y][maxX - 1 - x];

                if (bandUnit == MultilookOp.INTENSITY_DB) {

                    final double dn = srcData1.getElemDoubleAt(index);
                    filteredValue += Math.pow(10, dn / 10.0)*weight; // dB to linear

                } else if (bandUnit == MultilookOp.AMPLITUDE || bandUnit == MultilookOp.INTENSITY) {

                    filteredValue += srcData1.getElemDoubleAt(index)*weight;

                } else { // COMPLEX

                    final double i = srcData1.getElemDoubleAt(index);
                    final double q = srcData2.getElemDoubleAt(index);
                    filteredValue += Math.sqrt(i*i + q*q)*weight;
                }
            }
        }

        if (bandUnit == MultilookOp.INTENSITY_DB) {
            filteredValue = 10.0*Math.log10(filteredValue); // linear to dB
        }
        return filteredValue;
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
            super(UndersamplingOp.class);
        }
    }
}