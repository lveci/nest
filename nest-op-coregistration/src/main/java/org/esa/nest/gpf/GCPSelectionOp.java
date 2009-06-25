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
import com.bc.ceres.core.SubProgressMonitor;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
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
import org.esa.beam.util.math.MathUtils;
import org.esa.beam.visat.toolviews.placemark.PlacemarkNameFactory;
import org.esa.nest.datamodel.Unit;

import javax.media.jai.*;
import javax.media.jai.operator.DFTDescriptor;
import java.awt.*;
import java.awt.image.*;
import java.awt.image.DataBufferDouble;
import java.awt.image.renderable.ParameterBlock;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * Image co-registration is fundamental for Interferometry SAR (InSAR) imaging and its applications, such as
 * DEM map generation and analysis. To obtain a high quality InSAR image, the individual complex images need
 * to be co-registered to sub-pixel accuracy. The co-registration is accomplished through an alignment of a
 * master image with a slave image.
 *
 * To achieve the alignment of master and slave images, the first step is to generate a set of uniformly
 * spaced ground control points (GCPs) in the master image, along with the corresponding GCPs in the slave
 * image. These GCP pairs are used in constructing a warp distortion function, which establishes a map
 * between pixels in the slave and master images.
 *
 * This operator computes the slave GCPS for given master GCPs. First the geometric information of the
 * master GCPs is used in determining the initial positions of the slave GCPs. Then a cross-correlation
 * is performed between imagettes surrounding each master GCP and its corresponding slave GCP to obtain
 * accurate slave GCP position. This step is repeated several times until the slave GCP position is
 * accurate enough.
 */

@OperatorMetadata(alias="GCP-Selection",
                  description = "Automatic Selection of Ground Control Points")
public class GCPSelectionOp extends Operator {

    @SourceProduct
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

     @Parameter(description = "The number of GCPs to use in a grid", interval = "(10, 10000]", defaultValue = "200",
                label="Number of GCPs")
    private int numGCPtoGenerate = 200;

    @Parameter(valueSet = {"32","64","128","256","512","1024"}, defaultValue = "128", label="Coarse Registration Window Width")
    private String coarseRegistrationWindowWidth = "128";
    @Parameter(valueSet = {"32","64","128","256","512","1024"}, defaultValue = "128", label="Coarse Registration Window Height")
    private String coarseRegistrationWindowHeight = "128";
    @Parameter(valueSet = {"2","4","8","16"}, defaultValue = "2", label="Row Interpolation Factor")
    private String rowInterpFactor = "2";
    @Parameter(valueSet = {"2","4","8","16"}, defaultValue = "2", label="Column Interpolation Factor")
    private String columnInterpFactor = "2";
    @Parameter(description = "The maximum number of iterations", interval = "(1, 10]", defaultValue = "2",
                label="Max Iterations")
    private int maxIteration = 2;
    @Parameter(description = "Tolerance in slave GCP validation check", interval = "(0, *)", defaultValue = "0.5",
                label="GCP Tolerance")
    private double gcpTolerance = 0.5;

    // ==================== input parameters used for complex co-registration ==================
    @Parameter(valueSet = {"32","64","128","256","512","1024"}, defaultValue = "128", label="Fine Registration Window Width")
    private String fineRegistrationWindowWidth = "128";
    @Parameter(valueSet = {"32","64","128","256","512","1024"}, defaultValue = "128", label="Fine Registration Window Height")
    private String fineRegistrationWindowHeight = "128";
    @Parameter(description = "The coherence window size", interval = "(1, 10]", defaultValue = "3",
                label="Coherence Window Size")
    private int coherenceWindowSize = 3;
    @Parameter(description = "The coherence threshold", interval = "(0, *)", defaultValue = "0.6",
                label="Coherence Threshold")
    private double coherenceThreshold = 0.6;
    @Parameter(description = "Use sliding window for coherence calculation", defaultValue = "true",
                label="Compute coherence with sliding window")
    private boolean useSlidingWindow = true;

//    @Parameter(description = "The coherence function tolerance", interval = "(0, *)", defaultValue = "1.e-6",
//                label="Coherence Function Tolerance")
    private double coherenceFuncToler = 1.e-5;
//    @Parameter(description = "The coherence value tolerance", interval = "(0, *)", defaultValue = "1.e-3",
//                label="Coherence Value Tolerance")
    private double coherenceValueToler = 1.e-2;
    // =========================================================================================

    private Band masterBand1 = null;
    private Band masterBand2 = null;

    private boolean complexCoregistration = false;

    private ProductNodeGroup<Pin> masterGcpGroup = null;

    private int sourceImageWidth;
    private int sourceImageHeight;
    private int cWindowWidth = 0; // row dimension for master and slave imagette for cross correlation, must be power of 2
    private int cWindowHeight = 0; // column dimension for master and slave imagette for cross correlation, must be power of 2
    private int rowUpSamplingFactor = 0; // cross correlation interpolation factor in row direction, must be power of 2
    private int colUpSamplingFactor = 0; // cross correlation interpolation factor in column direction, must be power of 2
    private int cHalfWindowWidth;
    private int cHalfWindowHeight;

    // parameters used for complex co-registration
    private int fWindowWidth = 0;  // row dimension for master and slave imagette for computing coherence, must be power of 2
    private int fWindowHeight = 0; // column dimension for master and slave imagette for computing coherence, must be power of 2
    private int fHalfWindowWidth;
    private int fHalfWindowHeight;

    private final static int ITMAX = 200;
    private final static double TOL = 2.0e-4;      // Tolerance passed to brent
    private final static double GOLD = 1.618034;   // Here GOLD is the default ratio by which successive intervals are magnified
    private final static double GLIMIT = 100.0;    // GLIMIT is the maximum magnification allowed for a parabolic-fit step.
    private final static double TINY = 1.0e-20;
    private final static double CGOLD = 0.3819660; // CGOLD is the golden ratio;
    private final static double ZEPS = 1.0e-10;    // ZEPS is a small number that protects against trying to achieve fractional

