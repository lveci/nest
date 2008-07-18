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
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.*;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;

import javax.media.jai.*;
import javax.media.jai.operator.DFTDescriptor;
import java.awt.*;
import java.awt.image.*;
import java.awt.image.DataBufferDouble;
import java.awt.image.renderable.ParameterBlock;
import java.util.Hashtable;

/**
 * The sample operator implementation for an algorithm
 * that can compute bands independently of each other.
 */
@OperatorMetadata(alias="GCP-Selection",
                  description = "Automatic Selection of Ground Control Points")
public class GCPSelectionOperator extends Operator {

    @SourceProducts(count = 2)
    private Product[] sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(valueSet = {"32","64","128","256","512","1024"}, defaultValue = "32")
    private String imagetteWidth;
    @Parameter(valueSet = {"32","64","128","256","512","1024"}, defaultValue = "32")
    private String imagetteHeight;
    @Parameter(valueSet = {"2","4","8","16"}, defaultValue = "2")
    private String rowInterpFactor;
    @Parameter(valueSet = {"2","4","8","16"}, defaultValue = "2")
    private String columnInterpFactor;
    @Parameter(description = "Number of GCPs per tile row", interval = "[3, 100]", defaultValue = "4")
    private int numGCPsPerTileRow;
    @Parameter(description = "Number of GCPs per tile column", interval = "[3, 100]", defaultValue = "4")
    private int numGCPsPerTileCol;
    @Parameter(description = "The maximum number of iterations", interval = "(1, 100]", defaultValue = "10")
    private int maxIteration;
    @Parameter(description = "Threshold for quality parameter Q = max/mean", interval = "[1, *)", defaultValue = "1.1")
    private double qualityThreshold;
    @Parameter(description = "Tolerance in slave GCP validation check", interval = "(0, *)", defaultValue = "0.1")
    private double gcpTolerance;

    private Product masterProduct;
    private Product slaveProduct;
    private Band masterBand;
    private Band slaveBand;

    private ProductNodeGroup<Pin> masterGcpGroup;
    private ProductNodeGroup<Pin> targetGcpGroup;

    private TiePointGrid latitude;
    private TiePointGrid longitude;

    private Tile masterImagetteRaster;
    private Tile slaveImagetteRaster;

    private double[] mI; // master imagette for cross correlation
    private double[] sI; // slave imagette for cross correlation

    private int width; // row dimension for master and slave imagette, must be power of 2
    private int height; // column dimension for master and slave imagette, must be power of 2
    private int rowUpSamplingFactor; // cross correlation interpolation factor in row direction, must be power of 2
    private int colUpSamplingFactor; // cross correlation interpolation factor in column direction, must be power of 2
    private int gcpIdx;
    private boolean useAutoSetGCPs; // flag indicating using automatically set grid GCPs

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public GCPSelectionOperator() {
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
        masterProduct = sourceProduct[0];
        slaveProduct = sourceProduct[1];

        targetProduct = new Product(slaveProduct.getName(),
                                    slaveProduct.getProductType(),
                                    slaveProduct.getSceneRasterWidth(),
                                    slaveProduct.getSceneRasterHeight());

        masterBand = masterProduct.getBandAt(0);
        slaveBand = slaveProduct.getBandAt(0);
        targetProduct.addBand(slaveBand.getName(), slaveBand.getDataType());

        masterGcpGroup = masterProduct.getGcpGroup();
        targetGcpGroup = targetProduct.getGcpGroup();

        if (masterGcpGroup.getNodeCount() > 0) {
            useAutoSetGCPs = false;
        } else {
            useAutoSetGCPs = true;
        }
        //masterGcpGroup.removeAll();
        targetGcpGroup.removeAll();

        ProductUtils.copyGeoCoding(slaveProduct, targetProduct);
        ProductUtils.copyMetadata(slaveProduct, targetProduct);
        ProductUtils.copyTiePointGrids(slaveProduct, targetProduct);
        ProductUtils.copyFlagCodings(slaveProduct, targetProduct);

        latitude = getLatitude();
        longitude = getLongitude();

        width = Integer.parseInt(imagetteWidth);
        height = Integer.parseInt(imagetteHeight);
        rowUpSamplingFactor = Integer.parseInt(rowInterpFactor);
        colUpSamplingFactor = Integer.parseInt(columnInterpFactor);
        gcpIdx = 0;

        targetProduct.setPreferredTileSize(slaveProduct.getSceneRasterWidth(), 256);
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
        if(targetBand.isSynthetic())
            return;

        Rectangle targetTileRectangle = targetTile.getRectangle();
        int x0 = targetTileRectangle.x;
        int y0 = targetTileRectangle.y;
        int w = targetTileRectangle.width;
        int h = targetTileRectangle.height;
        //System.out.println("GCPSelectionOperator: x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        // set master and slave GCPs
        if (useAutoSetGCPs) {
            setAutoMasterSlaveGCPs(x0, y0, w, h);
        } else {
            setUserMasterSlaveGCPs(x0, y0, w, h);
        }

        // copy salve data to target
        targetTile.setRawSamples(getSourceTile(slaveBand, targetTile.getRectangle(), pm).getRawSamples());
    }

