package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProducts;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.datamodel.AbstractMetadata;

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
                  description = "Collocates two or more products based on their geo-codings.")
public class CreateStackOp extends Operator {

    private static final String NEAREST_NEIGHBOUR = "NEAREST_NEIGHBOUR";
    private static final String BILINEAR_INTERPOLATION = "BILINEAR_INTERPOLATION";
    private static final String CUBIC_CONVOLUTION = "CUBIC_CONVOLUTION";
    private static final String NONE = "NONE";

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

    @Parameter(valueSet = {NEAREST_NEIGHBOUR, BILINEAR_INTERPOLATION, CUBIC_CONVOLUTION},
               defaultValue = NEAREST_NEIGHBOUR, description = "The method to be used when resampling the slave grid onto the master grid.",
               label="Resampling Type")
    private String resamplingType = NEAREST_NEIGHBOUR;

    private final static Map<Band, Band> sourceRasterMap = new HashMap<Band, Band>(10);

    @Override
    public void initialize() throws OperatorException {

        for(final Product prod : sourceProduct) {
            if (prod.getGeoCoding() == null) {
                throw new OperatorException(
                        MessageFormat.format("Product ''{0}'' has no geo-coding.", prod.getName()));
            }
        }
        
        if(masterBandNames.length == 0) {
            final Product defaultProd = sourceProduct[0];
            if(defaultProd != null) {
                final Band defaultBand = defaultProd.getBandAt(0);
                if(defaultBand != null) {
                    if(defaultBand.getUnit().equals(Unit.REAL))
                        masterBandNames = new String[] { defaultProd.getBandAt(0).getName(),
                                                         defaultProd.getBandAt(1).getName() };
                    else
                        masterBandNames = new String[] { defaultProd.getBandAt(0).getName() };
                }
            }
            if(masterBandNames.length == 0) {
                targetProduct = new Product("tmp", "tmp", 1, 1);
                targetProduct.addBand(new Band("tmp", ProductData.TYPE_INT8, 1, 1));
                AbstractMetadata.addAbstractedMetadataHeader(targetProduct.getMetadataRoot());
                return;
            }
        }

        masterProduct = getMasterProduct(masterBandNames[0]);

        final Band[] slaveBandList = getSlaveBands();
        if(masterProduct == null || slaveBandList.length == 0 || slaveBandList[0] == null) {
            targetProduct = new Product("tmp", "tmp", 1, 1);
            targetProduct.addBand(new Band("tmp", ProductData.TYPE_INT8, 1, 1));
            AbstractMetadata.addAbstractedMetadataHeader(targetProduct.getMetadataRoot());
            return;
        }

        targetProduct = new Product(masterProduct.getName(),
                                    masterProduct.getProductType(),
                                    masterProduct.getSceneRasterWidth(),
                                    masterProduct.getSceneRasterHeight());

        OperatorUtils.copyProductNodes(masterProduct, targetProduct);

        String suffix = "_mst";
        // add master bands first
        for (final Band srcBand : slaveBandList) {
            if(srcBand == masterBands[0] || (masterBands.length > 1 && srcBand == masterBands[1])) {
                suffix = "_mst";
                final Band targetBand = targetProduct.addBand(srcBand.getName() + suffix, srcBand.getDataType());
                ProductUtils.copyRasterDataNodeProperties(srcBand, targetBand);
                sourceRasterMap.put(targetBand, srcBand);
            }
        }
        // then add slave bands
        int cnt = 1;
        suffix = "_slv";
        for (final Band srcBand : slaveBandList) {
            if(!(srcBand == masterBands[0] || (masterBands.length > 1 && srcBand == masterBands[1]))) {
                if(srcBand.getUnit() != null && srcBand.getUnit().equals(Unit.IMAGINARY)) {
                } else {
                    suffix = "_slv" + cnt++;
                }
                final Band targetBand = targetProduct.addBand(srcBand.getName() + suffix, srcBand.getDataType());
                ProductUtils.copyRasterDataNodeProperties(srcBand, targetBand);
                sourceRasterMap.put(targetBand, srcBand);
            }
        }

        // copy slave abstracted metadata
        copySlaveMetadata();

        // copy GCPs if found to master band
        final ProductNodeGroup<Pin> masterGCPgroup = masterProduct.getGcpGroup();
        if (masterGCPgroup.getNodeCount() > 0) {
            OperatorUtils.copyGCPsToTarget(masterGCPgroup, targetProduct.getGcpGroup(targetProduct.getBandAt(0)));
        }
    }