    private final static Map<Band, Band> sourceRasterMap = new HashMap<Band, Band>(10);
    private final static Map<Band, Band> complexSrcMap = new HashMap<Band, Band>(10);

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public GCPSelectionOp() {
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
    public void initialize() throws OperatorException
    {
        cWindowWidth = Integer.parseInt(coarseRegistrationWindowWidth);
        cWindowHeight = Integer.parseInt(coarseRegistrationWindowHeight);
        cHalfWindowWidth = cWindowWidth / 2;
        cHalfWindowHeight = cWindowHeight / 2;

        rowUpSamplingFactor = Integer.parseInt(rowInterpFactor);
        colUpSamplingFactor = Integer.parseInt(columnInterpFactor);

        final double achievableAccuracy = 1.0 / (double)Math.max(rowUpSamplingFactor, colUpSamplingFactor);
        if (gcpTolerance < achievableAccuracy) {
            throw new OperatorException("The achievable accuracy with current interpolation factors is " +
                    achievableAccuracy + ", GCP Tolerance is below it.");
        }

        masterBand1 = sourceProduct.getBandAt(0);
        if(masterBand1.getUnit()!= null && masterBand1.getUnit().equals(Unit.REAL) && sourceProduct.getNumBands() > 1) {
            masterBand2 = sourceProduct.getBandAt(1);
            complexCoregistration = true;
        }

        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();

        createTargetProduct();

        masterGcpGroup = sourceProduct.getGcpGroup(masterBand1);
        if (masterGcpGroup.getNodeCount() <= 0) {
            addGCPGrid(sourceImageWidth, sourceImageHeight, numGCPtoGenerate, masterGcpGroup);
        }

        OperatorUtils.copyGCPsToTarget(masterGcpGroup, targetProduct.getGcpGroup(targetProduct.getBandAt(0)));
        
        if (complexCoregistration) {
            fWindowWidth = Integer.parseInt(fineRegistrationWindowWidth);
            fWindowHeight = Integer.parseInt(fineRegistrationWindowHeight);
            fHalfWindowWidth = fWindowWidth / 2;
            fHalfWindowHeight = fWindowHeight / 2;
        }
    }

    private static void addGCPGrid(
            final int width, final int height, final int numPins, final ProductNodeGroup<Pin> group) {

        final float ratio = width / (float)height;
        final float n = (float)Math.sqrt(numPins / ratio);
        final float m = ratio * n;
        final float spacingX = width / m;
        final float spacingY = height / n;

        group.removeAll();

        for(float y=spacingY/2f; y < height; y+= spacingY) {

            for(float x=spacingX/2f; x < width; x+= spacingX) {

                final String[] uniquePinNameAndLabel =
                        PlacemarkNameFactory.createUniqueNameAndLabel(GcpDescriptor.INSTANCE, group);
                final Pin newPin = new Pin(uniquePinNameAndLabel[0],
                             uniquePinNameAndLabel[1], "",
                             new PixelPos((int)x, (int)y), null,
                             GcpDescriptor.INSTANCE.createDefaultSymbol());
                group.add(newPin);
            }
        }
    }

    /**
     * Create target product.
     */
    private void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceImageWidth,
                                    sourceImageHeight);

