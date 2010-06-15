//package org.esa.nest.doris.gpf;
//
//import com.bc.ceres.core.ProgressMonitor;
//import org.esa.beam.framework.datamodel.Band;
//import org.esa.beam.framework.datamodel.MetadataElement;
//import org.esa.beam.framework.datamodel.Product;
//import org.esa.beam.framework.gpf.Operator;
//import org.esa.beam.framework.gpf.OperatorException;
//import org.esa.beam.framework.gpf.OperatorSpi;
//import org.esa.beam.framework.gpf.Tile;
//import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
//import org.esa.beam.framework.gpf.annotations.Parameter;
//import org.esa.beam.framework.gpf.annotations.SourceProduct;
//import org.esa.beam.framework.gpf.annotations.TargetProduct;
//import org.esa.beam.util.ProductUtils;
//import org.esa.nest.datamodel.AbstractMetadata;
//import org.esa.nest.doris.datamodel.AbstractDorisMetadata;
//import org.esa.nest.doris.datamodel.CoarseOrbCoregDorisMetadata;
//import org.esa.nest.gpf.OperatorUtils;
//
//import java.awt.*;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * Created by IntelliJ IDEA.
// * User: pmar
// * Date: May , 2010
// */
//@OperatorMetadata(alias="Coarse Orbits Coregistration", internal=true)
//public class CoregistrationCorOrbOp extends Operator {
//
//    @SourceProduct(alias="source")
//    private Product sourceProduct;
//    @TargetProduct
//    private Product targetProduct;
//
//    private MetadataElement dorisAbsMetadata = null;
//    private MetadataElement dorisCoarseOrbCoregMetadata = null;
//
//    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
//            sourceProductId="source", label="Source Bands")
//    private
//    String[] sourceBandNames;
//
//    private final HashMap<Band, Band> targetBandToSourceBandMap = new HashMap<Band, Band>();
//
//    /**
//     * Default constructor. The graph processing framework
//     * requires that an operator has a default constructor.
//     */
//    public CoregistrationCorOrbOp() {
//    }
//
//    /**
//     * Initializes this operator and sets the one and only target product.
//     * <p>The target product can be either defined by a field of type {@link org.esa.beam.framework.datamodel.Product} annotated with the
//     * {@link org.esa.beam.framework.gpf.annotations.TargetProduct TargetProduct} annotation or
//     * by calling {@link #setTargetProduct} method.</p>
//     * <p>The framework calls this method after it has created this operator.
//     * Any client code that must be performed before computation of tile data
//     * should be placed here.</p>
//     *
//     * @throws org.esa.beam.framework.gpf.OperatorException
//     *          If an error occurs during operator initialisation.
//     * @see #getTargetProduct()
//     */
//    @Override
//    public void initialize() throws OperatorException {
//
//        // pull metadata
//        dorisAbsMetadata = AbstractDorisMetadata.getMetadata(sourceProduct);
//        dorisCoarseOrbCoregMetadata = CoarseOrbCoregDorisMetadata.getMetadata(sourceProduct).
//                getElement(CoarseOrbCoregDorisMetadata.DORIS_PROCSTEP_METADATA_ROOT);
//
//
//        dorisAbsMetadata.getElements()
//
//        // get additional info through some functions that are needed? how to get the orbits?
//
////        @Override
////        public void initialize() throws OperatorException {
////            try {
////                absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);
////
////                getPixelSpacings();
////
////                getSourceImageDimension();
////
////                getTiePointGrid();
////
////                setTargetReportFilePath();
////
////                createTargetProduct();
////
////            } catch (Exception e) {
////                OperatorUtils.catchOperatorException(getId(), e);
////            }
////        }
//
//
//        // get metadata from the source product
//        getSourceMetadata();
//
//        // check flags of previous runs: whether this operator can run on the data
//        // check in doris abstracted metadata whats happening
//
//        // targetBand.setSourceImage(sourceBand.getSourceImage());
//        // create target product
//        targetProduct = new Product(sourceProduct.getName(),
//                sourceProduct.getProductType(),
//                sourceProduct.getSceneRasterWidth(),
//                sourceProduct.getSceneRasterHeight());
//
//        // Add selected bands to the target product: this should be automatic for InSAR processing
//        addSelectedBands();
//
//        // copy or create product nodes for metadata, tiepoint grids, geocoding, start/end times, etc.
//        ProductUtils.copyMetadata(sourceProduct, targetProduct);
//        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
//        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
//        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
//        targetProduct.setStartTime(sourceProduct.getStartTime());
//        targetProduct.setEndTime(sourceProduct.getEndTime());
//        targetProduct.setDescription(sourceProduct.getDescription());
//
//        // update the metadata with the affect of the processing
//        updateTargetProductMetadata();
//
//        targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 50);
//    }
//
//    /**
//     * Compute mean pixel spacing (in m).
//     */
//    private void getSourceMetadata() {
//
//        // get metadata from stack: i need orbits and timing information for every product that is collocated in the stack
//
//        // here everything is in abstracted metadata
//
//    }
//
//    /**
//     * Update metadata in the target product.
//     */
//    private void updateTargetProductMetadata() {
//
//        // append static metadata entry
//
//        // list initial offset and dump information there
//
//    }
//
//    /**
//     * Add the user selected bands to target product.
//     * @throws OperatorException The exceptions.
//     */
//    private void addSelectedBands() throws OperatorException {
//
//        // WORK OUT THIS ONE: I AM NOT SURE HOW THIS WORKS IN THE OPERATOR WITHIN DAT!!!!
//
//        // check out how to just copy the bands that are already in the stack, i am simply working with the metadata!
//
//        // if user did not select any band then add all
//        if (sourceBandNames == null || sourceBandNames.length == 0) {
//            final Band[] bands = sourceProduct.getBands();
//            final ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
//            for (Band band : bands) {
//                    bandNameList.add(band.getName());
//            }
//            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
//        }
//
//        for (String srcBandName : sourceBandNames) {
//            final Band srcBand = sourceProduct.getBand(srcBandName);
//
//            final Band targetBand = ProductUtils.copyBand(srcBand.getName(), sourceProduct, targetProduct);
//
//            // copy first band by setSourceImage to avoid computing it
//            if(srcBand == sourceProduct.getBandAt(0)) {
//                targetBand.setSourceImage(srcBand.getSourceImage());
//            }
//
//            targetBandToSourceBandMap.put(targetBand, srcBand);
//        }
//    }
//
//	/**
//     * Called by the framework in order to compute the stack of tiles for the given target bands.
//     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
//     *
//     * @param targetTiles     The current tiles to be computed for each target band.
//     * @param targetRectangle The area in pixel coordinates to be computed (same for all rasters in <code>targetRasters</code>).
//     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
//     * @throws org.esa.beam.framework.gpf.OperatorException
//     *          if an error occurs during computation of the target rasters.
//     */
//    @Override
//    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {
//
////        // THIS IS AN OVERKILL : just re-writting, since only metadata is being used here
////
////        // FIND A REFERENCE IN THE CODE ON HOW TO USE IT
////
////        final Band[] targetBands = targetTiles.keySet().toArray(new Band[targetTiles.keySet().size()]);
////        final Tile sourceRaster = getSourceTile(targetBandToSourceBandMap.get(targetBands[0]), targetRectangle, pm);
////
////        final int x0 = targetRectangle.x;
////        final int y0 = targetRectangle.y;
////        final int w = targetRectangle.width;
////        final int h = targetRectangle.height;
////        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);
////
////        for (int y = y0; y < y0 + h; y++) {
////            for (int x = x0; x < x0 + w; x++) {
////                final double v = sourceRaster.getSampleDouble(x, y);
////
////                final int targetIndex = targetTiles.get(targetBands[0]).getDataBufferIndex(x, y);
////                for(Band b : targetBands) {
////                    targetTiles.get(b).getDataBuffer().setElemDoubleAt(targetIndex, y);
////                }
////            }
////        }
//    }
//
//
//    /**
//     * The SPI is used to register this operator in the graph processing framework
//     * via the SPI configuration file
//     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
//     * This class may also serve as a factory for new operator instances.
//     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator()
//     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator(java.util.Map, java.util.Map)
//     */
//    public static class Spi extends OperatorSpi {
//        public Spi() {
//            super(CoregistrationCorOrbOp.class);
//        }
//    }
//}