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
import java.util.HashMap;
import java.util.Map;

/**
 * The operator evaluates some local statistics for every pair of user selected master/slave bands of the image.
 * Let the user selected bands be masterBand and slaveBand, and the pixel value for the two bands be x and y
 * respectively, then the following statistics are computed:
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

    @Parameter(description = "The list of source bands.", alias = "masterBand", itemAlias = "band",
            sourceProductId="source", label="Master Band")
    private String masterBandName = null;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Slave Bands")
    private String[] sourceBandNames = null;

    private boolean statsCalculated = false;
    private int numOfBands = 0;     // total number of bands (master and slave) for statistics
    private int numOfPixels = 0;    // total number of pixel values
    private double[] sum;       // summation of pixel values for each band
    private double[] sum2;      // summation of pixel value squares for each band
    private double[] sumCross;  // summation of the dot product of each band and the master band
    private double[] mean;      // mean of pixel values for each band
    private double[] mean2;     // mean of pixel value squares for each band
    private double[] meanCross; // mean of the dot product of each band and the master band

    private final HashMap<String, Integer> statisticsBandIndex = new HashMap<String, Integer>();

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

            createTargetProduct();

            if(masterBandName != null) {
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

        targetProduct.setPreferredTileSize(JAI.getDefaultTileSize());

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
    }

    /**
     * Add user selected master and slave bands to target product.
     */
    private void addSelectedBands() {

        // if no slave band is selected by user, then select all bands
        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            final ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        // add master band in target product
        final Band masterBand = sourceProduct.getBand(masterBandName);
        if (masterBand == null) {
            throw new OperatorException("Source band not found: " + masterBandName);
        }
        Band targetBand = new Band(masterBandName,
                                   masterBand.getDataType(),
                                   masterBand.getRasterWidth(),
                                   masterBand.getRasterHeight());

        targetBand.setUnit(masterBand.getUnit());
        targetProduct.addBand(targetBand);

        numOfBands = 0;
        statisticsBandIndex.put(masterBandName, numOfBands);
        numOfBands++;

        // add slave bands in target product
        for (String slaveBandName : sourceBandNames) {

            if (targetProduct.getBand(slaveBandName) == null) {

                final Band slaveBand = sourceProduct.getBand(slaveBandName);
                if (slaveBand == null) {
                    throw new OperatorException("Source band not found: " + slaveBandName);
                }

                targetBand = new Band(slaveBandName,
                                      slaveBand.getDataType(),
                                      slaveBand.getRasterWidth(),
                                      slaveBand.getRasterHeight());

                targetBand.setUnit(slaveBand.getUnit());
                targetProduct.addBand(targetBand);

                statisticsBandIndex.put(slaveBandName, numOfBands);
                numOfBands++;
            }
        }
    }

    /**
     * Set initial values to some internal variables.
     */
    private void setInitialValues() {

        mean = new double[numOfBands];
        mean2 = new double[numOfBands];
        meanCross = new double[numOfBands];
        sum = new double[numOfBands];
        sum2 = new double[numOfBands];
        sumCross = new double[numOfBands];
        for (int i = 0; i < numOfBands; i++) {
            sum[i] = 0.0;
            sum2[i] = 0.0;
            sumCross[i] = 0.0;
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
            final Band masterBand = sourceProduct.getBand(masterBandName);
            final Tile masterRaster = getSourceTile(masterBand, targetRectangle, pm);
            final ProductData masterRawSamples = masterRaster.getRawSamples();
            final int n = masterRawSamples.getNumElems();

            for (String bandName : statisticsBandIndex.keySet())  {

                checkForCancelation(pm);

                final int bandIdx = statisticsBandIndex.get(bandName);
                final Band sourceBand = sourceProduct.getBand(bandName);
                final Tile sourceRaster = getSourceTile(sourceBand, targetRectangle, pm);
                final ProductData sourceRawSamples = sourceRaster.getRawSamples();

                for (int i = 0; i < n; i++) {
                    final double vm = masterRawSamples.getElemDoubleAt(i);
                    final double vs = sourceRawSamples.getElemDoubleAt(i);
                    sum[bandIdx] += vs;
                    sum2[bandIdx] += vs*vs;
                    sumCross[bandIdx] += vs*vm;
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

        for (String bandName : statisticsBandIndex.keySet())  {
            final int bandIdx = statisticsBandIndex.get(bandName);
            mean[bandIdx] = sum[bandIdx] / numOfPixels;
            mean2[bandIdx] = sum2[bandIdx] / numOfPixels;
            meanCross[bandIdx] = sumCross[bandIdx] / numOfPixels;
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
        setAttribute(tempElemRoot, "master band name", masterBandName);

        for (String bandName : statisticsBandIndex.keySet())  {
            final int bandIdx = statisticsBandIndex.get(bandName);
            final MetadataElement subElemRoot = createElement(tempElemRoot, bandName);
            setAttribute(subElemRoot, "mean", mean[bandIdx]);
            setAttribute(subElemRoot, "square mean", mean2[bandIdx]);
            setAttribute(subElemRoot, "cross mean", meanCross[bandIdx]);
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
    private static MetadataElement createElement(MetadataElement root, String tag) {

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

    // The following functions are for unit test only.
    public int getNumOfBands() {
        return numOfBands;
    }

    public double getMean(int bandIdx) {
        return mean[bandIdx];
    }

    public double getMean2(int bandIdx) {
        return mean2[bandIdx];
    }

    public double getMeanCross(int bandIdx) {
        return meanCross[bandIdx];
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