        final int numSrcBands = sourceProduct.getNumBands();
        for(int i=0; i < numSrcBands; ++i) {
            final Band srcBand = sourceProduct.getBandAt(i);
            final Band targetBand = targetProduct.addBand(srcBand.getName(), srcBand.getDataType());
            ProductUtils.copyRasterDataNodeProperties(srcBand, targetBand);
            sourceRasterMap.put(targetBand, srcBand);

            if(complexCoregistration) {
                if(srcBand.getUnit() != null && srcBand.getUnit().equals(Unit.REAL)) {
                    if(i + 1 < numSrcBands)
                        complexSrcMap.put(srcBand, sourceProduct.getBandAt(i+1));
                }
            }
        }

        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);
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

            boolean skipImaginary = false;
            for(Band targetBand : targetProduct.getBands()) {
                final Band slaveBand = sourceRasterMap.get(targetBand);
                if (slaveBand != masterBand1 && slaveBand != masterBand2) {
                    if(complexCoregistration) {
                        if(skipImaginary) {             // every other slave band
                            skipImaginary = false;
                        } else {
                            computeSlaveGCPs(slaveBand, complexSrcMap.get(slaveBand), targetBand, targetRectangle,
                                    SubProgressMonitor.create(pm, 1));
                            skipImaginary = true;
                        }
                    } else {
                        computeSlaveGCPs(slaveBand, null, targetBand, targetRectangle, SubProgressMonitor.create(pm, 1));
                    }
                }
                // copy slave data to target
                final Tile targetTile = targetTileMap.get(targetBand);
                targetTile.setRawSamples(getSourceTile(slaveBand, targetRectangle, pm).getRawSamples());
                pm.worked(1);
            }

        } catch (Exception e){
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }

    /**
     * Compute slave GCPs for the given tile.
     *
     * @param slaveBand the input band
     * @param slaveBand2 for complex
     * @param targetBand the output band
     * @param targetRectangle The coordinates of the current tile.
     * @param pm progress monitor
     */
    private void computeSlaveGCPs (final Band slaveBand, final Band slaveBand2, final Band targetBand,
                                   final Rectangle targetRectangle, final ProgressMonitor pm) {
     try {
        final ProductNodeGroup<Pin> targetGCPGroup = targetProduct.getGcpGroup(targetBand);

        pm.beginTask("computeSlaveGCPs ", masterGcpGroup.getNodeCount());
        for(int i = 0; i < masterGcpGroup.getNodeCount(); ++i) {

            final Pin mPin = masterGcpGroup.get(i);
            final PixelPos mGCPPixelPos = mPin.getPixelPos();

            if (checkMasterGCPValidity(mGCPPixelPos, targetRectangle)) {

                final GeoPos mGCPGeoPos = mPin.getGeoPos();
                final PixelPos sGCPPixelPos = slaveBand.getGeoCoding().getPixelPos(mGCPGeoPos, null);

                if (!checkSlaveGCPValidity(sGCPPixelPos)) {
                    //System.out.println("GCP(" + i + ") is outside slave image.");
                    continue;
                }
                //System.out.println(i + ", (" + mGCPPixelPos.x + "," + mGCPPixelPos.y + "), (" +
                //                              sGCPPixelPos.x + "," + sGCPPixelPos.y + ")");

                boolean getSlaveGCP = getCoarseSlaveGCPPosition(slaveBand, slaveBand2, mGCPPixelPos, sGCPPixelPos);

                if (getSlaveGCP && complexCoregistration) {
                    getSlaveGCP = getFineSlaveGCPPosition(slaveBand, slaveBand2, mGCPPixelPos, sGCPPixelPos);
                }

                if (getSlaveGCP) {

                    final Pin sPin = new Pin(mPin.getName(),
                                       mPin.getLabel(),
                                       mPin.getDescription(),
                                       sGCPPixelPos,
                                       mGCPGeoPos,
                                       mPin.getSymbol());

                    targetGCPGroup.add(sPin);
                    //System.out.println("final slave gcp[" + i + "] = " + "(" + sGCPPixelPos.x + "," + sGCPPixelPos.y + ")");
                    //System.out.println();

                } else {
                    //System.out.println("GCP(" + i + ") is invalid.");
                }
            }
            pm.worked(1);
        }
     } finally {
        pm.done();
     }
    }

    /**
     * Check if a given master GCP is within the given tile and the GCP imagette is within the image.
     * @param pixelPos The GCP pixel position.
     * @param targetRectangle The coordinates of the current tile.
     * @return flag Return true if the GCP is within the given tile and the GCP imagette is within the image,
     *              false otherwise.
     */
    private boolean checkMasterGCPValidity(final PixelPos pixelPos, final Rectangle targetRectangle) {

        return (pixelPos.x >= targetRectangle.x && pixelPos.x < targetRectangle.x + targetRectangle.width ) &&
               (pixelPos.y >= targetRectangle.y && pixelPos.y < targetRectangle.y + targetRectangle.height) &&
               (pixelPos.x - cHalfWindowWidth + 1 >= 0 && pixelPos.x + cHalfWindowWidth <= sourceImageWidth - 1) &&
               (pixelPos.y - cHalfWindowHeight + 1 >= 0 && pixelPos.y + cHalfWindowHeight <= sourceImageHeight -1);
    }

    /**
     * Check if a given slave GCP imagette is within the image.
     * @param pixelPos The GCP pixel position.
     * @return flag Return true if the GCP is within the image, false otherwise.
     */
    private boolean checkSlaveGCPValidity(final PixelPos pixelPos) {

        return (pixelPos.x - cHalfWindowWidth + 1 >= 0 && pixelPos.x + cHalfWindowWidth <= sourceImageWidth - 1) &&
               (pixelPos.y - cHalfWindowHeight + 1 >= 0 && pixelPos.y + cHalfWindowHeight <= sourceImageHeight -1);
    }

    private boolean getCoarseSlaveGCPPosition(final Band slaveBand, final Band slaveBand2,
                                              final PixelPos mGCPPixelPos, final PixelPos sGCPPixelPos) {

        final double[] mI = getMasterImagette(mGCPPixelPos);
        //System.out.println("Master imagette:");
        //outputRealImage(mI);

        double rowShift = gcpTolerance + 1;
        double colShift = gcpTolerance + 1;
        int numIter = 0;

        while (Math.abs(rowShift) >= gcpTolerance || Math.abs(colShift) >= gcpTolerance) {

            if (numIter >= maxIteration) {
                return false;
            }

            if (!checkSlaveGCPValidity(sGCPPixelPos)) {
                return false;
            }

            final double[] sI = getSlaveImagette(slaveBand, slaveBand2, sGCPPixelPos);
            //System.out.println("Slave imagette:");
            //outputRealImage(sI);

            final double[] shift = {0,0};
            if (!getSlaveGCPShift(shift, mI, sI)) {
                return false;
            }

            rowShift = shift[0];
            colShift = shift[1];
            sGCPPixelPos.x += (float) colShift;
            sGCPPixelPos.y += (float) rowShift;
            numIter++;
        }

        return true;
    }

    private double[] getMasterImagette(final PixelPos gcpPixelPos) throws OperatorException {
        final double[] mI = new double[cWindowWidth*cWindowHeight];
        final int x0 = (int)gcpPixelPos.x;
        final int y0 = (int)gcpPixelPos.y;
        final int xul = x0 - cHalfWindowWidth + 1;
        final int yul = y0 - cHalfWindowHeight + 1;
        final Rectangle masterImagetteRectangle = new Rectangle(xul, yul, cWindowWidth, cWindowHeight);

        try {
            final Tile masterImagetteRaster1 = getSourceTile(masterBand1, masterImagetteRectangle, null);
            final ProductData masterData1 = masterImagetteRaster1.getDataBuffer();

            ProductData masterData2 = null;
            if (complexCoregistration) {
                final Tile masterImagetteRaster2 = getSourceTile(masterBand2, masterImagetteRectangle, null);
                masterData2 = masterImagetteRaster2.getDataBuffer();
            }

            int k = 0;
            for (int j = 0; j < cWindowHeight; j++) {
                for (int i = 0; i < cWindowWidth; i++) {
                    final int index = masterImagetteRaster1.getDataBufferIndex(xul + i, yul + j);
                    if (complexCoregistration) {
                        final double v1 = masterData1.getElemDoubleAt(index);
                        final double v2 = masterData2.getElemDoubleAt(index);
                        mI[k++] = v1*v1 + v2*v2;
                    } else {
                        mI[k++] = masterData1.getElemDoubleAt(index);
                    }
                }
            }
            return mI;

        } catch (Exception e){
            throw new OperatorException(e);
        }
    }

    private double[] getSlaveImagette(final Band slaveBand, final Band slaveBand2, final PixelPos gcpPixelPos)
                                        throws OperatorException {
        
        final double[] sI = new double[cWindowWidth*cWindowHeight];
        final float x0 = gcpPixelPos.x;
        final float y0 = gcpPixelPos.y;
        final int xul = (int)x0 - cHalfWindowWidth + 1;
        final int yul = (int)y0 - cHalfWindowHeight + 1;
        final Rectangle slaveImagetteRectangle = new Rectangle(xul, yul, cWindowWidth + 1, cWindowHeight + 1);
        int k = 0;

        try {
            final Tile slaveImagetteRaster1 = getSourceTile(slaveBand, slaveImagetteRectangle, null);
            final ProductData slaveData1 = slaveImagetteRaster1.getDataBuffer();

            Tile slaveImagetteRaster2 = null;
            ProductData slaveData2 = null;
            if (complexCoregistration) {
                slaveImagetteRaster2 = getSourceTile(slaveBand2, slaveImagetteRectangle, null);
                slaveData2 = slaveImagetteRaster2.getDataBuffer();
            }

            for (int j = 0; j < cWindowHeight; j++) {
                final float y = y0 - cHalfWindowHeight + j + 1;
                for (int i = 0; i < cWindowWidth; i++) {
                    final float x = x0 - cHalfWindowWidth + i + 1;

                    if (complexCoregistration) {
                        final double v1 = getInterpolatedSampleValue(slaveImagetteRaster1, slaveData1, x, y);
                        final double v2 = getInterpolatedSampleValue(slaveImagetteRaster2, slaveData2, x, y);
                        sI[k++] = v1*v1 + v2*v2;
                    } else {
                        sI[k++] = getInterpolatedSampleValue(slaveImagetteRaster1, slaveData1, x, y);
                    }
                }
            }
            return sI;

        } catch (Exception e){
            throw new OperatorException(e);
        }
    }

    private static double getInterpolatedSampleValue(final Tile slaveRaster, final ProductData slaveData,
                                                     final float x, final float y) {
        final int x0 = (int)x;
        final int x1 = x0 + 1;
        final int y0 = (int)y;
        final int y1 = y0 + 1;
        final double v00 = slaveData.getElemDoubleAt(slaveRaster.getDataBufferIndex(x0, y0));
        final double v01 = slaveData.getElemDoubleAt(slaveRaster.getDataBufferIndex(x0, y1));
        final double v10 = slaveData.getElemDoubleAt(slaveRaster.getDataBufferIndex(x1, y0));
        final double v11 = slaveData.getElemDoubleAt(slaveRaster.getDataBufferIndex(x1, y1));
        final double wy = (double)(y - y0);
        final double wx = (double)(x - x0);

        return MathUtils.interpolate2D(wy, wx, v00, v01, v10, v11);
    }

    private boolean getSlaveGCPShift(final double[] shift, final double[] mI, final double[] sI) {

        // perform cross correlation
        final PlanarImage crossCorrelatedImage = computeCrossCorrelatedImage(mI, sI);

        // check peak validity
        final double mean = getMean(crossCorrelatedImage);
        if (Double.compare(mean, 0.0) == 0) {
            return false;
        }
        /*
        double max = getMax(crossCorrelatedImage);
        double qualityParam = max / mean;
        if (qualityParam <= qualityThreshold) {
            return false;
        }
        */

        // get peak shift: row and col
        final int w = crossCorrelatedImage.getWidth();
        final int h = crossCorrelatedImage.getHeight();

        final Raster idftData = crossCorrelatedImage.getData();
        final double[] real = idftData.getSamples(0, 0, w, h, 0, (double[])null);
        //System.out.println("Cross correlated imagette:");
        //outputRealImage(real);

        int peakRow = 0;
        int peakCol = 0;
        double peak = real[0];
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                final int k = r*h + c;
                if (real[k] > peak) {
                    peak = real[k];
                    peakRow = r;
                    peakCol = c;
                }
            }
        }
        //System.out.println("peak = " + peak + " at (" + peakRow + ", " + peakCol + ")");

        if (peakRow <= w/2) {
            shift[0] = (double)(-peakRow) / (double)rowUpSamplingFactor;
        } else {
            shift[0] = (double)(w - peakRow) / (double)rowUpSamplingFactor;
        }

        if (peakCol <= h/2) {
            shift[1] = (double)(-peakCol) / (double)colUpSamplingFactor;
        } else {
            shift[1] = (double)(h - peakCol) / (double)colUpSamplingFactor;
        }

        return true;
    }

    private PlanarImage computeCrossCorrelatedImage(final double[] mI, final double[] sI) {

        // get master imagette spectrum
        final RenderedImage masterImage = createRenderedImage(mI, cWindowWidth, cWindowHeight);
        final PlanarImage masterSpectrum = dft(masterImage);
        //System.out.println("Master spectrum:");
        //outputComplexImage(masterSpectrum);

        // get slave imagette spectrum
        final RenderedImage slaveImage = createRenderedImage(sI, cWindowWidth, cWindowHeight);
        final PlanarImage slaveSpectrum = dft(slaveImage);
        //System.out.println("Slave spectrum:");
        //outputComplexImage(slaveSpectrum);

        // get conjugate slave spectrum
        final PlanarImage conjugateSlaveSpectrum = conjugate(slaveSpectrum);
        //System.out.println("Conjugate slave spectrum:");
        //outputComplexImage(conjugateSlaveSpectrum);

        // multiply master spectrum and conjugate slave spectrum
        final PlanarImage crossSpectrum = multiplyComplex(masterSpectrum, conjugateSlaveSpectrum);
        //System.out.println("Cross spectrum:");
        //outputComplexImage(crossSpectrum);

        // upsampling cross spectrum
        final RenderedImage upsampledCrossSpectrum = upsampling(crossSpectrum);

        // perform IDF on the cross spectrum
        final PlanarImage correlatedImage = idft(upsampledCrossSpectrum);
        //System.out.println("Correlated image:");
        //outputComplexImage(correlatedImage);

        // compute the magnitode of the cross correlated image
        return magnitude(correlatedImage);
    }

    private static RenderedImage createRenderedImage(final double[] array, final int w, final int h) {

        // create rendered image with demension being width by height
        final SampleModel sampleModel = RasterFactory.createBandedSampleModel(DataBuffer.TYPE_DOUBLE, w, h, 1);
        final ColorModel colourModel = PlanarImage.createColorModel(sampleModel);
        final DataBufferDouble dataBuffer = new DataBufferDouble(array, array.length);
        final WritableRaster raster = RasterFactory.createWritableRaster(sampleModel, dataBuffer, new Point(0,0));

        return new BufferedImage(colourModel, raster, false, new Hashtable());
    }

    private static PlanarImage dft(final RenderedImage image) {

        final ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(DFTDescriptor.SCALING_NONE);
        pb.add(DFTDescriptor.REAL_TO_COMPLEX);
        return JAI.create("dft", pb, null);
    }

    private static PlanarImage idft(final RenderedImage image) {

        final ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(DFTDescriptor.SCALING_DIMENSIONS);
        pb.add(DFTDescriptor.COMPLEX_TO_COMPLEX);
        return JAI.create("idft", pb, null);
    }

    private static PlanarImage conjugate(final PlanarImage image) {

        final ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        return JAI.create("conjugate", pb, null);
    }

    private static PlanarImage multiplyComplex(final PlanarImage image1, final PlanarImage image2){

        final ParameterBlock pb = new ParameterBlock();
        pb.addSource(image1);
        pb.addSource(image2);
        return JAI.create("MultiplyComplex", pb, null);
    }

    private RenderedImage upsampling(final PlanarImage image) {

        // System.out.println("Source image:");
        // outputComplexImage(image);

        final int w = image.getWidth();  // w is power of 2
        final int h = image.getHeight(); // h is power of 2
        final int newWidth = rowUpSamplingFactor * w; // rowInterpFactor should be power of 2 to avoid zero padding in idft
        final int newHeight = colUpSamplingFactor * h; // colInterpFactor should be power of 2 to avoid zero padding in idft

        // create shifted image
        final ParameterBlock pb1 = new ParameterBlock();
        pb1.addSource(image);
        pb1.add(w/2);
        pb1.add(h/2);
        PlanarImage shiftedImage = JAI.create("PeriodicShift", pb1, null);
        //System.out.println("Shifted image:");
        //outputComplexImage(shiftedImage);

        // create zero padded image
        final ParameterBlock pb2 = new ParameterBlock();
        final int leftPad = (newWidth - w) / 2;
        final int rightPad = leftPad;
        final int topPad = (newHeight - h) / 2;
        final int bottomPad = topPad;
        pb2.addSource(shiftedImage);
        pb2.add(leftPad);
        pb2.add(rightPad);
        pb2.add(topPad);
        pb2.add(bottomPad);
        pb2.add(BorderExtender.createInstance(BorderExtender.BORDER_ZERO));
        final PlanarImage zeroPaddedImage = JAI.create("border", pb2);

        // reposition zero padded image so the image origin is back at (0,0)
        final ParameterBlock pb3 = new ParameterBlock();
        pb3.addSource(zeroPaddedImage);
        pb3.add(1.0f*leftPad);
        pb3.add(1.0f*topPad);
        final PlanarImage zeroBorderedImage = JAI.create("translate", pb3, null);
        //System.out.println("Zero padded image:");
        //outputComplexImage(zeroBorderedImage);

        // shift the zero padded image
        final ParameterBlock pb4 = new ParameterBlock();
        pb4.addSource(zeroBorderedImage);
        pb4.add(newWidth/2);
        pb4.add(newHeight/2);
        final PlanarImage shiftedZeroPaddedImage = JAI.create("PeriodicShift", pb4, null);
        //System.out.println("Shifted zero padded image:");
        //outputComplexImage(shiftedZeroPaddedImage);

        return shiftedZeroPaddedImage;
    }

    private static PlanarImage magnitude(final PlanarImage image) {

        final ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        return JAI.create("magnitude", pb, null);
    }

    private static double getMean(final RenderedImage image) {

        final ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(null); // null ROI means whole image
        pb.add(1); // check every pixel horizontally
        pb.add(1); // check every pixel vertically

        // Perform the mean operation on the source image.
        final RenderedImage meanImage = JAI.create("mean", pb, null);
        // Retrieve and report the mean pixel value.
        final double[] mean = (double[])meanImage.getProperty("mean");
        return mean[0];
    }

    private static double getMax(final RenderedImage image) {

        final ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(null); // null ROI means whole image
        pb.add(1); // check every pixel horizontally
        pb.add(1); // check every pixel vertically

        // Perform the extrema operation on the source image
        final RenderedOp op = JAI.create("extrema", pb);
        // Retrieve both the maximum and minimum pixel value
        final double[][] extrema = (double[][]) op.getProperty("extrema");
        return extrema[1][0];
    }

    // This function is for debugging only.
    private static void outputRealImage(final double[] I) {

        for (double v:I) {
            System.out.print(v + ",");
        }
        System.out.println();
    }

    // This function is for debugging only.
    private static void outputComplexImage(final PlanarImage image) {

        final int w = image.getWidth();
        final int h = image.getHeight();
        final Raster dftData = image.getData();
        final double[] real = dftData.getSamples(0, 0, w, h, 0, (double[])null);
        final double[] imag = dftData.getSamples(0, 0, w, h, 1, (double[])null);
        System.out.println("Real part:");
        for (double v:real) {
            System.out.print(v + ", ");
        }
        System.out.println();
        System.out.println("Imaginary part:");
        for (double v:imag) {
            System.out.print(v + ", ");
        }
        System.out.println();
    }

    /**
     * The function is for unit test only.
     *
     * @param windowWidth The window width for cross-correlation
     * @param windowHeight The window height for cross-correlation
     * @param rowUpSamplingFactor The row up sampling rate
     * @param colUpSamplingFactor The column up sampling rate
     * @param maxIter The maximum number of iterations in computing slave GCP shift
     * @param tolerance The stopping criterion for slave GCP shift calculation
     */
    public void setTestParameters(final String windowWidth,
                                  final String windowHeight,
                                  final String rowUpSamplingFactor,
                                  final String colUpSamplingFactor,
                                  final int maxIter,
                                  final double tolerance) {

        coarseRegistrationWindowWidth = windowWidth;
        coarseRegistrationWindowHeight = windowHeight;
        rowInterpFactor = rowUpSamplingFactor;
        columnInterpFactor = colUpSamplingFactor;
        maxIteration = maxIter;
        gcpTolerance = tolerance;
    }

    //=========================================== Complex Co-registration ==============================================

    // This function is for debugging only.
    private static void outputRealImage(final double[][] I) {
        final int row = I.length;
        final int col = I[0].length;
        for (int r = 0; r < row; r++) {
            for (int c = 0; c < col; c++) {
                System.out.print(I[r][c] + ",");
            }
        }
        System.out.println();
    }


    private boolean getFineSlaveGCPPosition(final Band slaveBand1, final Band slaveBand2,
                                            final PixelPos mGCPPixelPos, final PixelPos sGCPPixelPos) {

        //System.out.println("mGCP = (" + mGCPPixelPos.x + ", " + mGCPPixelPos.y + ")");
        //System.out.println("Initial sGCP = (" + sGCPPixelPos.x + ", " + sGCPPixelPos.y + ")");

        final ComplexCoregData compleData = new ComplexCoregData();
        getComplexMasterImagette(compleData, mGCPPixelPos);
        /*
        System.out.println("Real part of master imagette:");
        outputRealImage(compleData.mII);
        System.out.println("Imaginary part of master imagette:");
        outputRealImage(compleData.mIQ);
        */

        getInitialComplexSlaveImagette(compleData, slaveBand1, slaveBand2, sGCPPixelPos);
        /*
        System.out.println("Real part of initial slave imagette:");
        outputRealImage(compleData.sII0);
        System.out.println("Imaginary part of initial slave imagette:");
        outputRealImage(compleData.sIQ0);
        */

        final double[] p = {sGCPPixelPos.x, sGCPPixelPos.y};

        final double coherence = powell(compleData, p);
        //System.out.println("Final sGCP = (" + p[0] + ", " + p[1] + "), coherence = " + (1-coherence));

        if (1 - coherence < coherenceThreshold) {
            //System.out.println("Invalid GCP");
            return false;
        } else {
            sGCPPixelPos.x = (float)p[0];
            sGCPPixelPos.y = (float)p[1];
            //System.out.println("Valid GCP");
            return true;
        }
    }

    private void getComplexMasterImagette(final ComplexCoregData compleData, final PixelPos gcpPixelPos) {

        compleData.mII = new double[fWindowHeight][fWindowWidth];
        compleData.mIQ = new double[fWindowHeight][fWindowWidth];
        final int x0 = (int)gcpPixelPos.x;
        final int y0 = (int)gcpPixelPos.y;
        final int xul = x0 - fHalfWindowWidth + 1;
        final int yul = y0 - fHalfWindowHeight + 1;
        final Rectangle masterImagetteRectangle = new Rectangle(xul, yul, fWindowWidth, fWindowHeight);

        final Tile masterImagetteRaster1 = getSourceTile(masterBand1, masterImagetteRectangle, null);
        final Tile masterImagetteRaster2 = getSourceTile(masterBand2, masterImagetteRectangle, null);

        final ProductData masterData1 = masterImagetteRaster1.getDataBuffer();
        final ProductData masterData2 = masterImagetteRaster2.getDataBuffer();

        for (int j = 0; j < fWindowHeight; j++) {
            for (int i = 0; i < fWindowWidth; i++) {
                final int index = masterImagetteRaster1.getDataBufferIndex(xul + i, yul + j);
                compleData.mII[j][i] = masterData1.getElemDoubleAt(index);
                compleData.mIQ[j][i] = masterData2.getElemDoubleAt(index);
            }
        }
    }

    private void getInitialComplexSlaveImagette(final ComplexCoregData compleData,
                                                final Band slaveBand1, final Band slaveBand2,
                                                final PixelPos sGCPPixelPos) {

        compleData.sII0 = new double[fWindowHeight][fWindowWidth];
        compleData.sIQ0 = new double[fWindowHeight][fWindowWidth];

        final int x0 = (int)(sGCPPixelPos.x + 0.5);
        final int y0 = (int)(sGCPPixelPos.y + 0.5);

        compleData.point0[0] = sGCPPixelPos.x;
        compleData.point0[1] = sGCPPixelPos.y;

        final int xul = x0 - fHalfWindowWidth + 1;
        final int yul = y0 - fHalfWindowHeight + 1;
        final Rectangle slaveImagetteRectangle = new Rectangle(xul, yul, fWindowWidth, fWindowHeight);

        final Tile slaveImagetteRaster1 = getSourceTile(slaveBand1, slaveImagetteRectangle, null);
        final Tile slaveImagetteRaster2 = getSourceTile(slaveBand2, slaveImagetteRectangle, null);

        final ProductData slaveData1 = slaveImagetteRaster1.getDataBuffer();
        final ProductData slaveData2 = slaveImagetteRaster2.getDataBuffer();

        for (int j = 0; j < fWindowHeight; j++) {
            for (int i = 0; i < fWindowWidth; i++) {
                final int index = slaveImagetteRaster1.getDataBufferIndex(xul + i, yul + j);
                compleData.sII0[j][i] = slaveData1.getElemDoubleAt(index);
                compleData.sIQ0[j][i] = slaveData2.getElemDoubleAt(index);
            }
        }
    }

    private double computeCoherence(final ComplexCoregData compleData, final double[] point) {

        // Set penalty at the boundary of the pixel so that the searching area is within a pixel
        final double xShift = Math.abs(compleData.point0[0] - point[0]);
        final double yShift = Math.abs(compleData.point0[1] - point[1]);
        if (xShift >= 0.5 || yShift >= 0.5) {
            return 1.0;
        }

        getComplexSlaveImagette(compleData, point);
        /*
        System.out.println("Real part of master imagette:");
        outputRealImage(compleData.mII);
        System.out.println("Imaginary part of master imagette:");
        outputRealImage(compleData.mIQ);
        System.out.println("Real part of slave imagette:");
        outputRealImage(compleData.sII);
        System.out.println("Imaginary part of slave imagette:");
        outputRealImage(compleData.sIQ);
        */

        double coherence = 0.0;
        if (useSlidingWindow) {

            for (int r = 0; r <= fWindowHeight - coherenceWindowSize; r++) {
                for (int c = 0; c <= fWindowWidth - coherenceWindowSize; c++) {
                    coherence += getCoherence(compleData, r, c, coherenceWindowSize, coherenceWindowSize);
                }
            }

            coherence /= (fWindowHeight - coherenceWindowSize + 1)*(fWindowWidth - coherenceWindowSize + 1);

        } else {
            coherence = getCoherence(compleData, 0, 0, fWindowWidth, fWindowHeight);
        }
        //System.out.println("coherence = " + coherence);

        return 1 - coherence;
    }

    private double computeCoherence(final ComplexCoregData compleData,
                                    final double a, final double[] p, final double[] d) {

        final double[] point = {p[0] + a*d[0], p[1] + a*d[1]};
        return computeCoherence(compleData, point);
    }

    private void getComplexSlaveImagette(final ComplexCoregData compleData, final double[] point) {

        compleData.sII = new double[fWindowHeight][fWindowWidth];
        compleData.sIQ = new double[fWindowHeight][fWindowWidth];

        final int x0 = (int)(compleData.point0[0] + 0.5);
        final int y0 = (int)(compleData.point0[1] + 0.5);

        final double xShift = x0 - point[0];
        final double yShift = y0 - point[1];
        //System.out.println("xShift = " + xShift);
        //System.out.println("yShift = " + yShift);

        final double[] rowArray = new double[fWindowWidth*2];
        final double[] rowPhaseArray = new double[fWindowWidth*2];
        final DoubleFFT_1D row_fft = new DoubleFFT_1D(fWindowWidth);

        int signalLength = rowArray.length / 2;
        computeShiftPhaseArray(xShift, signalLength, rowPhaseArray);
        for (int r = 0; r < fWindowHeight; r++) {
            int k = 0;
            for (int c = 0; c < fWindowWidth; c++) {
                rowArray[k++] = compleData.sII0[r][c];
                rowArray[k++] = compleData.sIQ0[r][c];
            }

            row_fft.complexForward(rowArray);
            multiplySpectrumByShiftFactor(rowArray, rowPhaseArray);
            row_fft.complexInverse(rowArray, true);
            for (int c = 0; c < fWindowWidth; c++) {
                compleData.sII[r][c] = rowArray[2*c];
                compleData.sIQ[r][c] = rowArray[2*c+1];
            }
        }

        final double[] colArray = new double[2*fWindowHeight];
        final double[] colPhaseArray = new double[2*fWindowHeight];
        final DoubleFFT_1D col_fft = new DoubleFFT_1D(fWindowHeight);

        signalLength = colArray.length / 2;
        computeShiftPhaseArray(yShift, signalLength, colPhaseArray);
        for (int c = 0; c < fWindowWidth; c++) {
            int k = 0;
            for (int r = 0; r < fWindowHeight; r++) {
                colArray[k++] = compleData.sII[r][c];
                colArray[k++] = compleData.sIQ[r][c];
            }

            col_fft.complexForward(colArray);
            multiplySpectrumByShiftFactor(colArray, colPhaseArray);
            col_fft.complexInverse(colArray, true);
            for (int r = 0; r < fWindowHeight; r++) {
                compleData.sII[r][c] = colArray[2*r];
                compleData.sIQ[r][c] = colArray[2*r+1];
            }
        }
    }

    private static void computeShiftPhaseArray(final double shift, final int signalLength, final double[] phaseArray) {

        int k2;
        double phaseK;
        double phase = -2.0*Math.PI*shift/signalLength;
        int halfSignalLength = (int)(signalLength*0.5 + 0.5);

        for (int k = 0; k < signalLength; ++k) {
            if (k < halfSignalLength) {
                phaseK = phase*k;
            } else {
                phaseK = phase * (k - signalLength);
            }
            k2 = k * 2;
            phaseArray[k2] = Math.cos(phaseK);
            phaseArray[k2 + 1] = Math.sin(phaseK);
        }
    }

    private static void multiplySpectrumByShiftFactor(final double[] array, final double[] phaseArray) {

        int k2;
        double c, s;
        double real, imag;
        int signalLength = array.length / 2;
        for (int k = 0; k < signalLength; ++k) {
            k2 = k * 2;
            c = phaseArray[k2];
            s = phaseArray[k2+1];
            real = array[k2];
            imag = array[k2+1];
            array[k2] = real*c - imag*s;
            array[k2+1] = real*s + imag*c;
        }
    }

    private static double getCoherence(final ComplexCoregData compleData, final int row, final int col,
                                       final int coherenceWindowWidth, final int coherenceWindowHeight) {

        // Compute coherence of master and slave imagettes by creating a coherence image
        double sum1 = 0.0;
        double sum2 = 0.0;
        double sum3 = 0.0;
        double sum4 = 0.0;
        double mr, mi, sr, si;
        double[] mII, mIQ, sII, sIQ;
        int rIdx;
        for (int r = 0; r < coherenceWindowHeight; r++) {
            rIdx = row + r;
            mII = compleData.mII[rIdx];
            mIQ = compleData.mIQ[rIdx];
            sII = compleData.sII[rIdx];
            sIQ = compleData.sIQ[rIdx];
            for (int c = 0; c < coherenceWindowWidth; c++) {
                mr = mII[col+c];
                mi = mIQ[col+c];
                sr = sII[col+c];
                si = sIQ[col+c];
                sum1 += mr*sr + mi*si;
                sum2 += mi*sr - mr*si;
                sum3 += mr*mr + mi*mi;
                sum4 += sr*sr + si*si;
            }
        }

        return Math.sqrt(sum1*sum1 + sum2*sum2) / Math.sqrt(sum3*sum4);
    }

    /**
     * Minimize coherence as a function of row shift and column shift using
     * Powell's method. The 1-D minimization subroutine linmin() is used. p
     * is the starting point and also the final optimal point.  \
     *
     * @param complexData the master and slave complex data
     * @param p Starting point for the minimization.
     * @return fp
     */
    private double powell(final ComplexCoregData complexData, final double[] p) {

        final double ftol = 0.01;

        final double[][] directions = {{0, 1}, {1, 0}}; // set initial searching directions
        double fp = computeCoherence(complexData, p); // get function value for initial point
        //System.out.println("Initial 1 - coherence = " + fp);

        final double[] p0 = {p[0], p[1]}; // save the initial point
        final double[] currentDirection = {0.0, 0.0}; // current searching direction

        for (int iter = 0; iter < ITMAX; iter++) {

            //System.out.println("Iteration: " + iter);

            p0[0] = p[0];
            p0[1] = p[1];
            double fp0 = fp; // save function value for the initial point
            int imax = 0;     // direction index for the largest single step decrement
            double maxDecrement = 0.0; // the largest single step decrement

            for (int i = 0; i < 2; i++) { // for each iteration, loop through all directions in the set

                // copy the ith searching direction
                currentDirection[0] = directions[i][0];
                currentDirection[1] = directions[i][1];

                final double fpc = fp; // save function value at current point
                fp = linmin(complexData, p, currentDirection); // minimize function along the ith direction, and get new point in p
                //System.out.println("Decrement along direction " + (i+1) + ": " + (fpc - fp));

                final double decrement = Math.abs(fpc - fp);
                if (decrement > maxDecrement) { // if the single step decrement is the largest so far,
                    maxDecrement = decrement;   // record the decrement and the direction index.
                    imax = i;
                }
            }

            // After trying all directions, check the decrement from start point to end point.
            // If the decrement is less than certain amount, then stop.
            /*
            if (2.0*Math.abs(fp0 - fp) <= ftol*(Math.abs(fp0) + Math.abs(fp))) { //Termination criterion.
                System.out.println("Number of iterations: " + (iter+1));
                return fp;
            }
            */
            //Termination criterion 1: stop if coherence change is small
            if (Math.abs(fp0 - fp) < coherenceFuncToler) {
                //System.out.println("C1: Number of iterations: " + (iter+1));
                return fp;
            }

            //Termination criterion 2: stop if GCP shift is small
            if (Math.sqrt((p0[0] - p[0])*(p0[0] - p[0]) + (p0[1] - p[1])*(p0[1] - p[1])) < coherenceValueToler) {
                //System.out.println("C2: Number of iterations: " + (iter+1));
                return fp;
            }
            // Otherwise, prepare for the next iteration
            //final double[] pe = new double[2];
            final double[] averageDirection = {p[0] - p0[0] , p[1] - p0[1]};
            final double norm = Math.sqrt(averageDirection[0]*averageDirection[0] +
                                          averageDirection[1]*averageDirection[1]);
            for (int j = 0; j < 2; j++) {
                averageDirection[j] /= norm; // construct the average direction
                //pe[j] = p[j] + averageDirection[j]; // construct the extrapolated point
                //p0[j] = p[j]; // save the final opint of current iteration as the initial point for the next iteration
            }

            //final double fpe = computeCoherence(complexData, pe); // get function value for the extrapolated point.
            final double fpe = linmin(complexData, p, averageDirection); // JL test

            if (fpe < fp0) { // condition 1 for updating search direction

                final double d1 = (fp0 - fp - maxDecrement)*(fp0 - fp - maxDecrement);
                final double d2 = (fp0 - fpe)*(fp0 - fpe);

                if (2.0*(fp0 - 2.0*fp + fpe)*d1 < maxDecrement*d2) { // condition 2 for updating searching direction

                    // The calling of linmin() next line should be commented out because it changes
                    // the starting point for the next iteration and this average direction will be
                    // added to the searching directions anyway.
                    //fp = linmin(complexData, p, averageDirection); // minimize function along the average direction

                    for (int j = 0; j < 2; j++) {
                        directions[imax][j] = directions[1][j]; // discard the direction for the largest decrement
                        directions[1][j] = averageDirection[j]; // add the average direction as a new direction
                    }
                }
            }
        }
        return fp;
    }

    /**
     * Given a starting point p and a searching direction xi, moves and
     * resets p to where the function takes on a minimum value along the
     * direction xi from p, and replaces xi by the actual vector displacement
     * that p was moved. Also returns the minimum value. This is accomplished
     * by calling the routines mnbrak() and brent().
     *
     * @param complexData the master and slave complex data
     * @param p The starting point
     * @param xi The searching direction
     * @return The minimum function value
     */
    private double linmin(final ComplexCoregData complexData, final double[] p, final double[] xi) {

         // set initial guess for brackets: [ax, bx, cx]
        final double[] bracketPoints = {0.0, 0.02, 0.0};

        // get new brackets [ax, bx, cx] that bracket a minimum of the function
        mnbrak(complexData, bracketPoints, p, xi);

        // find function minimum in the brackets
        return brent(complexData, bracketPoints, p, xi);
    }

    /**
     * Given a distinct initial points ax and bx in bracketPoints,
     * this routine searches in the downhill direction (defined by the
     * function as evaluated at the initial points) and returns new points
     * ax, bx, cx that bracket a minimum of the function.
     *
     * @param complexData the master and slave complex data
     * @param bracketPoints The bracket points ax, bx and cx
     * @param p The starting point
     * @param xi The searching direction
     */
    private void mnbrak(final ComplexCoregData complexData,
                        final double[] bracketPoints, final double[] p, final double[] xi) {

        double ax = bracketPoints[0];
        double bx = bracketPoints[1];

        double fa = computeCoherence(complexData, ax, p, xi);
        double fb = computeCoherence(complexData, bx, p, xi);

        if (fb > fa) { // Switch roles of a and b so that we can go
                       // downhill in the direction from a to b.
            double tmp = ax;
            ax = bx;
            bx = tmp;

            tmp = fa;
            fa = fb;
            fb = tmp;
        }

        double cx = bx + GOLD*(bx - ax); // First guess for c.
        double fc = computeCoherence(complexData, cx, p, xi);

        double fu;
        while (fb > fc) { // Keep returning here until we bracket.

            final double r = (bx - ax)*(fb - fc); // Compute u by parabolic extrapolation from a; b; c.
                                            // TINY is used to prevent any possible division by zero.
            final double q = (bx - cx)*(fb - fa);

            double u = bx - ((bx - cx)*q - (bx - ax)*r) /
                       (2.0*sign(Math.max(Math.abs(q - r), TINY), q - r));

            final double ulim = bx + GLIMIT*(cx - bx);

            // We won't go farther than this. Test various possibilities:
            if ((bx - u)*(u - cx) > 0.0) { // Parabolic u is between b and c: try it.

                fu = computeCoherence(complexData, u, p, xi);

                if (fu < fc) { // Got a minimum between b and c.

                    ax = bx;
                    bx = u;
                    break;

                } else if (fu > fb) { // Got a minimum between between a and u.

                    cx = u;
                    break;
                }

                // reach this point can only be:  fc <= fu <= fb
                u = cx + GOLD*(cx - bx); // Parabolic fit was no use. Use default magnification.
                fu = computeCoherence(complexData, u, p, xi);

            } else if ((cx - u)*(u - ulim) > 0.0) { // Parabolic fit is between c and its allowed limit.

                fu = computeCoherence(complexData, u, p, xi);

                if (fu < fc) {
                    bx = cx;
                    cx = u;
                    u = cx + GOLD*(cx - bx);
                    fb = fc;
                    fc = fu;
                    fu = computeCoherence(complexData, u, p, xi);
                }

            } else if ((u - ulim)*(ulim - cx) >= 0.0) { // Limit parabolic u to maximum allowed value.

                u = ulim;
                fu = computeCoherence(complexData, u, p, xi);

            } else { // Reject parabolic u, use default magnification.
                u = cx + GOLD*(cx - bx);
                fu = computeCoherence(complexData, u, p, xi);
            }

            ax = bx;
            bx = cx;
            cx = u; // Eliminate oldest point and continue.

            fa = fb;
            fb = fc;
            fc = fu;
        }

        bracketPoints[0] = ax;
        bracketPoints[1] = bx;
        bracketPoints[2] = cx;
    }

    /**
     * Given a bracketing triplet of abscissas [ax, bx, cx] (such that bx
     * is between ax and cx, and f(bx) is less than both f(ax) and f(cx)),
     * this routine isolates the minimum to a fractional precision of about
     * tol using Brent's method. p is reset to the point where function
     * takes on a minimum value along direction xi from p, and xi is replaced
     * by the axtual displacement that p moved. The minimum function value
     * is returned.
     *
     * @param complexData the master and slave complex data
     * @param bracketPoints The bracket points ax, bx and cx
     * @param pp The starting point
     * @param xi The searching direction
     * @return The minimum unction value
     */
    private double brent(final ComplexCoregData complexData,
                         final double[] bracketPoints, final double[] pp, final double[] xi) {

        final int maxNumIterations = 100; // the maximum number of iterations

        final double ax = bracketPoints[0];
        final double bx = bracketPoints[1];
        final double cx = bracketPoints[2];

        double d = 0.0;
        double u = 0.0;
        double e = 0.0; //This will be the distance moved on the step before last.
        double a = (ax < cx ? ax : cx); // a and b must be in ascending order,
        double b = (ax > cx ? ax : cx); // but input abscissas need not be.
        double x = bx; // Initializations...
        double w = bx;
        double v = bx;
        double fw = computeCoherence(complexData, x, pp, xi);
        double fv = fw;
        double fx = fw;

        for (int iter = 0; iter < maxNumIterations; iter++) { // Main loop.

            final double xm = 0.5*(a + b);
            final double tol1 = TOL*Math.abs(x) + ZEPS;
            final double tol2 = 2.0*tol1;

            if (Math.abs(x - xm) <= (tol2 - 0.5*(b - a))) { // Test for done here.
                xi[0] *= x;
                xi[1] *= x;
                pp[0] += xi[0];
                pp[1] += xi[1];
                return fx;
            }

            if (Math.abs(e) > tol1) { // Construct a trial parabolic fit.

                final double r = (x - w)*(fx - fv);
                double q = (x - v)*(fx - fw);
                double p = (x - v)*q - (x - w)*r;
                q = 2.0*(q - r);

                if (q > 0.0) {
                    p = -p;
                }

                q = Math.abs(q);
                final double etemp = e;
                e = d;

                if (Math.abs(p) >= Math.abs(0.5*q*etemp) ||
                    p <= q*(a-x) || p >= q*(b-x)) {

                    e = (x >= xm ? a-x : b-x);
                    d = CGOLD*e;

                    // The above conditions determine the acceptability of the parabolic fit. Here we
                    // take the golden section step into the larger of the two segments.
                } else {

                    d = p/q; // Take the parabolic step.
                    u = x + d;
                    if (u - a < tol2 || b - u < tol2)
                        d = sign(tol1, xm - x);
                }

            } else {

                e = (x >= xm ? a - x : b - x); // larger part: from x to both ends
                d = CGOLD*e;
            }

            u = (Math.abs(d) >= tol1 ? x + d : x + sign(tol1, d));
            final double fu = computeCoherence(complexData, u, pp, xi);

            // This is the one function evaluation per iteration.
            if (fu <= fx) { // Now decide what to do with our func tion evaluation.

                if(u >= x){
                    a = x;
                } else {
                    b = x;
                }
                v = w;
                w = x;
                x = u;

                fv = fw;
                fw = fx;
                fx = fu;

            } else {

                if (u < x){
                    a = u;
                } else {
                    b = u;
                }

                if (fu <= fw || w == x) {

                    v = w;
                    w = u;
                    fv = fw;
                    fw = fu;

                } else if (fu <= fv || v == x || v == w) {

                    v = u;
                    fv = fu;
                }
            } // Done with housekeeping. Back for another iteration.
        }

        System.out.println("Too many iterations in brent");
        return -1.0;
    }

    private static double sign(final double a, final double b) {
        if (b >= 0) return a;
        return -a;
    }

    private static class ComplexCoregData {
        private double[][] mII = null;          // real part of master imagette for coherence computation
        private double[][] mIQ = null;          // imaginary part of master imagette for coherence computation
        private double[][] sII = null;          // real part of slave imagette for coherence computation
        private double[][] sIQ = null;          // imaginary part of slave imagette for coherence computation
        private double[][] sII0 = null;         // real part of initial slave imagette for coherence computation
        private double[][] sIQ0 = null;         // imaginary part of initial slave imagette for coherence computation
        final double[] point0 = new double[2];  // initial slave GCP position
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
            super(GCPSelectionOp.class);
            super.setOperatorUI(GCPSelectionOpUI.class);
        }
    }
}