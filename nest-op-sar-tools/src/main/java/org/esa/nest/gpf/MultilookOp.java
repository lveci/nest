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
import org.esa.beam.util.math.MathUtils;

import javax.media.jai.JAI;
import javax.media.jai.InterpolationNearest;
import java.awt.*;
import java.awt.image.renderable.ParameterBlock;

/*
 * todo Incidence angle, azimuth spacing, range spacing, azimuth looks and range looks should all be obtained
 * todo from the abstracted metadata, mission type should not be used.
 */
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

@OperatorMetadata(alias="Multilook")
public class MultilookOp extends Operator {

    @SourceProduct
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The user defined multi-look factor", interval = "[2, *)", defaultValue = "2")
    private int multiLookFactor;

    private Band sourceBand1;
    private Band sourceBand2;
    private Band targetBand;

    private String sampleType;
    private String missionType;
    private boolean srgrFlag;
    private int numAzimuthLooks;
    private int numRangeLooks;
    private int azimuthFactor;
    private int rangeFactor;
    private int sourceImageWidth;
    private int sourceImageHeight;
    private double rangeSpacing;
    private double azimuthSpacing;
    private double incidenceAngleAtCentreRangePixel; // in degree

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

        getSampleType();
        getMissionType();
        getSRGRFlag();
        getRangeAzimuthSpacing();
        getRangeAzimuthLooks();
        getSourceImageDimension();
        if (!srgrFlag) {
            getIncidenceAngleAtCentreRangePixel();
        }
        computeRangeAzimuthMultiLookFactors();
        createTargetProduct();

        if (sampleType.equals("DETECTED")) {
            sourceBand1 = sourceProduct.getBandAt(0);
        } else {
            sourceBand1 = sourceProduct.getBandAt(0);
            sourceBand2 = sourceProduct.getBandAt(1);
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

        if(targetBand.isSynthetic())
            return;

        Rectangle targetTileRectangle = targetTile.getRectangle();
        int tx0 = targetTileRectangle.x;
        int ty0 = targetTileRectangle.y;
        int tw  = targetTileRectangle.width;
        int th  = targetTileRectangle.height;

        int x0 = tx0 * rangeFactor;
        int y0 = ty0 * azimuthFactor;
        int w  = tw * rangeFactor;
        int h  = th * azimuthFactor;
        Rectangle sourceTileRectangle = new Rectangle(x0, y0, w, h);

        System.out.println("tx0 = " + tx0 + ", ty0 = " + ty0 + ", tw = " + tw + ", th = " + th);
        System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        Tile sourceRaster1 = null;
        Tile sourceRaster2 = null;
        if (sampleType.equals("DETECTED")) {
            sourceRaster1 = getSourceTile(sourceBand1, sourceTileRectangle, pm);
        } else {
            sourceRaster1 = getSourceTile(sourceBand1, sourceTileRectangle, pm);
            sourceRaster2 = getSourceTile(sourceBand2, sourceTileRectangle, pm);
        }

        computeMultiLookImageUsingTimeDomainMethod(tx0, ty0, tw, th, sourceRaster1, sourceRaster2, targetTile);
    }

    /**
     * Get the sample type.
     */
    void getSampleType() {

        MetadataElement abs = sourceProduct.getMetadataRoot().getElement("Abstracted Metadata");
        if (abs == null) {
            throw new OperatorException("Abstracted Metadata not found");
        }

        MetadataAttribute sampleTypeAttr = abs.getAttribute("SAMPLE_TYPE");
        if (sampleTypeAttr == null) {
            throw new OperatorException("sample_type not found");
        }

        sampleType = sampleTypeAttr.getData().getElemString();
        System.out.println("Sample type is " + sampleType);
    }

    /**
     * Get the mission type.
     */
    void getMissionType() {

        MetadataElement abs = sourceProduct.getMetadataRoot().getElement("Abstracted Metadata");
        if (abs == null) {
            throw new OperatorException("Abstracted Metadata not found");
        }

        MetadataAttribute missionTypeAttr = abs.getAttribute("MISSION");
        if (missionTypeAttr == null) {
            throw new OperatorException("mission not found");
        }

        missionType = missionTypeAttr.getData().getElemString();
        System.out.println("Mission is " + missionType);
    }

