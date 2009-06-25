/*
 * Copyright (C) 2002-2007 by Array System Computing Inc.
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
package org.esa.nest.gpf.filtering;

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
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.Unit;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Applies a Speckle Filter to the data
 * @todo enhanced Lee filter
 * @todo enhanced Frost filter
 * @todo Kuan filter
 * @todo complex data
 */
@OperatorMetadata(alias="Speckle-Filter",
                  description = "Speckle Reduction")
public class SpeckleFilterOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct = null;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band", 
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames;

    @Parameter(valueSet = {MEAN_SPECKLE_FILTER, MEDIAN_SPECKLE_FILTER, FROST_SPECKLE_FILTER,
            GAMMA_MAP_SPECKLE_FILTER, LEE_SPECKLE_FILTER}, defaultValue = MEAN_SPECKLE_FILTER, label="Filter")
    private String filter;
    @Parameter(description = "The kernel x dimension", interval = "(1, 100]", defaultValue = "3", label="Size X")
    private int filterSizeX = 3;
    @Parameter(description = "The kernel y dimension", interval = "(1, 100]", defaultValue = "3", label="Size Y")
    private int filterSizeY = 3;
    @Parameter(description = "The damping factor (Frost filter only)", interval = "(0, 100]", defaultValue = "2",
                label="Frost Damping Factor")
    private int dampingFactor = 2;

    private double enl;
    private double cu, cu2;
    private double[] neighborValues;
    private double[] mask;

    static final String MEAN_SPECKLE_FILTER = "Mean";
    static final String MEDIAN_SPECKLE_FILTER = "Median";
    static final String FROST_SPECKLE_FILTER = "Frost";
    static final String GAMMA_MAP_SPECKLE_FILTER = "Gamma Map";
    static final String LEE_SPECKLE_FILTER = "Lee";

    private transient Map<Band, Band> bandMap;
    private int halfSizeX;
    private int halfSizeY;
    private int sourceImageWidth;
    private int sourceImageHeight;

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public SpeckleFilterOp() {
    }

    /**
     * Set speckle filter. This function is used by unit test only.
     * @param s The filter name.
     */
    public void SetFilter(String s) {

        if (s.equals(MEAN_SPECKLE_FILTER) ||
            s.equals(MEDIAN_SPECKLE_FILTER) ||
            s.equals(FROST_SPECKLE_FILTER) ||
            s.equals(GAMMA_MAP_SPECKLE_FILTER) ||
            s.equals(LEE_SPECKLE_FILTER)) {
                filter = s;
        } else {
            throw new OperatorException(s + " is an invalid filter name.");
        }
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

        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        bandMap = new HashMap<Band, Band>(3);

        addSelectedBands();

        // copy meta data from source to target
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        halfSizeX = filterSizeX / 2;
        halfSizeY = filterSizeY / 2;
    }

    private void addSelectedBands() {

        if (sourceBandNames == null || sourceBandNames.length == 0) {
            Band[] bands = sourceProduct.getBands();
            ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        Band[] sourceBands = new Band[sourceBandNames.length];
        for (int i = 0; i < sourceBandNames.length; i++) {
            String sourceBandName = sourceBandNames[i];
            Band sourceBand = sourceProduct.getBand(sourceBandName);
            if (sourceBand == null) {
                throw new OperatorException("Source band not found: " + sourceBandName);
            }
            sourceBands[i] = sourceBand;
        }

        for(Band srcBand : sourceBands) {

            final String unit = srcBand.getUnit();
            if(unit == null) {
                throw new OperatorException("band "+srcBand.getName()+" requires a unit");
            }

            if (unit.contains(Unit.PHASE) || unit.contains(Unit.REAL) || unit.contains(Unit.IMAGINARY)) {
                throw new OperatorException("For complex product please select intensity band only");
            }

            Band targetBand = new Band(srcBand.getName(),
                                       ProductData.TYPE_FLOAT32,
                                       sourceProduct.getSceneRasterWidth(),
                                       sourceProduct.getSceneRasterHeight());

            targetBand.setUnit(srcBand.getUnit());
            targetProduct.addBand(targetBand);
            bandMap.put(targetBand, srcBand);
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

        Rectangle targetTileRectangle = targetTile.getRectangle();
        int x0 = targetTileRectangle.x;
        int y0 = targetTileRectangle.y;
        int w = targetTileRectangle.width;
        int h = targetTileRectangle.height;

        Rectangle sourceTileRectangle = getSourceTileRectangle(x0, y0, w, h);
        Tile sourceRaster = getSourceTile(bandMap.get(targetBand), sourceTileRectangle, pm);

        if(filter.equals(MEAN_SPECKLE_FILTER)) {

            computeMean(sourceRaster, targetTile, x0, y0, w, h, pm);

        } else if(filter.equals(MEDIAN_SPECKLE_FILTER)) {

            computeMedian(sourceRaster, targetTile, x0, y0, w, h, pm);

        } else if(filter.equals(FROST_SPECKLE_FILTER)) {

            computeFrost(sourceRaster, targetTile, x0, y0, w, h, pm);

        } else if(filter.equals(GAMMA_MAP_SPECKLE_FILTER)) {

            computeEquivalentNumberOfLooks(sourceRaster, x0, y0, w, h);
            computeGammaMap(sourceRaster, targetTile, x0, y0, w, h, pm);

        } else if(filter.equals(LEE_SPECKLE_FILTER)) {

            computeEquivalentNumberOfLooks(sourceRaster, x0, y0, w, h);
            computeLee(sourceRaster, targetTile, x0, y0, w, h, pm);
        }
    }

    private Rectangle getSourceTileRectangle(int x0, int y0, int w, int h) {

        int sx0 = x0;
        int sy0 = y0;
        int sw = w;
        int sh = h;

        if (x0 >= halfSizeX) {
            sx0 -= halfSizeX;
            sw += halfSizeX;
        }

        if (y0 >= halfSizeY) {
            sy0 -= halfSizeY;
            sh += halfSizeY;
        }

        if (x0 + w + halfSizeX <= sourceImageWidth) {
            sw += halfSizeX;
        }

        if (y0 + h + halfSizeY <= sourceImageHeight) {
            sh += halfSizeY;
        }

        return new Rectangle(sx0, sy0, sw, sh);
    }

    /**
     * Filter the given tile of image with Mean filter.
     *
     * @param sourceRaster The source tile.
     * @param targetTile   The current tile associated with the target band to be computed.
     * @param x0           x coordinate for the upper-left point of the target_Tile_Rectangle.
     * @param y0           y coordinate for the upper-left point of the target_Tile_Rectangle.
     * @param w            Width for the target_Tile_Rectangle.
     * @param h            Hight for the target_Tile_Rectangle.
     * @param pm           A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the filtered value.
     */
    private void computeMean(Tile sourceRaster, Tile targetTile, int x0, int y0, int w, int h, ProgressMonitor pm) {

        neighborValues = new double[filterSizeX*filterSizeY];
        final ProductData trgData = targetTile.getDataBuffer();

        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {

                getNeighborValues(x, y, sourceRaster);

                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x, y), getMeanValue());
            }
            pm.worked(1);
        }
    }

    /**
     * Filter the given tile of image with Median filter.
     *
     * @param sourceRaster The source tile.
     * @param targetTile   The current tile associated with the target band to be computed.
     * @param x0           x coordinate for the upper-left point of the target_Tile_Rectangle.
     * @param y0           y coordinate for the upper-left point of the target_Tile_Rectangle.
     * @param w            Width for the target_Tile_Rectangle.
     * @param h            Hight for the target_Tile_Rectangle.
     * @param pm           A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the filtered value.
     */
    private void computeMedian(Tile sourceRaster, Tile targetTile, int x0, int y0, int w, int h, ProgressMonitor pm) {

        neighborValues = new double[filterSizeX*filterSizeY];
        final ProductData trgData = targetTile.getDataBuffer();

        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {

                getNeighborValues(x, y, sourceRaster);

                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x, y), getMedianValue());
            }
        }
    }

    /**
     * Filter the given tile of image with Frost filter.
     *
     * @param sourceRaster The source tile.
     * @param targetTile   The current tile associated with the target band to be computed.
     * @param x0           x coordinate for the upper-left point of the target_Tile_Rectangle.
     * @param y0           y coordinate for the upper-left point of the target_Tile_Rectangle.
     * @param w            Width for the target_Tile_Rectangle.
     * @param h            Hight for the target_Tile_Rectangle.
     * @param pm           A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the filtered value.
     */
    private void computeFrost(Tile sourceRaster, Tile targetTile, int x0, int y0, int w, int h, ProgressMonitor pm) {

        neighborValues = new double[filterSizeX*filterSizeY];
        mask = new double[filterSizeX*filterSizeY];
        final ProductData trgData = targetTile.getDataBuffer();

        getFrostMask();

        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {

                getNeighborValues(x, y, sourceRaster);

                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x, y), getFrostValue());
            }
        }
    }

    /**
     * Filter the given tile of image with Gamma filter.
     *
     * @param sourceRaster The source tile.
     * @param targetTile   The current tile associated with the target band to be computed.
     * @param x0           x coordinate for the upper-left point of the target_Tile_Rectangle.
     * @param y0           y coordinate for the upper-left point of the target_Tile_Rectangle.
     * @param w            Width for the target_Tile_Rectangle.
     * @param h            Hight for the target_Tile_Rectangle.
     * @param pm           A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the filtered value.
     */
    private void computeGammaMap(Tile sourceRaster, Tile targetTile, int x0, int y0, int w, int h, ProgressMonitor pm) {

        neighborValues = new double[filterSizeX*filterSizeY];
        mask = new double[filterSizeX*filterSizeY];
        final ProductData trgData = targetTile.getDataBuffer();

        getFrostMask();

        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {

                getNeighborValues(x, y, sourceRaster);

                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x, y), getGammaMapValue());
            }
        }
    }

    /**
     * Filter the given tile of image with Lee filter.
     *
     * @param sourceRaster The source tile.
     * @param targetTile   The current tile associated with the target band to be computed.
     * @param x0           x coordinate for the upper-left point of the target_Tile_Rectangle.
     * @param y0           y coordinate for the upper-left point of the target_Tile_Rectangle.
     * @param w            Width for the target_Tile_Rectangle.
     * @param h            Hight for the target_Tile_Rectangle.
     * @param pm           A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the filtered value.
     */
    private void computeLee(Tile sourceRaster, Tile targetTile, int x0, int y0, int w, int h, ProgressMonitor pm) {

        neighborValues = new double[filterSizeX*filterSizeY];
        final ProductData trgData = targetTile.getDataBuffer();

        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {

                getNeighborValues(x, y, sourceRaster);

                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x, y), getLeeValue());
            }
        }
    }

    /**
     * Get pixel intensities in a filter size rectanglar region centered at the given pixel.
     *
     * @param x            x coordinate of a given pixel.
     * @param y            y coordinate of a given pixel.
     * @param sourceRaster The source tile.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs in obtaining the pixel values.
     */
    private void getNeighborValues(final int x, final int y, final Tile sourceRaster) {

        final ProductData srcData = sourceRaster.getDataBuffer();

        for (int i = 0; i < filterSizeX; i++) {

            int xi = x - halfSizeX + i;
            if (xi < 0) {
                xi = 0;
            } else if (xi >= sourceImageWidth) {
                xi = sourceImageWidth - 1;
            }

            final int stride = i*filterSizeY;

            for (int j = 0; j < filterSizeY; j++) {

                int yj = y - halfSizeY + j;
                if (yj < 0) {
                    yj = 0;
                } else if (yj >= sourceImageHeight) {
                    yj = sourceImageHeight - 1;
                }

                neighborValues[j + stride] = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(xi, yj));
            }
        }
    }

    /**
     * Get the mean value of pixel intensities in a given rectanglar region.
     *
     * @return mean The mean value.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs in computation of the mean value.
     */
    private double getMeanValue() {

        double mean = 0.0;
        for (double neighborValue : neighborValues) {
            mean += neighborValue;
        }
        mean /= neighborValues.length;

        return mean;
    }

    /**
     * Get the variance of pixel intensities in a given rectanglar region.
     *
     * @return var The variance value.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs in computation of the variance.
     */
    private double getVarianceValue() {

        double var = 0.0;

        if (neighborValues.length > 1) {

            final double mean = getMeanValue();
            for (double neighborValue : neighborValues) {
                final double diff = neighborValue - mean;
                var += diff * diff;
            }
            var /= (neighborValues.length - 1);
        }

        return var;
    }

    /**
     * Get the median value of pixel intensities in a given rectanglar region.
     *
     * @return median The median value.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs in computation of the median value.
     */
    private double getMedianValue() {

        Arrays.sort(neighborValues);

        // Then get the median value
        return neighborValues[(neighborValues.length / 2)];
    }

    /**
     * Get Frost mask for given Frost filter size.
     *
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs in computation of the Frost mask.
     */
    private void getFrostMask() {

        for (int i = 0; i < filterSizeX; i++) {

            final int s = i*filterSizeY;
            final int dr = Math.abs(i - halfSizeX);

            for (int j = 0; j < filterSizeY; j++) {

                final int dc = Math.abs(j - halfSizeY);
                mask[j + s] = Math.max(dr, dc);
            }
        }
    }

    /**
     * Get the Frost filtered pixel intensity for pixels in a given rectanglar region.
     *
     * @return val The Frost filtered value.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs in computation of the Frost filtered value.
     */
    private double getFrostValue() {

        final double mean = getMeanValue();
        if (Double.compare(mean, Double.MIN_VALUE) <= 0) {
            return mean;
        }

        final double var = getVarianceValue();
        if (Double.compare(var, Double.MIN_VALUE) <= 0) {
            return mean;
        }

        final double k = dampingFactor * var / (mean*mean);

        double sum = 0.0;
        double totalWeight = 0.0;
        for (int i = 0; i < neighborValues.length; i++) {
            final double weight = Math.exp(-k*mask[i]);
            sum += weight * neighborValues[i];
            totalWeight += weight;
        }
        return sum / totalWeight;
    }

    /**
     * Get the Gamma filtered pixel intensity for pixels in a given rectanglar region.
     *
     * @return val The Gamma filtered value.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs in computation of the Gamma filtered value.
     */
    private double getGammaMapValue() {

        final double mean = getMeanValue();
        if (Double.compare(mean, Double.MIN_VALUE) <= 0) {
            return mean;
        }

        final double var = getVarianceValue();
        if (Double.compare(var, Double.MIN_VALUE) <= 0) {
            return mean;
        }

        final double cp = neighborValues[(neighborValues.length/2)];

        final double ci = Math.sqrt(var) / mean;
        if (Double.compare(ci, cu) <= 0) {
            return mean;
        }

        final double cmax = Math.sqrt(2)*cu;
        if (cu < ci && ci < cmax) {
            final double alpha = (1 + cu2) / (ci*ci - cu2);
            final double b = alpha - enl - 1;
            final double d = mean*mean*b*b + 4*alpha*enl*mean*cp;
            return (b*mean + Math.sqrt(d)) / (2*alpha);
        }

        return cp;
    }

    /**
     * Get the Lee filtered pixel intensity for pixels in a given rectanglar region.
     *
     * @return val The Lee filtered value.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs in computation of the Lee filtered value.
     */
    private double getLeeValue() {

        final double mean = getMeanValue();
        if (Double.compare(mean, Double.MIN_VALUE) <= 0) {
            return mean;
        }

        final double var = getVarianceValue();
        if (Double.compare(var, Double.MIN_VALUE) <= 0) {
            return mean;
        }

        final double ci = Math.sqrt(var) / mean;
        if (ci < cu) {
            return mean;
        }

        final double cp = neighborValues[(neighborValues.length/2)];
        final double w = 1 - cu2 / (ci*ci);

        return cp*w + mean*(1 - w);
    }

    void computeEquivalentNumberOfLooks(final Tile sourceRaster,
                                        final int x0, final int y0, final int w, final int h) {

        final ProductData srcData = sourceRaster.getDataBuffer();

        double sum2 = 0;
        double sum4 = 0;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {

                final double v = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x, y));
                final double v2 = v*v;
                sum2 += v2;
                sum4 += v2*v2;
            }
        }

        final double area = h * w;
        final double m2 = sum2 / area;
        final double m4 = sum4 / area;
        final double m2m2 = m2*m2;
        enl = m2m2 / (m4 - m2m2);
        cu = 1.0 / Math.sqrt(enl);
        cu2 = cu*cu;
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     * @see OperatorSpi#createOperator()
     * @see OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(SpeckleFilterOp.class);
            setOperatorUI(SpeckleFilterOpUI.class);
        }
    }
}

