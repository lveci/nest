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

/**
 * Calibration for ALOS PALSAR data products.
 */

public class ALOSCalibrator implements Calibrator {

    private Product sourceProduct;
    private Product targetProduct;

    private boolean outputImageScaleInDb = false;

    protected MetadataElement abstractedMetadata = null;
    private String sampleType = null;
    private double calibrationFactor = 0;

    protected static final double underFlowFloat = 1.0e-30;

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public ALOSCalibrator() {
    }

    /**

     */
    @Override
    public void initialize(Product srcProduct, Product tgtProduct) throws OperatorException {
        try {
            sourceProduct = srcProduct;
            targetProduct = tgtProduct;

            abstractedMetadata = AbstractMetadata.getAbstractedMetadata(sourceProduct);

            final String mission = abstractedMetadata.getAttributeString(AbstractMetadata.MISSION);
            if(!mission.equals("ALOS"))
                throw new OperatorException(mission + " is not a valid mission for ALOS Calibration");

            if (abstractedMetadata.getAttribute(AbstractMetadata.abs_calibration_flag).getData().getElemBoolean()) {
                throw new OperatorException("Absolute radiometric calibration has already applied to the product");
            }

            sampleType = abstractedMetadata.getAttributeString(AbstractMetadata.SAMPLE_TYPE);

            getCalibrationFactor();

            updateTargetProductMetadata();

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

                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x, y), sigma);
            }
        }
    }

    /**
     * Gets a {@link Tile} for a given band and rectangle.
     *
     * @param rasterDataNode the raster data node of a data product,
     *                       e.g. a {@link org.esa.beam.framework.datamodel.Band Band} or
     *                       {@link org.esa.beam.framework.datamodel.TiePointGrid TiePointGrid}.
     * @param rectangle      the raster rectangle in pixel coordinates
     * @param pm             The progress monitor passed into the
     *                       the computeTile method or the computeTileStack method.
     * @return a tile.
     * @throws OperatorException if the tile request cannot be processed
     */
    public static Tile getSourceTile(RasterDataNode rasterDataNode, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        return OperatorContext.getSourceTile(rasterDataNode, rectangle, pm);
    }
}