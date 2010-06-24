package org.esa.nest.doris.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.gpf.OperatorUtils;

import org.jblas.*;

import java.awt.*;
import java.awt.image.RenderedImage;
import java.util.HashMap;
import java.util.Map;


@OperatorMetadata(alias = "ComplexSrpIfg",
        category = "InSAR Prototypes",
        description = "Compute interferograms from stack and subtract flat earth: JBLAS implementation", internal = true)
public class ComplexSrpIfgOp extends Operator {

    @SourceProduct
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    /*
        // Reference phase computed now in a separate Op
        @Parameter(description = "Num. of points for 'reference phase' polynomial estimation",
                valueSet = {"301", "401", "501", "601", "701", "801", "901", "1001"},
                defaultValue = "501",
                label="Number of SRP estimation points")
        private int srpNumberPoints = 501;

        @Parameter(description = "The order of 'reference phase' polynomial", valueSet = {"3", "4", "5", "6", "7", "8"},
                defaultValue = "5",
                label="SRP Polynomial Order")
        private int srpPolynomialOrder = 2;
    */

    private Band masterBand1 = null;
    private Band masterBand2 = null;

    private final Map<Band, Band> slaveRasterMap = new HashMap<Band, Band>(10);

    // see comment in finalize{} block of computeTileStack
    private int totalTileCount;
    private ThreadSafeCounter threadCounter = new ThreadSafeCounter();


    // thread safe counter for testing: copyed and pasted from DorisOpUtils.java
    public final class ThreadSafeCounter {
        private long value = 0;

        public synchronized long getValue() {
            return value;
        }

