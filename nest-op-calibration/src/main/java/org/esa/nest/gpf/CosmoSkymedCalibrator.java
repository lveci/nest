/*
 * Copyright (C) 2002-2010 by ?
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

import java.awt.*;
import java.io.File;
import java.util.HashMap;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Calibrator;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.datamodel.Unit.UnitType;
import org.esa.beam.framework.gpf.internal.OperatorContext;

import com.bc.ceres.core.ProgressMonitor;

/**
 * Calibration for Cosmo-Skymed data products.
 */

public class CosmoSkymedCalibrator implements Calibrator {

    private Product sourceProduct;
    private Product targetProduct;

    private boolean outputImageScaleInDb = false;

    private MetadataElement absRoot = null;
    private String sampleType = null;
    private double referenceSlantRange = 0.;
    private double referenceSlantRangeExp = 0.;
    private double referenceIncidenceAngle = 0.;
    private double rescalingFactor = 0.;
    private final HashMap<String, Double> calibrationFactor = new HashMap<String, Double>(2);
    
    private TiePointGrid incidenceAngle = null;
    private TiePointGrid slantRangeTime = null;
    private TiePointGrid latitude = null;
    private String incidenceAngleSelection = null;
    
    private boolean applyRangeSpreadingLossCorrection = false;
    private boolean applyIncidenceAngleCorrection = false;
    private boolean incAngleCompFlag = false;
    private boolean rangeSpreadCompFlag = false;

    private static final double underFlowFloat = 1.0e-30;

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public CosmoSkymedCalibrator() {
    }

    /**
     * Set flag indicating if target image is output in dB scale.
     */
    public void setOutputImageIndB(boolean flag) {
        outputImageScaleInDb = flag;
    }

    /**
     * Set external auxiliary file.
     */
    public void setExternalAuxFile(File file) throws OperatorException {
        if (file != null) {
            throw new OperatorException("No external auxiliary file should be selected for Cosmo-Skymed product");
        }
    }

    /**
     * Set auxiliary file flag.
     */
    @Override
    public void setAuxFileFlag(String file) {
    }
    
	public void setIncidenceAngleForSigma0(String incidenceAngleForSigma0) {
        incidenceAngleSelection = incidenceAngleForSigma0;
	}
	
