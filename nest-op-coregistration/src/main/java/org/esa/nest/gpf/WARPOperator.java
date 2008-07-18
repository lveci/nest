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
import java.io.File;

/**
 * The sample operator implementation for an algorithm
 * that can compute bands independently of each other.
 */
@OperatorMetadata(alias="WARP-Creation",
                  description = "Create WARP Function And Get Co-registrated Images")
public class WARPOperator extends Operator {

    @SourceProducts(count = 2)
    private Product[] sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The order of WARP polynomial function", interval = "[1, 3]", defaultValue = "1")
    private int warpPolynomialOrder;

    private Product masterProduct;
    private Product slaveProduct;

    private Band masterBand;
    private Band slaveBand;

    private ProductNodeGroup<Pin> masterGcpGroup;
    private ProductNodeGroup<Pin> slaveGcpGroup;

    private WarpPolynomial warp;
    private int imageWidth;
    private int imageHeight;

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

        targetProduct = new Product(slaveProduct.getName(),
                                    slaveProduct.getProductType(),
                                    slaveProduct.getSceneRasterWidth(),
                                    slaveProduct.getSceneRasterHeight());

        masterBand = masterProduct.getBandAt(0);
        slaveBand = slaveProduct.getBandAt(0);

        targetProduct.addBand(slaveBand.getName(), ProductData.TYPE_FLOAT32);
        
        imageWidth = slaveProduct.getSceneRasterWidth();
        imageHeight = slaveProduct.getSceneRasterHeight();

        //System.out.println("slave image width = " + imageWidth);
        //System.out.println("slave image height = " + imageHeight);
        //System.out.println("slave image data type = " + slaveBand.getDataType());

        masterGcpGroup = masterProduct.getGcpGroup();
        slaveGcpGroup = slaveProduct.getGcpGroup();

        // coregistrated image should have the same geo-coding as the master image
        ProductUtils.copyGeoCoding(masterProduct, targetProduct);
        ProductUtils.copyMetadata(masterProduct, targetProduct);
        ProductUtils.copyTiePointGrids(masterProduct, targetProduct);
        ProductUtils.copyFlagCodings(masterProduct, targetProduct);

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
        RenderedImage srcImage = sourceRaster.getRasterDataNode().getSourceImage(); //getImage();

        // compute warp polynomial
        getWARPPolynomial();

        // get warped image
        RenderedOp warpedImage = createWarpImage(srcImage);