    /**
     * Set initial master GCPs for the given tile.
     *
     * @param x0 The x coordinate for the upper left point in the current tile.
     * @param y0 The y coordinate for the upper left point in the current tile.
     * @param w The width of the tile.
     * @param h The height of the tile.
     */
    void setAutoMasterSlaveGCPs(int x0, int y0, int w, int h) {

        PinSymbol defaultPinSymbol = PinSymbol.createDefaultPinSymbol();
        for (int i = 0; i < numGCPsPerTileCol; i++) {
            int yi = y0 + (int)((i+1)*h/(numGCPsPerTileCol + 1));
            for (int j = 0; j < numGCPsPerTileRow; j++) {

                // set initial master GCP positions
                int xj = x0 + (int)((j+1)*w/(numGCPsPerTileRow + 1));
                float lat = latitude.getPixelFloat(xj + 0.5f, yi + 0.5f);
                float lon = longitude.getPixelFloat(xj + 0.5f, yi + 0.5f);
                PixelPos mGCPPixelPos = new PixelPos(xj, yi);
                GeoPos mGCPGeoPos = new GeoPos(lat, lon);
                Pin mPin = new Pin("gcp_" + gcpIdx, "GCP " + gcpIdx, "", mGCPPixelPos, mGCPGeoPos, defaultPinSymbol);
                masterGcpGroup.add(mPin);
                //System.out.println("master gcp[" + gcpIdx + "] = " + "(" + xj + "," + yi + ")");

                // set initial salve GCP positions
                PixelPos sGCPPixelPos = slaveBand.getGeoCoding().getPixelPos(mGCPGeoPos, null);
                Pin sPin = new Pin("gcp_" + gcpIdx, "GCP " + gcpIdx, "", sGCPPixelPos, mGCPGeoPos, defaultPinSymbol);
                targetGcpGroup.add(sPin);
                //System.out.println("Initial slave gcp[" + gcpIdx + "] = " + "(" + sGCPPixelPos.x + "," + sGCPPixelPos.y + ")");

                // get accurate slave GCP positions
                if (!getSlaveGCPPosition(mGCPPixelPos, sGCPPixelPos)) {
                    sGCPPixelPos.x = -1.0f;
                    sGCPPixelPos.y = -1.0f;
                    //System.out.println("GCP(" + gcpIdx + ") is invalid.");
                }
                targetGcpGroup.get(gcpIdx).setPixelPos(sGCPPixelPos);
                //System.out.println("Final slave gcp[" + gcpIdx + "] = " + "(" + sGCPPixelPos.x + "," + sGCPPixelPos.y + ")");

                gcpIdx++;
            }
        }
    }
    /**
     * Set initial master GCPs for the given tile.
     *
     * @param x0 The x coordinate for the upper left point in the current tile.
     * @param y0 The y coordinate for the upper left point in the current tile.
     * @param w The width of the tile.
     * @param h The height of the tile.
     */
    void setUserMasterSlaveGCPs(int x0, int y0, int w, int h) {

        for(int i = 0; i < masterGcpGroup.getNodeCount(); i++) {

            Pin mPin = masterGcpGroup.get(i);
            PixelPos mGCPPixelPos = mPin.getPixelPos();

            if (checkGCPValidity(mGCPPixelPos, x0, y0, w, h)) {

                GeoPos mGCPGeoPos = mPin.getGeoPos();
                PixelPos sGCPPixelPos = slaveBand.getGeoCoding().getPixelPos(mGCPGeoPos, null);
                //System.out.println("master gcp[" + i + "] = " + "(" + mGCPPixelPos.x + "," + mGCPPixelPos.y + ")");
                //System.out.println("init slave gcp[" + i + "] = " + "(" + sGCPPixelPos.x + "," + sGCPPixelPos.y + ")");

                if (!getSlaveGCPPosition(mGCPPixelPos, sGCPPixelPos)) {
                    sGCPPixelPos.x = -1.0f;
                    sGCPPixelPos.y = -1.0f;
                    //System.out.println("GCP(" + i + ") is invalid.");
                }
                //System.out.println("final slave gcp[" + i + "] = " + "(" + sGCPPixelPos.x + "," + sGCPPixelPos.y + ")");
                //System.out.println();

                Pin sPin = new Pin(mPin.getName(),
                                   mPin.getLabel(),
                                   mPin.getDescription(),
                                   sGCPPixelPos,
                                   mGCPGeoPos,
                                   mPin.getSymbol());

                targetGcpGroup.add(sPin);
            }
        }        
    }