	public void initialize(Product srcProduct, Product tgtProduct,
			boolean mustPerformRetroCalibration, boolean mustUpdateMetadata)
			throws OperatorException {
        try {
            sourceProduct = srcProduct;
            targetProduct = tgtProduct;

            absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);

            final String mission = absRoot.getAttributeString(AbstractMetadata.MISSION);
            if(!mission.startsWith("CSK"))
                throw new OperatorException(mission + " is not a valid mission for Cosmo-Skymed Calibration");

            final String productType = absRoot.getAttributeString(AbstractMetadata.PRODUCT_TYPE);
            if(productType.equals("SCS_U"))
            	throw new OperatorException(productType + " calibration is not currently supported");
            
            if (absRoot.getAttribute(AbstractMetadata.abs_calibration_flag).getData().getElemBoolean()) {
                throw new OperatorException("Absolute radiometric calibration has already been applied to the product");
            }

            sampleType = absRoot.getAttributeString(AbstractMetadata.SAMPLE_TYPE);

            getCalibrationFlags();
            getCalibrationFactors();

            getTiePointGridData(sourceProduct);

            if (mustUpdateMetadata) {
                updateTargetProductMetadata();
            }

        } catch(Exception e) {
            throw new OperatorException(e);
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
     * Get the antenna pattern correction flag and range spreading loss flag.
     * @throws Exception The exceptions.
     */
    private void getCalibrationFlags() throws Exception {

        if (absRoot.getAttribute(AbstractMetadata.abs_calibration_flag).getData().getElemBoolean()) {
            throw new OperatorException("The product has already been calibrated");
        }

        incAngleCompFlag = 
        	AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.inc_angle_comp_flag);
        if (incAngleCompFlag)
        	applyIncidenceAngleCorrection = true;
        
        rangeSpreadCompFlag = 
        	AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.range_spread_comp_flag);
        if (rangeSpreadCompFlag)
        	applyRangeSpreadingLossCorrection = true;
        
    }

    /**
     * Get calibration factors from abstracted metadata.
     * @throws Exception for missing metadata
     */
    private void getCalibrationFactors() throws Exception {

    	String pol;
    	double factor = 0.0;
        final MetadataElement root = sourceProduct.getMetadataRoot();
        final MetadataElement globalElem = root.getElement("Global_Attributes");
        final MetadataElement s01Elem = globalElem.getElement("S01");
        if(s01Elem != null) {
        	pol = s01Elem.getAttributeString("Polarisation").toUpperCase();
        	factor = s01Elem.getAttributeDouble("Calibration Constant");
        	calibrationFactor.put(pol, factor);
        }
         	
        final MetadataElement s02Elem = globalElem.getElement("S02");
        if(s02Elem != null) {
        	pol = s02Elem.getAttributeString("Polarisation").toUpperCase();
        	factor = s02Elem.getAttributeDouble("Calibration Constant");
        	calibrationFactor.put(pol, factor);
        }

        referenceSlantRange = absRoot.getAttributeDouble(
        		AbstractMetadata.ref_slant_range);
        referenceSlantRangeExp = absRoot.getAttributeDouble(
        		AbstractMetadata.ref_slant_range_exp);
        referenceIncidenceAngle = absRoot.getAttributeDouble(
        		AbstractMetadata.ref_inc_angle)*Math.PI/180.0;
        rescalingFactor = absRoot.getAttributeDouble(
        		AbstractMetadata.rescaling_factor);
        
        //System.out.println("Calibration factor is " + calibrationFactor);
    }

    
    /**
     * Get incidence angle and slant range time tie point grids.
     * @param sourceProduct the source
     */
    private void getTiePointGridData(Product sourceProduct) {
        slantRangeTime = OperatorUtils.getSlantRangeTime(sourceProduct);
        incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);
        latitude = OperatorUtils.getLatitude(sourceProduct);
    }

    /**
     * Apply calibrations to the given point. The following calibrations are included: calibration constant,
     * antenna pattern compensation, range spreading loss correction and incidence angle correction.
     * @param v The pixel value.
     * @param slantRange The slant range (in m).
     * @param satelliteHeight The distance from satellite to earth centre (in m).
     * @param sceneToEarthCentre The distance from the backscattering element position to earth centre (in m).
     * @param localIncidenceAngle The local incidence angle (in degrees).
     * @param bandPolar The source band polarization index.
     * @param bandUnit The source band unit.
     * @param subSwathIndex The sub swath index for current pixel for wide swath product case.
     * @return The calibrated pixel value.
     */
    public double applyCalibration(
            final double v, final double rangeIndex, final double azimuthIndex, final double slantRange,
            final double satelliteHeight, final double sceneToEarthCentre, final double localIncidenceAngle,
            final String bandPolar, final Unit.UnitType bandUnit, int[] subSwathIndex) {

        final double Ks = calibrationFactor.get(bandPolar.toUpperCase());

        double sigma = 0.0;
        if (bandUnit == Unit.UnitType.AMPLITUDE) {
            sigma = v*v;
        } else if (bandUnit == Unit.UnitType.INTENSITY || bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {
            sigma = v;
        } else if (bandUnit == Unit.UnitType.INTENSITY_DB) {
            sigma = Math.pow(10, v/10.0); // convert dB to linear scale
        } else {
            throw new OperatorException("Unknown band unit");
        }
        
        if (applyRangeSpreadingLossCorrection)
        	sigma *= Math.pow(referenceSlantRange, 2*referenceSlantRangeExp);
        
        if (applyIncidenceAngleCorrection)
        	sigma *= Math.sin(referenceIncidenceAngle);

        sigma /= (rescalingFactor*rescalingFactor*Ks);

        if (outputImageScaleInDb) { // convert calibration result to dB
            if (sigma < underFlowFloat) {
                sigma = -underFlowFloat;
            } else {
                sigma = 10.0 * Math.log10(sigma);
            }
        }
        return sigma;
	}

	public double applyRetroCalibration(int x, int y, double v,
			String bandPolar, UnitType bandUnit, int[] subSwathIndex) {
		return v;
	}

	public void computeTile(Band targetBand, Tile targetTile,
			HashMap<String, String[]> targetBandNameToSourceBandName,
			ProgressMonitor pm) throws OperatorException {

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
            sourceRaster1 = OperatorContext.getSourceTile(sourceBand1, targetTileRectangle, pm);
            srcData1 = sourceRaster1.getDataBuffer();
        } else {
            sourceBand1 = sourceProduct.getBand(srcBandNames[0]);
            final Band sourceBand2 = sourceProduct.getBand(srcBandNames[1]);
            sourceRaster1 = OperatorContext.getSourceTile(sourceBand1, targetTileRectangle, pm);
            final Tile sourceRaster2 = OperatorContext.getSourceTile(sourceBand2, targetTileRectangle, pm);
            srcData1 = sourceRaster1.getDataBuffer();
            srcData2 = sourceRaster2.getDataBuffer();
        }

        final Unit.UnitType bandUnit = Unit.getUnitType(sourceBand1);

        // copy band if unit is phase
        if(bandUnit == Unit.UnitType.PHASE) {
            targetTile.setRawSamples(sourceRaster1.getRawSamples());
            return;
        }

        final String pol = OperatorUtils.getBandPolarization(srcBandNames[0], absRoot).toUpperCase();
        double Ks = 0.0;
        if (pol != null) {
            Ks = calibrationFactor.get(pol);
        }

        final ProductData trgData = targetTile.getDataBuffer();

        final int maxY = y0 + h;
        final int maxX = x0 + w;

        double sigma, dn, i, q;
        int index;
        final double powFactor = Math.pow(referenceSlantRange, 2*referenceSlantRangeExp);
        final double sinRefIncidenceAngle = Math.sin(referenceIncidenceAngle);
        final double rescaleCalFactor = rescalingFactor*rescalingFactor*Ks;

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
                } else if (bandUnit == Unit.UnitType.INTENSITY_DB) {
                    sigma = Math.pow(10, srcData1.getElemDoubleAt(index)/10.0); // convert dB to linear scale
                } else {
                    throw new OperatorException("CosmoSkymed Calibration: unhandled unit");
                }

                if (applyRangeSpreadingLossCorrection)
                    sigma *= powFactor;
                
                if (applyIncidenceAngleCorrection)
                    sigma *= sinRefIncidenceAngle;

                sigma /= rescaleCalFactor;

                if (outputImageScaleInDb) { // convert calibration result to dB
                    if (sigma < underFlowFloat) {
                        sigma = -underFlowFloat;
                    } else {
                        sigma = 10.0 * Math.log10(sigma);
                    }
                }

                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x, y), sigma);
            }
        }
	}

	public void removeFactorsForCurrentTile(Band targetBand, Tile targetTile,
			String srcBandName, ProgressMonitor pm)  throws OperatorException {
        Band sourceBand = sourceProduct.getBand(targetBand.getName());
        Tile sourceTile = OperatorContext.getSourceTile(sourceBand, targetTile.getRectangle(), pm);
        targetTile.setRawSamples(sourceTile.getRawSamples());
		
	}

}