        // copy warped image data to target
        float[] dataArray = warpedImage.getData(targetTileRectangle).getSamples(x0, y0, w, h, 0, (float[])null);
        ProductData rawTargetData = ProductData.createInstance(dataArray);
        targetTile.setRawSamples(rawTargetData);
    }

    /**
     * Get WARP polynomial function using master and slave GCP pairs.
     */
    void getWARPPolynomial() {

        int numValidGCPs = 0;
        Vector mValidGCPPos = new Vector();
        Vector sValidGCPPos = new Vector();
        for(int i = 0; i < slaveGcpGroup.getNodeCount(); ++i) {
            Pin sPin = slaveGcpGroup.get(i);
            PixelPos sGCPPos = sPin.getPixelPos();
            String sName = sPin.getName();
            //System.out.println("WARP: slave gcp[" + i + "] = " + "(" + sGCPPos.x + "," + sGCPPos.y + ")");

            if (Float.compare(sGCPPos.x, -1.0f) != 0 && Float.compare(sGCPPos.y, -1.0f) != 0) {
                PixelPos mGCPPos = masterGcpGroup.get(sName).getPixelPos();
                System.out.println("WARP: master gcp[" + i + "] = " + "(" + mGCPPos.x + "," + mGCPPos.y + ")");

                mValidGCPPos.add(new PixelPos(mGCPPos.x, mGCPPos.y));
                sValidGCPPos.add(new PixelPos(sGCPPos.x, sGCPPos.y));
                numValidGCPs++;
            } else {
                //System.out.println("WARP: found invalid GCP(" + i + ").");
            }
        }
        //System.out.println("Total GCPs available = " + slaveGcpGroup.getNodeCount());
        //System.out.println("numValidGCPs = " + numValidGCPs);

        int pointsRequired = (warpPolynomialOrder + 2)*(warpPolynomialOrder + 1) / 2;
        //System.out.println("warpPolynomialOrder = " + warpPolynomialOrder + ", pointsRequired = " + pointsRequired);
        if (numValidGCPs < pointsRequired) {
            //throw new OperatorException("Not enough GCPs for creating WARP polynomial of order " + warpPolynomialOrder);

            // if the tile does not have enough valid GCPs, then copy the tile without warping. Here we try to create
            // an identity warp function.

            mValidGCPPos.clear();
            sValidGCPPos.clear();
            numValidGCPs = 0;
            for(int i = 0; i < slaveGcpGroup.getNodeCount(); ++i) {
                PixelPos mGCPPos = masterGcpGroup.get(slaveGcpGroup.get(i).getName()).getPixelPos();
                mValidGCPPos.add(new PixelPos(mGCPPos.x, mGCPPos.y));
                sValidGCPPos.add(new PixelPos(mGCPPos.x, mGCPPos.y));
                numValidGCPs++;
            }
            
        }

        float[] masterCoords = new float[2*numValidGCPs];
        float[] slaveCoords = new float[2*numValidGCPs];
        for (int i = 0; i < numValidGCPs; i++) {
            PixelPos mGCPPos = (PixelPos)mValidGCPPos.get(i);
            masterCoords[2*i] = mGCPPos.x;
            masterCoords[2*i+1] = mGCPPos.y;

            PixelPos sGCPPos = (PixelPos)sValidGCPPos.get(i);
            slaveCoords[2*i] = sGCPPos.x;
            slaveCoords[2*i+1] = sGCPPos.y;
        }

        /*
        // First degree test setting:
        int numValidGCPs = 6;
        warpPolynomialOrder = 1;
        float[] masterCoords = {-0.4326f, 1.1892f, -1.6656f, -0.0376f, 0.1253f, 0.3273f, 0.2877f, 0.1746f, -1.1465f, -0.1867f, 1.1909f, 0.7258f};
        float[] slaveCoords = {0.9572f, 0.7922f, 0.4854f, 0.9595f, 0.8003f, 0.6557f, 0.1419f, 0.0357f, 0.4218f, 0.8491f, 0.9157f, 0.9340f};

        // Second degree test setting:
        int numValidGCPs = 12;
        warpPolynomialOrder = 2;
        float[] masterCoords = {1.1908f, -2.1707f, -1.2025f, -0.0592f, -0.0198f, -1.0106f, -0.1567f, 0.6145f, -1.6041f, 0.5077f, 0.2573f, 1.6924f,
                                -1.0565f, 0.5913f, 1.4151f, -0.6436f, -0.8051f, 0.3803f, 0.5287f, -1.0091f, 0.2193f, -0.0195f, -0.9219f, -0.0482f};
        float[] slaveCoords = {0.7094f, 0.7513f, 0.7547f, 0.2551f, 0.2760f, 0.5060f, 0.6797f, 0.6991f, 0.6551f, 0.8909f, 0.1626f, 0.9593f,
                               0.1190f, 0.5472f, 0.4984f, 0.1386f, 0.9597f, 0.1493f, 0.3404f, 0.2575f, 0.5853f, 0.8407f, 0.2238f, 0.2543f};
        */

        warp = WarpPolynomial.createWarp(slaveCoords, //source
                                         0,
                                         masterCoords, // destination
                                         0,
                                         2*numValidGCPs,
                                         1.0F,
                                         1.0F,
                                         1.0F,
                                         1.0F,
                                         warpPolynomialOrder);


        System.out.println("WARP coefficients:");
        float[] xCoeffs = warp.getXCoeffs();
        for (int i = 0; i < xCoeffs.length; i++) {
            System.out.print(xCoeffs[i]);
            System.out.print(", ");
        }
        System.out.println();
        float[] yCoeffs = warp.getYCoeffs();
        for (int j = 0; j < yCoeffs.length; j++) {
            System.out.print(yCoeffs[j]);
            System.out.print(", ");
        }
        System.out.println();

        /*
        float[][] coeffs = warp.getCoeffs();
        for (int i = 0; i < coeffs.length; i++) {
            for (int j = 0; j < coeffs[i].length; j++) {
                System.out.print(coeffs[i][j]);
                System.out.print(", ");
            }
            System.out.println();
        }
        */
        /*
        // First degree test result:
        // 0.4300   -0.0226    0.5040
        // 0.5717   -0.1631    0.2410

        // Second degree test result:
        // 0.4875   -0.0327    0.0154   -0.0314   -0.2211   -0.0700
        // 0.4322   -0.0527    0.1838   -0.0757   -0.1331    0.1121
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
        pb2.add(new InterpolationBilinear());
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