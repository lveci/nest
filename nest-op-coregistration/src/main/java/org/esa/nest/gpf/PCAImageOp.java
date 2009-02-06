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
import org.esa.nest.util.DatUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
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
    private int numOfSourceBands = 0; // total number of user selected slave bands
    private int numPCA; // number of PCA images output
    private String[] sourceBandNames; // band names user selected slave bands
    private double eigenvalueThreshold; // threshold for selecting eigenvalues
    private double[][] eigenVectorMatrices; // eigenvector matrices for all slave bands
    private double[] eigenValues; // eigenvalues for all slave bands
    private double[] minPCA; // min value for first and second PCA images for all master/slave band pairs
    private boolean reloadStats = true;

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
     * @return true if statistics are ok
     */
    private boolean getStatistics() {

        try {

            final MetadataElement root = sourceProduct.getMetadataRoot();
            final MetadataElement tempElemRoot = root.getElement("temporary metadata");
            if (tempElemRoot == null) {
                //throw new OperatorException("Cannot find temporary metadata");
                return false;
            }
            eigenvalueThreshold = tempElemRoot.getAttributeDouble("eigenvalue threshold");
            numPCA = tempElemRoot.getAttributeInt("number of PCA images");

            MetadataElement staSubElemRoot = tempElemRoot.getElement("statistics");
            numOfSourceBands = staSubElemRoot.getNumElements();
            sourceBandNames = new String[numOfSourceBands];
            for (int i = 0; i < numOfSourceBands; i++) {
                MetadataElement subElemRoot = staSubElemRoot.getElementAt(i);
                sourceBandNames[i] = subElemRoot.getName();
            }

            minPCA = new double[numPCA];
            MetadataElement minSubElemRoot = tempElemRoot.getElement("PCA min");
            for (int i = 0; i < numPCA; i++)  {
                minPCA[i] = minSubElemRoot.getAttributeDouble("min " + i);
            }

            eigenValues = new double[numOfSourceBands];
            MetadataElement eigenvalueSubElemRoot = tempElemRoot.getElement("eigenvalues");
            for (int i = 0; i < numOfSourceBands; i++)  {
                eigenValues[i] = eigenvalueSubElemRoot.getAttributeDouble("element " + i);
            }

            eigenVectorMatrices = new double[numOfSourceBands][numOfSourceBands];
            MetadataElement eigenvectorSubElemRoot = tempElemRoot.getElement("eigenvectors");
            for (int j = 0; j < numOfSourceBands; j++)  {
                MetadataElement colSubElemRoot = eigenvectorSubElemRoot.getElement("vector " + j);
                for (int i = 0; i < numOfSourceBands; i++) {
                    eigenVectorMatrices[i][j] = colSubElemRoot.getAttributeDouble("element " + i);
                }
            }

        } catch (Exception e) {
            throw new OperatorException(e.getMessage());
        }
        return true;
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

        final MetadataElement root = targetProduct.getMetadataRoot();
        final MetadataElement tempElemRoot = root.getElement("temporary metadata");
        root.removeElement(tempElemRoot);
    }

    /**
     * Add user selected slave bands to target product.
     */
    private void addSelectedBands() {

        // add PCA bands in target product
        final Band sourcerBand = sourceProduct.getBand(sourceBandNames[0]);
        if (sourcerBand == null) {
            throw new OperatorException("Source band not found: " + sourcerBand);
        }

        final int imageWidth = sourcerBand.getRasterWidth();
        final int imageHeight = sourcerBand.getRasterHeight();
        final String unit = sourcerBand.getUnit();

        for (int i = 0; i < numPCA; i++) {
            final String targetBandName = "PC" + i;
            Band targetBand = new Band(targetBandName, ProductData.TYPE_FLOAT32, imageWidth, imageHeight);
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

        if(reloadStats) {
            if(getStatistics()) {
                reloadStats = false;
            }
        }

        try {
            ProductData[] bandsRawSamples = new ProductData[numOfSourceBands];
            for (int i = 0; i < numOfSourceBands; i++) {
                bandsRawSamples[i] =
                        getSourceTile(sourceProduct.getBand(sourceBandNames[i]), targetRectangle, pm).getRawSamples();
            }
            final int n = bandsRawSamples[0].getNumElems();

            for (int i = 0; i < numPCA; i++) {
                checkForCancelation(pm);

                final Band targetBand = targetProduct.getBand("PC" + i);
                final Tile targetTile = targetTileMap.get(targetBand);
                final ProductData trgData = targetTile.getDataBuffer();

                for (int k = 0; k < n; k++) {
                    double vPCA = 0.0;
                    for (int j = 0; j < numOfSourceBands; j++) {
                        vPCA += bandsRawSamples[j].getElemDoubleAt(k)*eigenVectorMatrices[j][i];
                    }
                    trgData.setElemDoubleAt(k, vPCA - minPCA[i]);
                }
            }

        } catch (Exception e){
            throw new OperatorException(e);
        } finally {
            pm.done();
        }

        pcaImageComputed = true;
    }

    /**
     * Compute statistics for the whole image.
     */
    @Override
    public void dispose() {

        if (!pcaImageComputed) {
            return;
        }

        createReportFile();
        removeTemporaryMetadata();
    }

    private void removeTemporaryMetadata() {

        final MetadataElement root = sourceProduct.getMetadataRoot();
        final MetadataElement tempElemRoot = root.getElement("temporary metadata");
        root.removeElement(tempElemRoot);

        try {
            ProductIO.writeProduct(sourceProduct, sourceProduct.getFileLocation(),
                                   DimapProductConstants.DIMAP_FORMAT_NAME,
                                   true, ProgressMonitor.NULL);
        } catch(Exception e) {
            throw new OperatorException(e.getMessage());
        }
    }


    private void createReportFile() {

        String fileName = sourceProduct.getName() + "_pca_report.txt";
        try {
            final File appUserDir = new File(DatUtils.getApplicationUserDir(true).getAbsolutePath() + File.separator + "log");
            if(!appUserDir.exists()) {
                appUserDir.mkdirs();
            }
            fileName = appUserDir.toString() + File.separator + fileName;
            final FileOutputStream out = new FileOutputStream(fileName);

            // Connect print stream to the output stream
            final PrintStream p = new PrintStream(out);

            p.println();
            p.println("User Selected Bands: ");
            for (int i = 0; i < numOfSourceBands; i++) {
                p.println("    " + sourceBandNames[i]);
            }
            p.println();
            p.println("User Input Eigenvalue Threshold: " + eigenvalueThreshold);
            p.println();
            p.println("Number of PCA Images Output: " + numPCA);
            p.println();
            p.println("Eigenvalues: ");
            for (int i = 0; i < numOfSourceBands; i++)  {
                p.println("    " + eigenValues[i]);
            }
            p.println();
            p.close();

        } catch(IOException exc) {
            throw new OperatorException(exc);
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