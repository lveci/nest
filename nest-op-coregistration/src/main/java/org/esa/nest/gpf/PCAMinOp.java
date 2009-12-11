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

import Jama.Matrix;
import Jama.SingularValueDecomposition;
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
import org.esa.nest.datamodel.AbstractMetadata;

import java.awt.*;
import java.util.Map;
import java.util.Arrays;

/**
 * The operator performs the following perations:
 *
 * 1. Create a covariance matrix from the statistics computed in the previous step by PCAStatisticsOp;
 * 2. Perform eigendecomposition on the covariance matrix to get the eigenvector matrix;
 * 3. Compute PCA images and get the min values of each;
 * 4. Save the min values and eigenvalues in the metadata.
 */

@OperatorMetadata(alias="PCA-Min", description="Computes minimum for PCA", internal=true)
public class PCAMinOp extends Operator {

    @SourceProduct
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    private boolean statsCalculated = false;
    private int numOfSourceBands = 0; // total number of user selected source bands
    private int numPCA; // number of PCA images output
    private double eigenvalueThreshold; // threshold for selecting eigenvalues (in decimal, not in %)

    private String selectEigenvaluesBy;
    private String[] sourceBandNames; // band names user selected source bands
    private double[] mean; // mean of source bands
    private double[][] meanCross; // cross mean of source bands

    private double[][] eigenVectorMatrices; // eigenvector matrices for all source bands
    private double[] eigenValues; // eigenvalues for all source bands
    private double[] minPCA; // min value for all PCA images
    private double totalEigenvalues; // summation of all eigenvalues
    private boolean reloadStats = true;
    private boolean initialValuesSet = false;

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
            throw new OperatorException(e);
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

            selectEigenvaluesBy = tempElemRoot.getAttributeString("select eigenvalues by");
            if (selectEigenvaluesBy.equals(PCAStatisticsOp.EIGENVALUE_THRESHOLD)) {
                eigenvalueThreshold = tempElemRoot.getAttributeDouble("eigenvalue threshold", -1.0)/100.0; // to decimal
            } else {
                numPCA = tempElemRoot.getAttributeInt("number Of PCA Images");
            }

            final MetadataElement staSubElemRoot = tempElemRoot.getElement("statistics");
            numOfSourceBands = staSubElemRoot.getNumElements();
                    
            sourceBandNames = new String[numOfSourceBands];
            mean = new double[numOfSourceBands];
            meanCross = new double[numOfSourceBands][numOfSourceBands];

