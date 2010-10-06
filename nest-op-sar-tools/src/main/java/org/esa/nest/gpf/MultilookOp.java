/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
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
package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
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
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Original SAR images generally appears with inherent speckle noise. Multi-look integration is one category
 * of methods to reduce this inherent speckle noise. The frequency-domain method consis of
 *
 * (1) dividing the bandwidth of the azimuth spectrum of the image into L segments (called looks),
 * (2) forming L independent images from these spectra, and
 * (3) incoherently averaing them.
 *
 * There is also a time-domain method which produce the multi-looked image by averaging the single look image
 * with a small sliding window.
 *
 * This operator implements the simple time-domain method. The multi-looked image is produced according to a
 * user specified factor and a default factor determined by range and azimuth spacings of the original image.
 * As a result, image with equal pixel spacing is produced.
 */

@OperatorMetadata(alias="Multilook",
        category = "SAR Tools",
        description="Averages the power across a number of lines in both the azimuth and range directions")
public final class MultilookOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    private String[] sourceBandNames;

    @Parameter(description = "The user defined number of range looks", interval = "[1, *)", defaultValue = "1",
                label="Number of Range Looks")
    private int nRgLooks = 1;

    @Parameter(description = "The user defined number of azimuth looks", interval = "[1, *)", defaultValue = "1",
                label="Number of Azimuth Looks")
    private int nAzLooks = 1;

    @Parameter(defaultValue="Currently, detection for complex data is performed without any resampling", label="Note")
    String note;

    private MetadataElement absRoot = null;

    private double azimuthLooks; // original azimuth_looks from metadata
    private double rangeLooks;   // original range_looks from metadata
    private int sourceImageWidth;
    private int sourceImageHeight;
    private int targetImageWidth;
    private int targetImageHeight;

    private double rangeSpacing;
    private double azimuthSpacing;

    private final HashMap<String, String[]> targetBandNameToSourceBandName = new HashMap<String, String[]>();

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
            absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);

            getRangeAzimuthSpacing();

            getRangeAzimuthLooks();

            getSourceImageDimension();

            createTargetProduct();

        } catch(Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetBand The target band.
     * @param targetTile The current tile associated with the target band to be computed.
     * @param pm         A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the target raster.
     */
    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetTileRectangle = targetTile.getRectangle();
        final int tx0 = targetTileRectangle.x;
        final int ty0 = targetTileRectangle.y;
        final int tw  = targetTileRectangle.width;
        final int th  = targetTileRectangle.height;

        final int x0 = tx0 * nRgLooks;
        final int y0 = (int)(ty0 * nAzLooks);
        final int w  = tw * nRgLooks;
        final int h  = (int)(th * nAzLooks);
        final Rectangle sourceTileRectangle = new Rectangle(x0, y0, w, h);

        //System.out.println("tx0 = " + tx0 + ", ty0 = " + ty0 + ", tw = " + tw + ", th = " + th);
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        try {
            Tile sourceRaster1;
            Tile sourceRaster2 = null;
            final String[] srcBandNames = targetBandNameToSourceBandName.get(targetBand.getName());
            Band sourceBand1;
            if (srcBandNames.length == 1) {
                sourceBand1 = sourceProduct.getBand(srcBandNames[0]);
                sourceRaster1 = getSourceTile(sourceBand1, sourceTileRectangle, pm);
                if (sourceRaster1 == null) {
                    throw new OperatorException("Cannot get source tile");
                }
            } else {
                sourceBand1 = sourceProduct.getBand(srcBandNames[0]);
                Band sourceBand2 = sourceProduct.getBand(srcBandNames[1]);
                sourceRaster1 = getSourceTile(sourceBand1, sourceTileRectangle, pm);
                sourceRaster2 = getSourceTile(sourceBand2, sourceTileRectangle, pm);
                if (sourceRaster1 == null || sourceRaster2 == null) {
                    throw new OperatorException("Cannot get source tile");
                }
            }

            final Unit.UnitType bandUnitType = Unit.getUnitType(sourceBand1);

            computeMultiLookImageUsingTimeDomainMethod(tx0, ty0, tw, th, sourceRaster1, sourceRaster2, targetTile, bandUnitType);
        } catch(Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        } finally {
            pm.done();
        }
    }

    /**
     * Get the range and azimuth spacings (in meter).
     * @throws Exception when metadata is missing or equal to default no data value
     */
    private void getRangeAzimuthSpacing() throws Exception {

        rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
        azimuthSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_spacing);
        //System.out.println("Range spacing is " + rangeSpacing);
        //System.out.println("Azimuth spacing is " + azimuthSpacing);
    }

    /**
     * Get azimuth and range looks.
     * @throws Exception when metadata is missing or equal to default no data value
     */
    private void getRangeAzimuthLooks() throws Exception {

        azimuthLooks = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_looks);
        rangeLooks = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_looks);
        //System.out.println("Azimuth looks is " + azimuthLooks);
        //System.out.println("Range looks is " + rangeLooks);
    }

    /**
     * Get source image dimension.
     */
    private void getSourceImageDimension() {
        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();
        //System.out.println("Source image width = " + sourceImageWidth);
        //System.out.println("Source image height = " + sourceImageHeight);
    }

    /**
     * Create target product.
     * @throws Exception The exception.
     */
    private void createTargetProduct() throws Exception {

        targetImageWidth = sourceImageWidth / nRgLooks;
        targetImageHeight = sourceImageHeight / nAzLooks;

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    targetImageWidth,
                                    targetImageHeight);

        addSelectedBands();

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        //ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        //ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        addGeoCoding();

        updateTargetProductMetadata();
    }

    private void addGeoCoding() {

        TiePointGrid lat = OperatorUtils.getLatitude(sourceProduct);
        TiePointGrid lon = OperatorUtils.getLongitude(sourceProduct);
        TiePointGrid incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);
        TiePointGrid slantRgTime = OperatorUtils.getSlantRangeTime(sourceProduct);
        if (lat == null || lon == null || incidenceAngle == null || slantRgTime == null) { // for unit test
            ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
            ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
            return;
        }

        int gridWidth = 11;
        int gridHeight = 11;
        float subSamplingX = targetImageWidth / (gridWidth - 1.0f);
        float subSamplingY = targetImageHeight / (gridHeight - 1.0f);
        PixelPos[] newTiePointPos = new PixelPos[gridWidth*gridHeight];

        int k = 0;
        for (int j = 0; j < gridHeight; j++) {
            float y = (nAzLooks - 1)/2 + Math.min(j*subSamplingY, targetImageHeight - 1)*nAzLooks;
            for (int i = 0; i < gridWidth; i++) {
                float x = (nRgLooks - 1)/2 + Math.min(i*subSamplingX, targetImageWidth - 1)*nRgLooks;
                newTiePointPos[k] = new PixelPos();
                newTiePointPos[k].x = x;
                newTiePointPos[k].y = y;
                k++;
            }
        }

        OperatorUtils.createNewTiePointGridsAndGeoCoding(
                sourceProduct,
                targetProduct,
                gridWidth,
                gridHeight,
                subSamplingX,
                subSamplingY,
                newTiePointPos);
    }

    /**
     * Update metadata in the target product.
     */
    private void updateTargetProductMetadata() {

        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.multilook_flag, 1);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.azimuth_looks, azimuthLooks*nAzLooks);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.range_looks, rangeLooks*nRgLooks);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.azimuth_spacing, azimuthSpacing*nAzLooks);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.range_spacing, rangeSpacing*nRgLooks);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.num_output_lines, targetImageHeight);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.num_samples_per_line, targetImageWidth);

        final float oldLineTimeInterval = (float)absTgt.getAttributeDouble(AbstractMetadata.line_time_interval);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.line_time_interval, oldLineTimeInterval*nAzLooks);

        final double oldNearEdgeSlantRange = absTgt.getAttributeDouble(AbstractMetadata.slant_range_to_first_pixel);
        final double newNearEdgeSlantRange = oldNearEdgeSlantRange + rangeSpacing*(nRgLooks - 1)/2.0;
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.slant_range_to_first_pixel, newNearEdgeSlantRange);

        double oldFirstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD(); // in days
        double newFirstLineUTC = oldFirstLineUTC + oldLineTimeInterval*((nAzLooks - 1)/2.0) / 86400.0;
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.first_line_time, new ProductData.UTC(newFirstLineUTC));
    }

    private void addSelectedBands() throws OperatorException {

        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            final ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                if(!(band instanceof VirtualBand))
                    bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        final Band[] sourceBands = new Band[sourceBandNames.length];
        for (int i = 0; i < sourceBandNames.length; i++) {
            final String sourceBandName = sourceBandNames[i];
            final Band sourceBand = sourceProduct.getBand(sourceBandName);
            if (sourceBand == null) {
                throw new OperatorException("Source band not found: " + sourceBandName);
            }
            sourceBands[i] = sourceBand;
        }

        String targetBandName;
        for (int i = 0; i < sourceBands.length; i++) {

            final Band srcBand = sourceBands[i];
            String unit = srcBand.getUnit();
            if(unit == null) {
                unit = Unit.AMPLITUDE;  // assume amplitude
            }

            String targetUnit = "";

            if (unit.equals(Unit.IMAGINARY)) {

                throw new OperatorException("Real and imaginary bands should be selected in pairs");

            } else if (unit.equals(Unit.REAL)) {

                if (i == sourceBands.length - 1) {
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");
                }
                final String nextUnit = sourceBands[i+1].getUnit();
                if (nextUnit == null || !nextUnit.equals(Unit.IMAGINARY)) {
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");
                }
                final String[] srcBandNames = new String[2];
                srcBandNames[0] = srcBand.getName();
                srcBandNames[1] = sourceBands[i+1].getName();
                targetBandName = "Intensity";
                final String suff = OperatorUtils.getSuffixFromBandName(srcBandNames[0]);
                if (suff != null) {
                    targetBandName += "_" + suff;
                }
                final String pol = OperatorUtils.getBandPolarization(srcBandNames[0], absRoot);
                if (pol != null && !pol.isEmpty() && !targetBandName.toLowerCase().contains(pol)) {
                    targetBandName += "_" + pol.toUpperCase();
                }
                ++i;
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                    targetUnit = Unit.INTENSITY;
                }

            } else {

                final String[] srcBandNames = {srcBand.getName()};
                targetBandName = srcBand.getName();
                final String pol = OperatorUtils.getBandPolarization(targetBandName, absRoot);
                if (pol != null && !pol.isEmpty() && !targetBandName.toLowerCase().contains(pol)) {
                    targetBandName += "_" + pol.toUpperCase();
                }
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                    targetUnit = unit;
                }
            }

            if(targetProduct.getBand(targetBandName) == null) {

                final Band targetBand = new Band(targetBandName,
                                           ProductData.TYPE_FLOAT32,
                                           targetImageWidth,
                                           targetImageHeight);

                targetBand.setUnit(targetUnit);
                targetProduct.addBand(targetBand);
            }
        }
    }

    /**
     * Compute multi-looked image using time domain method.
     *
     * @param tx0 The x coordinate of the upper left point in the current target tile.
     * @param ty0 The y coordinate of the upper left point in the current target tile.
     * @param tw  The width of the current target tile.
     * @param th  The height of the current target tile.
     * @param sourceRaster1 The source raster for the 1st band.
     * @param sourceRaster2 The source raster for the 2nd band.
     * @param targetTile The current target tile associated with the target band to be computed.
     * @param bandUnit Integer indicating the unit of source data.
     */
    private void computeMultiLookImageUsingTimeDomainMethod(
            int tx0, int ty0, int tw, int th, Tile sourceRaster1, Tile sourceRaster2, Tile targetTile, Unit.UnitType bandUnit) {

        final ProductData trgData = targetTile.getDataBuffer();

        final ProductData srcData1 = sourceRaster1.getDataBuffer();
        ProductData srcData2 = null;
        if(sourceRaster2 != null)
            srcData2 = sourceRaster2.getDataBuffer();

        final int tileOffset = targetTile.getScanlineOffset();
        final int tileStride = targetTile.getScanlineStride();
        final int tileMinX = targetTile.getMinX();
        final int tileMinY = targetTile.getMinY();

        double meanValue;
        final int maxy = ty0 + th;
        final int maxx = tx0 + tw;
        for (int ty = ty0; ty < maxy; ty++) {
            final int stride = ((ty - tileMinY) * tileStride) + tileOffset;
            for (int tx = tx0; tx < maxx; tx++) {
                meanValue = getMeanValue(tx, ty, sourceRaster1, srcData1, srcData2, nRgLooks, nAzLooks, bandUnit);
                trgData.setElemDoubleAt((tx - tileMinX) + stride, meanValue);
            }
        }
    }

    /**
     * Compute the mean value of pixels of the source image in the sliding window.
     * @param tx The x coordinate of a pixel in the current target tile.
     * @param ty The y coordinate of a pixel in the current target tile.
     * @param sourceRaster1 The source raster for the 1st band.
     * @param srcData1 The product data for i band in case of complex product.
     * @param srcData2 The product data for q band in case of complex product.
     * @param nRgLooks
     * @param nAzLooks
     * @param bandUnit Integer indicating the unit of source data.
     * @return The mean value.
     */
    private static double getMeanValue(final int tx, final int ty, final Tile sourceRaster1,
                                       final ProductData srcData1, final ProductData srcData2,
                                       final int nRgLooks, final int nAzLooks,
                                       final Unit.UnitType bandUnit) {

        final int xStart = tx * nRgLooks;
        final int yStart = ty * nAzLooks;
        final int xEnd = xStart + nRgLooks;
        final int yEnd = yStart + nAzLooks;

        final int tileOffset = sourceRaster1.getScanlineOffset();
        final int tileStride = sourceRaster1.getScanlineStride();
        final int tileMinX = sourceRaster1.getMinX();
        final int tileMinY = sourceRaster1.getMinY();

        double meanValue = 0.0;
        if (bandUnit == Unit.UnitType.INTENSITY_DB || bandUnit == Unit.UnitType.AMPLITUDE_DB) {
            for (int y = yStart; y < yEnd; y++) {
                final int stride = ((y - tileMinY) * tileStride) + tileOffset;
                for (int x = xStart; x < xEnd; x++) {
                    meanValue += Math.pow(10, srcData1.getElemDoubleAt((x - tileMinX) + stride) / 10.0); // dB to linear
                }
            }

            meanValue /= (nRgLooks * nAzLooks);
            return 10.0*Math.log10(meanValue); // linear to dB
        } else if (bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) { // COMPLEX
            double i, q;
            int index;
            for (int y = yStart; y < yEnd; y++) {
                final int stride = ((y - tileMinY) * tileStride) + tileOffset;
                for (int x = xStart; x < xEnd; x++) {
                    index = (x - tileMinX) + stride;
                    i = srcData1.getElemDoubleAt(index);
                    q = srcData2.getElemDoubleAt(index);
                    meanValue += i*i + q*q;
                }
            }
        } else {
            for (int y = yStart; y < yEnd; y++) {
                final int stride = ((y - tileMinY) * tileStride) + tileOffset;
                for (int x = xStart; x < xEnd; x++) {
                    meanValue += srcData1.getElemDoubleAt((x - tileMinX) + stride);
                }
            }
        }

        return meanValue / (nRgLooks * nAzLooks);
    }

    /**
     * Compute number of azimuth looks and the mean ground pixel spacings for given number of range looks.
     * @param srcProduct The source product.
     * @param nRgLooks The number of range looks.
     * @param param The computed parameters.
     * @throws Exception The exception.
     */
    public static void getDerivedParameters(Product srcProduct, int nRgLooks, DerivedParams param) throws Exception {

        final MetadataElement abs = AbstractMetadata.getAbstractedMetadata(srcProduct);
        final boolean srgrFlag = AbstractMetadata.getAttributeBoolean(abs, AbstractMetadata.srgr_flag);
        final double rangeSpacing = AbstractMetadata.getAttributeDouble(abs, AbstractMetadata.range_spacing);
        final double azimuthSpacing = AbstractMetadata.getAttributeDouble(abs, AbstractMetadata.azimuth_spacing);

        double groundRangeSpacing = rangeSpacing;
        if (!srgrFlag) {
            final double incidenceAngleAtCentreRangePixel = getIncidenceAngleAtCentreRangePixel(srcProduct);
            groundRangeSpacing /= Math.sin(incidenceAngleAtCentreRangePixel*MathUtils.DTOR);
        }

        final int nAzLooks = Math.max(1, (int)((double)nRgLooks * groundRangeSpacing / azimuthSpacing + 0.5));
        final float meanGRSqaurePixel = (float)((nRgLooks*groundRangeSpacing + nAzLooks*azimuthSpacing)*0.5);
        param.nAzLooks = nAzLooks;
        param.meanGRSqaurePixel = meanGRSqaurePixel;
    }

    /**
     * Get incidence angle at centre range pixel (in degree).
     * @param srcProduct The source product.
     * @throws OperatorException
     * @return The incidence angle.
     */
    private static double getIncidenceAngleAtCentreRangePixel(Product srcProduct) throws OperatorException {

        final int sourceImageWidth = srcProduct.getSceneRasterWidth();
        final int sourceImageHeight = srcProduct.getSceneRasterHeight();
        final int x = sourceImageWidth / 2;
        final int y = sourceImageHeight / 2;
        final TiePointGrid incidenceAngle = OperatorUtils.getIncidenceAngle(srcProduct);
        if(incidenceAngle == null) {
            throw new OperatorException("incidence_angle tie point grid not found in product");
        }
        return incidenceAngle.getPixelFloat((float)x, (float)y);
    }

    static class DerivedParams {
        int nAzLooks = 0;
        float meanGRSqaurePixel = 0;
    }

    /**
     * Set the number of range looks. This method is for unit test only.
     * @param numRangelooks The number of range looks.
     */
    public void setNumRangeLooks(int numRangelooks) {
        nRgLooks = numRangelooks;
    }

    /**
     * Set the number of azimuth looks. This method is for unit test only.
     * @param numAzimuthlooks The number of azimuth looks.
     */
    public void setNumAzimuthLooks(int numAzimuthlooks) {
        nAzLooks = numAzimuthlooks;
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
            super(MultilookOp.class);
            super.setOperatorUI(MultilookOpUI.class);
        }
    }
}