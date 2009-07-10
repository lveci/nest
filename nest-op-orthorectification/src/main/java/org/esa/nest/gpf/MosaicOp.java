package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProducts;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.dataio.ReaderUtils;

import java.awt.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The Mosaic operator.
 *
 */
@OperatorMetadata(alias = "Mosaic",
                  description = "Mosaics two or more products based on their geo-codings.")
public class MosaicOp extends Operator {

    static final String NEAREST_NEIGHBOUR = "NEAREST_NEIGHBOUR";
    static final String BILINEAR_INTERPOLATION = "BILINEAR_INTERPOLATION";
    static final String CUBIC_CONVOLUTION = "CUBIC_CONVOLUTION";
    static final String NONE = "NONE";

    @SourceProducts
    private Product[] sourceProduct;
    @TargetProduct
    private Product targetProduct = null;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    private String[] sourceBandNames = null;

    @Parameter(valueSet = {NEAREST_NEIGHBOUR, BILINEAR_INTERPOLATION, CUBIC_CONVOLUTION},
               defaultValue = NEAREST_NEIGHBOUR, description = "The method to be used when resampling the slave grid onto the master grid.",
               label="Resampling Type")
    private String resamplingMethod = NEAREST_NEIGHBOUR;

    @Parameter(defaultValue = "false", description = "Average the overlapping areas", label="Average Overlap")
    private boolean average = false;

    @Parameter(defaultValue = "0", description = "Pixel Size X (deg)", label="Pixel Size X (deg)")
    private float pixelSizeX = 0;
    @Parameter(defaultValue = "0", description = "Pixel Size Y (deg)", label="Pixel Size Y (deg)")
    private float pixelSizeY = 0;
    @Parameter(defaultValue = "0", description = "Target width", label="Scene Width")
    private int sceneWidth = 0;
    @Parameter(defaultValue = "0", description = "Target height", label="Scene Height")
    private int sceneHeight = 0;

    private final static Map<Product, double[]> srcCornerLatitudeMap = new HashMap<Product, double[]>(10);
    private final static Map<Product, double[]> srcCornerLongitudeMap = new HashMap<Product, double[]>(10);
    private final static Map<Product, Band> srcBandMap = new HashMap<Product, Band>(10);

    private static final double MeanEarthRadius = 6371008.7714; // in m (WGS84)

    private double latMin = 0.0;
    private double latMax = 0.0;
    private double lonMin = 0.0;
    private double lonMax = 0.0;

    private Resampling resampling = null;
    private Resampling.Index resamplingIndex = null;

    @Override
    public void initialize() throws OperatorException {
        try {
            for(final Product prod : sourceProduct) {
                if (prod.getGeoCoding() == null) {
                    throw new OperatorException(
                            MessageFormat.format("Product ''{0}'' has no geo-coding.", prod.getName()));
                }
            }

            final Band[] srcBands = getSourceBands();
            for(Band srcBand : srcBands) {
                srcBandMap.put(srcBand.getProduct(), srcBand);
            }
     
            if (resamplingMethod.equals(NEAREST_NEIGHBOUR))
                resampling = Resampling.NEAREST_NEIGHBOUR;
            else if (resamplingMethod.equals(BILINEAR_INTERPOLATION))
                resampling = Resampling.BILINEAR_INTERPOLATION;
            else
                resampling = (Resampling.CUBIC_CONVOLUTION);
            resamplingIndex = resampling.createIndex();

            final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct[0]);

            double rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
            double azimuthSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_spacing);

            computeImageGeoBoundary();

            final double minSpacing = Math.min(rangeSpacing, azimuthSpacing);
            double minAbsLat;
            if (latMin*latMax > 0) {
                minAbsLat = Math.min(Math.abs(latMin), Math.abs(latMax)) * org.esa.beam.util.math.MathUtils.DTOR;
            } else {
                minAbsLat = 0.0;
            }
            double delLat = minSpacing / MeanEarthRadius * org.esa.beam.util.math.MathUtils.RTOD;
            double delLon = minSpacing / (MeanEarthRadius * Math.cos(minAbsLat)) * org.esa.beam.util.math.MathUtils.RTOD;
            delLat = Math.min(delLat, delLon);
            delLon = delLat;

            sceneWidth = (int)((lonMax - lonMin)/ delLon) + 1;
            sceneHeight = (int)((latMax - latMin)/ delLat) + 1;

            sceneWidth /= 2;
            sceneHeight /= 2;
            
            targetProduct = new Product("mosiac", "mosiac",
                    sceneWidth,
                    sceneHeight);

            addGeoCoding();

            final Band targetBand = new Band("mosaic",
                                                 ProductData.TYPE_FLOAT32,
                    sceneWidth,
                    sceneHeight);