    private void copySlaveMetadata() {
        final MetadataElement targetRoot = targetProduct.getMetadataRoot();
        MetadataElement targetSlaveMetadataRoot = targetRoot.getElement("Slave Metadata");
        if(targetSlaveMetadataRoot == null) {
            targetSlaveMetadataRoot = new MetadataElement("Slave Metadata");
            targetRoot.addElement(targetSlaveMetadataRoot);
        }
        for(Product prod : sourceProduct) {
            if(prod != masterProduct) {
                final MetadataElement targetSlaveMetadata = new MetadataElement(prod.getName());
                targetSlaveMetadataRoot.addElement(targetSlaveMetadata);
                ProductUtils.copyMetadata(AbstractMetadata.getAbstractedMetadata(prod), targetSlaveMetadata);
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
        if(unit == null) {
            throw new OperatorException("band " + masterBands[0].getName() + " requires a unit");
        } else if (unit.contains(Unit.PHASE)) {
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

        // add slave bands
        for(int i = 0; i < slaveBandNames.length; i++) {
            final String name = slaveBandNames[i];
            if(contains(masterBandNames, name)) {
                continue;
            }
            final String bandName = getBandName(name);
            final String productName = getProductName(name);

            final Product prod = getProduct(productName);
            final Band band = prod.getBand(bandName);
            final String bandUnit = band.getUnit();
            if(bandUnit == null) {
                throw new OperatorException("band " + bandName + " requires a unit");
            } else if (bandUnit.contains(Unit.PHASE)) {
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
        }
        return bandList.toArray(new Band[bandList.size()]);
    }

    private Product getProduct(String productName) {
        for(Product prod : sourceProduct) {
            if(prod.getName().equals(productName)) {
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

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Band sourceRaster = sourceRasterMap.get(targetBand);
        final Product srcProduct = sourceRaster.getProduct();

        if (srcProduct == masterProduct || srcProduct.isCompatibleProduct(masterProduct, 1.0e-3f)) {
            targetTile.setRawSamples(getSourceTile(sourceRaster, targetTile.getRectangle(), pm).getRawSamples());
        } else {
            final PixelPos[] sourcePixelPositions = ProductUtils.computeSourcePixelCoordinates(
                    srcProduct.getGeoCoding(),
                    srcProduct.getSceneRasterWidth(),
                    srcProduct.getSceneRasterHeight(),
                    masterProduct.getGeoCoding(),
                    targetTile.getRectangle());
            final Rectangle sourceRectangle = getBoundingBox(
                    sourcePixelPositions,
                    srcProduct.getSceneRasterWidth(),
                    srcProduct.getSceneRasterHeight());

            collocateSourceBand(sourceRaster, sourceRectangle, sourcePixelPositions, targetTile, pm);
        }
    }

    private void collocateSourceBand(RasterDataNode sourceBand, Rectangle sourceRectangle, PixelPos[] sourcePixelPositions,
                                     Tile targetTile, ProgressMonitor pm) throws OperatorException {
        pm.beginTask(MessageFormat.format("collocating band {0}", sourceBand.getName()), targetTile.getHeight());
        try {
            final RasterDataNode targetBand = targetTile.getRasterDataNode();
            final Rectangle targetRectangle = targetTile.getRectangle();

            final Product srcProduct = sourceBand.getProduct();
            final int sourceRasterHeight = srcProduct.getSceneRasterHeight();
            final int sourceRasterWidth = srcProduct.getSceneRasterWidth();
            final ProductData trgBuffer = targetTile.getDataBuffer();

            final Resampling resampling;
            if (isFlagBand(sourceBand) || isValidPixelExpressionUsed(sourceBand)) {
                resampling = Resampling.NEAREST_NEIGHBOUR;
            } else {
                if(resamplingType.equals(NEAREST_NEIGHBOUR))
                    resampling = Resampling.NEAREST_NEIGHBOUR;
                else if(resamplingType.equals(BILINEAR_INTERPOLATION))
                    resampling = Resampling.BILINEAR_INTERPOLATION;
                else
                    resampling = (Resampling.CUBIC_CONVOLUTION);
            }
            final Resampling.Index resamplingIndex = resampling.createIndex();
            final float noDataValue = (float) targetBand.getGeophysicalNoDataValue();

            if (sourceRectangle != null) {
                final Tile sourceTile = getSourceTile(sourceBand, sourceRectangle, pm);
                final ResamplingRaster resamplingRaster = new ResamplingRaster(sourceTile);

                for (int y = targetRectangle.y, index = 0; y < targetRectangle.y + targetRectangle.height; ++y) {
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; ++x, ++index) {
                        checkForCancelation(pm);
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
            } else {
                for (int y = targetRectangle.y, index = 0; y < targetRectangle.y + targetRectangle.height; ++y) {
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; ++x, ++index) {
                        checkForCancelation(pm);
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
        private final RasterDataNode rasterDataNode;
        private final ProductData dataBuffer;

        public ResamplingRaster(final Tile tile) {
            this.tile = tile;
            this.dataBuffer = tile.getDataBuffer();
            this.rasterDataNode = tile.getRasterDataNode();
            this.usesNoData = rasterDataNode.isNoDataValueUsed();
        }

        public final int getWidth() {
            return tile.getWidth();
        }

        public final int getHeight() {
            return tile.getHeight();
        }

        public final float getSample(final int x, final int y) throws Exception {
            final double sample = dataBuffer.getElemDoubleAt(tile.getDataBufferIndex(x, y));

            if (usesNoData && isNoDataValue(rasterDataNode, sample)) {
                return Float.NaN;
            }

            return (float) sample;
        }

        private static boolean isNoDataValue(final RasterDataNode rasterDataNode, final double sample) {
            if (rasterDataNode.isScalingApplied())
                return rasterDataNode.getGeophysicalNoDataValue() == sample;
            return rasterDataNode.getNoDataValue() == sample;
        }
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