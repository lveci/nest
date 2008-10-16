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
import java.awt.image.renderable.ParameterBlock;
import java.util.Hashtable;
import java.util.Vector;
import java.util.ArrayList;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;

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

        computeWARPPolynomial(false); // compute initial warp polynomial

        eliminateGCPsBasedOnRMS();

        computeWARPPolynomial(true); // compute final warp polynomial

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
        if(targetBand.isSynthetic()) {
            return;
        }

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
    void computeWARPPolynomial(boolean appendFlag) {

        getNumOfValidGCPs();

        getMasterAndSlaveGCPCoordinates();

        computeWARP();

        computeRMS();

        outputCoRegistrationInfo(appendFlag);
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

        rms = new float[numValidGCPs];
        PixelPos slavePos = new PixelPos(0.0f,0.0f);
        for (int i = 0; i < rms.length; i++) {
            getWarpedCoords(masterGCPCoords[2*i], masterGCPCoords[2*i+1], slavePos);
            float dX = slavePos.x - slaveGCPCoords[2*i];
            float dY = slavePos.y - slaveGCPCoords[2*i+1];
            rms[i] = (float)Math.sqrt(dX*dX + dY*dY);
        }
    }

    void eliminateGCPsBasedOnRMS() {

        ArrayList pinList = new ArrayList();
        for (int i = 0; i < rms.length; i++) {
            if (rms[i] >= rmsThreshold) {
                pinList.add(slaveGCPGroup.get(i));
                //System.out.println("WARP: slave gcp[" + i + "] is eliminated");
            }
        }

        for (int i = 0; i < pinList.size(); i++) {
            slaveGCPGroup.remove((Pin)pinList.get(i));
        }
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

    void outputCoRegistrationInfo(boolean appendFlag) {

        System.out.println("WARP coefficients:");
        float[] xCoeffs = warp.getXCoeffs();
        for (float xCoeff : xCoeffs) {
            System.out.print(xCoeff);
            System.out.print(", ");
        }
        System.out.println();
        float[] yCoeffs = warp.getYCoeffs();
        for (float yCoeff : yCoeffs) {
            System.out.print(yCoeff);
            System.out.print(", ");
        }
        System.out.println();
        System.out.println();

        System.out.println("No. |  Master GCP x   |  Master GCP y   |   Slave GCP x   |   Slave GCP y   |        RMS      |");
        System.out.println("-----------------------------------------------------------------------------------------------");
        for (int i = 0; i < rms.length; i++) {
            System.out.format("%3d | %15.3f | %15.3f | %15.3f | %15.3f | %15.3f |\n",
                              i+1, masterGCPCoords[2*i], masterGCPCoords[2*i+1],
                              slaveGCPCoords[2*i], slaveGCPCoords[2*i+1], rms[i]);
        }

        FileWriter fw;
        String str;
        String fileName = slaveProduct.getName() + "_residual.txt";
        DecimalFormat myformat = new DecimalFormat("##########0.000");
        try {
            fw = new FileWriter(fileName, appendFlag);
            str = " " + "\r\n";
            fw.write(str);
            str = "WARP coefficients:" + "\r\n";
            fw.write(str);
            for (int i = 0; i < xCoeffs.length; i++) {
                str = xCoeffs[i] + ", ";
                fw.write(str);
            }
            str = " " + "\r\n";
            fw.write(str);
            for (int j = 0; j < yCoeffs.length; j++) {
                str = yCoeffs[j] + ", ";
                fw.write(str);
            }
            str = "\r\n";
            fw.write(str);
            if (appendFlag) {
                str = "Final Valid GCPs: \r\n";
            } else {
                str = "Initial Valid GCPs: \r\n";
            }
            fw.write(str);
            str = "No. |  Master GCP x   |  Master GCP y   |   Slave GCP x   |   Slave GCP y   |        RMS      |" + "\r\n";
            fw.write(str);
            str = "-----------------------------------------------------------------------------------------------" + "\r\n";
            fw.write(str);
            for (int i = 0; i < rms.length; i++) {
                str = i + " | " +
                      myformat.format(masterGCPCoords[2*i]) + " | " +
                      myformat.format(masterGCPCoords[2*i+1]) + " | " +
                      myformat.format(slaveGCPCoords[2*i]) + " | " +
                      myformat.format(slaveGCPCoords[2*i+1]) + " | " +
                      myformat.format(rms[i]) + " | " + "\r\n";
                fw.write(str);
            }
            fw.close();
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