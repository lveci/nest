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
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import javax.media.jai.JAI;
import java.awt.*;
import java.util.ArrayList;
import java.util.Map;

/**
 * The operator evaluates some local statistics for every pair of user selected source bands.
 * Let the user selected bands be x and y, then the following statistics are computed:
 *
 * 1. Mean of x
 * 2. Mean of x*x
 * 3. Mean of y
 * 4. Mean of y*y
 * 5. Mean of x*y
 */

@OperatorMetadata(alias="PCA-Statistic", description="Computes statistics for PCA", internal=true)
public class PCAStatisticsOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    private String[] sourceBandNames = null;

    @Parameter(description = "The threshold for selecting eigenvalues", interval = "(0, 1]", defaultValue = "1.0",
                label="Eigenvalue Threshold")
    private double eigenvalueThreshold = 1.0;

    private boolean statsCalculated = false;
    private int numOfPixels = 0;    // total number of pixel values
    private int numOfSourceBands;   // number of user selected bands
    private double[] sum;           // summation of pixel values for each band
    private double[][] sumCross;    // summation of the dot product of each band and the master band
    private double[] mean;          // mean of pixel values for each band
    private double[][] meanCross;   // mean of the dot product of each band and the master band

//    private final HashMap<String, Integer> statisticsBandIndex = new HashMap<String, Integer>();

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public PCAStatisticsOp() {
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
            if(!OperatorUtils.isDIMAP(sourceProduct)) {
                throw new OperatorException("Source Product should be in BEAM-DIMAP format");
            }

            createTargetProduct();

            if(sourceBandNames != null && sourceBandNames.length > 0) {
                addSelectedBands();

                setInitialValues();

                // write initial temporary metadata
                writeStatsToMetadata();
            }
        } catch(Exception e) {
            throw new OperatorException(e.getMessage());
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
     * Add user selected master and source bands to target product.
     */
    private void addSelectedBands() {

        // if no source band is selected by user, then select all bands
        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            final ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        numOfSourceBands = sourceBandNames.length;

        // add selected source bands in target product
        for (String bandName : sourceBandNames) {

            final Band sourceBand = sourceProduct.getBand(bandName);
            if (sourceBand == null) {
                throw new OperatorException("Source band not found: " + bandName);
            }

            Band targetBand = new Band(bandName,
                                       sourceBand.getDataType(),
                                       sourceBand.getRasterWidth(),
                                       sourceBand.getRasterHeight());

            targetBand.setUnit(sourceBand.getUnit());
            targetProduct.addBand(targetBand);
        }
    }

    /**
     * Set initial values to some internal variables.
     */
    private void setInitialValues() {

        mean = new double[numOfSourceBands];
        meanCross = new double[numOfSourceBands][numOfSourceBands];
        sum = new double[numOfSourceBands];
        sumCross = new double[numOfSourceBands][numOfSourceBands];
        for (int i = 0; i < numOfSourceBands; i++) {
            sum[i] = 0.0;
            mean[i] = 0.0;
            for (int j = 0; j < numOfSourceBands; j++) {
                sumCross[i][j] = 0.0;
                meanCross[i][j] = 0.0;
            }
        }

        numOfPixels = sourceProduct.getSceneRasterWidth() * sourceProduct.getSceneRasterHeight();
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
            /*
            int x0 = targetRectangle.x;
            int y0 = targetRectangle.y;
            int w = targetRectangle.width;
            int h = targetRectangle.height;
            System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);
            */
            ProductData[] bandsRawSamples = new ProductData[numOfSourceBands];
            for (int i = 0; i < numOfSourceBands; i++) {
                bandsRawSamples[i] =
                        getSourceTile(sourceProduct.getBand(sourceBandNames[i]), targetRectangle, pm).getRawSamples();
            }

            final int n = bandsRawSamples[0].getNumElems();

            double vi, vj;
            for (int i = 0; i < numOfSourceBands; i++)  {
                checkForCancelation(pm);
                for (int j = 0; j <= i; j++) {

                    //System.out.println("i = " + i + ", j = " + j);
                    if (j < i) {

                        for (int k = 0; k < n; k++) {
                            vi = bandsRawSamples[i].getElemDoubleAt(k);
                            vj = bandsRawSamples[j].getElemDoubleAt(k);
                            sumCross[i][j] += vi*vj;
                        }

                    } else { // j == i

                        for (int k = 0; k < n; k++) {
                            vi = bandsRawSamples[i].getElemDoubleAt(k);
                            sum[i] += vi;
                            sumCross[i][j] += vi*vi;
                        }
                    }
                }
            }

        } catch (Exception e){
            throw new OperatorException(e);
        } finally {
            pm.done();
        }

        statsCalculated = true;
    }

    /**
     * Compute statistics for the whole image and output statistics to source product metadata.
     */
    @Override
    public void dispose() {

        if (!statsCalculated) {
            return;
        }

        completeStatistics();

        writeStatsToMetadata();
    }

    /**
     * Compute statistics for the whole image.
     */
    private void completeStatistics() {

        for (int i = 0; i < numOfSourceBands; i++)  {
            mean[i] = sum[i] / numOfPixels;
            for (int j = 0; j <= i; j++) {
                meanCross[i][j] = sumCross[i][j] / numOfPixels;
                if (j != i) {
                    meanCross[j][i] = meanCross[i][j];
                }
            }
        }
    }

    /**
     * Output statistics to source product metadata.
     * @throws OperatorException when can't save metadata
     */
    private void writeStatsToMetadata() throws OperatorException {

        // create temporary metadata
        final MetadataElement root = sourceProduct.getMetadataRoot();
        final MetadataElement tempElemRoot = createElement(root, "temporary metadata");
        setAttribute(tempElemRoot, "eigenvalue threshold", eigenvalueThreshold);

        final MetadataElement staSubElemRoot = createElement(tempElemRoot, "statistics");
        for (int i = 0; i < numOfSourceBands; i++)  {
            String bandName = sourceBandNames[i];
            final MetadataElement subElemRoot = createElement(staSubElemRoot, bandName);
            setAttribute(subElemRoot, "mean", mean[i]);
            for (int j = 0; j < numOfSourceBands; j++) {
                setAttribute(subElemRoot, "cross mean " + j, meanCross[i][j]);
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

    /**
     * Create sub-metadata element.
     * @param root The root metadata element.
     * @param tag The sub-metadata element name.
     * @return The sub-metadata element.
     */
    public static MetadataElement createElement(MetadataElement root, String tag) {

        MetadataElement subElemRoot = root.getElement(tag);
        if(subElemRoot == null) {
            subElemRoot = new MetadataElement(tag);
            root.addElement(subElemRoot);
        }
        return subElemRoot;
    }

    /**
     * Set attribute value.
     * @param root The root metadata element.
     * @param tag The attribute name.
     * @param value The value for the attribute.
     */
    private static void setAttribute(MetadataElement root, String tag, String value) {

        MetadataAttribute attr = root.getAttribute(tag);
        if(attr == null) {
            attr = new MetadataAttribute(tag, ProductData.TYPE_ASCII, 1);
            root.addAttributeFast(attr);
        }
        AbstractMetadata.setAttribute(root, tag, value);
    }

    /**
     * Set attribute value.
     * @param root The root metadata element.
     * @param tag The attribute name.
     * @param value The value for the attribute.
     */
    static void setAttribute(MetadataElement root, String tag, double value) {

        MetadataAttribute attr = root.getAttribute(tag);
        if(attr == null) {
            attr = new MetadataAttribute(tag, ProductData.TYPE_FLOAT64, 1);
            root.addAttributeFast(attr);
        }
        AbstractMetadata.setAttribute(root, tag, value);
    }

    /**
     * Set attribute value.
     * @param root The root metadata element.
     * @param tag The attribute name.
     * @param value The value for the attribute.
     */
    static void setAttribute(MetadataElement root, String tag, int value) {

        MetadataAttribute attr = root.getAttribute(tag);
        if(attr == null) {
            attr = new MetadataAttribute(tag, ProductData.TYPE_INT32, 1);
            root.addAttributeFast(attr);
        }
        AbstractMetadata.setAttribute(root, tag, value);
    }

    // The following functions are for unit test only.
    public int getNumOfBands() {
        return numOfSourceBands;
    }

    public double getMean(int bandIdx) {
        return mean[bandIdx];
    }

    public double getMeanCross(int i, int j) {
        return meanCross[i][j];
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
            super(PCAStatisticsOp.class);
        }
    }
}