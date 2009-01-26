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

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.JAI;
import java.awt.*;
import java.util.HashMap;

/**
 * The operator performs the following perations for all master/slave pairs that user selected:
 *
 * 1. Create a covariance matrix from the statistics computed in the previous step by PCAStatisticsOp;
 * 2. Perform eigendecomposition on the covariance matrix to get the eigenvector matrix;
 * 3. Compute two PCA images and get the min values of each;
 * 4. Save the two min values in the metadata.
 */

@OperatorMetadata(alias="PCA-Min", description="Computes minimum for PCA", internal=true)
public class PCAMinOp extends Operator {

    @SourceProduct
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    private boolean statsCalculated = false;
    private int numOfSlaveBands = 0; // total number of user selected slave bands

    private String masterBandName; // master band name
    private double masterMean; // mean of mater band
    private double masterMean2; // square mean of master band

    private String[] slaveBandNames; // band names user selected slave bands
    private double[] slaveMean; // mean of slave bands
    private double[] slaveMean2; // square mean of slave bands
    private double[] slaveMeanCross; // cross mean of slave bands

    private double[][] eigenVectorMatrices; // eigenvector matrices for all slave bands
    private double[][] minPCA; // min value for first and second PCA images for all master/slave band pairs
    private boolean reloadStats = true;

