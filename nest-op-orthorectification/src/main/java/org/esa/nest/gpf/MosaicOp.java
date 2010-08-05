package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.framework.dataop.maptransf.IdentityTransformDescriptor;
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
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.Constants;

import java.awt.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The Mosaic operator.
 */
@OperatorMetadata(alias = "Mosaic",
        category = "Geometry",
        description = "Mosaics two or more products based on their geo-codings.")
public class MosaicOp extends Operator {

    @SourceProducts
    private Product[] sourceProduct;
    @TargetProduct
    private Product targetProduct = null;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId = "source", label = "Source Bands")
    private String[] sourceBandNames = null;

    @Parameter(valueSet = {ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
            ResamplingFactory.BILINEAR_INTERPOLATION_NAME, ResamplingFactory.CUBIC_CONVOLUTION_NAME},
            defaultValue = ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
            description = "The method to be used when resampling the slave grid onto the master grid.",
            label = "Resampling Type")
    private String resamplingMethod = ResamplingFactory.NEAREST_NEIGHBOUR_NAME;

    //@Parameter(description = "The projection name", defaultValue = IdentityTransformDescriptor.NAME)
    private String projectionName = IdentityTransformDescriptor.NAME;

    @Parameter(defaultValue = "false", description = "Average the overlapping areas", label = "Average Overlap")
    private boolean average = true;
    @Parameter(defaultValue = "false", description = "Normalize by Mean", label = "Normalize by Mean")
    private boolean normalizeByMean = false;

    @Parameter(defaultValue = "0", description = "Pixel Size (m)", label = "Pixel Size (m)")
    private double pixelSize = 0;
    @Parameter(defaultValue = "0", description = "Target width", label = "Scene Width (pixels)")
    private int sceneWidth = 0;
    @Parameter(defaultValue = "0", description = "Target height", label = "Scene Height (pixels)")
    private int sceneHeight = 0;

    private final OperatorUtils.SceneProperties scnProp = new OperatorUtils.SceneProperties();
    private final Map<Product, Band> srcBandMap = new HashMap<Product, Band>(10);
    private final Map<Product, Rectangle> srcRectMap = new HashMap<Product, Rectangle>(10);
    private Product[] selectedProducts = null;

    @Override
    public void initialize() throws OperatorException {
        try {
            for (final Product prod : sourceProduct) {
                if (prod.getGeoCoding() == null) {
                    throw new OperatorException(
                            MessageFormat.format("Product ''{0}'' has no geo-coding.", prod.getName()));
                }
            }

            final Band[] srcBands = getSourceBands();
            final ArrayList<Product> selectedProductList = new ArrayList<Product>();
            for (Band srcBand : srcBands) {
                srcBandMap.put(srcBand.getProduct(), srcBand);
                selectedProductList.add(srcBand.getProduct());
            }
            selectedProducts = selectedProductList.toArray(new Product[selectedProductList.size()]);

            OperatorUtils.computeImageGeoBoundary(selectedProducts, scnProp);

            if (sceneWidth == 0 || sceneHeight == 0) {

                final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct[0]);

                if(pixelSize == 0 && absRoot != null) {
                    final double rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
                    final double azimuthSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_spacing);
                    pixelSize = Math.min(rangeSpacing, azimuthSpacing);
                }
                OperatorUtils.getSceneDimensions(pixelSize, scnProp);

                sceneWidth = scnProp.sceneWidth;
                sceneHeight = scnProp.sceneHeight;
                final double ratio = sceneWidth / (double)sceneHeight;
                long dim = (long) sceneWidth * (long) sceneHeight;
                while (sceneWidth > 0 && sceneHeight > 0 && dim > Integer.MAX_VALUE) {
                    sceneWidth -= 1000;
                    sceneHeight = (int)(sceneWidth / ratio);
                    dim = (long) sceneWidth * (long) sceneHeight;
                }
            }

            targetProduct = new Product("mosiac", "mosiac", sceneWidth, sceneHeight);

            OperatorUtils.addGeoCoding(targetProduct, scnProp);
            ReaderUtils.createMapGeocoding(targetProduct, projectionName, 0);

            final Band targetBand = new Band("mosaic", ProductData.TYPE_FLOAT32, sceneWidth, sceneHeight);

            targetBand.setUnit(sourceProduct[0].getBandAt(0).getUnit());
            targetBand.setNoDataValue(0);
            targetBand.setNoDataValueUsed(true);
            targetProduct.addBand(targetBand);

            for (Product srcProduct : selectedProducts) {
                final Rectangle srcRect = getSrcRect(targetProduct.getGeoCoding(),
                        scnProp.srcCornerLatitudeMap.get(srcProduct),
                        scnProp.srcCornerLongitudeMap.get(srcProduct));
                srcRectMap.put(srcProduct, srcRect);
            }

            updateTargetProductMetadata();

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Update metadata in the target product.
     */
    private void updateTargetProductMetadata() {
        MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(targetProduct);
        if(absRoot == null) {
            absRoot = AbstractMetadata.addAbstractedMetadataHeader(targetProduct.getMetadataRoot());
        }
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing, pixelSize);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing, pixelSize);
    }

    private Band[] getSourceBands() throws OperatorException {
        final ArrayList<Band> bandList = new ArrayList<Band>(5);

        if (sourceBandNames.length == 0) {
            for (Product slvProduct : sourceProduct) {

                for (Band band : slvProduct.getBands()) {
                    if (band.getUnit() != null && band.getUnit().equals(Unit.PHASE))
                        continue;
                    if (band instanceof VirtualBand)
                        continue;
                    bandList.add(band);
                    break;
                }
            }
        } else {

            for (final String name : sourceBandNames) {
                final String bandName = getBandName(name);
                final String productName = getProductName(name);

                final Product prod = getProduct(productName);
                final Band band = prod.getBand(bandName);
                final String bandUnit = band.getUnit();
                if (bandUnit != null) {
                    if (bandUnit.contains(Unit.PHASE)) {
                        throw new OperatorException("Phase bands not handled");
                    } else if (bandUnit.contains(Unit.IMAGINARY) || bandUnit.contains(Unit.REAL)) {
                        throw new OperatorException("Real and imaginary bands not handled");
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

    private Product getProduct(final String productName) {
        for (Product prod : sourceProduct) {
            if (prod.getName().equals(productName)) {
                return prod;
            }
        }
        return null;
    }

    private static String getBandName(final String name) {
        if (name.contains("::"))
            return name.substring(0, name.indexOf("::"));
        return name;
    }

    private String getProductName(final String name) {
        if (name.contains("::"))
            return name.substring(name.indexOf("::") + 2, name.length());
        return sourceProduct[0].getName();
    }

    private static Rectangle getSrcRect(final GeoCoding destGeoCoding,
                                        final double[] lats, final double[] lons) {

        double srcLatMin = 90.0;
        double srcLatMax = -90.0;
        double srcLonMin = 180.0;
        double srcLonMax = -180.0;

        for (double lat : lats) {
            if (lat < srcLatMin) {
                srcLatMin = lat;
            }
            if (lat > srcLatMax) {
                srcLatMax = lat;
            }
        }

        for (double lon : lons) {
            if (lon < srcLonMin) {
                srcLonMin = lon;
            }
            if (lon > srcLonMax) {
                srcLonMax = lon;
            }
        }

        final GeoPos geoPos = new GeoPos();
        final PixelPos[] pixelPos = new PixelPos[4];
        geoPos.setLocation((float) srcLatMin, (float) srcLonMin);
        pixelPos[0] = destGeoCoding.getPixelPos(geoPos, null);
        geoPos.setLocation((float) srcLatMin, (float) srcLonMax);
        pixelPos[1] = destGeoCoding.getPixelPos(geoPos, null);
        geoPos.setLocation((float) srcLatMax, (float) srcLonMax);
        pixelPos[2] = destGeoCoding.getPixelPos(geoPos, null);
        geoPos.setLocation((float) srcLatMax, (float) srcLonMin);
        pixelPos[3] = destGeoCoding.getPixelPos(geoPos, null);

        return getBoundingBox(pixelPos, 0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private static Rectangle getBoundingBox(final PixelPos[] pixelPositions,
                                            final int minOffsetX, final int minOffsetY,
                                            final int maxWidth, final int maxHeight) {
        int minX = Integer.MAX_VALUE;
        int maxX = -Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = -Integer.MAX_VALUE;

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

        minX = Math.max(minX - 2, minOffsetX);
        maxX = Math.min(maxX + 2, maxWidth - 1);
        minY = Math.max(minY - 2, minOffsetY);
        maxY = Math.min(maxY + 2, maxHeight - 1);

        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        try {
            final Rectangle targetRect = targetTile.getRectangle();
            final ArrayList<Product> validProducts = new ArrayList<Product>(sourceProduct.length);

            for (final Product srcProduct : selectedProducts) {
                final Rectangle srcRect = srcRectMap.get(srcProduct);
                if (srcRect == null || !srcRect.intersects(targetRect)) {
                    continue;
                }
                validProducts.add(srcProduct);
            }
            if (validProducts.isEmpty())
                return;

            final ArrayList<PixelPos[]> srcPixelCoords = new ArrayList<PixelPos[]>(validProducts.size());
            final int numPixelPos = targetRect.width * targetRect.height;
            for (Product validProduct : validProducts) {
                srcPixelCoords.add(new PixelPos[numPixelPos]);
            }

            final GeoCoding targetGeoCoding = targetProduct.getGeoCoding();
            final GeoPos geoPos = new GeoPos();
            final PixelPos pixelPos = new PixelPos();
            final int maxX = targetRect.x + targetRect.width - 1;
            final int maxY = targetRect.y + targetRect.height - 1;

            int coordIndex = 0;
            int index;
            for (int y = targetRect.y; y <= maxY; ++y) {
                for (int x = targetRect.x; x <= maxX; ++x) {
                    pixelPos.x = x + 0.5f;
                    pixelPos.y = y + 0.5f;
                    targetGeoCoding.getGeoPos(pixelPos, geoPos);

                    index = 0;
                    for (Product srcProduct : validProducts) {
                        srcProduct.getGeoCoding().getPixelPos(geoPos, pixelPos);
                        if (pixelPos.x >= 0.0f && pixelPos.y >= 0.0f &&
                                pixelPos.x < srcProduct.getSceneRasterWidth() &&
                                pixelPos.y < srcProduct.getSceneRasterHeight()) {

                            srcPixelCoords.get(index)[coordIndex] = new PixelPos(pixelPos.x, pixelPos.y);
                        } else {
                            srcPixelCoords.get(index)[coordIndex] = null;
                        }
                        ++index;
                    }
                    ++coordIndex;
                }
            }

            final Resampling resampling = ResamplingFactory.createResampling(resamplingMethod);
            final ArrayList<SourceData> validSourceData = new ArrayList<SourceData>(validProducts.size());

            index = 0;
            for (Product srcProduct : validProducts) {
                final PixelPos[] pixPos = srcPixelCoords.get(index);
                final Rectangle sourceRectangle = getBoundingBox(
                        pixPos, 0, 0,
                        srcProduct.getSceneRasterWidth(),
                        srcProduct.getSceneRasterHeight());

                if (sourceRectangle != null) {
                    final Band srcBand = srcBandMap.get(srcProduct);
                    double min = 0, max = 0, mean = 0;
                    if(normalizeByMean) {                  // get stat values
                        final Stx stats = srcBand.getStx();
                        mean = stats.getMean();
                        min = stats.getMin();
                        max = stats.getMax();
                    }

                    final Tile srcTile = getSourceTile(srcBand, sourceRectangle, ProgressMonitor.NULL);
                    if(srcTile != null) {
                        validSourceData.add(new SourceData(srcTile, pixPos, resampling, min, max, mean));
                    }
                }
                ++index;
            }

            if(!validSourceData.isEmpty()) {
                collocateSourceBand(validSourceData, resampling, targetTile, pm);
            }
        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        } finally {
            pm.done();
        }
    }

    private void collocateSourceBand(final ArrayList<SourceData> validSourceData, final Resampling resampling,
                                     final Tile targetTile, final ProgressMonitor pm) throws OperatorException {
        try {
            final Rectangle targetRectangle = targetTile.getRectangle();
            final ProductData trgBuffer = targetTile.getDataBuffer();

            float sample;
            final int maxY = targetRectangle.y + targetRectangle.height;
            final int maxX = targetRectangle.x + targetRectangle.width;

            int cnt = 0;
            double overalMean = 0;
            if (normalizeByMean) {
                double sum = 0;
                for(SourceData srcDat : validSourceData) {
                    sum += srcDat.srcMean;
                    ++cnt;
                }
                overalMean = sum / cnt;
            }

            for (int y = targetRectangle.y, index = 0; y < maxY; ++y) {
                checkForCancellation(pm);
                for (int x = targetRectangle.x; x < maxX; ++x, ++index) {
                    final int trgIndex = targetTile.getDataBufferIndex(x, y);

                    cnt = 0;
                    double targetVal = 0;
                    for(SourceData srcDat : validSourceData) {
                        final PixelPos sourcePixelPos = srcDat.srcPixPos[index];
                        if(sourcePixelPos == null)
                            continue;
                        
                        resampling.computeIndex(sourcePixelPos.x, sourcePixelPos.y,
                                srcDat.srcRasterWidth, srcDat.srcRasterHeight, srcDat.resamplingIndex);
                        sample = resampling.resample(srcDat.resamplingRaster, srcDat.resamplingIndex);

                        if (!Float.isNaN(sample) && sample != srcDat.nodataValue) {

                            if (normalizeByMean) {
                                sample /= srcDat.srcMean;
                            }
                            if (average) {
                                targetVal += sample;
                                ++cnt;
                            } else {
                                targetVal = sample;
                            }
                        }
                    }
                    if(targetVal != 0) {
                        if (average) {
                            targetVal /= cnt;
                        }
                        if(normalizeByMean) {
                            targetVal *= overalMean;
                        }
                        trgBuffer.setElemDoubleAt(trgIndex, targetVal);
                    }
                }
                pm.worked(1);
            }

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
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
                if (scalingApplied && geophysicalNoDataValue == sample)
                    return Float.NaN;
                else if (noDataValue == sample)
                    return Float.NaN;
            }
            return (float) sample;
        }
    }

    private static class SourceData {
        final Tile srcTile;
        final ResamplingRaster resamplingRaster;
        final Resampling.Index resamplingIndex;
        final double nodataValue;
        final PixelPos[] srcPixPos;
        final int srcRasterHeight;
        final int srcRasterWidth;
        final double srcMean;
        final double srcMax;
        final double srcMin;

        public SourceData(final Tile tile,
                          final PixelPos[] pixPos, final Resampling resampling,
                          final double min, final double max, final double mean) {
            srcTile = tile;
            resamplingRaster = new ResamplingRaster(srcTile);
            resamplingIndex = resampling.createIndex();
            nodataValue = tile.getRasterDataNode().getNoDataValue();
            srcPixPos = pixPos;

            final Product srcProduct = tile.getRasterDataNode().getProduct();
            srcRasterHeight = srcProduct.getSceneRasterHeight();
            srcRasterWidth = srcProduct.getSceneRasterWidth();
            srcMin = min;
            srcMax = max;
            srcMean = mean;
        }
    }

    /**
     * Operator SPI.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(MosaicOp.class);
            super.setOperatorUI(MosaicOpUI.class);
        }
    }
}