    /**
     * Check if a given GCP is covered by the current tile.
     *
     * @param pixelPos The GCP pixel position.
     * @param x0 The x coordinate for the upper left point in the current tile.
     * @param y0 The y coordinate for the upper left point in the current tile.
     * @param w The width of the tile.
     * @param h The height of the tile.
     * @return flag Return true if the GCP is covered by the tile, false otherwise.
     */
    boolean checkGCPValidity(PixelPos pixelPos, int x0, int y0, int w, int h) {

        return (pixelPos.x >= x0 && pixelPos.x < x0 + w &&
                pixelPos.y >= y0 && pixelPos.y < y0 + h);
    }

    /**
     * Get latitude tie point grid.
     *
     * @return srcTPG The latitude tie point grid.
     */
    TiePointGrid getLatitude() {

        for (int i = 0; i < masterProduct.getNumTiePointGrids(); i++) {
            TiePointGrid srcTPG = masterProduct.getTiePointGridAt(i);
            if (srcTPG.getName().equals("latitude")) {
                return srcTPG;
            }
        }

        return null;
    }

    /**
     * Get longitude tie point grid.
     *
     * @return srcTPG The longitude tie point grid.
     */
    TiePointGrid getLongitude() {

        for (int i = 0; i < masterProduct.getNumTiePointGrids(); i++) {
            TiePointGrid srcTPG = masterProduct.getTiePointGridAt(i);
            if (srcTPG.getName().equals("longitude")) {
                return srcTPG;
            }
        }

        return null;
    }

    boolean getSlaveGCPPosition(PixelPos mGCPPixelPos, PixelPos sGCPPixelPos) {
 
        getMasterImagette(mGCPPixelPos);
        //System.out.println("Master imagette:");
        //outputRealImage(mI);

        double rowShift = gcpTolerance + 1;
        double colShift = gcpTolerance + 1;
        int numIter = 0;

        while (Math.abs(rowShift) > gcpTolerance || Math.abs(colShift) > gcpTolerance) {

            if (numIter >= maxIteration) {
                return false;
            }

            getSlaveImagette(sGCPPixelPos);
            //System.out.println("Slave imagette:");
            //outputRealImage(sI);

            double[] shift = {0,0};
            if (!getSlaveGCPShift(shift)) {
                return false;
            }

            rowShift = shift[0];
            colShift = shift[1];
            sGCPPixelPos.x = sGCPPixelPos.x + (float)colShift;
            sGCPPixelPos.y = sGCPPixelPos.y + (float)rowShift;
            numIter++;
        }

        return true;
    }

