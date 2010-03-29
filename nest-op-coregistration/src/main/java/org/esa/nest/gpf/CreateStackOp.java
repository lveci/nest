package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Coordinate;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.framework.dataop.resamp.ResamplingFactory;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProducts;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.dataio.ProductSubsetBuilder;
import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.StringUtils;
import org.esa.beam.util.FeatureCollectionClipper;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;

import java.awt.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The CreateStack operator.
 *
 */
@OperatorMetadata(alias = "CreateStack",
                  category = "Utilities",
                  description = "Collocates two or more products based on their geo-codings.")
public class CreateStackOp extends Operator {

    @SourceProducts
    private Product[] sourceProduct;

    @Parameter(description = "The list of source bands.", alias = "masterBands", itemAlias = "band",
            sourceProductId="source", label="Master Band")
    private String[] masterBandNames = null;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="sourceProduct", label="Slave Bands")
    private String[] slaveBandNames = null;

    private Product masterProduct = null;
    private final Band[] masterBands = new Band[2];

    @TargetProduct(description = "The target product which will use the master's grid.")
    private Product targetProduct = null;

    @Parameter(valueSet = {ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
                           ResamplingFactory.BILINEAR_INTERPOLATION_NAME, ResamplingFactory.CUBIC_CONVOLUTION_NAME},
               defaultValue = ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
               description = "The method to be used when resampling the slave grid onto the master grid.",
               label="Resampling Type")
    private String resamplingType = ResamplingFactory.NEAREST_NEIGHBOUR_NAME;
    private Resampling selectedResampling = Resampling.NEAREST_NEIGHBOUR;

    @Parameter(valueSet = {MASTER_EXTENT, MIN_EXTENT, MAX_EXTENT },
               defaultValue = MASTER_EXTENT,
               description = "The output image extents.",
               label="Output Extents")
    private String extent = MASTER_EXTENT;

    protected final static String MASTER_EXTENT = "Master";
    protected final static String MIN_EXTENT = "Minimum";
    protected final static String MAX_EXTENT = "Maximum";

    private final Map<Band, Band> sourceRasterMap = new HashMap<Band, Band>(10);

    @Override
    public void initialize() throws OperatorException {

        try {
            if(sourceProduct == null)
                return;
            if(sourceProduct.length < 2)
                throw new OperatorException("Please select at least two source products");
            for(final Product prod : sourceProduct) {
                if (prod.getGeoCoding() == null) {
                    throw new OperatorException(
                            MessageFormat.format("Product ''{0}'' has no geo-coding.", prod.getName()));
                }
            }

            if(masterBandNames == null || masterBandNames.length == 0) {
                final Product defaultProd = sourceProduct[0];
                if(defaultProd != null) {
                    final Band defaultBand = defaultProd.getBandAt(0);
                    if(defaultBand != null) {
                        if(defaultBand.getUnit() != null && defaultBand.getUnit().equals(Unit.REAL))
                            masterBandNames = new String[] { defaultProd.getBandAt(0).getName(),
                                                             defaultProd.getBandAt(1).getName() };
                        else
                            masterBandNames = new String[] { defaultBand.getName() };
                    }
                }
                if(masterBandNames.length == 0) {
                    targetProduct = OperatorUtils.createDummyTargetProduct(sourceProduct);
                    return;
                }
            }

            masterProduct = getMasterProduct(masterBandNames[0]);
            if(masterProduct == null) {
                targetProduct = OperatorUtils.createDummyTargetProduct(sourceProduct);
                return;
            }

            final Band[] slaveBandList = getSlaveBands();
            if(masterProduct == null || slaveBandList.length == 0 || slaveBandList[0] == null) {
                targetProduct = OperatorUtils.createDummyTargetProduct(sourceProduct);
                return;
            }

            if(extent.equals(MIN_EXTENT)) {
                determinMinimumExtents();
            } else if(extent.equals(MAX_EXTENT)) {
                determinMaximumExtents();
            } else {

                targetProduct = new Product(masterProduct.getName(),
                                            masterProduct.getProductType(),
                                            masterProduct.getSceneRasterWidth(),
                                            masterProduct.getSceneRasterHeight());

                OperatorUtils.copyProductNodes(masterProduct, targetProduct);
            }
            
            String suffix = "_mst";
            // add master bands first
            for (final Band srcBand : slaveBandList) {
                if(srcBand == masterBands[0] || (masterBands.length > 1 && srcBand == masterBands[1])) {
                    suffix = "_mst" + getBandTimeStamp(srcBand.getProduct());

                    final Band targetBand = new Band(srcBand.getName() + suffix,
                            srcBand.getDataType(),
                            targetProduct.getSceneRasterWidth(),
                            targetProduct.getSceneRasterHeight());
                    ProductUtils.copyRasterDataNodeProperties(srcBand, targetBand);
                    if(extent.equals(MASTER_EXTENT)) {
                        targetBand.setSourceImage(srcBand.getSourceImage());
                    }
                    sourceRasterMap.put(targetBand, srcBand);
                    targetProduct.addBand(targetBand);
                }
            }
            // then add slave bands
            int cnt = 1;
            for (final Band srcBand : slaveBandList) {
                if(!(srcBand == masterBands[0] || (masterBands.length > 1 && srcBand == masterBands[1]))) {
                    if(srcBand.getUnit() != null && srcBand.getUnit().equals(Unit.IMAGINARY)) {
                    } else {
                        suffix = "_slv" + cnt++ + getBandTimeStamp(srcBand.getProduct());
                    }

                    final Product srcProduct = srcBand.getProduct();
                    final Band targetBand = new Band(srcBand.getName() + suffix,
                            srcBand.getDataType(),
                            targetProduct.getSceneRasterWidth(),
                            targetProduct.getSceneRasterHeight());
                    ProductUtils.copyRasterDataNodeProperties(srcBand, targetBand);
                    if (srcProduct == masterProduct || srcProduct.isCompatibleProduct(targetProduct, 1.0e-3f)) {
                        targetBand.setSourceImage(srcBand.getSourceImage());
                    }

                    sourceRasterMap.put(targetBand, srcBand);
                    targetProduct.addBand(targetBand);
                }
            }

            // copy slave abstracted metadata
            copySlaveMetadata();

            // copy GCPs if found to master band
            final ProductNodeGroup<Placemark> masterGCPgroup = masterProduct.getGcpGroup();
            if (masterGCPgroup.getNodeCount() > 0) {
                OperatorUtils.copyGCPsToTarget(masterGCPgroup, targetProduct.getGcpGroup(targetProduct.getBandAt(0)));
            }

            selectedResampling = ResamplingFactory.createResampling(resamplingType);

        } catch(Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    private static String getBandTimeStamp(final Product product) {
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
        if(absRoot != null) {
            final String mission = absRoot.getAttributeString(AbstractMetadata.MISSION, "");
            String dateString = OperatorUtils.getAcquisitionDate(absRoot);
            if(!dateString.isEmpty())
                dateString = '_' + dateString;
            return StringUtils.createValidName("_"+mission + dateString, new char[]{'_', '.'}, '_');
        }
        return "";        
    }

    private void copySlaveMetadata() {
        final MetadataElement targetRoot = targetProduct.getMetadataRoot();
        MetadataElement targetSlaveMetadataRoot = targetRoot.getElement(AbstractMetadata.SLAVE_METADATA_ROOT);
        if(targetSlaveMetadataRoot == null) {
            targetSlaveMetadataRoot = new MetadataElement(AbstractMetadata.SLAVE_METADATA_ROOT);
            targetRoot.addElement(targetSlaveMetadataRoot);
        }
        for(Product prod : sourceProduct) {
            if(prod != masterProduct) {
                final MetadataElement slvAbsMetadata = AbstractMetadata.getAbstractedMetadata(prod);
                if(slvAbsMetadata != null) {
                    final String timeStamp = getBandTimeStamp(prod);
                    final MetadataElement targetSlaveMetadata = new MetadataElement(prod.getName()+timeStamp);
                    targetSlaveMetadataRoot.addElement(targetSlaveMetadata);
                    ProductUtils.copyMetadata(slvAbsMetadata, targetSlaveMetadata);
                }
            }
        }
    }

    private Product getMasterProduct(final String name) {
        final String masterName = getProductName(name);
        for(Product prod : sourceProduct) {
            if(prod.getName().equals(masterName)) {
                return prod;
            }
        }
        return null;
    }

    private Band[] getSlaveBands() throws OperatorException {
        final ArrayList<Band> bandList = new ArrayList<Band>(5);

        // add master band
        if(masterProduct == null) {
            throw new OperatorException("masterProduct is null");
        }
        if(masterBandNames.length > 2) {
            throw new OperatorException("Master band should be one real band or a real and imaginary band");
        }
        masterBands[0] = masterProduct.getBand(getBandName(masterBandNames[0]));
        bandList.add(masterBands[0]);

        final String unit = masterBands[0].getUnit();
        if(unit != null) {
            if (unit.contains(Unit.PHASE)) {
                throw new OperatorException("Phase band should not be selected for co-registration");
            } else if (unit.contains(Unit.IMAGINARY)) {
                throw new OperatorException("Real and imaginary master bands should be selected in pairs");
            } else if (unit.contains(Unit.REAL)) {
                if(masterBandNames.length < 2) {
                    throw new OperatorException("Real and imaginary master bands should be selected in pairs");
                } else {
                    final Product prod = getMasterProduct(masterBandNames[1]);
                    if(prod != masterProduct) {
                        throw new OperatorException("Please select master bands from the same product");
                    }
                    masterBands[1] = masterProduct.getBand(getBandName(masterBandNames[1]));
                    if(!masterBands[1].getUnit().equals(Unit.IMAGINARY))
                        throw new OperatorException("For complex products select a real and an imaginary band");
                    bandList.add(masterBands[1]);
                }
            }
        }

        // add slave bands
        if(slaveBandNames == null || slaveBandNames.length == 0) {
            for(Product slvProduct : sourceProduct) {
                if(slvProduct == masterProduct) continue;

                for(Band band : slvProduct.getBands()) {
                    if(band.getUnit() != null && band.getUnit().equals(Unit.PHASE))
                        continue;
                    if(band instanceof VirtualBand)
                        continue;
                    bandList.add(band);
                }
            }
        } else {

            for(int i = 0; i < slaveBandNames.length; i++) {
                final String name = slaveBandNames[i];
                if(contains(masterBandNames, name)) {
                    throw new OperatorException("Please do not select the same band as master and slave");
                }
                final String bandName = getBandName(name);
                final String productName = getProductName(name);

                final Product prod = getProduct(productName, bandName);
                if(prod == null) continue;

                final Band band = prod.getBand(bandName);
                final String bandUnit = band.getUnit();
                if(bandUnit != null) {
                    if (bandUnit.contains(Unit.PHASE)) {
                        throw new OperatorException("Phase band should not be selected for co-registration");
                    } else if (bandUnit.contains(Unit.IMAGINARY)) {
                        throw new OperatorException("Real and imaginary slave bands should be selected in pairs");
                    } else if (bandUnit.contains(Unit.REAL)) {
                        if (slaveBandNames.length < 2) {
                            throw new OperatorException("Real and imaginary slave bands should be selected in pairs");
                        }
                        final String nextBandName = getBandName(slaveBandNames[i+1]);
                        final String nextBandProdName = getProductName(slaveBandNames[i+1]);
                        if (!nextBandProdName.contains(productName)){
                            throw new OperatorException("Real and imaginary slave bands should be selected from the same product in pairs");
                        }
                        final Band nextBand = prod.getBand(nextBandName);
                        if (!nextBand.getUnit().contains(Unit.IMAGINARY)) {
                            throw new OperatorException("Real and imaginary slave bands should be selected in pairs");
                        }
                        bandList.add(band);
                        bandList.add(nextBand);
                        i++;
                    } else {
                        bandList.add(band);
                    }
                } else {
                    bandList.add(band);
                }
            }
        }
        return bandList.toArray(new Band[bandList.size()]);
    }

    private Product getProduct(final String productName, final String bandName) {
        for(Product prod : sourceProduct) {
            if(prod.getName().equals(productName)) {
                if(prod.getBand(bandName) != null)
                    return prod;
            }
        }
        return null;
    }

    private static boolean contains(final String[] nameList, final String name) {
        for(String nameInList : nameList) {
            if(name.equals(nameInList))
                return true;
        }
        return false;
    }

    private static String getBandName(final String name) {
        if(name.contains("::"))
            return name.substring(0, name.indexOf("::"));
        return name;
    }

    private String getProductName(final String name) {
        if(name.contains("::"))
            return name.substring(name.indexOf("::")+2, name.length());
        return sourceProduct[0].getName();
    }

    /**
     * Minimum extents consists of the overlapping area
     */
    private void determinMinimumExtents() {

        Geometry mstGeometry = FeatureCollectionClipper.createGeoBoundaryPolygon(masterProduct);

        for(final Product slvProd : sourceProduct) {
            if(slvProd == masterProduct) continue;

            final Geometry slvGeometry = FeatureCollectionClipper.createGeoBoundaryPolygon(slvProd);

            mstGeometry = mstGeometry.intersection(slvGeometry);
        }

        final GeoCoding mstGeoCoding = masterProduct.getGeoCoding();
        final PixelPos pixPos = new PixelPos();
        final GeoPos geoPos = new GeoPos();
        final float mstWidth = masterProduct.getSceneRasterWidth();
        final float mstHeight = masterProduct.getSceneRasterHeight();

        float maxX=0, maxY=0;
        float minX = mstWidth;
        float minY = mstHeight;
        for(Coordinate c : mstGeometry.getCoordinates()) {
            //System.out.println("geo "+c.x +", "+ c.y);
            geoPos.setLocation((float)c.y, (float)c.x);
            mstGeoCoding.getPixelPos(geoPos, pixPos);
            //System.out.println("pix "+pixPos.x +", "+ pixPos.y);
            if(pixPos.isValid() && pixPos.x != -1 && pixPos.y != -1) {
                if(pixPos.x < minX) {
                    minX = Math.max(0, pixPos.x);
                }
                if(pixPos.y < minY) {
                    minY = Math.max(0, pixPos.y);
                }
                if(pixPos.x > maxX) {
                    maxX = Math.min(mstWidth, pixPos.x);
                }
                if(pixPos.y > maxY) {
                    maxY = Math.min(mstHeight, pixPos.y);
                }
            }
        }

        final ProductSubsetBuilder subsetReader = new ProductSubsetBuilder();
        final ProductSubsetDef subsetDef = new ProductSubsetDef();
        subsetDef.addNodeNames(masterProduct.getTiePointGridNames());

        subsetDef.setRegion((int)minX, (int)minY, (int)(maxX-minX), (int)(maxY-minY));
        subsetDef.setSubSampling(1, 1);
        subsetDef.setIgnoreMetadata(false);

        try {
            targetProduct = subsetReader.readProductNodes(masterProduct, subsetDef);
            final Band[] bands = targetProduct.getBands();
            for(Band b : bands) {
                targetProduct.removeBand(b);
            }
        } catch (Throwable t) {
            throw new OperatorException(t);
        }
    }

    /**
     * Maximum extents consists of overall area
     */
    private void determinMaximumExtents() throws Exception {
        final OperatorUtils.SceneProperties scnProp = new OperatorUtils.SceneProperties();
        OperatorUtils.computeImageGeoBoundary(sourceProduct, scnProp);

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct[0]);
        if(absRoot == null) {
            throw new OperatorException("Abstract Metadata missing");
        }
        final double rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
        final double azimuthSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_spacing);
        final double pixelSize = Math.min(rangeSpacing, azimuthSpacing);

        OperatorUtils.getSceneDimensions(pixelSize, scnProp);

        int sceneWidth = scnProp.sceneWidth;
        int sceneHeight = scnProp.sceneHeight;
        final double ratio = sceneWidth / (double)sceneHeight;
        long dim = (long) sceneWidth * (long) sceneHeight;
        while (sceneWidth > 0 && sceneHeight > 0 && dim > Integer.MAX_VALUE) {
            sceneWidth -= 1000;
            sceneHeight = (int)(sceneWidth / ratio);
            dim = (long) sceneWidth * (long) sceneHeight;
        }

        targetProduct = new Product(masterProduct.getName(),
                                    masterProduct.getProductType(),
                                    sceneWidth, sceneHeight);

        OperatorUtils.addGeoCoding(targetProduct, scnProp);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        try {
            final Band sourceRaster = sourceRasterMap.get(targetBand);
            final Product srcProduct = sourceRaster.getProduct();

            final PixelPos[] sourcePixelPositions = ProductUtils.computeSourcePixelCoordinates(
                    srcProduct.getGeoCoding(),
                    srcProduct.getSceneRasterWidth(),
                    srcProduct.getSceneRasterHeight(),
                    targetProduct.getGeoCoding(),
                    targetTile.getRectangle());
            final Rectangle sourceRectangle = getBoundingBox(
                    sourcePixelPositions,
                    srcProduct.getSceneRasterWidth(),
                    srcProduct.getSceneRasterHeight());

            collocateSourceBand(sourceRaster, sourceRectangle, sourcePixelPositions, targetTile, pm);
        } catch(Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    private void collocateSourceBand(RasterDataNode sourceBand, Rectangle sourceRectangle, PixelPos[] sourcePixelPositions,
                                     Tile targetTile, ProgressMonitor pm) throws OperatorException {
        pm.beginTask(MessageFormat.format("collocating band {0}", sourceBand.getName()), targetTile.getHeight());
        try {
            final RasterDataNode targetBand = targetTile.getRasterDataNode();
            final Rectangle targetRectangle = targetTile.getRectangle();
            final ProductData trgBuffer = targetTile.getDataBuffer();

            final float noDataValue = (float) targetBand.getGeophysicalNoDataValue();
            final int maxX = targetRectangle.x + targetRectangle.width;
            final int maxY = targetRectangle.y + targetRectangle.height;

            if (sourceRectangle != null) {
                final Product srcProduct = sourceBand.getProduct();
                final int sourceRasterHeight = srcProduct.getSceneRasterHeight();
                final int sourceRasterWidth = srcProduct.getSceneRasterWidth();

                final Resampling resampling;
                if (isFlagBand(sourceBand) || isValidPixelExpressionUsed(sourceBand)) {
                    resampling = Resampling.NEAREST_NEIGHBOUR;
                } else {
                    resampling = selectedResampling;
                }
                final Resampling.Index resamplingIndex = resampling.createIndex();

                final Tile sourceTile = getSourceTile(sourceBand, sourceRectangle, pm);
                final ResamplingRaster resamplingRaster = new ResamplingRaster(sourceTile);

                for (int y = targetRectangle.y, index = 0; y < maxY; ++y) {
                    checkForCancelation(pm);
                    for (int x = targetRectangle.x; x < maxX; ++x, ++index) {
                        final PixelPos sourcePixelPos = sourcePixelPositions[index];

                        final int trgIndex = targetTile.getDataBufferIndex(x, y);
                        if (sourcePixelPos != null) {
                            resampling.computeIndex(sourcePixelPos.x, sourcePixelPos.y,
                                                    sourceRasterWidth, sourceRasterHeight, resamplingIndex);
                            try {
                                float sample = resampling.resample(resamplingRaster, resamplingIndex);
                                if (Float.isNaN(sample)) {
                                    sample = noDataValue;
                                }
                                trgBuffer.setElemDoubleAt(trgIndex, sample);
                            } catch (Exception e) {
                                throw new OperatorException(e.getMessage());
                            }
                        } else {
                            trgBuffer.setElemDoubleAt(trgIndex, noDataValue);
                        }
                    }
                    pm.worked(1);
                }
                sourceTile.getDataBuffer().dispose();
            } else {
                for (int y = targetRectangle.y, index = 0; y < maxY; ++y) {
                    checkForCancelation(pm);
                    for (int x = targetRectangle.x; x < maxX; ++x, ++index) {
                        trgBuffer.setElemDoubleAt(targetTile.getDataBufferIndex(x, y), noDataValue);
                    }
                    pm.worked(1);
                }
            }
        } finally {
            pm.done();
        }
    }

    private static Rectangle getBoundingBox(PixelPos[] pixelPositions, int maxWidth, int maxHeight) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (final PixelPos pixelsPos : pixelPositions) {
            if (pixelsPos != null) {
                final int x = (int) Math.floor(pixelsPos.getX());
                final int y = (int) Math.floor(pixelsPos.getY());

                if (x < minX) {
                    minX = x;
                }
                if (x > maxX) {
                    maxX = x;
                }
                if (y < minY) {
                    minY = y;
                }
                if (y > maxY) {
                    maxY = y;
                }
            }
        }
        if (minX > maxX || minY > maxY) {
            return null;
        }

        minX = Math.max(minX - 2, 0);
        maxX = Math.min(maxX + 2, maxWidth - 1);
        minY = Math.max(minY - 2, 0);
        maxY = Math.min(maxY + 2, maxHeight - 1);

        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static boolean isFlagBand(final RasterDataNode sourceRaster) {
        return (sourceRaster instanceof Band && ((Band) sourceRaster).isFlagBand());
    }

    private static boolean isValidPixelExpressionUsed(final RasterDataNode sourceRaster) {
        final String validPixelExpression = sourceRaster.getValidPixelExpression();
        return validPixelExpression != null && !validPixelExpression.trim().isEmpty();
    }

    private static class ResamplingRaster implements Resampling.Raster {

        private final Tile tile;
        private final boolean usesNoData;
        private final boolean scalingApplied;
        private final double noDataValue;
        private final double geophysicalNoDataValue;
        private final ProductData dataBuffer;

        public ResamplingRaster(final Tile tile) {
            this.tile = tile;
            this.dataBuffer = tile.getDataBuffer();
            final RasterDataNode rasterDataNode = tile.getRasterDataNode();
            this.usesNoData = rasterDataNode.isNoDataValueUsed();
            this.noDataValue = rasterDataNode.getNoDataValue();
            this.geophysicalNoDataValue = rasterDataNode.getGeophysicalNoDataValue();
            this.scalingApplied = rasterDataNode.isScalingApplied();
        }

        public final int getWidth() {
            return tile.getWidth();
        }

        public final int getHeight() {
            return tile.getHeight();
        }

        public final float getSample(final int x, final int y) throws Exception {
            final double sample = dataBuffer.getElemDoubleAt(tile.getDataBufferIndex(x, y));

            if (usesNoData) {
                if(scalingApplied && geophysicalNoDataValue == sample)
                    return Float.NaN;
                else if(noDataValue == sample)
                    return Float.NaN;
            }

            return (float) sample;
        }
    }

    // for unit test
    protected void setTestParameters(final String ext) {
        extent = ext;
    }

    /**
     * Operator SPI.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CreateStackOp.class);
        }
    }
}