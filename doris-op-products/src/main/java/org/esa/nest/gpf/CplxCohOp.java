package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.MetadataElement;
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
// import org.esa.nest.doris.datamodel.AbstractDorisMetadata; // repackage this

import java.awt.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


@OperatorMetadata(alias = "CplxCoh",
        category = "InSAR Products",
        description = "Estimate coherence from stack of coregistered images", internal = false)
public class CplxCohOp extends Operator {

    // ----------------------------------------------------
    // NOTE 1: Assumed that slave bands are in master coord system, and are smaller
    //         or equal to master.
    // NOTE 2: No reference phase (dem nor flat earth) is removed by default in computation
    // NOTE 3: No multilooking happening in this operator
    // ----------------------------------------------------

    int testCounter = 0;

    @SourceProduct
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The coherence window size", interval = "(1, 10]", defaultValue = "5",
            label = "Coherence Window Size")
    private int coherenceWindowSize = 5;

    private Band masterBand0 = null;
    private Band masterBand1 = null;
//    private int sourceImageWidth;
//    private int sourceImageHeight;

    private String[] masterBandNames;
    private String[] slaveBandNames;

    private final Map<Band, Band> sourceRasterMap = new HashMap<Band, Band>(10);
    private final Map<Band, Band> complexSrcMapI = new HashMap<Band, Band>(10);
    private final Map<Band, Band> complexSrcMapQ = new HashMap<Band, Band>(10);
    //private final Map<Band, Band> complexIfgMap = new HashMap<Band, Band>(10);

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public CplxCohOp() {
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

            // TODO: throw in exception here!
            masterBand0 = sourceProduct.getBandAt(0);
            masterBand1 = sourceProduct.getBandAt(1);

            createTargetProduct();

        } catch (Exception e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Thread safe counter
     */
    class Counter {
        private int count = 0;
        public synchronized void increment() {
            int n = count;
            count = n + 1;
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

        // TODO: this doesnt work for multichannel data
        final int numSrcBands = sourceProduct.getNumBands();

        masterBandNames = new String[numSrcBands];
        slaveBandNames = new String[numSrcBands];
        String iBandName = "null";
        String qBandName = "null";

        // counters
        int cnt = 1;
        int inc = 2;
        int slaveArrayCounter = 0;

        // add only master and band for real coherence
        // i need for every slave to have one coherence image or complex coherence?!
        for (int i = 0; i < numSrcBands; i += inc) {

            final Band srcBandI = sourceProduct.getBandAt(i);
            final Band srcBandQ = sourceProduct.getBandAt(i + 1);

            // TODO: beautify names of ifg bands
            if (srcBandI.getUnit().equals(Unit.REAL) && srcBandQ.getUnit().equals(Unit.IMAGINARY)) {

                if (srcBandI == masterBand0) {
                    iBandName = srcBandI.getName();
                    masterBandNames[0] = iBandName;
                    final Band targetBandI = targetProduct.addBand(iBandName, ProductData.TYPE_FLOAT32);
                    ProductUtils.copyRasterDataNodeProperties(srcBandI, targetBandI);
                    sourceRasterMap.put(targetBandI, srcBandI);
                } else {
                    slaveBandNames[slaveArrayCounter++] = srcBandI.getName();
                }

                if (srcBandQ == masterBand1) {
                    qBandName = srcBandQ.getName();
                    masterBandNames[1] = qBandName;
                    final Band targetBandQ = targetProduct.addBand(qBandName, ProductData.TYPE_FLOAT32);
                    ProductUtils.copyRasterDataNodeProperties(srcBandQ, targetBandQ);
                    sourceRasterMap.put(targetBandQ, srcBandQ);
                } else {
                    slaveBandNames[slaveArrayCounter++] = srcBandQ.getName();
                }

                complexSrcMapQ.put(srcBandI, srcBandQ);
                complexSrcMapI.put(srcBandQ, srcBandI);

                if (srcBandI != masterBand0 && srcBandQ != masterBand1 && srcBandQ.getUnit().equals(Unit.IMAGINARY)) {
                    String coherenceBandName = "Coherence_" + (cnt - 1);
                    addTargetBand(coherenceBandName, ProductData.TYPE_FLOAT32, "coherence");
                }

                cnt++;

                //ReaderUtils.createVirtualIntensityBand(targetProduct, targetBandI, targetBandQ, suffix);                
                // only coherence: REAL VALUES
                //ReaderUtils.createVirtualPhaseBand(targetProduct, targetBandI, targetBandQ, suffix);
            }

        }

//        coherenceSlaveMap.put(coherenceBandName, iqBandNames);
        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);
        targetProduct.setPreferredTileSize(sourceProduct.getSceneRasterWidth(), 200);

        System.out.println("--------------------------------------------------------------------");
        System.out.println("Product size (width X height):" + sourceProduct.getSceneRasterWidth() + " X " + sourceProduct.getSceneRasterHeight());
        System.out.println("--------------------------------------------------------------------");


    }

    private void addTargetBand(String bandName, int dataType, String bandUnit) {
        if (targetProduct.getBand(bandName) == null) {
            final Band targetBand = new Band(bandName,
                    ProductData.TYPE_FLOAT32, //dataType,
                    sourceProduct.getSceneRasterWidth(),
                    sourceProduct.getSceneRasterHeight());
            targetBand.setUnit(bandUnit);
            targetProduct.addBand(targetBand);
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

       
        try {

//            int n,count;

            final Rectangle targetTileRectangle = targetTile.getRectangle();

            final int x0 = targetTileRectangle.x;
            final int y0 = targetTileRectangle.y;
            final int w = targetTileRectangle.width;
            final int h = targetTileRectangle.height;

//             synchronized (this) {

// //                count = n + 1;
// //
// //                System.out.println("Count: " + count);

//                 final MetadataElement targetRoot = targetProduct.getMetadataRoot();
//                 //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//                 // DORIS: Abstracted Metadata Root
//                 // can i put some synchronization here?!
//                 MetadataElement dorisMetadataRoot = targetRoot.getElement(AbstractDorisMetadata.DORIS_PROCSTEP_METADATA_ROOT);
//                 if (dorisMetadataRoot == null) {
//                     System.out.println("tx0 = " + x0 + ", ty0 = " + y0 + ", w = " + w + ", h = " + h);
//                     System.out.println("Loop counter: " + y0);
//                     dorisMetadataRoot = new MetadataElement(AbstractDorisMetadata.DORIS_PROCSTEP_METADATA_ROOT);
//                     targetRoot.addElement(dorisMetadataRoot);
//                 } else {
//                     System.out.println("tx0 = " + x0 + ", ty0 = " + y0 + ", w = " + w + ", h = " + h);
//                     System.out.println("Loop counter: " + y0);
//                 }
//             }

//        final Rectangle targetTileRectangleOverlap = targetTileRectangle.clone();
//        targetTileRectangleOverlap.setSize(targetTileRectangle.width,targetTileRectangle.height+10);

            // try to work out an overlap between tiles?
            final int x0_overlap = x0;
            final int y0_overlap = y0;
            final int w_overlap = w;
            int h_overlap = h + 10; // fnc of coherence window

            if (y0_overlap + h_overlap > sourceProduct.getSceneRasterHeight()) {
                h_overlap = h;
            }

            final Rectangle targetTileRectangleOverlap = new Rectangle(x0_overlap, y0_overlap, w_overlap, h_overlap);

//            System.out.println("---- test --- ");
//            System.out.println("tileOriginal (x,y,w,h):" + x0 + "," + y0 + "," + w + "," + h);
//            System.out.println("tileOverlap (x,y,w,h):" + x0_overlap + "," + y0_overlap + "," + w_overlap + "," + h_overlap);

//            // for coherence window
//            final int winL = coherenceWindowSize;
//            final int winP = coherenceWindowSize;
//
//            final int leadingZeros = (winP - 1) / 2; // number of pixels=0 floor...
//            final int trailingZeros = (winP) / 2;  // floor....

            final Band srcBand = sourceRasterMap.get(targetBand);
            //final Band srcBand = sourceProduct.getBand(targetBand.getName());

            // dump master into a product
            if (srcBand == masterBand0 || srcBand == masterBand1) {
                final Tile masterRaster = getSourceTile(srcBand, targetTileRectangle, pm);
                final ProductData masterData = masterRaster.getDataBuffer();
                final ProductData targetData = targetTile.getDataBuffer();

                
                for (int y = y0; y < y0 + h; y++) {
                    for (int x = x0; x < x0 + w; x++) {

                        final int index = masterRaster.getDataBufferIndex(x, y);
                        targetData.setElemFloatAt(index, masterData.getElemFloatAt(index));
                    }
                }
            } else { //coherence bands only one band per slave

                // loop through pairs of slaveBandNames

                if (targetBand.getUnit().contains("coherence")) {

                    int inc = 2;
                    for (int slaveBandNameIndex = 0; slaveBandNameIndex < slaveBandNames.length; slaveBandNameIndex += inc) {

                        if (slaveBandNames[slaveBandNameIndex] != null && slaveBandNames[slaveBandNameIndex + 1] != null) {

                            final Tile masterRasterI = getSourceTile(masterBand0, targetTileRectangleOverlap, pm);
                            final ProductData masterDataI = masterRasterI.getDataBuffer();

                            final Tile masterRasterQ = getSourceTile(masterBand1, targetTileRectangleOverlap, pm);
                            final ProductData masterDataQ = masterRasterQ.getDataBuffer();


                            final Tile slaveRasterI = getSourceTile(sourceProduct.getBand(slaveBandNames[0]), targetTileRectangleOverlap, pm);
                            final ProductData slaveDataI = slaveRasterI.getDataBuffer();

                            final Tile slaveRasterQ = getSourceTile(sourceProduct.getBand(slaveBandNames[1]), targetTileRectangleOverlap, pm);
                            final ProductData slaveDataQ = slaveRasterQ.getDataBuffer();

                            final ProductData targetData = targetTile.getDataBuffer();

                            // separate estimation along the edges
                            for (int y = y0; y < y0 + h; y++) {
                                for (int x = x0; x < x0 + w; x++) {

                                    final int index = slaveRasterQ.getDataBufferIndex(x, y);

                                    double sum1 = 0.0;
                                    double sum2 = 0.0;
                                    double sum3 = 0.0;
                                    double sum4 = 0.0;

                                    int coherenceWindowHeight = y + coherenceWindowSize;
                                    // check on the last tile!
                                    if (h == h_overlap && coherenceWindowHeight > y0 + h) {
                                        coherenceWindowHeight = y0 + h; // - (y + coherenceWindowSize);
                                    }

                                    // assume no tiling in range
                                    int coherenceWindowLength = x + coherenceWindowSize;
                                    if (coherenceWindowLength > x0 + w) {
                                        coherenceWindowLength = x0 + w; // - (x + coherenceWindowSize);
                                    }


                                    int line;
                                    int pix;
                                    for (line = y; line < coherenceWindowHeight; line++) {
                                        for (pix = x; pix < coherenceWindowLength; pix++) {

                                            final int indexCohWind = slaveRasterQ.getDataBufferIndex(pix, line);

                                            final float mr = masterDataI.getElemFloatAt(indexCohWind);
                                            final float mi = masterDataQ.getElemFloatAt(indexCohWind);
                                            final float sr = slaveDataI.getElemFloatAt(indexCohWind);
                                            final float si = slaveDataQ.getElemFloatAt(indexCohWind);

                                            sum1 += mr * sr + mi * si;
                                            sum2 += mi * sr - mr * si;
                                            sum3 += mr * mr + mi * mi;
                                            sum4 += sr * sr + si * si;

                                        }

                                    }

                                    float cohValue = (float) (Math.sqrt(sum1 * sum1 + sum2 * sum2) / Math.sqrt(sum3 * sum4));
                                    targetData.setElemFloatAt(index, cohValue);
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            throw new OperatorException(e);
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
            super(CplxCohOp.class);
        }
    }
}
