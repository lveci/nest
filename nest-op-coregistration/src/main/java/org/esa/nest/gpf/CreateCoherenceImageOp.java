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
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.nest.datamodel.AbstractMetadata;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * The operator computes coherence images for given master and co-registrated slave bands.
 */

@OperatorMetadata(alias="Create-Coherence-Image", description="Create Coherence Image", internal=true)
public final class CreateCoherenceImageOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The coherence window size", interval = "(1, 10]", defaultValue = "5",
                label="Coherence Window Size")
    private int coherenceWindowSize = 5;

    private Band masterBandI = null;
    private Band masterBandQ = null;
    private int sourceImageWidth;
    private int sourceImageHeight;

    private boolean complexCoregistration = false;
    private final Map<String, String[]> coherenceSlaveMap = new HashMap<String, String[]>(10);

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public CreateCoherenceImageOp() {
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
            sourceImageWidth = sourceProduct.getSceneRasterWidth();
            sourceImageHeight = sourceProduct.getSceneRasterHeight();

            complexCoregistration = AbstractMetadata.getAbstractedMetadata(sourceProduct).
                    getAttributeString(AbstractMetadata.SAMPLE_TYPE).contains("COMPLEX");

            createTargetProduct();

        } catch(Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Create target product.
     */
    void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        addSelectedBands();

        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);
    }

    /**
     * Add user selected slave bands to target product.
     */
    private void addSelectedBands() {

        final int numSrcBands = sourceProduct.getNumBands();
        if(numSrcBands < 2) {
            throw new OperatorException("To create coherence image, more than 2 bands are needed.");
        }

        String[] bandNames = sourceProduct.getBandNames();
        if (complexCoregistration) {
            // add master bands
            masterBandI = sourceProduct.getBand(findBandName(bandNames, 'i', "mst"));
            masterBandQ = sourceProduct.getBand(findBandName(bandNames, 'q', "mst"));
            addTargetBand(masterBandI.getName(), masterBandI.getDataType(), masterBandI.getUnit());
            addTargetBand(masterBandQ.getName(), masterBandQ.getDataType(), masterBandQ.getUnit());

            // add slave and coherence bands
            for (int i = 1; i <= numSrcBands; i++) {

                final String slaveBandNameI = findBandName(bandNames, 'i', "slv" + i);
                if (slaveBandNameI == null) {
                    break;
                }
                final String slaveBandNameQ = findBandName(bandNames, 'q', "slv" + i);
                if (slaveBandNameQ == null) {
                    break;
                }
                final Band slaveBandI = sourceProduct.getBand(slaveBandNameI);
                final Band slaveBandQ = sourceProduct.getBand(slaveBandNameQ);
                addTargetBand(slaveBandNameI, slaveBandI.getDataType(), slaveBandI.getUnit());
                addTargetBand(slaveBandNameQ, slaveBandQ.getDataType(), slaveBandQ.getUnit());

                String[] iqBandNames = new String[2];
                iqBandNames[0] = slaveBandNameI;
                iqBandNames[1] = slaveBandNameQ;

                String coherenceBandName = "Coherence_slv" + i;
                addTargetBand(coherenceBandName, ProductData.TYPE_FLOAT32, "coherence");
                coherenceSlaveMap.put(coherenceBandName, iqBandNames);
            }

        } else {

            masterBandI = sourceProduct.getBand(findBandName(bandNames, ' ', "mst"));
            addTargetBand(masterBandI.getName(), masterBandI.getDataType(), masterBandI.getUnit());

            // add slave and coherence bands
            for (int i = 1; i <= numSrcBands; i++) {

                final String slaveBandName = findBandName(bandNames, ' ', "slv" + i);
                if (slaveBandName == null) {
                    break;
                }
                final Band slaveBandI = sourceProduct.getBand(slaveBandName);
                addTargetBand(slaveBandName, slaveBandI.getDataType(), slaveBandI.getUnit());

                String[] iqBandNames = new String[1];
                iqBandNames[0] = slaveBandName;

                String coherenceBandName = "Coherence_slv" + i;
                addTargetBand(coherenceBandName, ProductData.TYPE_FLOAT32, "coherence");
                coherenceSlaveMap.put(coherenceBandName, iqBandNames);
            }
        }
    }

    private String findBandName(String[] bandNames, char firstChar, String namePattern) {

        String bandName = null;
        for(String name : bandNames) {
            if (name.contains(namePattern)) {
                if (firstChar == 'i' || firstChar == 'q') {
                    if (name.charAt(0) == firstChar) {
                        bandName = name;
                        break;
                    }
                } else {
                    bandName = name;
                    break;
                }
            }
        }
        return bandName;
    }

    private void addTargetBand(String bandName, int dataType, String bandUnit) {

        if(targetProduct.getBand(bandName) == null) {
            final Band targetBand = new Band(bandName,
                                             ProductData.TYPE_FLOAT32, //dataType,
                                             sourceProduct.getSceneRasterWidth(),
                                             sourceProduct.getSceneRasterHeight());

            targetBand.setUnit(bandUnit);
            targetProduct.addBand(targetBand);
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
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException
    {

        try {
            final Rectangle targetTileRectangle = targetTile.getRectangle();
            final int x0 = targetTileRectangle.x;
            final int y0 = targetTileRectangle.y;
            final int w = targetTileRectangle.width;
            final int h = targetTileRectangle.height;
            //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

            final Band srcBand = sourceProduct.getBand(targetBand.getName());
            if(!targetBand.getUnit().contains("coherence")) { // master and slave bands

                final Tile srcRaster = getSourceTile(srcBand, targetTileRectangle);
                final ProductData srcData = srcRaster.getDataBuffer();
                final ProductData targetData = targetTile.getDataBuffer();
                for (int y = y0; y < y0 + h; y++) {
                    for (int x = x0; x < x0 + w; x++) {
                        final int index = srcRaster.getDataBufferIndex(x, y);
                        targetData.setElemFloatAt(targetTile.getDataBufferIndex(x, y), srcData.getElemFloatAt(index));
                    }
                }

            } else { // coherence bands

                final String[] iqBandNames = coherenceSlaveMap.get(targetBand.getName());
                float[] dataArray;
                if (!complexCoregistration) { // real image
                    final Band slaveBand = sourceProduct.getBand(iqBandNames[0]);
                    dataArray = createCoherenceImage(targetTileRectangle, slaveBand);
                } else { // complex image
                    final Band iSlaveBand = sourceProduct.getBand(iqBandNames[0]);
                    final Band qSlaveBand = sourceProduct.getBand(iqBandNames[1]);
                    dataArray = createCoherenceImage(targetTileRectangle, iSlaveBand, qSlaveBand);
                }

                final ProductData rawTargetData = ProductData.createInstance(dataArray);
                targetTile.setRawSamples(rawTargetData);
            }

        } catch(Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    private float[] createCoherenceImage(Rectangle targetTileRectangle, Band slaveBand) {

        final int x0 = targetTileRectangle.x;
        final int y0 = targetTileRectangle.y;
        final int w = targetTileRectangle.width;
        final int h = targetTileRectangle.height;
        final RealCoherenceData realData = new RealCoherenceData();
        float[] array = new float[w*h];
        int k = 0;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                getMasterSlaveDataForCurWindow(x, y, slaveBand, realData);
                array[k++] = computeCoherence(realData);
            }
        }

        return array;
    }

    private float[] createCoherenceImage(
            Rectangle targetTileRectangle, Band iSlaveBand, Band qSlaveBand) {

        final int x0 = targetTileRectangle.x;
        final int y0 = targetTileRectangle.y;
        final int w = targetTileRectangle.width;
        final int h = targetTileRectangle.height;
        final ComplexCoherenceData complexData = new ComplexCoherenceData();
        float[] array = new float[w*h];
        int k = 0;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                getMasterSlaveDataForCurWindow(x, y, iSlaveBand, qSlaveBand, complexData);
                array[k++] = computeCoherence(complexData);
            }
        }
        return array;
    }

    private void getMasterSlaveDataForCurWindow(
            int xC, int yC, Band slaveBand, RealCoherenceData realData) {

        // compute upper left corner coordinate (xUL, yUL)
        final int halfWindowSize = coherenceWindowSize / 2;
        final int xUL = Math.max(xC - halfWindowSize, 0);
        final int yUL = Math.max(yC - halfWindowSize, 0);

        // compute lower right corner coordinate (xLR, yLR)
        final int xLR = Math.min(xC + halfWindowSize, sourceImageWidth - 1);
        final int yLR = Math.min(yC + halfWindowSize, sourceImageHeight - 1);

        // compute actual window width (w) and height (h)
        final int w = xLR - xUL + 1;
        final int h = yLR - yUL + 1;

        realData.m = new double[w*h];
        realData.s = new double[w*h];

        final Rectangle windowRectangle = new Rectangle(xUL, yUL, w, h);
        final Tile masterRaster = getSourceTile(masterBandI, windowRectangle);
        final Tile slaveRaster = getSourceTile(slaveBand, windowRectangle);
        final ProductData masterData = masterRaster.getDataBuffer();
        final ProductData slaveData = slaveRaster.getDataBuffer();

        int k = 0;
        for (int y = yUL; y <= yLR; y++) {
            for (int x = xUL; x <= xLR; x++) {
                final int index = masterRaster.getDataBufferIndex(x, y);
                realData.m[k] = masterData.getElemDoubleAt(index);
                realData.s[k] = slaveData.getElemDoubleAt(index);
                k++;
            }
        }
    }

    private void getMasterSlaveDataForCurWindow(
            int xC, int yC, Band iSlaveBand, Band qSlaveBand, ComplexCoherenceData complexData) {

        // compute upper left corner coordinate (xUL, yUL)
        final int halfWindowSize = coherenceWindowSize / 2;
        final int xUL = Math.max(xC - halfWindowSize, 0);
        final int yUL = Math.max(yC - halfWindowSize, 0);

        // compute lower right corner coordinate (xLR, yLR)
        final int xLR = Math.min(xC + halfWindowSize, sourceImageWidth - 1);
        final int yLR = Math.min(yC + halfWindowSize, sourceImageHeight - 1);

        // compute actual window width (w) and height (h)
        final int w = xLR - xUL + 1;
        final int h = yLR - yUL + 1;

        complexData.mI = new double[w*h];
        complexData.mQ = new double[w*h];
        complexData.sI = new double[w*h];
        complexData.sQ = new double[w*h];

        final Rectangle windowRectangle = new Rectangle(xUL, yUL, w, h);
        final Tile masterRasterI = getSourceTile(masterBandI, windowRectangle);
        final Tile masterRasterQ = getSourceTile(masterBandQ, windowRectangle);
        final ProductData masterDataI = masterRasterI.getDataBuffer();
        final ProductData masterDataQ = masterRasterQ.getDataBuffer();
        final Tile slaveRasterI = getSourceTile(iSlaveBand, windowRectangle);
        final Tile slaveRasterQ = getSourceTile(qSlaveBand, windowRectangle);
        final ProductData slaveDataI = slaveRasterI.getDataBuffer();
        final ProductData slaveDataQ = slaveRasterQ.getDataBuffer();

        int k = 0;
        for (int y = yUL; y <= yLR; y++) {
            for (int x = xUL; x <= xLR; x++) {
                final int index = masterRasterI.getDataBufferIndex(x, y);
                complexData.mI[k] = masterDataI.getElemDoubleAt(index);
                complexData.mQ[k] = masterDataQ.getElemDoubleAt(index);
                complexData.sI[k] = slaveDataI.getElemDoubleAt(index);
                complexData.sQ[k] = slaveDataQ.getElemDoubleAt(index);
                k++;
            }
        }
    }

    private static float computeCoherence(RealCoherenceData realData) {

        double sum1 = 0.0;
        double sum2 = 0.0;
        double sum3 = 0.0;
        for (int i = 0; i < realData.m.length; i++) {
            final double m = realData.m[i];
            final double s = realData.s[i];
            sum1 += m*s;
            sum2 += m*m;
            sum3 += s*s;
        }

        return (float)(Math.abs(sum1) / Math.sqrt(sum2*sum3));
    }

    private static float computeCoherence(ComplexCoherenceData complexData) {

        double sum1 = 0.0;
        double sum2 = 0.0;
        double sum3 = 0.0;
        double sum4 = 0.0;
        for (int i = 0; i < complexData.mI.length; i++) {
            final double mr = complexData.mI[i];
            final double mi = complexData.mQ[i];
            final double sr = complexData.sI[i];
            final double si = complexData.sQ[i];
            sum1 += mr*sr + mi*si;
            sum2 += mi*sr - mr*si;
            sum3 += mr*mr + mi*mi;
            sum4 += sr*sr + si*si;
        }

        return (float)(Math.sqrt(sum1*sum1 + sum2*sum2) / Math.sqrt(sum3*sum4));
    }

    private static class RealCoherenceData {
        private double[] m = null;          // real master data for coherence computation
        private double[] s = null;          // real slave data for coherence computation
    }

    private static class ComplexCoherenceData {
        private double[] mI = null;          // real part of master for coherence computation
        private double[] mQ = null;          // imaginary part of master for coherence computation
        private double[] sI = null;          // real part of slave for coherence computation
        private double[] sQ = null;          // imaginary part of slave for coherence computation
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
            super(CreateCoherenceImageOp.class);
        }
    }
}