            for (int i = 0; i < numOfSourceBands; i++) {
                final MetadataElement subElemRoot = staSubElemRoot.getElementAt(i);
                sourceBandNames[i] = subElemRoot.getName();
                mean[i] = subElemRoot.getAttributeDouble("mean");
                for (int j = 0; j < numOfSourceBands; j++) {
                    meanCross[i][j] = subElemRoot.getAttributeDouble("cross mean " + j);
                }
            }

        } catch (Exception e) {
            throw new OperatorException(e);
        }
        return true;
    }

    /**
     * Set initial values to some internal variables.
     */
    private synchronized void setInitialValues() {

        if (initialValuesSet) return;

        minPCA = new double[numOfSourceBands];
        for (int i = 0; i < numOfSourceBands; i++) {
            minPCA[i] = Double.MAX_VALUE;
        }

        computeEigenDecompositionOfCovarianceMatrix();
        initialValuesSet = true;
    }

    /**
     * Compute covariance matrices and perform EVD on each of them.
     */
    void computeEigenDecompositionOfCovarianceMatrix() {

        eigenVectorMatrices = new double[numOfSourceBands][numOfSourceBands];
        eigenValues = new double[numOfSourceBands];

        final double[][] cov = new double[numOfSourceBands][numOfSourceBands];
        for (int i = 0; i < numOfSourceBands; i++) {
            for (int j = 0; j < numOfSourceBands; j++) {
                cov[i][j] = meanCross[i][j] - mean[i]*mean[j];
            }
        }

        final Matrix Cov = new Matrix(cov);
        final SingularValueDecomposition Svd = Cov.svd(); // Cov = USV'
        final Matrix S = Svd.getS();
        final Matrix U = Svd.getU();
        final Matrix V = Svd.getV();

        totalEigenvalues = 0.0;
        for (int i = 0; i < numOfSourceBands; i++) {
            eigenValues[i] = S.get(i,i);
            totalEigenvalues += eigenValues[i];
            for (int j = 0; j < numOfSourceBands; j++) {
                eigenVectorMatrices[i][j] = U.get(i,j);
            }
        }

        if (selectEigenvaluesBy.equals(PCAStatisticsOp.EIGENVALUE_THRESHOLD)) {
            double sum = 0.0;
            for (int i = 0; i < numOfSourceBands; i++) {
                sum += eigenValues[i];
                if (sum / totalEigenvalues >= eigenvalueThreshold) {
                    numPCA = i + 1;
                    break;
                }
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

        //targetProduct.setPreferredTileSize(JAI.getDefaultTileSize());
        targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 10);

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
    }

    /**
     * Add user selected source bands to target product.
     */
    private void addSelectedBands() {

        // add source bands in target product
        for (String bandName : sourceBandNames) {

            final Band sourceBand = sourceProduct.getBand(bandName);
            if (sourceBand == null) {
                throw new OperatorException("Source band not found: " + bandName);
            }

            final Band targetBand = new Band(bandName,
                                             sourceBand.getDataType(),
                                             sourceBand.getRasterWidth(),
                                             sourceBand.getRasterHeight());

            targetBand.setUnit(sourceBand.getUnit());
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

        if(reloadStats) {
            if(getStatistics()) {
                setInitialValues();
                reloadStats = false;
            }
        }

        try {
            final ProductData[] bandsRawSamples = new ProductData[numOfSourceBands];
            for (int i = 0; i < numOfSourceBands; i++) {
                bandsRawSamples[i] =
                        getSourceTile(sourceProduct.getBand(sourceBandNames[i]), targetRectangle, pm).getRawSamples();
            }
            final int n = bandsRawSamples[0].getNumElems();

            double[] tileMinPCA = new double[numOfSourceBands];
            Arrays.fill(tileMinPCA, Double.MAX_VALUE);

            for (int i = 0; i < numPCA; i++) {
                checkForCancelation(pm);
                for (int k = 0; k < n; k++) {
                    double vPCA = 0.0;
                    for (int j = 0; j < numOfSourceBands; j++) {
                        vPCA += bandsRawSamples[j].getElemDoubleAt(k)*eigenVectorMatrices[j][i];
                    }
                    if(vPCA < tileMinPCA[i])
                        tileMinPCA[i] = vPCA;
                }
            }

            computePCAMin(tileMinPCA);

        } catch (Exception e){
            throw new OperatorException(e);
        } finally {
            pm.done();
        }

        statsCalculated = true;
    }

    /**
     * Compute minimum values for all PCA images.
     * @param tileMinPCA The minimum values for all PCA images for a given tile.
     */
    private synchronized void computePCAMin(double[] tileMinPCA) {
        for (int i = 0; i < numPCA; i++) {
            if (tileMinPCA[i] < minPCA[i]) {
                minPCA[i] = tileMinPCA[i];
            }
        }
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
        if (selectEigenvaluesBy.equals(PCAStatisticsOp.EIGENVALUE_THRESHOLD)) {
            tempElemRoot.setAttributeInt("number of PCA images", numPCA);
        }
        final MetadataElement minSubElemRoot = AbstractMetadata.addElement(tempElemRoot, "PCA min");
        for (int i = 0; i < numPCA; i++)  {
            minSubElemRoot.setAttributeDouble("min " + i, minPCA[i]);
        }

        final MetadataElement eigenvalueSubElemRoot = AbstractMetadata.addElement(tempElemRoot, "eigenvalues");
        for (int i = 0; i < numOfSourceBands; i++)  {
            eigenvalueSubElemRoot.setAttributeDouble("element " + i, eigenValues[i]/totalEigenvalues);
        }

        final MetadataElement eigenVectorSubElemRoot = AbstractMetadata.addElement(tempElemRoot, "eigenvectors");
        for (int j = 0; j < numOfSourceBands; j++)  {
            MetadataElement colSubElemRoot = AbstractMetadata.addElement(eigenVectorSubElemRoot, "vector " + j);
            for (int i = 0; i < numOfSourceBands; i++) {
                colSubElemRoot.setAttributeDouble("element " + i, eigenVectorMatrices[i][j]);
            }
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
    public double getMinM(final int i) {
        return minPCA[i];
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