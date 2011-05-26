package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
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
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.jblas.ComplexDoubleMatrix;
import org.jblas.DoubleMatrix;
import org.jdoris.core.Baseline;
import org.jdoris.core.Orbit;
import org.jdoris.core.SLCImage;
import org.jdoris.core.utils.SarUtils;

import java.awt.*;
import java.awt.image.RenderedImage;
import java.util.HashMap;
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

    @SourceProduct
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(interval = "(1, 20]",
            description = "Size of coherence estimation window in Azimuth direction",
            defaultValue = "10",
            label = "Coherence Window Size in Azimuth")
    private int coherenceWindowSizeAzimuth = 10;

    @Parameter(interval = "(1, 20]",
            description = "Size of coherence estimation window in Range direction",
            defaultValue = "2",
            label = "Coherence Window Size in Range")
    private int coherenceWindowSizeRange = 2;

    private Band masterBand0 = null;
    private Band masterBand1 = null;

    private String[] masterBandNames;
    private String[] slaveBandNames;

    private MetadataElement masterRoot;
    private MetadataElement slaveRoot;
    private SLCImage masterMetadata;
    private SLCImage slaveMetadata;
    private Orbit masterOrbit;
    private Orbit slaveOrbit;
    private Baseline baseline = new Baseline();

    private final Map<Band, Band> complexSrcMapI = new HashMap<Band, Band>(10);
    private final Map<Band, Band> complexSrcMapQ = new HashMap<Band, Band>(10);

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

            masterBand0 = sourceProduct.getBandAt(0);
            masterBand1 = sourceProduct.getBandAt(1);

            createTargetProduct();
            defineMetadata();
            defineOrbits();
            estimateBaseline();

            System.out.println("Perpendicular baseline at pixel [100,100]: " + baseline.getBperp(100,100));
            System.out.println("Parallel baseline at pixel [200,200]: " + baseline.getBpar(100,100));

        } catch (Exception e) {
            throw new OperatorException(e);
        }
    }

    private void defineMetadata() {

        masterRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);
        masterMetadata = new SLCImage(masterRoot);

        MetadataElement slaveRoot = sourceProduct.getMetadataRoot().getElement(AbstractMetadata.SLAVE_METADATA_ROOT).getElementAt(0);
        slaveMetadata = new SLCImage(slaveRoot);
    }

    private void defineOrbits() throws Exception {
        masterOrbit = new Orbit(masterRoot, 3);
        slaveOrbit = new Orbit(slaveRoot, 3);
    }

    private void estimateBaseline() throws Exception {
        baseline.model(masterMetadata, slaveMetadata, masterOrbit, slaveOrbit);
    }


    /**
     * Create target product.
     */
    private void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                sourceProduct.getProductType(),
                sourceProduct.getSceneRasterWidth(),
                sourceProduct.getSceneRasterHeight());

        final int numSrcBands = sourceProduct.getNumBands();

        masterBandNames = new String[numSrcBands];
        slaveBandNames = new String[numSrcBands];
        String iBandName;
        String qBandName;

        // counters
        int cnt = 1;
        int inc = 2;
        int slaveArrayCounter = 0;

        // add only master and band for real coherence
        // i need for every slave to have one coherence image or complex coherence?!
        for (int i = 0; i < numSrcBands; i += inc) {

            final Band srcBandI = sourceProduct.getBandAt(i);
            final Band srcBandQ = sourceProduct.getBandAt(i + 1);

            if (srcBandI.getUnit().equals(Unit.REAL) && srcBandQ.getUnit().equals(Unit.IMAGINARY)) {

                if (srcBandI == masterBand0) {
                    iBandName = srcBandI.getName();
                    masterBandNames[0] = iBandName;
                } else {
                    slaveBandNames[slaveArrayCounter++] = srcBandI.getName();
                }

                if (srcBandQ == masterBand1) {
                    qBandName = srcBandQ.getName();
                    masterBandNames[1] = qBandName;
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

            }

        }
        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);
        targetProduct.setPreferredTileSize(sourceProduct.getSceneRasterWidth(), 50);
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

            final Rectangle targetTileRectangle = targetTile.getRectangle();

            final int x0 = targetTileRectangle.x;
            final int y0 = targetTileRectangle.y;
            final int w = targetTileRectangle.width;
            final int h = targetTileRectangle.height;
            // System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

            // loop through pairs of slaveBandNames
            if (targetBand.getUnit().contains("coherence")) {

                int inc = 2;
                for (int slaveBandNameIndex = 0; slaveBandNameIndex < slaveBandNames.length; slaveBandNameIndex += inc) {

                    if (slaveBandNames[slaveBandNameIndex] != null && slaveBandNames[slaveBandNameIndex + 1] != null) {

                        DoubleMatrix masterDataI = getDoubleMatrix(targetTileRectangle, masterBand0);
                        DoubleMatrix masterDataQ = getDoubleMatrix(targetTileRectangle, masterBand1);

                        DoubleMatrix slaveDataI = getDoubleMatrix(targetTileRectangle, sourceProduct.getBand(slaveBandNames[0]));
                        DoubleMatrix slaveDataQ = getDoubleMatrix(targetTileRectangle, sourceProduct.getBand(slaveBandNames[1]));

                        DoubleMatrix coherence = SarUtils.coherence(new ComplexDoubleMatrix(masterDataI, masterDataQ),
                                new ComplexDoubleMatrix(slaveDataI, slaveDataQ), coherenceWindowSizeAzimuth, coherenceWindowSizeRange);

                        targetTile.setRawSamples(ProductData.createInstance(coherence.toArray()));

                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        }
    }

    private DoubleMatrix getDoubleMatrix(Rectangle targetTileRectangleOverlap, Band masterBand0) {

        final Tile masterRasterI = getSourceTile(masterBand0, targetTileRectangleOverlap);
        RenderedImage srcImage = masterRasterI.getRasterDataNode().getSourceImage();
        double[] dataArray = srcImage.getData(targetTileRectangleOverlap).
                getSamples(targetTileRectangleOverlap.x, targetTileRectangleOverlap.y,
                        targetTileRectangleOverlap.width, targetTileRectangleOverlap.height, 0, (double[]) null);
        return new DoubleMatrix(masterRasterI.getHeight(), masterRasterI.getWidth(), dataArray);

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
