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
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.internal.OperatorContext;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Calibrator;
import org.esa.nest.datamodel.Unit;

import java.awt.*;
import java.util.HashMap;
import java.io.File;

/**
 * Calibration for Radarsat2 data products.
 */

public class Radarsat2Calibrator implements Calibrator {

    private Product sourceProduct;
    private Product targetProduct;

    private boolean outputImageScaleInDb = false;

    private boolean isComplex = false;
    private static final double underFlowFloat = 1.0e-30;

    private static final String lutsigma = "lutsigma";
    private static final String lutgamma = "lutgamma";
    private static final String lutbeta = "lutbeta";

    private double offset = 0.0;
    private double[] gains = null;

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public Radarsat2Calibrator() {
    }

    /**
     * Set flag indicating if target image is output in dB scale.
     */
    @Override
    public void setOutputImageIndB(boolean flag) {
        outputImageScaleInDb = flag;
    }

    /**
     * Set external auxiliary file.
     */
    @Override
    public void setExternalAuxFile(File file) throws OperatorException {
        if (file != null) {
            throw new OperatorException("No external auxiliary file should be selected for Radarsat2 product");
        }
    }

    /**

     */
    @Override
    public void initialize(Product srcProduct, Product tgtProduct,
                           boolean mustPerformRetroCalibration, boolean mustUpdateMetadata)
            throws OperatorException {
        try {
            sourceProduct = srcProduct;
            targetProduct = tgtProduct;

            final MetadataElement abstractedMetadata = AbstractMetadata.getAbstractedMetadata(sourceProduct);

            final String mission = abstractedMetadata.getAttributeString(AbstractMetadata.MISSION);
            if(!mission.equals("RS2"))
                throw new OperatorException(mission + " is not a valid mission for Radarsat2 Calibration");

            if (abstractedMetadata.getAttribute(AbstractMetadata.abs_calibration_flag).getData().getElemBoolean()) {
                throw new OperatorException("Absolute radiometric calibration has already been applied to the product");
            }

            final String sampleType = abstractedMetadata.getAttributeString(AbstractMetadata.SAMPLE_TYPE);
            if(sampleType.equals("COMPLEX")) {
                isComplex = true;
            }

            getLUT();

            if (mustUpdateMetadata) {
                updateTargetProductMetadata();
            }

        } catch(Exception e) {
            throw new OperatorException(e);
        }
    }

    private void getLUT() {
        final MetadataElement root = sourceProduct.getMetadataRoot();
        final MetadataElement lutSigmaElem = root.getElement(lutsigma);

        if(lutSigmaElem != null) {
            offset = lutSigmaElem.getAttributeDouble("offset", 0);

            final MetadataAttribute gainsAttrib = lutSigmaElem.getAttribute("gains");
            if(gainsAttrib !=null) {
                gains = (double[])gainsAttrib.getData().getElems();
            }
        }
    }

    /**
     * Update the metadata in the target product.
     */
    private void updateTargetProductMetadata() {

        final MetadataElement abs = AbstractMetadata.getAbstractedMetadata(targetProduct);

        if (isComplex) {
            abs.setAttributeString(AbstractMetadata.SAMPLE_TYPE, "DETECTED");
        }

        abs.getAttribute(AbstractMetadata.abs_calibration_flag).getData().setElemBoolean(true);

        final MetadataElement root = targetProduct.getMetadataRoot();
        root.removeElement(root.getElement(lutsigma));
        root.removeElement(root.getElement(lutgamma));
        root.removeElement(root.getElement(lutbeta));
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
                    throw new OperatorException("Calibration: unhandled unit");
                }

                if(isComplex) {
                    if(gains != null) {
                        sigma /= (gains[x] * gains[x]);
                    }
                } else {
                    sigma += offset;
                    if(gains != null) {
                        sigma /= gains[x];
                    }
                }

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

    public double applyCalibration(
            final double v, final int rangeIndex, final double slantRange, final double satelliteHeight,
            final double sceneToEarthCentre,final double localIncidenceAngle, final int bandPolar,
            final Unit.UnitType bandUnit, int[] subSwathIndex) {

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

        if(isComplex) {
            if(gains != null) {
                sigma /= (gains[rangeIndex] * gains[rangeIndex]);
            }
        } else {
            sigma += offset;
            if(gains != null) {
                sigma /= gains[rangeIndex];
            }
        }

        return sigma;
    }

    public double applyRetroCalibration(int x, int y, double v, int bandPolar, final Unit.UnitType bandUnit, int[] subSwathIndex) {
        return v;
    }

    public void removeFactorsForCurrentTile(Band targetBand, Tile targetTile, String srcBandName, ProgressMonitor pm) throws OperatorException {

    }    
}