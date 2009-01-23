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
import com.bc.ceres.swing.progress.DialogProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.PropertyMap;
import org.esa.beam.util.Debug;
import org.esa.beam.dataio.dimap.DimapProductReader;
import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.nest.util.DatUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import javax.media.jai.JAI;
import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.File;
import java.io.IOException;

/**
 * The operator performs the following perations for all master/slave pairs that user selected:
 *
 * 1. For both PCA images read in the min values computed in the previous step by PCAMinOp;
 * 2. Read also the eigendevector matrix saved in the temporary metadata;
 * 3. Compute the PCA images again and substract the min value from each;
 * 4. Output the final PCA images to target product.
 */

@OperatorMetadata(alias="PCA-Image", description="Computes PCA Images", internal=true)
public class PCAImageOp extends Operator {

    @SourceProduct
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    private boolean pcaImageComputed = false;
    private int numOfSlaveBands; // total number of user selected slave bands

    private String masterBandName; // master band name
    private String[] slaveBandNames; // band names user selected slave bands

    private double[][] eigenVectorMatrices; // eigenvector matrices for all slave bands
    private double[][] minPCA; // min value for first and second PCA images for all master/slave band pairs
    private HashMap<String, Integer> slaveBandIndex;

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public PCAImageOp() {
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

            getStatistics();

            createTargetProduct();

        } catch(Exception e) {
            throw new OperatorException(e.getMessage());
        }
    }

    /**
     * Get some statistics from the temporary metadata.
     */
    private void getStatistics() {

        try {

            MetadataElement root = sourceProduct.getMetadataRoot();
            MetadataElement tempElemRoot = root.getElement("temporary metadata");
            if (tempElemRoot == null) {
                throw new OperatorException("Cannot find temporary metadata");
            }

            masterBandName = tempElemRoot.getAttributeString("master band name");
            numOfSlaveBands = tempElemRoot.getNumElements() - 1;
            if (numOfSlaveBands <= 0) {
                throw new OperatorException("There is no slave band");
            }

            slaveBandNames = new String[numOfSlaveBands];
            eigenVectorMatrices = new double[numOfSlaveBands][4];
            minPCA = new double[numOfSlaveBands][2];
            slaveBandIndex = new HashMap<String, Integer>();

            int k = 0;
            for (MetadataElement subElemRoot : tempElemRoot.getElements()) {
                String bandName = subElemRoot.getName();
                if (!bandName.equals(masterBandName)) {
                    slaveBandNames[k] = bandName;
                    minPCA[k][0] = subElemRoot.getAttributeDouble("min1");
                    minPCA[k][1] = subElemRoot.getAttributeDouble("min2");
                    eigenVectorMatrices[k][0] = subElemRoot.getAttributeDouble("eigen vector matrix 0");
                    eigenVectorMatrices[k][1] = subElemRoot.getAttributeDouble("eigen vector matrix 1");
                    eigenVectorMatrices[k][2] = subElemRoot.getAttributeDouble("eigen vector matrix 2");
                    eigenVectorMatrices[k][3] = subElemRoot.getAttributeDouble("eigen vector matrix 3");
                    slaveBandIndex.put(bandName, k);
                    k++;
                }
            }

        } catch (Exception e) {
            throw new OperatorException(e.getMessage());
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

        //targetProduct.setPreferredTileSize(JAI.getDefaultTileSize());
        targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 10);

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        addSelectedBands();
    }

    /**
     * Add user selected slave bands to target product.
     */
    private void addSelectedBands() {

        // add PCA bands in target product
        final Band masterBand = sourceProduct.getBand(masterBandName);
        if (masterBand == null) {
            throw new OperatorException("Source band not found: " + masterBand);
        }

        int imageWidth = masterBand.getRasterWidth();
        int imageHeight = masterBand.getRasterHeight();
        String unit = masterBand.getUnit();

        for (String slaveBandName : slaveBandNames) {

            String targetBandName1 = "PC1_" + masterBandName + "_" + slaveBandName;
            String targetBandName2 = "PC2_" + masterBandName + "_" + slaveBandName;

            Band targetBand = new Band(targetBandName1, ProductData.TYPE_FLOAT32, imageWidth, imageHeight);
            targetBand.setUnit(unit);
            targetProduct.addBand(targetBand);

            targetBand = new Band(targetBandName2, ProductData.TYPE_FLOAT32, imageWidth, imageHeight);
            targetBand.setUnit(unit);
            targetProduct.addBand(targetBand);
        }
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

            for (String slaveBandName : slaveBandNames) {

                checkForCancelation(pm);

                String targetBandName1 = "PC1_" + masterBandName + "_" + slaveBandName;
                String targetBandName2 = "PC2_" + masterBandName + "_" + slaveBandName;

                final Band targetBand1 = targetProduct.getBand(targetBandName1);
                final Band targetBand2 = targetProduct.getBand(targetBandName2);

                final Tile targetTile1 = targetTileMap.get(targetBand1);
                final Tile targetTile2 = targetTileMap.get(targetBand2);

                computePCAImages(slaveBandName, targetTile1, targetTile2, pm);
            }

        } catch (Exception e){
            throw new OperatorException(e);
        } finally {
            pm.done();
        }

        pcaImageComputed = true;
    }

    /**
     * Compute PCA images for given master/slave pair and output them to target.
     *
     * @param slaveBandName The slave band name.
     * @param targetTile1 The target tile for the first target band.
     * @param targetTile2 The target tile for the second target band.
     * @param pm A progress monitor which should be used to determine computation cancelation requests.
     */
    private void computePCAImages(
            String slaveBandName, Tile targetTile1, Tile targetTile2, ProgressMonitor pm) {

        // method 1
        final ProductData trgData1 = targetTile1.getDataBuffer();
        final ProductData trgData2 = targetTile2.getDataBuffer();

        final Rectangle targetTileRectangle = targetTile1.getRectangle();

        final Band masterBand = sourceProduct.getBand(masterBandName);
        final Band slaveBand = sourceProduct.getBand(slaveBandName);

        final Tile masterRaster = getSourceTile(masterBand, targetTileRectangle, pm);
        final ProductData masterRawSamples = masterRaster.getRawSamples();

        final Tile slaveRaster = getSourceTile(slaveBand, targetTileRectangle, pm);
        final ProductData slaveRawSamples = slaveRaster.getRawSamples();

        final int idx = slaveBandIndex.get(slaveBandName);
        final int n = masterRawSamples.getNumElems();

        for (int i = 0; i < n; i++) {

            final double vm = masterRawSamples.getElemDoubleAt(i);
            final double vs = slaveRawSamples.getElemDoubleAt(i);

            final double vPCA1 = eigenVectorMatrices[idx][0]*vm + eigenVectorMatrices[idx][1]*vs - minPCA[idx][0];
            final double vPCA2 = eigenVectorMatrices[idx][2]*vm + eigenVectorMatrices[idx][3]*vs - minPCA[idx][1];

            trgData1.setElemDoubleAt(i, vPCA1);
            trgData2.setElemDoubleAt(i, vPCA2);
        }

        // method 2
        /*
        final Rectangle targetTileRectangle = targetTile1.getRectangle();
        final int tx0 = targetTileRectangle.x;
        final int ty0 = targetTileRectangle.y;
        final int tw  = targetTileRectangle.width;
        final int th  = targetTileRectangle.height;
        //System.out.println("tx0 = " + tx0 + ", ty0 = " + ty0 + ", tw = " + tw + ", th = " + th);

        final Band masterBand = sourceProduct.getBand(masterBandName);
        final Band slaveBand = sourceProduct.getBand(slaveBandName);

        final Tile masterRaster = getSourceTile(masterBand, targetTileRectangle, pm);
        final Tile slaveRaster = getSourceTile(slaveBand, targetTileRectangle, pm);

        final ProductData masterData = masterRaster.getDataBuffer();
        final ProductData slaveData = slaveRaster.getDataBuffer();

        final ProductData trgData1 = targetTile1.getDataBuffer();
        final ProductData trgData2 = targetTile2.getDataBuffer();

        final int bandIdx = slaveBandIndex.get(slaveBandName);

        for (int ty = ty0; ty < ty0 + th; ty++) {
            for (int tx = tx0; tx < tx0 + tw; tx++) {

                //int index = targetTile1.getDataBufferIndex(tx, ty);
                int index = (ty - ty0)*tw + tx - tx0;

                final double vm = masterData.getElemDoubleAt(index);
                final double vs = slaveData.getElemDoubleAt(index);

                final double vPCA1 =
                        eigenVectorMatrices[bandIdx][0]*vm + eigenVectorMatrices[bandIdx][1]*vs - minPCA[bandIdx][0];
                final double vPCA2 =
                        eigenVectorMatrices[bandIdx][2]*vm + eigenVectorMatrices[bandIdx][3]*vs - minPCA[bandIdx][1];

                trgData1.setElemDoubleAt(index, vPCA1);
                trgData2.setElemDoubleAt(index, vPCA2);                
            }
        }
        */
    }

    /**
     * Compute statistics for the whole image.
     */
    @Override
    public void dispose() {

        if (!pcaImageComputed) {
            return;
        }

        removeTemporaryMetadata();
    }

    private void removeTemporaryMetadata() {

        MetadataElement root = sourceProduct.getMetadataRoot();
        MetadataElement tempElemRoot = root.getElement("temporary metadata");
        root.removeElement(tempElemRoot);

        try {
            ProductIO.writeProduct(sourceProduct, sourceProduct.getFileLocation(),
                                   DimapProductConstants.DIMAP_FORMAT_NAME,
                                   true, ProgressMonitor.NULL);
        } catch(Exception e) {
            throw new OperatorException(e.getMessage());
        }
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
            super(PCAImageOp.class);
        }
    }
}