        public synchronized long increment() {
            return ++value;
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
        try {

            masterBand1 = sourceProduct.getBandAt(0);
            if (masterBand1.getUnit() != null && masterBand1.getUnit().equals(Unit.REAL) && sourceProduct.getNumBands() > 1) {
                masterBand2 = sourceProduct.getBandAt(1);
            }

            createTargetProduct();

        } catch (Exception e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Create target product.
     */
    private void createTargetProduct() {

        // total number of bands in stack: master and slaves
        final int totalNumOfBands = sourceProduct.getNumBands();

        targetProduct = new Product(sourceProduct.getName(),
                sourceProduct.getProductType(),
                sourceProduct.getSceneRasterWidth(),
                sourceProduct.getSceneRasterHeight());

        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);

        System.out.println("Number of bands of the source product: " + sourceProduct.getNumBands());
        System.out.println("Number of bands of the target product: " + targetProduct.getNumBands());

//        targetProduct.setPreferredTileSize(sourceProduct.getSceneRasterWidth(), 2000);
        targetProduct.setPreferredTileSize(500, 500);

//        // TILE housekeeping

        // WHY NULL??!?!?
        final Dimension tileSize = targetProduct.getPreferredTileSize();
        final int rasterHeight = targetProduct.getSceneRasterHeight();
        final int rasterWidth = targetProduct.getSceneRasterWidth();

        int tileCountX = MathUtils.ceilInt(rasterWidth / (double) tileSize.width);
        int tileCountY = MathUtils.ceilInt(rasterHeight / (double) tileSize.height);
        totalTileCount = tileCountX * tileCountY;

        System.out.println("TotalTileCount: [" + totalTileCount + "]");

        int cnt = 1;
        int inc = 2;
        //int cnt_master = 0;

        // band names
        String iBandName;
        String qBandName;

        // construct target product only for interferogram bands: totalNumOfBands - 2 (i/q for master)
        // assume that master bands are first 2 bands in the stack!
        for (int i = 0; i < totalNumOfBands; i += inc) {

            // TODO: coordinate this naming with metadata information
            final Band srcBandI = sourceProduct.getBandAt(i);
            final Band srcBandQ = sourceProduct.getBandAt(i + 1);
            if (srcBandI != masterBand1 && srcBandQ != masterBand2) {

                // TODO: beautify names of ifg bands
                if (srcBandI.getUnit().equals(Unit.REAL) && srcBandQ.getUnit().equals(Unit.IMAGINARY)) {

                    iBandName = "i_ifg" + cnt + "_" +
                            masterBand1.getName() + "_" +
                            srcBandI.getName();

                    final Band targetBandI = targetProduct.addBand(iBandName, ProductData.TYPE_FLOAT32);
                    ProductUtils.copyRasterDataNodeProperties(srcBandI, targetBandI);

//                    sourceRasterMap.put(targetBandI, srcBandI);

                    qBandName = "q_ifg" + cnt + "_" +
                            masterBand2.getName() + "_" +
                            srcBandQ.getName();

                    final Band targetBandQ = targetProduct.addBand(qBandName, ProductData.TYPE_FLOAT32);
                    ProductUtils.copyRasterDataNodeProperties(srcBandQ, targetBandQ);

//                    sourceRasterMap.put(targetBandQ, srcBandQ);

                    String suffix = "";
                    if (srcBandI != masterBand1) {
                        suffix = "_ifg" + cnt++;
                        //System.out.println(suffix);
                    }

                    slaveRasterMap.put(srcBandI, srcBandQ);
//                    ReaderUtils.createVirtualIntensityBand(targetProduct, targetBandI, targetBandQ, suffix);
//                    ReaderUtils.createVirtualPhaseBand(targetProduct, targetBandI, targetBandQ, suffix);

                }
            }


        }

        // virtual bands count like a regular bands!!!
        System.out.println("Number of bands of the source product: " + sourceProduct.getNumBands());
        System.out.println("Number of bands of the target product: " + targetProduct.getNumBands());

    }

    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTileMap   The target tiles associated with all target bands to be computed.
     * @param targetRectangle The rectangle of target tile.
     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the target raster.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTileMap, Rectangle targetRectangle, ProgressMonitor pm)
            throws OperatorException {
        try {

            int x0 = targetRectangle.x;
            int y0 = targetRectangle.y;
            int w = targetRectangle.width;
            int h = targetRectangle.height;
//            System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

//            pm.beginTask("Computation of interferogram", targetProduct.getNumBands());

            // initialization of complex matrices: local for thread safety
            ComplexFloatMatrix cplxMatrixMaster = new ComplexFloatMatrix(w, h);
            ComplexFloatMatrix cplxMatrixSlave = new ComplexFloatMatrix(w, h);

            // these are not very optimal loops: redo the looping to a single for Band loop with using HashMaps
            for (Band sourceBand : sourceProduct.getBands()) {

                String sourceBandUnit = sourceBand.getUnit();

                if (sourceBand == masterBand1) {
                } else if (sourceBand == masterBand2) {

                    Tile masterRasterI = getSourceTile(masterBand1, targetRectangle, pm);
                    RenderedImage srcImage = masterRasterI.getRasterDataNode().getSourceImage();
                    float[] dataArray = srcImage.getData(targetRectangle).getSamples(x0,y0,w,h,0,(float[])null);

                    FloatMatrix dataRealMatrix = new FloatMatrix(masterRasterI.getWidth(),masterRasterI.getHeight(),dataArray);

                    Tile masterRasterQ = getSourceTile(masterBand2, targetRectangle, pm);
                    srcImage = masterRasterQ.getRasterDataNode().getSourceImage();
                    dataArray = srcImage.getData(targetRectangle).getSamples(x0,y0,w,h,0,(float[])null);

                    FloatMatrix dataImagMatrix = new FloatMatrix(masterRasterQ.getWidth(),masterRasterQ.getHeight(),dataArray);

                    cplxMatrixMaster = new ComplexFloatMatrix(dataRealMatrix, dataImagMatrix);

                } else if (sourceBandUnit != null && sourceBandUnit.contains(Unit.REAL)) {

                    Tile slaveRasterI = getSourceTile(sourceBand, targetRectangle, pm);
                    RenderedImage srcImage = slaveRasterI.getRasterDataNode().getSourceImage();
                    float[] dataArray = srcImage.getData(targetRectangle).getSamples(x0,y0,w,h,0,(float[])null);

                    FloatMatrix dataRealMatrix = new FloatMatrix(slaveRasterI.getWidth(),slaveRasterI.getHeight(),dataArray);

                    Tile slaveRasterQ = getSourceTile(slaveRasterMap.get(sourceBand),targetRectangle,pm);
                    srcImage = slaveRasterQ.getRasterDataNode().getSourceImage();
                    dataArray = srcImage.getData(targetRectangle).getSamples(x0,y0,w,h,0,(float[])null);

                    FloatMatrix dataImagMatrix = new FloatMatrix(slaveRasterQ.getWidth(),slaveRasterQ.getHeight(),dataArray);

                    cplxMatrixSlave = new ComplexFloatMatrix(dataRealMatrix, dataImagMatrix);

                }
            }

            ComplexFloatMatrix cplxIfg = cplxMatrixMaster.muli(cplxMatrixSlave.conji());

            for (Band targetBand : targetProduct.getBands()) {

                String targetBandUnit = targetBand.getUnit();

                final Tile targetTile = targetTileMap.get(targetBand);

                // all bands except for virtual ones
                if (targetBandUnit.contains(Unit.REAL)) {

                    final float[] dataArray = cplxIfg.real().toArray();

                    targetTile.setRawSamples(ProductData.createInstance(dataArray));

                    System.out.println("Dumping a tile");

                } else if (targetBandUnit.contains(Unit.IMAGINARY)) {

                    final float[] dataArray = cplxIfg.imag().toArray();
                    targetTile.setRawSamples(ProductData.createInstance(dataArray));

                }

            }

        } catch (Exception e) {

            OperatorUtils.catchOperatorException(getId(), e);

        } finally {

//            pm.done();

            // can i update of metadata here!?
            // this is not very thread safe?! :: tile scheduling is performed in ROW_BAND_COLUMN order, see OperatorExecutor
            // perhaps it would make sense to change the dispatching order to ROW_COLUMN_BAND?

            threadCounter.increment();
            System.out.println("Am I finalizing? Thread nr: " + threadCounter.increment() + " out of " + totalTileCount);
            if (totalTileCount == threadCounter.getValue()) {
                System.out.println("-----------------------------------");
                System.out.println("Finished for this thread");
                System.out.println("-----------------------------------");
            }

            // check out dispose(): see email of Luis, 31.05.2010

        }
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator()
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ComplexSrpIfgOp.class);
        }
    }
}