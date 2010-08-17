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
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.CalibrationFactory;
import org.esa.nest.datamodel.Calibrator;
import org.esa.nest.datamodel.Unit;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Calibration for all data products.
 *
 * @todo automatically search aux file in local repository using time period
 */
@OperatorMetadata(alias = "Calibration",
        category = "SAR Tools",
        description = "Calibration of products")
public class CalibrationOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Band")
    private String[] sourceBandNames;

    @Parameter(valueSet = {LATEST_AUX, PRODUCT_AUX, EXTERNAL_AUX}, description = "The auxiliary file",
               defaultValue=LATEST_AUX, label="Auxiliary File")
    private String auxFile = LATEST_AUX;

    @Parameter(description = "The antenne elevation pattern gain auxiliary data file.", label="External Aux File")
    private File externalAuxFile = null;

    @Parameter(description = "Output image scale", defaultValue = "false", label="Scale in dB")
    private boolean outputImageScaleInDb = false;

    @Parameter(description = "Create gamma0 virtual band", defaultValue = "false", label="Create gamma0 virtual band")
    private boolean createGammaBand = false;

    @Parameter(description = "Create beta0 virtual band", defaultValue = "false", label="Create beta0 virtual band")
    private boolean createBetaBand = false;

    private final HashMap<String, String[]> targetBandNameToSourceBandName = new HashMap<String, String[]>(2);

    private Calibrator calibrator = null;

    public static final String PRODUCT_AUX = "Product Auxiliary File";
    public static final String LATEST_AUX = "Latest Auxiliary File";
    public static final String EXTERNAL_AUX = "External Auxiliary File";

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public CalibrationOp() {
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
            MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);
            if (AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.coregistered_stack)) {
                throw new OperatorException("Cannot apply calibration to coregistered product.");
            }
            
            createTargetProduct();

            calibrator = CalibrationFactory.createCalibrator(sourceProduct);
            calibrator.setAuxFileFlag(auxFile);
            calibrator.setExternalAuxFile(externalAuxFile);
            calibrator.setOutputImageIndB(outputImageScaleInDb);
            calibrator.initialize(sourceProduct, targetProduct, false, true);

            if(createGammaBand) {
                createGammaVirtualBand(targetProduct, outputImageScaleInDb);
            }

            if(createBetaBand) {
                createBetaVirtualBand(targetProduct, outputImageScaleInDb);
            }

        } catch(Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
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

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);
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
                if (nextUnit == null || !nextUnit.contains(Unit.IMAGINARY)) {
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");
                }
                final String[] srcBandNames = new String[2];
                srcBandNames[0] = srcBand.getName();
                srcBandNames[1] = sourceBands[i+1].getName();
                targetBandName = createTargetBandName(srcBandNames[0], absRoot);
                ++i;
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                }

            } else {

                final String[] srcBandNames = {srcBand.getName()};
                targetBandName = createTargetBandName(srcBandNames[0], absRoot);
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                }
            }

            // add band only if it doesn't already exist
            if(targetProduct.getBand(targetBandName) == null) {
                final Band targetBand = new Band(targetBandName,
                                           ProductData.TYPE_FLOAT32,
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

    private String createTargetBandName(final String srcBandName, final MetadataElement absRoot) {
        final String pol = OperatorUtils.getBandPolarization(srcBandName, absRoot);
        String targetBandName = "Sigma0";
        if (pol != null && !pol.isEmpty()) {
            targetBandName = "Sigma0_" + pol.toUpperCase();
        }
        if(outputImageScaleInDb) {
            targetBandName += "_dB";
        }
        return targetBandName;
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
        try {
            calibrator.computeTile(targetBand, targetTile, targetBandNameToSourceBandName, pm);
        } catch(Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Create Gamma image as a virtual band.
     * @param trgProduct The target product
     * @param outputImageScaleInDb flag if output is in dB
     */
    public static void createGammaVirtualBand(Product trgProduct, boolean outputImageScaleInDb) {

        int count=1;
        final Band[] bands = trgProduct.getBands();
        for(Band trgBand : bands) {

            final String unit = trgBand.getUnit();
            if (trgBand.isSynthetic() || (unit != null && unit.contains("phase"))) {
                continue;
            }

            final String trgBandName = trgBand.getName();
            final String expression;
            if (outputImageScaleInDb) {
                expression = "(pow(10," + trgBandName + "/10.0)" + " / cos(incident_angle * PI/180.0)) "
                        + "==0 ? 0 : 10 * log10(abs("
                        +"(pow(10," + trgBandName + "/10.0)" + " / cos(incident_angle * PI/180.0))"
                        +"))";
            } else {
                expression = trgBandName + " / cos(incident_angle * PI/180.0)";
            }
            String gammeBandName = "Gamma0";

            if(bands.length > 1) {
                if(trgBandName.contains("_HH"))
                    gammeBandName += "_HH";
                else if(trgBandName.contains("_VV"))
                    gammeBandName += "_VV";
                else if(trgBandName.contains("_HV"))
                    gammeBandName += "_HV";
                else if(trgBandName.contains("_VH"))
                    gammeBandName += "_VH";
            }
            if(outputImageScaleInDb) {
                gammeBandName += "_dB";
            }

            while(trgProduct.getBand(gammeBandName) != null) {
                gammeBandName += "_" + ++count;
            }

            final VirtualBand band = new VirtualBand(gammeBandName,
                    ProductData.TYPE_FLOAT32,
                    trgProduct.getSceneRasterWidth(),
                    trgProduct.getSceneRasterHeight(),
                    expression);
            band.setSynthetic(true);
            band.setUnit(unit);
            band.setDescription("Gamma0 image");
            trgProduct.addBand(band);
        }
    }

    /**
     * Create Beta image as a virtual band.
     * @param trgProduct The target product
     * @param outputImageScaleInDb flag if output is in dB
     */
    public static void createBetaVirtualBand(Product trgProduct, boolean outputImageScaleInDb) {

        int count=1;
        final Band[] bands = trgProduct.getBands();
        for(Band trgBand : bands) {

            final String unit = trgBand.getUnit();
            if (trgBand.isSynthetic() || (unit != null && unit.contains("phase"))) {
                continue;
            }

            final String trgBandName = trgBand.getName();
            final String expression;
            if (outputImageScaleInDb) {
                expression = "(pow(10," + trgBandName + "/10.0)" + " / sin(incident_angle * PI/180.0)) "
                        + "==0 ? 0 : 10 * log10(abs("
                        +"(pow(10," + trgBandName + "/10.0)" + " / sin(incident_angle * PI/180.0))"
                        +"))";
            } else {
                expression = trgBandName + " / sin(incident_angle * PI/180.0)";
            }
            String betaBandName = "Beta0";

            if(bands.length > 1) {
                if(trgBandName.contains("_HH"))
                    betaBandName += "_HH";
                else if(trgBandName.contains("_VV"))
                    betaBandName += "_VV";
                else if(trgBandName.contains("_HV"))
                    betaBandName += "_HV";
                else if(trgBandName.contains("_VH"))
                    betaBandName += "_VH";
            }
            if(outputImageScaleInDb) {
                betaBandName += "_dB";
            }

            while(trgProduct.getBand(betaBandName) != null) {
                betaBandName += "_" + ++count;
            }

            final VirtualBand band = new VirtualBand(betaBandName,
                    ProductData.TYPE_FLOAT32,
                    trgProduct.getSceneRasterWidth(),
                    trgProduct.getSceneRasterHeight(),
                    expression);
            band.setSynthetic(true);
            band.setUnit(unit);
            band.setDescription("Beta0 image");
            trgProduct.addBand(band);
        }
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator()
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(CalibrationOp.class);
            setOperatorUI(CalibrationOpUI.class);
        }
    }
}