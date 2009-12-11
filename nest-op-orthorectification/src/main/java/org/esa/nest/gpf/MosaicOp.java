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

    private final SceneProperties scnProp = new SceneProperties();
    private final static Map<Product, Band> srcBandMap = new HashMap<Product, Band>(10);
    private final static Map<Product, Rectangle> srcRectMap = new HashMap<Product, Rectangle>(10);

    private static final double MeanEarthRadius = 6371008.7714; // in m (WGS84)

    private Resampling resampling = null;

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
            for (Band srcBand : srcBands) {
                srcBandMap.put(srcBand.getProduct(), srcBand);
            }

            resampling = ResamplingFactory.createResampling(resamplingMethod);

            computeImageGeoBoundary(sourceProduct, scnProp);

            if (sceneWidth == 0 || sceneHeight == 0) {

                final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct[0]);

                if(pixelSize == 0) {
                    final double rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
                    final double azimuthSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_spacing);
                    final double minSpacing = Math.min(rangeSpacing, azimuthSpacing);
                    getSceneDimensions(minSpacing, scnProp);
                } else {
                    getSceneDimensions(pixelSize, scnProp);
                }

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

            addGeoCoding(scnProp);

            final Band targetBand = new Band("mosaic", ProductData.TYPE_FLOAT32, sceneWidth, sceneHeight);

            targetBand.setUnit(sourceProduct[0].getBandAt(0).getUnit());
            targetBand.setNoDataValue(0);
            targetBand.setNoDataValueUsed(true);
            targetProduct.addBand(targetBand);

            for (Product srcProduct : sourceProduct) {
                final Rectangle srcRect = getSrcRect(targetProduct.getGeoCoding(),
                        scnProp.srcCornerLatitudeMap.get(srcProduct),
                        scnProp.srcCornerLongitudeMap.get(srcProduct));
                srcRectMap.put(srcProduct, srcRect);
            }

        } catch (Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
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

    private Product getProduct(String productName) {
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

    /**
     * Compute source image geodetic boundary (minimum/maximum latitude/longitude) from the its corner
     * latitude/longitude.
     */
    public static void computeImageGeoBoundary(final Product[] sourceProducts, final SceneProperties scnProp) {

        scnProp.latMin = 90.0;
        scnProp.latMax = -90.0;
        scnProp.lonMin = 180.0;
        scnProp.lonMax = -180.0;

        for (final Product srcProd : sourceProducts) {
            final GeoCoding geoCoding = srcProd.getGeoCoding();
            final GeoPos geoPosFirstNear = geoCoding.getGeoPos(new PixelPos(0, 0), null);
            final GeoPos geoPosFirstFar = geoCoding.getGeoPos(new PixelPos(srcProd.getSceneRasterWidth() - 1, 0), null);
            final GeoPos geoPosLastNear = geoCoding.getGeoPos(new PixelPos(0, srcProd.getSceneRasterHeight() - 1), null);
            final GeoPos geoPosLastFar = geoCoding.getGeoPos(new PixelPos(srcProd.getSceneRasterWidth() - 1,
                    srcProd.getSceneRasterHeight() - 1), null);

            final double[] lats = {geoPosFirstNear.getLat(), geoPosFirstFar.getLat(), geoPosLastNear.getLat(), geoPosLastFar.getLat()};
            final double[] lons = {geoPosFirstNear.getLon(), geoPosFirstFar.getLon(), geoPosLastNear.getLon(), geoPosLastFar.getLon()};
            scnProp.srcCornerLatitudeMap.put(srcProd, lats);
            scnProp.srcCornerLongitudeMap.put(srcProd, lons);

            for (double lat : lats) {
                if (lat < scnProp.latMin) {
                    scnProp.latMin = lat;
                }
                if (lat > scnProp.latMax) {
                    scnProp.latMax = lat;
                }
            }

            for (double lon : lons) {
                if (lon < scnProp.lonMin) {
                    scnProp.lonMin = lon;
                }
                if (lon > scnProp.lonMax) {
                    scnProp.lonMax = lon;
                }
            }
        }
    }

    public static void getSceneDimensions(double minSpacing, SceneProperties scnProp) {
        double minAbsLat;
        if (scnProp.latMin * scnProp.latMax > 0) {
            minAbsLat = Math.min(Math.abs(scnProp.latMin), Math.abs(scnProp.latMax)) * org.esa.beam.util.math.MathUtils.DTOR;
        } else {
            minAbsLat = 0.0;
        }
        double delLat = minSpacing / MeanEarthRadius * org.esa.beam.util.math.MathUtils.RTOD;
        double delLon = minSpacing / (MeanEarthRadius * Math.cos(minAbsLat)) * org.esa.beam.util.math.MathUtils.RTOD;
        delLat = Math.min(delLat, delLon);
        delLon = delLat;

        scnProp.sceneWidth = (int) ((scnProp.lonMax - scnProp.lonMin) / delLon) + 1;
        scnProp.sceneHeight = (int) ((scnProp.latMax - scnProp.latMin) / delLat) + 1;
    }

    /**
     * Add geocoding to the target product.
     */
    private void addGeoCoding(SceneProperties scnProp) {

        final float[] latTiePoints = {(float) scnProp.latMax, (float) scnProp.latMax,
                (float) scnProp.latMin, (float) scnProp.latMin};
        final float[] lonTiePoints = {(float) scnProp.lonMin, (float) scnProp.lonMax,
                (float) scnProp.lonMin, (float) scnProp.lonMax};

        final int gridWidth = 10;
        final int gridHeight = 10;

        final float[] fineLatTiePoints = new float[gridWidth * gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, latTiePoints, fineLatTiePoints);

        float subSamplingX = (float) sceneWidth / (gridWidth - 1);
        float subSamplingY = (float) sceneHeight / (gridHeight - 1);

        final TiePointGrid latGrid = new TiePointGrid("latitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLatTiePoints);
        latGrid.setUnit(Unit.DEGREES);

        final float[] fineLonTiePoints = new float[gridWidth * gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, lonTiePoints, fineLonTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid("longitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLonTiePoints, TiePointGrid.DISCONT_AT_180);
        lonGrid.setUnit(Unit.DEGREES);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        targetProduct.addTiePointGrid(latGrid);
        targetProduct.addTiePointGrid(lonGrid);
        targetProduct.setGeoCoding(tpGeoCoding);

        ReaderUtils.createMapGeocoding(targetProduct, projectionName, 0);
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

    private static Rectangle getBoundingBox(PixelPos[] pixelPositions,
                                            int minOffsetX, int minOffsetY, int maxWidth, int maxHeight) {
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

            for (final Product srcProduct : sourceProduct) {
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
            for (int y = targetRect.y; y <= maxY; ++y) {
                for (int x = targetRect.x; x <= maxX; ++x) {
                    pixelPos.x = x + 0.5f;
                    pixelPos.y = y + 0.5f;
                    targetGeoCoding.getGeoPos(pixelPos, geoPos);

                    int index = 0;
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

            final ArrayList<SourceData> validSourceData = new ArrayList<SourceData>(validProducts.size());
            int index = 0;
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

                    validSourceData.add(new SourceData(srcBand, sourceRectangle, pixPos, resampling,
                                                        min, max, mean));
                }
                ++index;
            }

            if(!validSourceData.isEmpty()) {
                collocateSourceBand(validSourceData, targetTile, pm);
            }
        } catch (Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        } finally {
            pm.done();
        }
    }

    private void collocateSourceBand(ArrayList<SourceData> validSourceData,
                                     Tile targetTile, ProgressMonitor pm) throws OperatorException {
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
                checkForCancelation(pm);
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

        } catch (Exception e) {
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

    public static class SceneProperties {
        int sceneWidth, sceneHeight;
        double latMin, lonMin, latMax, lonMax;

        final Map<Product, double[]> srcCornerLatitudeMap = new HashMap<Product, double[]>(10);
        final Map<Product, double[]> srcCornerLongitudeMap = new HashMap<Product, double[]>(10);
    }

    private static class SourceData {
        final Band srcBand;
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

        public SourceData(Band sourceBand, Rectangle sourceRectangle, PixelPos[] pixPos, Resampling resampling,
                          double min, double max, double mean) {
            srcBand = sourceBand;
            srcTile = getSourceTile(sourceBand, sourceRectangle, ProgressMonitor.NULL);
            resamplingRaster = new ResamplingRaster(srcTile);
            resamplingIndex = resampling.createIndex();
            nodataValue = sourceBand.getNoDataValue();
            srcPixPos = pixPos;

            final Product srcProduct = sourceBand.getProduct();
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