    /**
     * Get srgr flag.
     */
    void getSRGRFlag() {

        MetadataElement abs = sourceProduct.getMetadataRoot().getElement("Abstracted Metadata");
        if (abs == null) {
            throw new OperatorException("Abstracted Metadata not found");
        }

        MetadataAttribute attr = abs.getAttribute("srgr_flag");
        if (attr == null) {
            throw new OperatorException("srgr_flag not found");
        }

        srgrFlag = attr.getData().getElemBoolean();
        System.out.println("SRGR flag is " + srgrFlag);
    }

    /**
     * Get the range and azimuth spacings (in meter).
     */
    void getRangeAzimuthSpacing() {

        MetadataAttribute rangeSpacingAttr = null;
        MetadataAttribute azimuthSpacingAttr = null;

        if (missionType.contains("ENVISAT")) {

            // get range spacing (in m) from abstracted metadata
            MetadataElement abs = sourceProduct.getMetadataRoot().getElement("Abstracted Metadata");
            if (abs == null) {
                throw new OperatorException("Abstracted Metadata not found");
            }

            rangeSpacingAttr = abs.getAttribute("range_spacing");
            if (rangeSpacingAttr == null) {
                throw new OperatorException("range_spacing not found");
            }

            azimuthSpacingAttr = abs.getAttribute("azimuth_spacing");
            if (azimuthSpacingAttr == null) {
                throw new OperatorException("azimuth_spacing not found");
            }

        } else if (missionType.contains("ERS")) {

            // For now, get azimuth and range spacings from ERS metadata
            // Fields 121 and 122 in PRI Data Set Summary Record (in m)
            MetadataElement facility = sourceProduct.getMetadataRoot().getElement("Leader").getElement("Scene Parameters");
            if (facility == null) {
                throw new OperatorException("Scene Parameters not found");
            }

            rangeSpacingAttr = facility.getAttribute("pixel spacing");
            if (rangeSpacingAttr == null) {
                throw new OperatorException("Pixel spacing not found");
            }

            azimuthSpacingAttr = facility.getAttribute("line spacing");
            if (azimuthSpacingAttr == null) {
                throw new OperatorException("Line spacing not found");
            }
        }

        rangeSpacing = rangeSpacingAttr.getData().getElemFloat();
        azimuthSpacing = azimuthSpacingAttr.getData().getElemFloat();
        System.out.println("Range spacing is " + rangeSpacing);
        System.out.println("Azimuth spacing is " + azimuthSpacing);
    }

    /**
     * Get azimuth and range looks.
     */
    void getRangeAzimuthLooks() {

        MetadataAttribute azimuthLooksAttr = null;
        MetadataAttribute rangeLooksAttr = null;

        if (missionType.contains("ENVISAT")) {

            MetadataElement abs = sourceProduct.getMetadataRoot().getElement("Abstracted Metadata");
            if (abs == null) {
                throw new OperatorException("Abstracted Metadata not found");
            }

            azimuthLooksAttr = abs.getAttribute("AZIMUTH_LOOKS");
            if (azimuthLooksAttr == null) {
                throw new OperatorException("azimuth_looks not found");
            }

            rangeLooksAttr = abs.getAttribute("RANGE_LOOKS");
            if (rangeLooksAttr == null) {
                throw new OperatorException("range_looks not found");
            }

        } else if (missionType.contains("ERS")) {

            // For now, get azimuth and range looks from ERS metadata
            // Fields 88 and 89 in PRI Data Set Summary Record (in m)
            MetadataElement facility = sourceProduct.getMetadataRoot().getElement("Leader").getElement("Scene Parameters");
            if (facility == null) {
                throw new OperatorException("Scene Parameters not found");
            }

            azimuthLooksAttr = facility.getAttribute("Nominal number of looks processed in azimuth");
            if (azimuthLooksAttr == null) {
                throw new OperatorException("Nominal number of looks processed in azimuth not found");
            }

            rangeLooksAttr = facility.getAttribute("Nominal number of looks processed in range");
            if (rangeLooksAttr == null) {
                throw new OperatorException("Nominal number of looks processed in range not found");
            }
        }

        numAzimuthLooks = azimuthLooksAttr.getData().getElemInt();
        numRangeLooks = rangeLooksAttr.getData().getElemInt();
        System.out.println("Azimuth looks is " + numAzimuthLooks);
        System.out.println("Range looks is " + numRangeLooks);
    }