            targetBand.setUnit(sourceProduct[0].getBandAt(0).getUnit());
            targetBand.setNoDataValue(0);
            targetBand.setNoDataValueUsed(true);
            targetProduct.addBand(targetBand);

        } catch(Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    private Band[] getSourceBands() throws OperatorException {
        final ArrayList<Band> bandList = new ArrayList<Band>(5);

        if(sourceBandNames.length == 0) {
            for(Product slvProduct : sourceProduct) {

                for(Band band : slvProduct.getBands()) {
                    if(band.getUnit() != null && band.getUnit().equals(Unit.PHASE))
                        continue;
                    if(band instanceof VirtualBand)
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
        for(Product prod : sourceProduct) {
            if(prod.getName().equals(productName)) {
                return prod;
            }
        }
        return null;
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
     * Compute source image geodetic boundary (minimum/maximum latitude/longitude) from the its corner
     * latitude/longitude.
     */
    private void computeImageGeoBoundary() {

        latMin = 90.0;
        latMax = -90.0;
        lonMin = 180.0;
        lonMax = -180.0;

        for(final Product srcProd : sourceProduct) {
            final GeoCoding geoCoding = srcProd.getGeoCoding();
            final GeoPos geoPosFirstNear = geoCoding.getGeoPos(new PixelPos(0,0), null);
            final GeoPos geoPosFirstFar = geoCoding.getGeoPos(new PixelPos(srcProd.getSceneRasterWidth()-1,0), null);
            final GeoPos geoPosLastNear = geoCoding.getGeoPos(new PixelPos(0,srcProd.getSceneRasterHeight()-1), null);
            final GeoPos geoPosLastFar = geoCoding.getGeoPos(new PixelPos(srcProd.getSceneRasterWidth()-1,
                                                                          srcProd.getSceneRasterHeight()-1), null);

            final double[] lats  = {geoPosFirstNear.getLat(), geoPosFirstFar.getLat(), geoPosLastNear.getLat(), geoPosLastFar.getLat()};
            final double[] lons  = {geoPosFirstNear.getLon(), geoPosFirstFar.getLon(), geoPosLastNear.getLon(), geoPosLastFar.getLon()};
            srcCornerLatitudeMap.put(srcProd, lats);
            srcCornerLongitudeMap.put(srcProd, lons);

            for (double lat : lats) {
                if (lat < latMin) {
                    latMin = lat;
                }
                if (lat > latMax) {
                    latMax = lat;
                }
            }

            for (double lon : lons) {
                if (lon < lonMin) {
                    lonMin = lon;
                }
                if (lon > lonMax) {
                    lonMax = lon;
                }
            }
        }
    }

    /**
     * Add geocoding to the target product.
     */
    private void addGeoCoding() {

        final float[] latTiePoints = {(float)latMax, (float)latMax, (float)latMin, (float)latMin};
        final float[] lonTiePoints = {(float)lonMin, (float)lonMax, (float)lonMin, (float)lonMax};

        final int gridWidth = 10;
        final int gridHeight = 10;

        final float[] fineLatTiePoints = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, latTiePoints, fineLatTiePoints);

        float subSamplingX = (float) sceneWidth / (gridWidth - 1);
        float subSamplingY = (float) sceneHeight / (gridHeight - 1);

        final TiePointGrid latGrid = new TiePointGrid("latitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLatTiePoints);
        latGrid.setUnit(Unit.DEGREES);

        final float[] fineLonTiePoints = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, lonTiePoints, fineLonTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid("longitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLonTiePoints, TiePointGrid.DISCONT_AT_180);
        lonGrid.setUnit(Unit.DEGREES);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        targetProduct.addTiePointGrid(latGrid);
        targetProduct.addTiePointGrid(lonGrid);
        targetProduct.setGeoCoding(tpGeoCoding);

        ReaderUtils.createMapGeocoding(targetProduct, 0);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetRect = targetTile.getRectangle();
        final GeoCoding targetGeoCoding = targetProduct.getGeoCoding();
        final ArrayList<Product> validProducts = new ArrayList<Product>(sourceProduct.length);
        
        for (final Product srcProduct : sourceProduct) {
            if(!isWithinTile(srcProduct, targetRect, targetGeoCoding)) {
                continue;
            }
            validProducts.add(srcProduct);
        }
        if(validProducts.isEmpty())
            return;

        final ArrayList<PixelPos[]> srcPixelCoords = new ArrayList<PixelPos[]>(validProducts.size());
        for (Product validProduct : validProducts) {
            srcPixelCoords.add(new PixelPos[targetRect.width * targetRect.height]);
        }
        
        final GeoPos geoPos = new GeoPos();
        final PixelPos pixelPos = new PixelPos();
        final int minX = targetRect.x;
        final int minY = targetRect.y;
        final int maxX = minX + targetRect.width - 1;
        final int maxY = minY + targetRect.height - 1;        

        int coordIndex = 0;
        for (int y = minY; y <= maxY; ++y) {
            for (int x = minX; x <= maxX; ++x) {
                pixelPos.x = x + 0.5f;
                pixelPos.y = y + 0.5f;
                targetGeoCoding.getGeoPos(pixelPos, geoPos);

                int index = 0;
                for(Product srcProduct : validProducts) {
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

        int index = 0;
        pm.beginTask("Mosaicking...", validProducts.size());
        for(Product srcProduct : validProducts) {
            final Rectangle sourceRectangle = getBoundingBox(
                    srcPixelCoords.get(index), 0, 0,
                    srcProduct.getSceneRasterWidth(),
                    srcProduct.getSceneRasterHeight());

            if(sourceRectangle != null) {
                final Band srcBand = srcBandMap.get(srcProduct);
                collocateSourceBand(srcBand, sourceRectangle, srcPixelCoords.get(index), targetTile,
                                    SubProgressMonitor.create(pm, 1));
            }
            ++index;
        }
        pm.done();
    }

    private static boolean isWithinTile(final Product srcProduct, final Rectangle destArea, final GeoCoding destGeoCoding) {

        final double[] lats = srcCornerLatitudeMap.get(srcProduct);
        final double[] lons = srcCornerLongitudeMap.get(srcProduct);

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
        geoPos.setLocation((float)srcLatMin, (float)srcLonMin);
        pixelPos[0] = destGeoCoding.getPixelPos(geoPos, null);
        geoPos.setLocation((float)srcLatMin, (float)srcLonMax);
        pixelPos[1] = destGeoCoding.getPixelPos(geoPos, null);
        geoPos.setLocation((float)srcLatMax, (float)srcLonMax);
        pixelPos[2] = destGeoCoding.getPixelPos(geoPos, null);
        geoPos.setLocation((float)srcLatMax, (float)srcLonMin);
        pixelPos[3] = destGeoCoding.getPixelPos(geoPos, null);
        final Rectangle srcRect = getBoundingBox(pixelPos, 0, 0,
                                                Integer.MAX_VALUE, Integer.MAX_VALUE);
        return srcRect != null && srcRect.intersects(destArea);
    }

    private void collocateSourceBand(RasterDataNode sourceBand, Rectangle sourceRectangle, PixelPos[] sourcePixelPositions,
                                     Tile targetTile, ProgressMonitor pm) throws OperatorException {
        pm.beginTask(MessageFormat.format("collocating band {0}", sourceBand.getName()), targetTile.getHeight());
        try {
            final Rectangle targetRectangle = targetTile.getRectangle();

            final Product srcProduct = sourceBand.getProduct();
            final int sourceRasterHeight = srcProduct.getSceneRasterHeight();
            final int sourceRasterWidth = srcProduct.getSceneRasterWidth();
            final ProductData trgBuffer = targetTile.getDataBuffer();

            final Tile sourceTile = getSourceTile(sourceBand, sourceRectangle, pm);
            final ResamplingRaster resamplingRaster = new ResamplingRaster(sourceTile);
            final double nodataValue = sourceBand.getNoDataValue();
            float sample;
            double targetVal;

            for (int y = targetRectangle.y, index = 0; y < targetRectangle.y + targetRectangle.height; ++y) {
                checkForCancelation(pm);
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; ++x, ++index) {
                    final PixelPos sourcePixelPos = sourcePixelPositions[index];

                    if (sourcePixelPos != null) {
                        resampling.computeIndex(sourcePixelPos.x, sourcePixelPos.y,
                                sourceRasterWidth, sourceRasterHeight, resamplingIndex);

                        sample = resampling.resample(resamplingRaster, resamplingIndex);
                        if (!Float.isNaN(sample)) {
                            final int trgIndex = targetTile.getDataBufferIndex(x, y);
                            if(average) {
                                targetVal = trgBuffer.getElemDoubleAt(trgIndex);
                                if(targetVal != nodataValue) {
                                    sample = (float)((sample+targetVal) / 2f);    
                                }
                                trgBuffer.setElemDoubleAt(trgIndex, sample);
                            } else {
                                trgBuffer.setElemDoubleAt(trgIndex, sample);
                            }
                        }
                    }
                }
                pm.worked(1);
            }
        } catch (Exception e) {
            throw new OperatorException(e.getMessage());
        } finally {
            pm.done();
        }
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
            super(MosaicOp.class);
            super.setOperatorUI(MosaicOpUI.class);
        }
    }
}