    private final HashMap<String, Integer> slaveBandIndex = new HashMap<String, Integer>(5);

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public PCAMinOp() {
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

            createTargetProduct();
            
            if(getStatistics()) {
                addSelectedBands();
            }
        } catch(Exception e) {
            throw new OperatorException(e.getMessage());
        }
    }

    /**
     * Get some statistics from the temporary metadata.
     * @return true if all stats found ok
     */
    private boolean getStatistics() {

        try {

            final MetadataElement root = sourceProduct.getMetadataRoot();
            final MetadataElement tempElemRoot = root.getElement("temporary metadata");
            if (tempElemRoot == null) {
                //throw new OperatorException("Cannot find temporary metadata");
                return false;
            }

            masterBandName = tempElemRoot.getAttributeString("master band name");
            numOfSlaveBands = tempElemRoot.getNumElements() - 1;
            if (numOfSlaveBands <= 0) {
                throw new OperatorException("There is no slave band");
            }

            slaveBandNames = new String[numOfSlaveBands];
            slaveMean = new double[numOfSlaveBands];
            slaveMean2 = new double[numOfSlaveBands];
            slaveMeanCross = new double[numOfSlaveBands];

            int k = 0;
            for (MetadataElement subElemRoot : tempElemRoot.getElements()) {
                final String bandName = subElemRoot.getName();
                if (bandName.equals(masterBandName)) {
                    masterMean = subElemRoot.getAttributeDouble("mean");
                    masterMean2 = subElemRoot.getAttributeDouble("square mean");
                } else {
                    slaveBandNames[k] = bandName;
                    slaveMean[k] = subElemRoot.getAttributeDouble("mean");
                    slaveMean2[k] = subElemRoot.getAttributeDouble("square mean");
                    slaveMeanCross[k] = subElemRoot.getAttributeDouble("cross mean");
                    slaveBandIndex.put(bandName, k);
                    k++;
                }
            }

        } catch (Exception e) {
            throw new OperatorException(e.getMessage());
        }
        return true;
    }

    /**
     * Set initial values to some internal variables.
     */
    private void setInitialValues() {

        minPCA = new double[numOfSlaveBands][2];
        for (int i = 0; i < numOfSlaveBands; i++) {
            minPCA[i][0] = Double.MAX_VALUE;
            minPCA[i][1] = Double.MAX_VALUE;
        }

        computeEigenDecompositionOfCovarianceMatrix();
    }

    /**
     * Compute covariance matrices and perform EVD on each of them.
     */
    void computeEigenDecompositionOfCovarianceMatrix() {

        eigenVectorMatrices = new double[numOfSlaveBands][4];

        final double[][] cov = new double[2][2];
        for (int i = 0; i < numOfSlaveBands; i++) {

            cov[0][0] = masterMean2 - masterMean*masterMean;
            cov[0][1] = slaveMeanCross[i] - masterMean*slaveMean[i];
            cov[1][0] = cov[0][1];
            cov[1][1] = slaveMean2[i] - slaveMean[i]*slaveMean[i];

            final Matrix Cov = new Matrix(cov);
            final EigenvalueDecomposition Eig = Cov.eig();
            final Matrix D = Eig.getD();
            final Matrix V = Eig.getV();

            // eigenVectorMatrices saves V' in row, i.e. {v00, v10, v01, v11}
            if (D.get(0,0) >= D.get(1,1)) {
                eigenVectorMatrices[i][0] = V.get(0,0);
                eigenVectorMatrices[i][1] = V.get(1,0);
                eigenVectorMatrices[i][2] = V.get(0,1);
                eigenVectorMatrices[i][3] = V.get(1,1);
            } else {
                eigenVectorMatrices[i][0] = V.get(0,1);
                eigenVectorMatrices[i][1] = V.get(1,1);
                eigenVectorMatrices[i][2] = V.get(0,0);
                eigenVectorMatrices[i][3] = V.get(1,0);
            }
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

        targetProduct.setPreferredTileSize(JAI.getDefaultTileSize());

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
    }

    /**
     * Add user selected slave bands to target product.
     */
    private void addSelectedBands() {

        // add slave bands in target product
        for (String slaveBandName : slaveBandNames) {

            if (targetProduct.getBand(slaveBandName) == null) {

                final Band slaveBand = sourceProduct.getBand(slaveBandName);
                if (slaveBand == null) {
                    throw new OperatorException("Source band not found: " + slaveBandName);
                }

                final Band targetBand = new Band(slaveBandName,
                                                 slaveBand.getDataType(),
                                                 slaveBand.getRasterWidth(),
                                                 slaveBand.getRasterHeight());

                targetBand.setUnit(slaveBand.getUnit());
                targetProduct.addBand(targetBand);
            }
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

        if(reloadStats) {
            if(getStatistics()) {
                setInitialValues();
                reloadStats = false;
            }
        }

        Rectangle targetTileRectangle = targetTile.getRectangle();

        final Band masterBand = sourceProduct.getBand(masterBandName);
        final Band slaveBand = sourceProduct.getBand(targetBand.getName());

        final Tile masterRaster = getSourceTile(masterBand, targetTileRectangle, pm);
        final ProductData masterRawSamples = masterRaster.getRawSamples();

        final Tile slaveRaster = getSourceTile(slaveBand, targetTileRectangle, pm);
        final ProductData slaveRawSamples = slaveRaster.getRawSamples();

        final int idx = slaveBandIndex.get(targetBand.getName());
        final int n = masterRawSamples.getNumElems();

        for (int i = 0; i < n; i++) {

            final double vm = masterRawSamples.getElemDoubleAt(i);
            final double vs = slaveRawSamples.getElemDoubleAt(i);

            final double vPCA1 = eigenVectorMatrices[idx][0]*vm + eigenVectorMatrices[idx][1]*vs;
            final double vPCA2 = eigenVectorMatrices[idx][2]*vm + eigenVectorMatrices[idx][3]*vs;

            if(vPCA1 < minPCA[idx][0])
                minPCA[idx][0] = vPCA1;

            if(vPCA2 < minPCA[idx][1])
                minPCA[idx][1] = vPCA2;
        }

        statsCalculated = true;
    }

    /**
     * Output min values of PCA images to themporary metadata.
     */
    @Override
    public void dispose() {

        if (!statsCalculated) {
            return;
        }

        writeMinsToMetadata();
    }

    private void writeMinsToMetadata() {

        final MetadataElement root = sourceProduct.getMetadataRoot();
        final MetadataElement tempElemRoot = root.getElement("temporary metadata");

        for (String bandName : slaveBandIndex.keySet())  {
            final int bandIdx = slaveBandIndex.get(bandName);
            final MetadataElement subElemRoot = tempElemRoot.getElement(bandName);
            PCAStatisticsOp.setAttribute(subElemRoot, "min1", minPCA[bandIdx][0]);
            PCAStatisticsOp.setAttribute(subElemRoot, "min2", minPCA[bandIdx][1]);
            PCAStatisticsOp.setAttribute(subElemRoot, "eigen vector matrix 0", eigenVectorMatrices[bandIdx][0]);
            PCAStatisticsOp.setAttribute(subElemRoot, "eigen vector matrix 1", eigenVectorMatrices[bandIdx][1]);
            PCAStatisticsOp.setAttribute(subElemRoot, "eigen vector matrix 2", eigenVectorMatrices[bandIdx][2]);
            PCAStatisticsOp.setAttribute(subElemRoot, "eigen vector matrix 3", eigenVectorMatrices[bandIdx][3]);
        }

        try {
            ProductIO.writeProduct(sourceProduct, sourceProduct.getFileLocation(),
                                   DimapProductConstants.DIMAP_FORMAT_NAME,
                                   true, ProgressMonitor.NULL);
        } catch(Exception e) {
            throw new OperatorException(e.getMessage());
        }
    }

    // The following function is for unit test only.
    public double getMinM(final int bandIdx, final int pcaImageIdx) {
        return minPCA[bandIdx][pcaImageIdx];
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
            super(PCAMinOp.class);
        }
    }
}