    /**
     * Get source image dimension.
     */
    void getSourceImageDimension() {
        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();
        System.out.println("Source image width = " + sourceImageWidth);
        System.out.println("Source image height = " + sourceImageHeight);
    }

    /**
     * Get incidence angle at centre range pixel (in degree).
     */
    void getIncidenceAngleAtCentreRangePixel() {

        if (missionType.contains("ERS")) {
            incidenceAngleAtCentreRangePixel = getIncidenceAngleForERSProduct();
        } else if (missionType.contains("ENVISAT")) {
            incidenceAngleAtCentreRangePixel = getIncidenceAngleForASARProduct();
        } else {
            throw new OperatorException("Invalid mission type");
        }
        System.out.println("Incidence angle at centre range pixel is " + incidenceAngleAtCentreRangePixel);
    }

    /**
     * Get the incidence angle at the centre range pixel for ERS product(in degree).
     *
     * @return The incidence angle.
     */
    double getIncidenceAngleForERSProduct() {
        // Field 57 in PRI Facility Related Data Record (in degree)
        MetadataElement facility = sourceProduct.getMetadataRoot().getElement("Leader").getElement("Facility Related");
        if (facility == null) {
            throw new OperatorException("Facility Related not found");
        }

        MetadataAttribute attr = facility.getAttribute("Incidence angle at centre range pixel");
        if (attr == null) {
            throw new OperatorException("Incidence angle at centre range pixel not found");
        }

        return attr.getData().getElemFloat();
    }

    /**
     * Get the incidence angle at the centre range pixel for ASAR product(in degree).
     *
     * @return The incidence angle.
     */
    double getIncidenceAngleForASARProduct() {

        int x = sourceImageWidth / 2;
        int y = sourceImageHeight / 2;
        TiePointGrid incidenceAngle = getIncidenceAngle();

        return incidenceAngle.getPixelFloat(x + 0.5f, y + 0.5f);
    }

    /**
     * Get incidence angle tie point grid.
     *
     * @return srcTPG The incidence angle tie point grid.
     */
    TiePointGrid getIncidenceAngle() {

        for (int i = 0; i < sourceProduct.getNumTiePointGrids(); i++) {
            TiePointGrid srcTPG = sourceProduct.getTiePointGridAt(i);
            if (srcTPG.getName().equals("incident_angle")) {
                return srcTPG;
            }
        }

        return null;
    }

    /**
     * Compute range and azimuth multi-look factors.
     */
    void computeRangeAzimuthMultiLookFactors() {

        double groundRangeSpacing;
        if (srgrFlag) {
            groundRangeSpacing = rangeSpacing;
        } else {
            groundRangeSpacing = rangeSpacing / Math.sin(incidenceAngleAtCentreRangePixel*MathUtils.DTOR);
        }

        if (groundRangeSpacing < azimuthSpacing) {

            azimuthFactor = multiLookFactor;
            rangeFactor = ((int)(azimuthSpacing / groundRangeSpacing + 0.5))*azimuthFactor;

        } else if (groundRangeSpacing > azimuthSpacing) {

            rangeFactor = multiLookFactor;
            azimuthFactor = ((int)(groundRangeSpacing / azimuthSpacing + 0.5))*rangeFactor;

        } else {

            azimuthFactor = multiLookFactor;
            rangeFactor = multiLookFactor;
        }

        System.out.println("Range factor = " + rangeFactor);
        System.out.println("Azimuth factor = " + azimuthFactor);
    }

    /**
     * Create target product.
     */
    void createTargetProduct() {

        int targetImageWidth = sourceImageWidth / rangeFactor;
        int targetImageHeight = sourceImageHeight / azimuthFactor;

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    targetImageWidth,
                                    targetImageHeight);

        targetBand = targetProduct.addBand("Multi-look", ProductData.TYPE_FLOAT32);
        //targetProduct.setPreferredTileSize(targetImageWidth, 256);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);

