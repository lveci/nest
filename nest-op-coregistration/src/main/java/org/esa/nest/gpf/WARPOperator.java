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

import javax.media.jai.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.image.renderable.ParameterBlock;
import java.util.ArrayList;
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

@OperatorMetadata(alias="WARP-Creation",
                  description = "Create WARP Function And Get Co-registrated Images")
public class WARPOperator extends Operator {

    @SourceProducts(count = 2)
    private Product[] sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The RMS threshold for eliminating invalid GCPs", interval = "(0, *)", defaultValue = "1.0",
                label="RMS Threshold")
    private float rmsThreshold;

    @Parameter(description = "The order of WARP polynomial function", interval = "[1, 3]", defaultValue = "2",
                label="Warp Polynomial Order")
    private int warpPolynomialOrder;

    @Parameter(valueSet = {NEAREST_NEIGHBOR, BILINEAR, BICUBIC, BICUBIC2}, defaultValue = BILINEAR,
                label="Interpolation Method")
    private String interpolationMethod;

    private Product masterProduct;
    private Product slaveProduct;

    private Band masterBand;
    private Band slaveBand;

    private ProductNodeGroup<Pin> masterGCPGroup;
    private ProductNodeGroup<Pin> slaveGCPGroup;
    private ProductNodeGroup<Pin> targetGCPGroup;

    private int numValidGCPs;
    private float[] masterGCPCoords;
    private float[] slaveGCPCoords;
    private float[] rms;
    private float[] rowResiduals;
    private float[] colResiduals;

    private double rmsStd;
    private double rmsMean;
    private double rowResidualStd;
    private double rowResidualMean;
    private double colResidualStd;
    private double colResidualMean;

    private WarpPolynomial warp;
    private Interpolation interp;

    private static final String NEAREST_NEIGHBOR = "Nearest-neighbor interpolation";
    private static final String BILINEAR = "Bilinear interpolation";
    private static final String BICUBIC = "Bicubic interpolation";
    private static final String BICUBIC2 = "Bicubic2 interpolation";

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public WARPOperator() {
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

        masterBand = masterProduct.getBandAt(0);
        slaveBand = slaveProduct.getBandAt(0);

        masterGCPGroup = masterProduct.getGcpGroup();
        slaveGCPGroup = slaveProduct.getGcpGroup();

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

        int parseIdex = 0;
        computeWARPPolynomial(); // compute initial warp polynomial
        outputCoRegistrationInfo(false, 0.0f, parseIdex);

        //============
        if (rmsMean > rmsThreshold && eliminateGCPsBasedOnRMS((float)rmsMean)) {
            float threshold = (float)rmsMean;
            computeWARPPolynomial(); // compute 2nd warp polynomial
            outputCoRegistrationInfo(true, threshold, ++parseIdex);
        }

        if (rmsMean > rmsThreshold && eliminateGCPsBasedOnRMS((float)rmsMean)) {
            float threshold = (float)rmsMean;
            computeWARPPolynomial(); // compute 3rd warp polynomial
            outputCoRegistrationInfo(true, threshold, ++parseIdex);
        }
        //============

        eliminateGCPsBasedOnRMS(rmsThreshold);
        computeWARPPolynomial(); // compute final warp polynomial
        outputCoRegistrationInfo(true, rmsThreshold, ++parseIdex);

        createTargetProduct();
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
        Rectangle targetTileRectangle = targetTile.getRectangle();
        int x0 = targetTileRectangle.x;
        int y0 = targetTileRectangle.y;
        int w = targetTileRectangle.width;
        int h = targetTileRectangle.height;
        //System.out.println("WARPOperator: x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        // create source image
        Tile sourceRaster = getSourceTile(slaveBand, targetTileRectangle, pm);
        RenderedImage srcImage = sourceRaster.getRasterDataNode().getSourceImage();

        // get warped image
        RenderedOp warpedImage = createWarpImage(srcImage);

        // copy warped image data to target
        float[] dataArray = warpedImage.getData(targetTileRectangle).getSamples(x0, y0, w, h, 0, (float[])null);
        ProductData rawTargetData = ProductData.createInstance(dataArray);
        targetTile.setRawSamples(rawTargetData);
    }

    /**
     * Copy slave GCPs to target product.
     */
    void setTargetGCPs() {

        targetGCPGroup = targetProduct.getGcpGroup();
        targetGCPGroup.removeAll();

        for(int i = 0; i < slaveGCPGroup.getNodeCount(); ++i) {
            Pin sPin = slaveGCPGroup.get(i);
            Pin tPin = new Pin(sPin.getName(),
                               sPin.getLabel(),
                               sPin.getDescription(),
                               sPin.getPixelPos(),
                               sPin.getGeoPos(),
                               sPin.getSymbol());

            targetGCPGroup.add(tPin);
        }
    }

    /**
     * Compute WARP polynomial function using master and slave GCP pairs.
     */
    void computeWARPPolynomial() {

        getNumOfValidGCPs();

        getMasterAndSlaveGCPCoordinates();

        computeWARP();

        computeRMS();
    }

    void getNumOfValidGCPs() {

        numValidGCPs = slaveGCPGroup.getNodeCount();
        int requiredGCPs = (warpPolynomialOrder + 2)*(warpPolynomialOrder + 1) / 2;
        if (numValidGCPs < requiredGCPs) {
            throw new OperatorException("Order " + warpPolynomialOrder + " requires " + requiredGCPs +
                    " GCPs, valid GCPs are " + numValidGCPs + ", try a larger RMS threshold.");
        }
    }

    void getMasterAndSlaveGCPCoordinates() {

        masterGCPCoords = new float[2*numValidGCPs];
        slaveGCPCoords = new float[2*numValidGCPs];

        for(int i = 0; i < numValidGCPs; ++i) {

            Pin sPin = slaveGCPGroup.get(i);
            PixelPos sGCPPos = sPin.getPixelPos();
            //System.out.println("WARP: slave gcp[" + i + "] = " + "(" + sGCPPos.x + "," + sGCPPos.y + ")");

            PixelPos mGCPPos = masterGCPGroup.get(sPin.getName()).getPixelPos();
            //System.out.println("WARP: master gcp[" + i + "] = " + "(" + mGCPPos.x + "," + mGCPPos.y + ")");

            int j = 2 * i;
            masterGCPCoords[j] = mGCPPos.x;
            masterGCPCoords[j+1] = mGCPPos.y;
            slaveGCPCoords[j] = sGCPPos.x;
            slaveGCPCoords[j+1] = sGCPPos.y;

        }
    }

    void computeWARP() {

        warp = WarpPolynomial.createWarp(slaveGCPCoords, //source
                                         0,
                                         masterGCPCoords, // destination
                                         0,
                                         2*numValidGCPs,
                                         1.0F,
                                         1.0F,
                                         1.0F,
                                         1.0F,
                                         warpPolynomialOrder);
    }

    void computeRMS() {

        // compute RMS for all valid GCPs
        rms = new float[numValidGCPs];
        colResiduals = new float[numValidGCPs];
        rowResiduals = new float[numValidGCPs];
        PixelPos slavePos = new PixelPos(0.0f,0.0f);
        for (int i = 0; i < rms.length; i++) {
            int i2 = 2*i;
            getWarpedCoords(masterGCPCoords[i2], masterGCPCoords[i2+1], slavePos);
            double dX = slavePos.x - slaveGCPCoords[i2];
            double dY = slavePos.y - slaveGCPCoords[i2+1];
            colResiduals[i] = (float)dX;
            rowResiduals[i] = (float)dY;
            rms[i] = (float)Math.sqrt(dX*dX + dY*dY);
        }

        // compute some statistics
        rmsMean = 0.0;
        rowResidualMean = 0.0;
        colResidualMean = 0.0;
        double rms2Mean = 0.0;
        double rowResidual2Mean = 0.0;
        double colResidual2Mean = 0.0;

        for (int i = 0; i < rms.length; i++) {
            rmsMean += rms[i];
            rms2Mean += rms[i]*rms[i];
            rowResidualMean += rowResiduals[i];
            rowResidual2Mean += rowResiduals[i]*rowResiduals[i];
            colResidualMean += colResiduals[i];
            colResidual2Mean += colResiduals[i]*colResiduals[i];
        }
        rmsMean /= rms.length;
        rms2Mean /= rms.length;
        rowResidualMean /= rms.length;
        rowResidual2Mean /= rms.length;
        colResidualMean /= rms.length;
        colResidual2Mean /= rms.length;

        rmsStd = Math.sqrt(rms2Mean - rmsMean*rmsMean);
        rowResidualStd = Math.sqrt(rowResidual2Mean - rowResidualMean*rowResidualMean);
        colResidualStd = Math.sqrt(colResidual2Mean - colResidualMean*colResidualMean);
    }

    boolean eliminateGCPsBasedOnRMS(float threshold) {

        ArrayList pinList = new ArrayList();
        for (int i = 0; i < rms.length; i++) {
            if (rms[i] >= threshold) {
                pinList.add(slaveGCPGroup.get(i));
                //System.out.println("WARP: slave gcp[" + i + "] is eliminated");
            }
        }

        for (Object aPinList : pinList) {
            slaveGCPGroup.remove((Pin) aPinList);
        }

        return !pinList.isEmpty();
    }

    void getWarpedCoords(float mX, float mY, PixelPos slavePos) {

        float[] xCoeffs = warp.getXCoeffs();
        float[] yCoeffs = warp.getYCoeffs();
        if (xCoeffs.length != yCoeffs.length) {
            throw new OperatorException("WARP has different number of coefficients for X and Y");
        }

        int numOfCoeffs = xCoeffs.length;
        switch (warpPolynomialOrder) {
            case 1:
                if (numOfCoeffs != 3) {
                    throw new OperatorException("Number of WARP coefficients do not match WARP degree");
                }
                slavePos.x = xCoeffs[0] + xCoeffs[1]*mX + xCoeffs[2]*mY;

                slavePos.y = yCoeffs[0] + yCoeffs[1]*mX + yCoeffs[2]*mY;
                break;

            case 2:
                if (numOfCoeffs != 6) {
                    throw new OperatorException("Number of WARP coefficients do not match WARP degree");
                }
                slavePos.x = xCoeffs[0] + xCoeffs[1]*mX + xCoeffs[2]*mY +
                             xCoeffs[3]*mX*mX + xCoeffs[4]*mX*mY + xCoeffs[5]*mY*mY;

                slavePos.y = yCoeffs[0] + yCoeffs[1]*mX + yCoeffs[2]*mY +
                             yCoeffs[3]*mX*mX + yCoeffs[4]*mX*mY + yCoeffs[5]*mY*mY;
                break;

            case 3:
                if (numOfCoeffs != 10) {
                    throw new OperatorException("Number of WARP coefficients do not match WARP degree");
                }
                slavePos.x = xCoeffs[0] + xCoeffs[1]*mX + xCoeffs[2]*mY +
                             xCoeffs[3]*mX*mX + xCoeffs[4]*mX*mY + xCoeffs[5]*mY*mY +
                             xCoeffs[6]*mX*mX*mX + xCoeffs[7]*mX*mX*mY + xCoeffs[8]*mX*mY*mY + xCoeffs[9]*mY*mY*mY;

                slavePos.y = yCoeffs[0] + yCoeffs[1]*mX + yCoeffs[2]*mY +
                             yCoeffs[3]*mX*mX + yCoeffs[4]*mX*mY + yCoeffs[5]*mY*mY +
                             yCoeffs[6]*mX*mX*mX + yCoeffs[7]*mX*mX*mY + yCoeffs[8]*mX*mY*mY + yCoeffs[9]*mY*mY*mY;
                break;

            default:
                throw new OperatorException("Incorrect WARP degree");
        }
    }

    void outputCoRegistrationInfo(boolean appendFlag, float threshold, int parseIndex) {

        float[] xCoeffs = warp.getXCoeffs();
        float[] yCoeffs = warp.getYCoeffs();

        FileOutputStream out; // declare a file output object
        PrintStream p; // declare a print stream object
        String fileName = slaveProduct.getName() + "_residual.txt";
        try {
            File appUserDir = new File(DatUtils.getApplicationUserDir(true).getAbsolutePath() + File.separator + "log");
            if(!appUserDir.exists()) {
                appUserDir.mkdirs();
            }
            fileName = appUserDir.toString() + File.separator + fileName;
            out = new FileOutputStream(fileName, appendFlag);

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
            for (int i = 0; i < rms.length; i++) {
                p.format("%2d  |%13.3f |%13.3f |%12.3f |%12.3f |%13.3f |%13.3f |%10.3f |",
                        i, masterGCPCoords[2*i], masterGCPCoords[2*i+1], slaveGCPCoords[2*i], slaveGCPCoords[2*i+1],
                        rowResiduals[i], colResiduals[i], rms[i]);
                p.println();
            }

            p.println();
            p.format("Row residual mean = %8.3f", rowResidualMean);
            p.println();
            p.format("Row residual std = %8.3f", rowResidualStd);
            p.println();

            p.println();
            p.format("Col residual mean = %8.3f", colResidualMean);
            p.println();
            p.format("Col residual std = %8.3f", colResidualStd);
            p.println();

            p.println();
            p.format("RMS mean = %8.3f", rmsMean);
            p.println();
            p.format("RMS std = %8.3f", rmsStd);

            p.close();

        } catch(IOException exc) {
            throw new OperatorException(exc);
        }
    }

    void createTargetProduct() {

        targetProduct = new Product(slaveProduct.getName(),
                                    slaveProduct.getProductType(),
                                    masterProduct.getSceneRasterWidth(),
                                    masterProduct.getSceneRasterHeight());

        Band targetBand = targetProduct.addBand(slaveBand.getName(), ProductData.TYPE_FLOAT32);
        targetBand.setUnit(slaveBand.getUnit());

        // coregistrated image should have the same geo-coding as the master image
        ProductUtils.copyMetadata(masterProduct, targetProduct);
        ProductUtils.copyTiePointGrids(masterProduct, targetProduct);
        ProductUtils.copyFlagCodings(masterProduct, targetProduct);
        ProductUtils.copyGeoCoding(masterProduct, targetProduct);
        targetProduct.setStartTime(masterProduct.getStartTime());
        targetProduct.setEndTime(masterProduct.getEndTime());

        setTargetGCPs(); // copy slave GCPs to target product
        updateTargetMetadata();

        targetProduct.setPreferredTileSize(slaveProduct.getSceneRasterWidth(), 256);
    }

    void updateTargetMetadata() {

        // output RMS to Metadata
        /*
        MetadataElement elem = new MetadataElement("co-registrationInfo");
        for (int i = 0; i < numValidGCPs; i++) {
            elem.addAttribute(new MetadataAttribute("RMS_" + i,
                    ProductData.createInstance(new float[]{rms[i]}), false));            
        }
        ProductUtils.addElementToHistory(targetProduct, elem);
        */
    }

    RenderedOp createWarpImage(RenderedImage srcImage) {

        // reformat source image by casting pixel values from ushort to float
        ParameterBlock pb1 = new ParameterBlock();
        pb1.addSource(srcImage);
        pb1.add(DataBuffer.TYPE_FLOAT);
        RenderedImage srcImageFloat = JAI.create("format", pb1);

        // get warped image
        ParameterBlock pb2 = new ParameterBlock();
        pb2.addSource(srcImageFloat);
        pb2.add(warp);
        pb2.add(interp);
        return JAI.create("warp", pb2);
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
            super(WARPOperator.class);
        }
    }
}