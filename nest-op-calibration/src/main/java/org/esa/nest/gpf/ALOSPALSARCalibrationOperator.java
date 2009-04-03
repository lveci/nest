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
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Calibration for ALOS PALSAR data products.
 */

@OperatorMetadata(alias = "ALOSPALSAR-Calibration",
        description = "Calibration of ALOS PALSAR data products")
public class ALOSPALSARCalibrationOperator extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Band")
    String[] sourceBandNames;

    @Parameter(description = "Output image scale", defaultValue = "false", label="Scale in dB")
    private boolean outputImageScaleInDb = false;

    @Parameter(description = "Create gamma0 virtual band", defaultValue = "false", label="Create gamma0 virtual band")
    private boolean createGammaBand = false;

    @Parameter(description = "Create beta0 virtual band", defaultValue = "false", label="Create beta0 virtual band")
    private boolean createBetaBand = false;

    protected MetadataElement abstractedMetadata = null;
    private String sampleType = null;
    private double calibrationFactor = 0;

    protected static final double underFlowFloat = 1.0e-30;
    private final HashMap<String, String[]> targetBandNameToSourceBandName = new HashMap<String, String[]>();;

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public ALOSPALSARCalibrationOperator() {
    }

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
            abstractedMetadata = AbstractMetadata.getAbstractedMetadata(sourceProduct);

            final String mission = abstractedMetadata.getAttributeString(AbstractMetadata.MISSION);
            if(!mission.equals("ALOS"))
                throw new OperatorException(mission + " is not a valid mission for ALOS Calibration");

            if (abstractedMetadata.getAttribute(AbstractMetadata.abs_calibration_flag).getData().getElemBoolean()) {
                throw new OperatorException("Absolute radiometric calibration has already applied to the product");
            }

            sampleType = abstractedMetadata.getAttributeString(AbstractMetadata.SAMPLE_TYPE);

            getCalibrationFactor();

            createTargetProduct();

            if(createGammaBand) {
                ASARCalibrationOperator.createGammaVirtualBand(targetProduct, outputImageScaleInDb);
            }

            if(createBetaBand) {
                ASARCalibrationOperator.createBetaVirtualBand(targetProduct, outputImageScaleInDb);
            }

            targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 10);

        } catch(Exception e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Get calibration factor.
     * @throws Exception for missing metadata
     */
    private void getCalibrationFactor() throws Exception {

        calibrationFactor = abstractedMetadata.getAttributeDouble(AbstractMetadata.calibration_factor);

        if (sampleType.contains("COMPLEX")) {
            calibrationFactor -= 32.0; // calibration factor offset is 32 dB
        }

        calibrationFactor = Math.pow(10.0, calibrationFactor/10.0); // dB to linear scale
        //System.out.println("Calibration factor is " + calibrationFactor);
    }

    /**
     * Create target product.
     */
    private void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        addSelectedBands();

        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);

        updateTargetProductMetadata();
    }

    /**
     * Add the user selected bands to the target product.
     */
    private void addSelectedBands() {

        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            final ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
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
            final String unit = srcBand.getUnit();
            if(unit == null) {
                throw new OperatorException("band "+srcBand.getName()+" requires a unit");
            }

            String targetUnit = Unit.INTENSITY;

            if(unit.contains(Unit.DB)) {

                throw new OperatorException("Calibration of bands in dB is not supported");
            } else if (unit.contains(Unit.PHASE)) {

                final String[] srcBandNames = {srcBand.getName()};
                targetBandName = srcBand.getName();
                targetUnit = Unit.PHASE;
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                }

            } else if (unit.contains(Unit.IMAGINARY)) {

                throw new OperatorException("Real and imaginary bands should be selected in pairs");

            } else if (unit.contains(Unit.REAL)) {
                if(i+1 >= sourceBands.length)
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");

                final String nextUnit = sourceBands[i+1].getUnit();
                if (nextUnit == null || !nextUnit.contains("imaginary")) {
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");
                }
                final String[] srcBandNames = new String[2];
                srcBandNames[0] = srcBand.getName();
                srcBandNames[1] = sourceBands[i+1].getName();
                final String pol = OperatorUtils.getPolarizationFromBandName(srcBandNames[0]);
                if (pol != null) {
                    targetBandName = "Sigma0_" + pol.toUpperCase();
                } else {
                    targetBandName = "Sigma0";
                }
                if(outputImageScaleInDb) {
                    targetBandName += "_dB";
                }
                ++i;
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                }

            } else {

                final String[] srcBandNames = {srcBand.getName()};
                final String pol = OperatorUtils.getPolarizationFromBandName(srcBandNames[0]);
                if (pol != null) {
                    targetBandName = "Sigma0_" + pol.toUpperCase();  
                } else
                    targetBandName = "Sigma0";
                if(outputImageScaleInDb) {
                    targetBandName += "_dB";
                }
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                }
            }

            // add band only if it doesn't already exist
            if(targetProduct.getBand(targetBandName) == null) {
                final Band targetBand = new Band(targetBandName,
                                           ProductData.TYPE_FLOAT64,
                                           sourceProduct.getSceneRasterWidth(),
                                           sourceProduct.getSceneRasterHeight());

                if (outputImageScaleInDb && !targetUnit.equals(Unit.PHASE)) {
                    targetUnit = Unit.INTENSITY_DB;
                }
                targetBand.setUnit(targetUnit);
                targetProduct.addBand(targetBand);
            }
        }
    }

    /**
     * Update the metadata in the target product.
     */
    private void updateTargetProductMetadata() {

        final MetadataElement abs = AbstractMetadata.getAbstractedMetadata(targetProduct);

        if (sampleType.contains("COMPLEX")) {
            abs.setAttributeString(AbstractMetadata.SAMPLE_TYPE, "DETECTED");
        }

        abs.getAttribute(AbstractMetadata.abs_calibration_flag).getData().setElemBoolean(true);
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
        final int x0 = targetTileRectangle.x;
        final int y0 = targetTileRectangle.y;
        final int w = targetTileRectangle.width;
        final int h = targetTileRectangle.height;

        Tile sourceRaster1 = null;
        ProductData srcData1 = null;
        ProductData srcData2 = null;
        Band sourceBand1 = null;

        final String[] srcBandNames = targetBandNameToSourceBandName.get(targetBand.getName());
        if (srcBandNames.length == 1) {
            sourceBand1 = sourceProduct.getBand(srcBandNames[0]);
            sourceRaster1 = getSourceTile(sourceBand1, targetTileRectangle, pm);
            srcData1 = sourceRaster1.getDataBuffer();
        } else {
            sourceBand1 = sourceProduct.getBand(srcBandNames[0]);
            final Band sourceBand2 = sourceProduct.getBand(srcBandNames[1]);
            sourceRaster1 = getSourceTile(sourceBand1, targetTileRectangle, pm);
            final Tile sourceRaster2 = getSourceTile(sourceBand2, targetTileRectangle, pm);
            srcData1 = sourceRaster1.getDataBuffer();
            srcData2 = sourceRaster2.getDataBuffer();
        }

        final Unit.UnitType bandUnit = Unit.getUnitType(sourceBand1);

        // copy band if unit is phase
        if(bandUnit == Unit.UnitType.PHASE) {
            targetTile.setRawSamples(sourceRaster1.getRawSamples());
            return;
        }

        final ProductData trgData = targetTile.getDataBuffer();

        final int maxY = y0 + h;
        final int maxX = x0 + w;

        double sigma, dn, i, q;
        int index;

        for (int y = y0; y < maxY; ++y) {
            for (int x = x0; x < maxX; ++x) {

                index = sourceRaster1.getDataBufferIndex(x, y);

                if (bandUnit == Unit.UnitType.AMPLITUDE) {
                    dn = srcData1.getElemDoubleAt(index);
                    sigma = dn*dn;
                } else if (bandUnit == Unit.UnitType.INTENSITY) {
                    sigma = srcData1.getElemDoubleAt(index);
                } else if (bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {
                    i = srcData1.getElemDoubleAt(index);
                    q = srcData2.getElemDoubleAt(index);
                    sigma = i * i + q * q;
                } else {
                    throw new OperatorException("ASAR Calibration: unhandled unit");
                }

                sigma *= calibrationFactor;

                if (outputImageScaleInDb) { // convert calibration result to dB
                    if (sigma < underFlowFloat) {
                        sigma = -underFlowFloat;
                    } else {
                        sigma = 10.0 * Math.log10(sigma);
                    }
                }

                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x,y), sigma);
            }
        }
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see OperatorSpi#createOperator()
     * @see OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ALOSPALSARCalibrationOperator.class);
        }
    }
}