        updateTargetProductMetadata();
    }

    /**
     * Update metadata in the target product.
     */
    void updateTargetProductMetadata() {

        MetadataElement abs = targetProduct.getMetadataRoot().getElement("Abstracted Metadata");
        if (abs == null) {
            throw new OperatorException("Abstracted Metadata not found");
        }

        MetadataAttribute azimuthLooksAttr = abs.getAttribute("AZIMUTH_LOOKS");
        if (azimuthLooksAttr == null) {
            throw new OperatorException("azimuth_looks not found");
        }
        azimuthLooksAttr.getData().setElemFloat(numAzimuthLooks*azimuthFactor);

        MetadataAttribute rangeLooksAttr = abs.getAttribute("RANGE_LOOKS");
        if (rangeLooksAttr == null) {
            throw new OperatorException("range_looks not found");
        }
        rangeLooksAttr.getData().setElemFloat(numRangeLooks*rangeFactor);

        MetadataAttribute azimuthSpacingAttr = abs.getAttribute("azimuth_spacing");
        if (azimuthSpacingAttr == null) {
            throw new OperatorException("azimuth_spacing not found");
        }
        azimuthSpacingAttr.getData().setElemFloat((float)(azimuthSpacing*azimuthFactor));

        MetadataAttribute rangeSpacingAttr = abs.getAttribute("range_spacing");
        if (rangeSpacingAttr == null) {
            throw new OperatorException("range_spacing not found");
        }
        rangeSpacingAttr.getData().setElemFloat((float)(rangeSpacing*rangeFactor));

        MetadataAttribute lineTimeintervalAttr = abs.getAttribute("LINE_TIME_INTERVAL");
        if (lineTimeintervalAttr == null) {
            throw new OperatorException("LINE_TIME_INTERVAL not found");
        }
        float oldLineTimeInterval = lineTimeintervalAttr.getData().getElemFloat();
        lineTimeintervalAttr.getData().setElemFloat(oldLineTimeInterval*azimuthFactor);

        MetadataAttribute firstLineTimeAttr = abs.getAttribute("FIRST_LINE_TIME");
        if (firstLineTimeAttr == null) {
            throw new OperatorException("FIRST_LINE_TIME not found");
        }
        String oldFirstLineTime = firstLineTimeAttr.getData().getElemString();
        int idx = oldFirstLineTime.lastIndexOf(":") + 1;
        String oldSecondsStr = oldFirstLineTime.substring(idx);
        double oldSeconds = Double.parseDouble(oldSecondsStr);
        double newSeconds = oldSeconds + oldLineTimeInterval*((azimuthFactor - 1)/2);
        String newFirstLineTime = oldFirstLineTime.subSequence(0,idx) + "" + newSeconds + "000000";
        abs.removeAttribute(firstLineTimeAttr);
        abs.addAttribute(new MetadataAttribute(
                "FIRST_LINE_TIME", ProductData.createInstance(newFirstLineTime.substring(0,26)), false));        
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
     */
    void computeMultiLookImageUsingTimeDomainMethod(
            int tx0, int ty0, int tw, int th, Tile sourceRaster1, Tile sourceRaster2, Tile targetTile) {

        double meanValue;
        for (int ty = ty0; ty < ty0 + th; ty++) {
            for (int tx = tx0; tx < tx0 + tw; tx++) {
                meanValue = getMeanValue(tx, ty, sourceRaster1, sourceRaster2);
                targetTile.setSample(tx, ty, meanValue);
            }
        }
    }

    /**
     * Compute the mean value of pixels of the source image in the sliding window.
     *
     * @param tx The x coordinate of a pixel in the current target tile.
     * @param ty The y coordinate of a pixel in the current target tile.
     * @param sourceRaster1 The source raster for the 1st band.
     * @param sourceRaster2 The source raster for the 2nd band.
     * @return The mean value.
     */
    double getMeanValue(int tx, int ty, Tile sourceRaster1, Tile sourceRaster2) {

        int xStart = tx * rangeFactor;
        int yStart = ty * azimuthFactor;
        int xEnd = xStart + rangeFactor;
        int yEnd = yStart + azimuthFactor;

        double meanValue = 0.0;
        for (int y = yStart; y < yEnd; y++) {
            for (int x = xStart; x < xEnd; x++) {

                if (sampleType.equals("DETECTED")) {
                    double dn = sourceRaster1.getSampleDouble(x, y);
                    meanValue += dn*dn;
                } else {
                    double i = sourceRaster1.getSampleDouble(x, y);
                    double q = sourceRaster2.getSampleDouble(x, y);
                    meanValue += i*i + q*q;
                }
            }
        }
        meanValue /= rangeFactor * azimuthFactor;
        return meanValue;
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
        }
    }
}