    void getMasterImagette(PixelPos gcpPixelPos) {

        mI = new double[width*height];
        int x0 = (int)gcpPixelPos.x;
        int y0 = (int)gcpPixelPos.y;
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        int xul = x0 - halfWidth + 1;
        int yul = y0 - halfHeight + 1;
        Rectangle masterImagetteRectangle = new Rectangle(xul, yul, width, height);
        try {
            masterImagetteRaster = getSourceTile(masterBand, masterImagetteRectangle, null);
        } catch (OperatorException e) {
            throw new OperatorException(e);
        }

        int k = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int x = xul + i;
                int y = yul + j;
                mI[k] = masterImagetteRaster.getSampleDouble(x, y);
                k++;
            }
        }
    }

    void getSlaveImagette(PixelPos gcpPixelPos) {

        sI = new double[width*height];
        float x0 = gcpPixelPos.x;
        float y0 = gcpPixelPos.y;
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        int xul = (int)x0 - halfWidth + 1;
        int yul = (int)y0 - halfHeight + 1;
        Rectangle slaveImagetteRectangle = new Rectangle(xul, yul, width + 1, height + 1);
        try {
            slaveImagetteRaster = getSourceTile(slaveBand, slaveImagetteRectangle, null);
        } catch (OperatorException e) {
            throw new OperatorException(e);
        }

        int k = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                float x = x0 - halfWidth + i + 1;
                float y = y0 - halfHeight + j + 1;
                sI[k] = getInterpolatedSampleValue(x, y);
                k++;
            }
        }
    }

    double getInterpolatedSampleValue(float x, float y) {

        int x0 = (int)x;
        int x1 = x0 + 1;
        int y0 = (int)y;
        int y1 = y0 + 1;
        double v00 = slaveImagetteRaster.getSampleDouble(x0, y0);
        double v01 = slaveImagetteRaster.getSampleDouble(x0, y1);
        double v10 = slaveImagetteRaster.getSampleDouble(x1, y0);
        double v11 = slaveImagetteRaster.getSampleDouble(x1, y1);
        double wy = (double)(y - y0);
        double wx = (double)(x - x0);

        return MathUtils.interpolate2D(wy, wx, v00, v01, v10, v11);
    }

    boolean getSlaveGCPShift(double[] shift) {

        // perform cross correlation
        PlanarImage crossCorrelatedImage = computeCrossCorrelatedImage();

        // check peak validity
        double mean = getMean(crossCorrelatedImage);
        double max = getMax(crossCorrelatedImage);
        if (Double.compare(mean, 0.0) == 0) {
            return false;
        }
        double qualityParam = max / mean;
        if (qualityParam <= qualityThreshold) {
            return false;
        }

        // get peak shift: row and col
        int w = crossCorrelatedImage.getWidth();
        int h = crossCorrelatedImage.getHeight();
        //int nb = crossCorrelatedImage.getSampleModel().getNumBands();
        //int dt = crossCorrelatedImage.getSampleModel().getDataType();

        Raster idftData = crossCorrelatedImage.getData();
        double[] real = idftData.getSamples(0, 0, w, h, 0, (double[])null);
        //System.out.println("Cross correlated imagette:");
        //outputRealImage(real);

        int peakRow = 0;
        int peakCol = 0;
        double peak = real[0];
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                int k = r*h + c;
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

    PlanarImage computeCrossCorrelatedImage() {

        // get master imagette spectrum
        RenderedImage masterImage = createRenderedImage(mI);
        PlanarImage masterSpectrum = dft(masterImage);
        //System.out.println("Master spectrum:");
        //outputComplexImage(masterSpectrum);

        // get slave imagette spectrum
        RenderedImage slaveImage = createRenderedImage(sI);
        PlanarImage slaveSpectrum = dft(slaveImage);
        //System.out.println("Slave spectrum:");
        //outputComplexImage(slaveSpectrum);

        // get conjugate slave spectrum
        PlanarImage conjugateSlaveSpectrum = conjugate(slaveSpectrum);
        //System.out.println("Conjugate slave spectrum:");
        //outputComplexImage(conjugateSlaveSpectrum);

        // multiply master spectrum and conjugate slave spectrum
        PlanarImage crossSpectrum = multiplyComplex(masterSpectrum, conjugateSlaveSpectrum);
        //System.out.println("Cross spectrum:");
        //outputComplexImage(crossSpectrum);

        // upsampling cross spectrum
        RenderedImage upsampledCrossSpectrum = upsampling(crossSpectrum);

        // perform IDF on the cross spectrum
        PlanarImage correlatedImage = idft(upsampledCrossSpectrum);
        //System.out.println("Correlated image:");
        //outputComplexImage(correlatedImage);

        // compute the magnitode of the cross correlated image
        return magnitude(correlatedImage);
    }

    RenderedImage createRenderedImage(double[] array) {

        // create rendered image with demension being width by height
        SampleModel sampleModel = RasterFactory.createBandedSampleModel(DataBuffer.TYPE_DOUBLE, width, height, 1);
        ColorModel colourModel = PlanarImage.createColorModel(sampleModel);
        DataBufferDouble dataBuffer = new DataBufferDouble(array, array.length);
        WritableRaster raster = RasterFactory.createWritableRaster(sampleModel, dataBuffer, new Point(0,0));
        BufferedImage image = new BufferedImage(colourModel, raster, false, new Hashtable());

        return image;
    }

    PlanarImage dft(RenderedImage image) {

        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(DFTDescriptor.SCALING_NONE);
        pb.add(DFTDescriptor.REAL_TO_COMPLEX);
        return JAI.create("dft", pb, null);
    }

    PlanarImage idft(RenderedImage image) {

        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(DFTDescriptor.SCALING_DIMENSIONS);
        pb.add(DFTDescriptor.COMPLEX_TO_COMPLEX);
        return JAI.create("idft", pb, null);
    }

    PlanarImage conjugate(PlanarImage image) {

        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        return JAI.create("conjugate", pb, null);
    }

    PlanarImage multiplyComplex(PlanarImage image1, PlanarImage image2){

        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image1);
        pb.addSource(image2);
        return JAI.create("MultiplyComplex", pb, null);
    }

    RenderedImage upsampling(PlanarImage image) {

        // System.out.println("Source image:");
        // outputComplexImage(image);

        int w = image.getWidth();  // w is power of 2
        int h = image.getHeight(); // h is power of 2
        int newWidth = rowUpSamplingFactor * w; // rowInterpFactor should be power of 2 to avoid zero padding in idft
        int newHeight = colUpSamplingFactor * h; // colInterpFactor should be power of 2 to avoid zero padding in idft

        // create shifted image
        ParameterBlock pb1 = new ParameterBlock();
        pb1.addSource(image);
        pb1.add(w/2);
        pb1.add(h/2);
        PlanarImage shiftedImage = JAI.create("PeriodicShift", pb1, null);
        //System.out.println("Shifted image:");
        //outputComplexImage(shiftedImage);

        // create zero padded image
        ParameterBlock pb2 = new ParameterBlock();
        int leftPad = (newWidth - w) / 2;
        int rightPad = leftPad;
        int topPad = (newHeight - h) / 2;
        int bottomPad = topPad;
        pb2.addSource(shiftedImage);
        pb2.add(leftPad);
        pb2.add(rightPad);
        pb2.add(topPad);
        pb2.add(bottomPad);
        pb2.add(BorderExtender.createInstance(BorderExtender.BORDER_ZERO));
        PlanarImage zeroPaddedImage = JAI.create("border", pb2);

        // reposition zero padded image so the image origin is back at (0,0)
        ParameterBlock pb3 = new ParameterBlock();
        pb3.addSource(zeroPaddedImage);
        pb3.add(1.0f*leftPad);
        pb3.add(1.0f*topPad);
        PlanarImage zeroBorderedImage = JAI.create("translate", pb3, null);
        //System.out.println("Zero padded image:");
        //outputComplexImage(zeroBorderedImage);

        // shift the zero padded image
        ParameterBlock pb4 = new ParameterBlock();
        pb4.addSource(zeroBorderedImage);
        pb4.add(newWidth/2);
        pb4.add(newHeight/2);
        PlanarImage shiftedZeroPaddedImage = JAI.create("PeriodicShift", pb4, null);
        //System.out.println("Shifted zero padded image:");
        //outputComplexImage(shiftedZeroPaddedImage);
        return shiftedZeroPaddedImage;
    }

    PlanarImage magnitude(PlanarImage image) {

        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        return JAI.create("magnitude", pb, null);
    }

    double getMean(RenderedImage image) {

        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(null); // null ROI means whole image
        pb.add(1); // check every pixel horizontally
        pb.add(1); // check every pixel vertically

        // Perform the mean operation on the source image.
        RenderedImage meanImage = JAI.create("mean", pb, null);
        // Retrieve and report the mean pixel value.
        double[] mean = (double[])meanImage.getProperty("mean");
        return mean[0];
    }

    double getMax(RenderedImage image) {

        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(null); // null ROI means whole image
        pb.add(1); // check every pixel horizontally
        pb.add(1); // check every pixel vertically

        // Perform the extrema operation on the source image
        RenderedOp op = JAI.create("extrema", pb);
        // Retrieve both the maximum and minimum pixel value
        double[][] extrema = (double[][]) op.getProperty("extrema");
        double max = extrema[1][0];
        return max;
    }

    // This function is for debugging only.
    void outputRealImage(double[] I) {

        for (double v:I) {
            System.out.print(v + ",");
        }
        System.out.println();
    }

    // This function is for debugging only.
    void outputComplexImage(PlanarImage image) {

        int w = image.getWidth();
        int h = image.getHeight();
        int nb = image.getSampleModel().getNumBands();
        int dt = image.getSampleModel().getDataType();
        Raster dftData = image.getData();
        double[] real = dftData.getSamples(0, 0, w, h, 0, (double[])null);
        double[] imag = dftData.getSamples(0, 0, w, h, 1, (double[])null);
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
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     * @see OperatorSpi#createOperator()
     * @see OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(GCPSelectionOperator.class);
        }
    }
}
