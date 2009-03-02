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
import org.esa.nest.util.DatUtils;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.dataio.ReaderUtils;

import javax.media.jai.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.image.renderable.ParameterBlock;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.*;

/**
 * Image co-registration is fundamental for Interferometry SAR (InSAR) imaging and its applications, such as
 * DEM map generation and analysis. To obtain a high quality InSAR image, the individual complex images need
 * to be co-registered to sub-pixel accuracy. The co-registration is accomplished through an alignment of a
 * master image with a slave image.
 *
 * To achieve the alignment of master and slave images, the first step is to generate a set of uniformly
 * spaced ground control points (GCPs) in the master image, along with the corresponding GCPs in the slave
 * image. Details of the generation of the GCP pairs are given in GCPSelectionOperator. The next step is to
 * construct a warp distortion function from the computed GCP pairs and generate co-registered slave image.
 *
 * This operator computes the warp function from the master-slave GCP pairs for given polynomial order.
 * Basically coefficients of two polynomials are determined from the GCP pairs with each polynomial for
 * one coordinate of the image pixel. With the warp function determined, the co-registered image can be
 * obtained by mapping slave image pixels to master image pixels. In particular, for each pixel position in
 * the master image, warp function produces its corresponding pixel position in the slave image, and the
 * pixel value is computed through interpolation. The following interpolation methods are available:
 *
 * 1. Nearest-neighbour interpolation
 * 2. Bilinear interpolation
 * 3. Bicubic interpolation
 * 4. Bicubic2 interpolation
 */

@OperatorMetadata(alias="Warp",
                  description = "Create Warp Function And Get Co-registrated Images")
public class WarpOp extends Operator {

    @SourceProduct
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The RMS threshold for eliminating invalid GCPs", interval = "(0, *)", defaultValue = "1.0",
                label="RMS Threshold")
    private float rmsThreshold = 1.0f;

    @Parameter(description = "The order of WARP polynomial function", valueSet = {"1", "2", "3"}, defaultValue = "2",
                label="Warp Polynomial Order")
    private int warpPolynomialOrder = 2;

