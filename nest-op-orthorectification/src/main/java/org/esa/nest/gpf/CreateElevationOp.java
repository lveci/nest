package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.resamp.Resampling;
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

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
    CreateElevationBandOp adds an elevation band to a product
 */

@OperatorMetadata(alias="CreateElevation", description="Creates a DEM band")
public final class CreateElevationOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(valueSet = {"ACE", "GETASSE30", "SRTM 3Sec GeoTiff"},
            description = "The digital elevation model.", defaultValue="SRTM 3Sec GeoTiff", label="Digital Elevation Model")
    private String demName = "SRTM 3Sec GeoTiff";

    @Parameter(description = "The elevation band name.", defaultValue="elevation", label="Elevation Band Name")
    private String elevationBandName = "elevation";

    @Parameter(valueSet = { NEAREST_NEIGHBOUR, BILINEAR, CUBIC }, defaultValue = BILINEAR,
                label="Resampling Method")
    private String resamplingMethod = BILINEAR;

    static final String NEAREST_NEIGHBOUR = "Nearest Neighbour";
    static final String BILINEAR = "Bilinear Interpolation";
    static final String CUBIC = "Cubic Convolution";

    private ElevationModel dem = null;
    private Band elevationBand = null;

    private final Map<Band, Band> sourceRasterMap = new HashMap<Band, Band>(10);

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

            final ElevationModelRegistry elevationModelRegistry = ElevationModelRegistry.getInstance();
            final ElevationModelDescriptor demDescriptor = elevationModelRegistry.getDescriptor(demName);
            if (demDescriptor == null)
                throw new OperatorException("The DEM '" + demName + "' is not supported.");
            if (demDescriptor.isInstallingDem())
                throw new OperatorException("The DEM '" + demName + "' is currently being installed.");

            Resampling resamplingMethod = Resampling.BILINEAR_INTERPOLATION;
            if(resamplingMethod.equals(NEAREST_NEIGHBOUR)) {
                resamplingMethod = Resampling.NEAREST_NEIGHBOUR;
            } else if(resamplingMethod.equals(BILINEAR)) {
                resamplingMethod = Resampling.BILINEAR_INTERPOLATION;
            } else if(resamplingMethod.equals(CUBIC)) {
                resamplingMethod = Resampling.CUBIC_CONVOLUTION;
            }

            dem = demDescriptor.createDem(resamplingMethod);
            if(dem == null)
                throw new OperatorException("The DEM '" + demName + "' has not been installed.");
            
            createTargetProduct();

            final float noDataValue = dem.getDescriptor().getNoDataValue();
            elevationBand = targetProduct.addBand(elevationBandName, ProductData.TYPE_INT16);
            elevationBand.setSynthetic(true);
            elevationBand.setNoDataValue(noDataValue);
            elevationBand.setUnit(Unit.METERS);
            elevationBand.setDescription(demDescriptor.getName());

        } catch(Exception e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Create target product.
     */
    void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());
        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);

        for(Band band : sourceProduct.getBands()) {
            if(band.getName().equalsIgnoreCase(elevationBandName))
                throw new OperatorException("Band "+elevationBandName+" already exists. Try another name.");
            if(band instanceof VirtualBand) {
                final VirtualBand sourceBand = (VirtualBand) band;
                final VirtualBand targetBand = new VirtualBand(sourceBand.getName(),
                                   sourceBand.getDataType(),
                                   sourceBand.getRasterWidth(),
                                   sourceBand.getRasterHeight(),
                                   sourceBand.getExpression());
                ProductUtils.copyRasterDataNodeProperties(sourceBand, targetBand);
                targetProduct.addBand(targetBand);
                sourceRasterMap.put(targetBand, band);
            } else {
                final Band targetBand = ProductUtils.copyBand(band.getName(), sourceProduct, targetProduct);
                sourceRasterMap.put(targetBand, band);
            }
        }
    }

    /**
     * Called by the framework in order to compute the stack of tiles for the given target bands.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTiles The current tiles to be computed for each target band.
     * @param pm          A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          if an error occurs during computation of the target rasters.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {

        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;
        final int w = targetRectangle.width;
        final int h = targetRectangle.height;

        final GeoCoding geoCoding = targetProduct.getGeoCoding();
        final float noDataValue = dem.getDescriptor().getNoDataValue();

        final Set<Band> keys = targetTiles.keySet();
        for(Band targetBand : keys) {
            final Tile targetTile = targetTiles.get(targetBand);

            if(targetBand == elevationBand) {
                final ProductData trgData = targetTile.getDataBuffer();

                pm.beginTask("Computing elevations from " + demName + "...", h);
                try {
                    final GeoPos geoPos = new GeoPos();
                    final PixelPos pixelPos = new PixelPos();
                    float elevation;

                    for (int y = y0; y < y0 + h; ++y) {
                        for (int x = x0; x < x0 + w; ++x) {

                            pixelPos.setLocation(x + 0.5f, y + 0.5f);
                            geoCoding.getGeoPos(pixelPos, geoPos);
                            try {
                                elevation = dem.getElevation(geoPos);
                            } catch (Exception e) {
                                elevation = noDataValue;
                            }

                            trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x, y), (short) Math.round(elevation));
                        }
                        pm.worked(1);
                    }
                } finally {
                    pm.done();
                }
            } else if(targetBand instanceof VirtualBand) {
                //System.out.println("skipping virtual band");
            } else {
                final Band sourceBand = sourceRasterMap.get(targetBand);
                targetTile.setRawSamples(getSourceTile(sourceBand, targetRectangle, pm).getRawSamples());    
            }
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
            super(CreateElevationOp.class);
            setOperatorUI(CreateElevationOpUI.class);
        }
    }
}