//    @Parameter(valueSet = {NEAREST_NEIGHBOR, BILINEAR, BICUBIC, BICUBIC2}, defaultValue = BILINEAR,
//                label="Interpolation Method")
    @Parameter(valueSet = {NEAREST_NEIGHBOR, BILINEAR}, defaultValue = BILINEAR,
                label="Interpolation Method")
    private String interpolationMethod = BILINEAR;
    private Interpolation interp = null;

    @Parameter(description = "Show the Residuals file in a text viewer", defaultValue = "true", label="Show Residuals")
    private boolean openResidualsFile = true;

    private ProductNodeGroup<Pin> masterGCPGroup = null;
    private Band masterBand = null;
    private Band masterBand2 = null;
    private boolean complexCoregistration = false;

    private static final String NEAREST_NEIGHBOR = "Nearest-neighbor interpolation";
    private static final String BILINEAR = "Bilinear interpolation";
    private static final String BICUBIC = "Bicubic interpolation";
    private static final String BICUBIC2 = "Bicubic2 interpolation";

    private final static Map<Band, Band> sourceRasterMap = new HashMap<Band, Band>(10);
    private final static Map<Band, Band> complexSrcMap = new HashMap<Band, Band>(10);
    private final static Map<Band, WarpData> warpDataMap = new HashMap<Band, WarpData>(10);

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public WarpOp() {
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
        try {
            masterBand = sourceProduct.getBandAt(0);
            masterGCPGroup = sourceProduct.getGcpGroup(masterBand);
            if(masterBand.getUnit() != null && masterBand.getUnit().equals(Unit.REAL) && sourceProduct.getNumBands() > 1) {
                complexCoregistration = true;
                masterBand2 = sourceProduct.getBandAt(1);
            }

            // The following code is temporary
            
            if (complexCoregistration) {
                interp = Interpolation.getInstance(Interpolation.INTERP_NEAREST);
            } else {
                if (interpolationMethod.equals(NEAREST_NEIGHBOR)) {
                    interp = Interpolation.getInstance(Interpolation.INTERP_NEAREST);
                } else if (interpolationMethod.equals(BILINEAR)) {
                    interp = Interpolation.getInstance(Interpolation.INTERP_BILINEAR);
                }
            }
            /*
            // determine interpolation method for warp function
            if (interpolationMethod.equals(NEAREST_NEIGHBOR)) {
                interp = Interpolation.getInstance(Interpolation.INTERP_NEAREST);
            } else if (interpolationMethod.equals(BILINEAR)) {
                interp = Interpolation.getInstance(Interpolation.INTERP_BILINEAR);
            } else if (interpolationMethod.equals(BICUBIC)) {
                interp = Interpolation.getInstance(Interpolation.INTERP_BICUBIC);
            } else if (interpolationMethod.equals(BICUBIC2)) {
                interp = Interpolation.getInstance(Interpolation.INTERP_BICUBIC_2);
            }
            */
            createTargetProduct();

            // for all slave bands or band pairs compute a warp
            final int numSrcBands = sourceProduct.getNumBands();
            int inc = 1;
            if(complexCoregistration)
                inc = 2;
            for(int i=0; i < numSrcBands; i+=inc) {

                final Band srcBand = sourceProduct.getBandAt(i);
                if(srcBand == masterBand || srcBand == masterBand2)
                    continue;

                final ProductNodeGroup<Pin> slaveGCPGroup = sourceProduct.getGcpGroup(srcBand);
                if(slaveGCPGroup.getNodeCount() < 3) {
                    throw new OperatorException(slaveGCPGroup.getNodeCount() + 
                            " GCPs survived. Try using more GCPs or a larger window");
                }

                final WarpData warpData = new WarpData(slaveGCPGroup);
                warpDataMap.put(srcBand, warpData);

                int parseIdex = 0;
                computeWARPPolynomial(warpData); // compute initial warp polynomial
                outputCoRegistrationInfo(warpData, false, 0.0f, parseIdex);

                //============
                if (warpData.rmsMean > rmsThreshold && eliminateGCPsBasedOnRMS(warpData, (float)warpData.rmsMean)) {
                    final float threshold = (float)warpData.rmsMean;
                    computeWARPPolynomial(warpData); // compute 2nd warp polynomial
                    outputCoRegistrationInfo(warpData, true, threshold, ++parseIdex);
                }

                if (warpData.rmsMean > rmsThreshold && eliminateGCPsBasedOnRMS(warpData, (float)warpData.rmsMean)) {
                    final float threshold = (float)warpData.rmsMean;
                    computeWARPPolynomial(warpData); // compute 3rd warp polynomial
                    outputCoRegistrationInfo(warpData, true, threshold, ++parseIdex);
                }
                //============

                eliminateGCPsBasedOnRMS(warpData, rmsThreshold);
                computeWARPPolynomial(warpData); // compute final warp polynomial
                outputCoRegistrationInfo(warpData, true, rmsThreshold, ++parseIdex);
            }

        } catch(Exception e) {
            openResidualsFile = true;
            throw new OperatorException(e);
        } finally {
            if(openResidualsFile) {
                final File residualsFile = getResidualsFile(sourceProduct);
                if(Desktop.isDesktopSupported()) {
                    try {
                        Desktop.getDesktop().open(residualsFile);
                    } catch(Exception e) {
                        System.out.println(e.getMessage());
                        // do nothing
                    }
                }
            }
        }
    }

    private void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        final int numSrcBands = sourceProduct.getNumBands();
        int cnt = 1;
        int inc = 1;
        if(complexCoregistration)
            inc = 2;
        for(int i=0; i < numSrcBands; i+=inc) {
            final Band srcBand = sourceProduct.getBandAt(i);
            final Band targetBand = targetProduct.addBand(srcBand.getName(), ProductData.TYPE_FLOAT32);
            ProductUtils.copyRasterDataNodeProperties(srcBand, targetBand);
            sourceRasterMap.put(targetBand, srcBand);

            if(complexCoregistration) {
                final Band srcBandQ = sourceProduct.getBandAt(i+1);
                final Band targetBandQ = targetProduct.addBand(srcBandQ.getName(), ProductData.TYPE_FLOAT32);
                ProductUtils.copyRasterDataNodeProperties(srcBandQ, targetBandQ);
                sourceRasterMap.put(targetBandQ, srcBandQ);

                complexSrcMap.put(srcBandQ, srcBand);
                String suffix = "_mst";
                if(srcBand != masterBand)
                    suffix = "_slv" + cnt++;
                ReaderUtils.createVirtualIntensityBand(targetProduct, targetBand, targetBandQ, suffix);
                ReaderUtils.createVirtualPhaseBand(targetProduct, targetBand, targetBandQ, suffix);
            }
        }

        // coregistrated image should have the same geo-coding as the master image
        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);

        //targetProduct.setPreferredTileSize(sourceProduct.getSceneRasterWidth(), 20);
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
            //System.out.println("WARPOperator: x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

            final Band srcBand = sourceRasterMap.get(targetBand);
            if(srcBand == masterBand || srcBand == masterBand2) {

                final Tile masterRaster = getSourceTile(srcBand, targetTileRectangle, pm);
                final ProductData masterData = masterRaster.getDataBuffer();
                final ProductData targetData = targetTile.getDataBuffer();
                for (int y = y0; y < y0 + h; y++) {
                    for (int x = x0; x < x0 + w; x++) {
                        final int index = masterRaster.getDataBufferIndex(x, y);
                        targetData.setElemFloatAt(index, masterData.getElemFloatAt(index));
                    }
                }
                /* The following code has problem that is cannot handle the last tile at right and bottom
                final ProductData masterData = masterRaster.getRawSamples();
                final ProductData targetData = targetTile.getRawSamples();
                final int n = masterData.getNumElems();
                for (int i = 0; i < n; ++i) {
                    targetData.setElemFloatAt(i, masterData.getElemFloatAt(i));
                }
                */
            } else {

                Band realSrcBand = complexSrcMap.get(srcBand);
                if(realSrcBand == null)
                    realSrcBand = srcBand;

                // create source image
                final Tile sourceRaster = getSourceTile(srcBand, targetTileRectangle, pm);
                final RenderedImage srcImage = sourceRaster.getRasterDataNode().getSourceImage();

                // get warped image
                final RenderedOp warpedImage = createWarpImage(warpDataMap.get(realSrcBand).warp, srcImage);

                // copy warped image data to target
                final float[] dataArray = warpedImage.getData(targetTileRectangle).getSamples(x0, y0, w, h, 0, (float[])null);
                final ProductData rawTargetData = ProductData.createInstance(dataArray);
                targetTile.setRawSamples(rawTargetData);
            }
        } catch(Exception e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Compute WARP polynomial function using master and slave GCP pairs.
     * @param warpData stores the warp information per band
     */
    private void computeWARPPolynomial(final WarpData warpData) {

        getNumOfValidGCPs(warpData);

        getMasterAndSlaveGCPCoordinates(warpData);

        computeWARP(warpData);

        computeRMS(warpData);
    }

    private void getNumOfValidGCPs(final WarpData warpData) throws OperatorException {

        warpData.numValidGCPs = warpData.slaveGCPGroup.getNodeCount();
        final int requiredGCPs = (warpPolynomialOrder + 2)*(warpPolynomialOrder + 1) / 2;
        if (warpData.numValidGCPs < requiredGCPs) {
            throw new OperatorException("Order " + warpPolynomialOrder + " requires " + requiredGCPs +
                    " GCPs, valid GCPs are " + warpData.numValidGCPs + ", try a larger RMS threshold.");
        }
    }

    private void getMasterAndSlaveGCPCoordinates(final WarpData warpData) {

        warpData.masterGCPCoords = new float[2*warpData.numValidGCPs];
        warpData.slaveGCPCoords = new float[2*warpData.numValidGCPs];

        for(int i = 0; i < warpData.numValidGCPs; ++i) {

            final Pin sPin = warpData.slaveGCPGroup.get(i);
            final PixelPos sGCPPos = sPin.getPixelPos();
            //System.out.println("WARP: slave gcp[" + i + "] = " + "(" + sGCPPos.x + "," + sGCPPos.y + ")");

            final Pin mPin = masterGCPGroup.get(sPin.getName());
            final PixelPos mGCPPos = mPin.getPixelPos();
            //System.out.println("WARP: master gcp[" + i + "] = " + "(" + mGCPPos.x + "," + mGCPPos.y + ")");

            final int j = 2 * i;
            warpData.masterGCPCoords[j] = mGCPPos.x;
            warpData.masterGCPCoords[j+1] = mGCPPos.y;
            warpData.slaveGCPCoords[j] = sGCPPos.x;
            warpData.slaveGCPCoords[j+1] = sGCPPos.y;
        }
    }

    private void computeWARP(final WarpData warpData) {

        warpData.warp = WarpPolynomial.createWarp(warpData.slaveGCPCoords, //source
                                         0,
                                         warpData.masterGCPCoords, // destination
                                         0,
                                         2*warpData.numValidGCPs,
                                         1.0F,
                                         1.0F,
                                         1.0F,
                                         1.0F,
                                         warpPolynomialOrder);
    }

    private void computeRMS(final WarpData warpData) {

        // compute RMS for all valid GCPs
        warpData.rms = new float[warpData.numValidGCPs];
        warpData.colResiduals = new float[warpData.numValidGCPs];
        warpData.rowResiduals = new float[warpData.numValidGCPs];
        final PixelPos slavePos = new PixelPos(0.0f,0.0f);
        for (int i = 0; i < warpData.rms.length; i++) {
            final int i2 = 2*i;
            getWarpedCoords(warpData.warp, warpData.masterGCPCoords[i2], warpData.masterGCPCoords[i2+1], slavePos);
            final double dX = slavePos.x - warpData.slaveGCPCoords[i2];
            final double dY = slavePos.y - warpData.slaveGCPCoords[i2+1];
            warpData.colResiduals[i] = (float)dX;
            warpData.rowResiduals[i] = (float)dY;
            warpData.rms[i] = (float)Math.sqrt(dX*dX + dY*dY);
        }

        // compute some statistics
        warpData.rmsMean = 0.0;
        warpData.rowResidualMean = 0.0;
        warpData.colResidualMean = 0.0;
        double rms2Mean = 0.0;
        double rowResidual2Mean = 0.0;
        double colResidual2Mean = 0.0;

        for (int i = 0; i < warpData.rms.length; i++) {
            warpData.rmsMean += warpData.rms[i];
            rms2Mean += warpData.rms[i]*warpData.rms[i];
            warpData.rowResidualMean += warpData.rowResiduals[i];
            rowResidual2Mean += warpData.rowResiduals[i]*warpData.rowResiduals[i];
            warpData.colResidualMean += warpData.colResiduals[i];
            colResidual2Mean += warpData.colResiduals[i]*warpData.colResiduals[i];
        }
        warpData.rmsMean /= warpData.rms.length;
        rms2Mean /= warpData.rms.length;
        warpData.rowResidualMean /= warpData.rms.length;
        rowResidual2Mean /= warpData.rms.length;
        warpData.colResidualMean /= warpData.rms.length;
        colResidual2Mean /= warpData.rms.length;

        warpData.rmsStd = Math.sqrt(rms2Mean - warpData.rmsMean*warpData.rmsMean);
        warpData.rowResidualStd = Math.sqrt(rowResidual2Mean - warpData.rowResidualMean*warpData.rowResidualMean);
        warpData.colResidualStd = Math.sqrt(colResidual2Mean - warpData.colResidualMean*warpData.colResidualMean);
    }

    private static boolean eliminateGCPsBasedOnRMS(final WarpData warpData, final float threshold) {

        final ArrayList<Pin> pinList = new ArrayList<Pin>();
        for (int i = 0; i < warpData.rms.length; i++) {
            if (warpData.rms[i] >= threshold) {
                pinList.add(warpData.slaveGCPGroup.get(i));
                //System.out.println("WARP: slave gcp[" + i + "] is eliminated");
            }
        }

        for (Pin aPinList : pinList) {
            warpData.slaveGCPGroup.remove(aPinList);
        }

        return !pinList.isEmpty();
    }

    private void getWarpedCoords(final WarpPolynomial warp, final float mX, final float mY, final PixelPos slavePos)
                                throws OperatorException {

        final float[] xCoeffs = warp.getXCoeffs();
        final float[] yCoeffs = warp.getYCoeffs();
        if (xCoeffs.length != yCoeffs.length) {
            throw new OperatorException("WARP has different number of coefficients for X and Y");
        }

        final int numOfCoeffs = xCoeffs.length;
        switch (warpPolynomialOrder) {
            case 1: {
                if (numOfCoeffs != 3) {
                    throw new OperatorException("Number of WARP coefficients do not match WARP degree");
                }
                slavePos.x = xCoeffs[0] + xCoeffs[1]*mX + xCoeffs[2]*mY;

                slavePos.y = yCoeffs[0] + yCoeffs[1]*mX + yCoeffs[2]*mY;
                break;
            }
            case 2: {
                if (numOfCoeffs != 6) {
                    throw new OperatorException("Number of WARP coefficients do not match WARP degree");
                }
                final float mXmX = mX*mX;
                final float mXmY = mX*mY;
                final float mYmY = mY*mY;

                slavePos.x = xCoeffs[0] + xCoeffs[1]*mX + xCoeffs[2]*mY +
                             xCoeffs[3]*mXmX + xCoeffs[4]*mXmY + xCoeffs[5]*mYmY;

                slavePos.y = yCoeffs[0] + yCoeffs[1]*mX + yCoeffs[2]*mY +
                             yCoeffs[3]*mXmX + yCoeffs[4]*mXmY + yCoeffs[5]*mYmY;
                break;
            }
            case 3: {
                if (numOfCoeffs != 10) {
                    throw new OperatorException("Number of WARP coefficients do not match WARP degree");
                }
                final float mXmX = mX*mX;
                final float mXmY = mX*mY;
                final float mYmY = mY*mY;

                slavePos.x = xCoeffs[0] + xCoeffs[1]*mX + xCoeffs[2]*mY +
                             xCoeffs[3]*mXmX + xCoeffs[4]*mXmY + xCoeffs[5]*mYmY +
                             xCoeffs[6]*mXmX*mX + xCoeffs[7]*mX*mXmY + xCoeffs[8]*mXmY*mY + xCoeffs[9]*mYmY*mY;

                slavePos.y = yCoeffs[0] + yCoeffs[1]*mX + yCoeffs[2]*mY +
                             yCoeffs[3]*mXmX + yCoeffs[4]*mXmY + yCoeffs[5]*mYmY +
                             yCoeffs[6]*mXmX*mX + yCoeffs[7]*mX*mXmY + yCoeffs[8]*mXmY*mY + yCoeffs[9]*mYmY*mY;
                break;
            }
            default:
                throw new OperatorException("Incorrect WARP degree");
        }
    }

    private void outputCoRegistrationInfo(final WarpData warpData, final boolean appendFlag,
                                          final float threshold, final int parseIndex) throws OperatorException {

        final float[] xCoeffs = warpData.warp.getXCoeffs();
        final float[] yCoeffs = warpData.warp.getYCoeffs();

        final File residualFile = getResidualsFile(sourceProduct);
        PrintStream p = null; // declare a print stream object

        try {
            final FileOutputStream out = new FileOutputStream(residualFile.getAbsolutePath(), appendFlag);

            // Connect print stream to the output stream
            p = new PrintStream(out);

            p.println();

            if (!appendFlag) {
                p.println();
                p.format("Transformation degree = %d", warpPolynomialOrder);
                p.println();
            }

            p.println();
            p.print("------------------------------------------------ Parse " + parseIndex +
                    " ------------------------------------------------");
            p.println();

            p.println();
            p.println("WARP coefficients:");
            for (float xCoeff : xCoeffs) {
                p.format("%10.5f, ", xCoeff);
            }

            p.println();
            for (float yCoeff : yCoeffs) {
                p.format("%10.5f, ", yCoeff);
            }
            p.println();

            if (appendFlag) {
                p.println();
                p.format("RMS Threshold: %5.2f", threshold);
                p.println();
            }

            p.println();
            if (appendFlag) {
                p.print("Valid GCPs after parse " + parseIndex + " :");
            } else {
                p.print("Initial Valid GCPs:");
            }
            p.println();

            p.println();
            p.println("No. | Master GCP x | Master GCP y | Slave GCP x |" +
                      " Slave GCP y | Row Residual | Col Residual |    RMS    |");
            p.println("-------------------------------------------------" +
                      "--------------------------------------------------------");
            for (int i = 0; i < warpData.rms.length; i++) {
                p.format("%2d  |%13.3f |%13.3f |%12.3f |%12.3f |%13.3f |%13.3f |%10.3f |",
                        i, warpData.masterGCPCoords[2*i], warpData.masterGCPCoords[2*i+1],
                        warpData.slaveGCPCoords[2*i], warpData.slaveGCPCoords[2*i+1],
                        warpData.rowResiduals[i], warpData.colResiduals[i], warpData.rms[i]);
                p.println();
            }

            p.println();
            p.format("Row residual mean = %8.3f", warpData.rowResidualMean);
            p.println();
            p.format("Row residual std = %8.3f", warpData.rowResidualStd);
            p.println();

            p.println();
            p.format("Col residual mean = %8.3f", warpData.colResidualMean);
            p.println();
            p.format("Col residual std = %8.3f", warpData.colResidualStd);
            p.println();

            p.println();
            p.format("RMS mean = %8.3f", warpData.rmsMean);
            p.println();
            p.format("RMS std = %8.3f", warpData.rmsStd);

        } catch(IOException exc) {
            throw new OperatorException(exc);
        } finally {
            if(p != null)
                p.close();
        }
    }

    private static File getResidualsFile(final Product sourceProduct) {
        String fileName = sourceProduct.getName() + "_residual.txt";
        final File appUserDir = new File(DatUtils.getApplicationUserDir(true).getAbsolutePath() + File.separator + "log");
        if(!appUserDir.exists()) {
            appUserDir.mkdirs();
        }
        return new File(appUserDir.toString(), fileName);
    }

    private RenderedOp createWarpImage(WarpPolynomial warp, final RenderedImage srcImage) {

        // reformat source image by casting pixel values from ushort to float
        final ParameterBlock pb1 = new ParameterBlock();
        pb1.addSource(srcImage);
        pb1.add(DataBuffer.TYPE_FLOAT);
        final RenderedImage srcImageFloat = JAI.create("format", pb1);

        // get warped image
        final ParameterBlock pb2 = new ParameterBlock();
        pb2.addSource(srcImageFloat);
        pb2.add(warp);
        pb2.add(interp);
        return JAI.create("warp", pb2);
    }

    private static class WarpData {
        final ProductNodeGroup<Pin> slaveGCPGroup;
        WarpPolynomial warp = null;

        int numValidGCPs = 0;
        float[] rms = null;
        float[] rowResiduals = null;
        float[] colResiduals = null;
        float[] masterGCPCoords = null;
        float[] slaveGCPCoords = null;

        double rmsStd = 0;
        double rmsMean = 0;
        double rowResidualStd = 0;
        double rowResidualMean = 0;
        double colResidualStd = 0;
        double colResidualMean = 0;

        WarpData(ProductNodeGroup<Pin> slaveGCPGroup) {
            this.slaveGCPGroup = slaveGCPGroup;
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
            super(WarpOp.class);
        }
    }
}
