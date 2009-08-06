/*
 * $Id: PixelGeoCoding.java,v 1.2 2009-08-06 15:21:21 lveci Exp $
 *
 * Copyright (c) 2003 Brockmann Consult GmbH. All right reserved.
 * http://www.brockmann-consult.de
 */
package org.esa.beam.framework.datamodel;

import Jama.LUDecomposition;
import Jama.Matrix;
import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.BitRaster;
import org.esa.beam.util.Debug;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.IndexValidator;

import java.io.IOException;


/**
 * The <code>PixelGeoCoding</code> is an implementation of a {@link GeoCoding} which uses
 * dedicated latitude and longitude bands in order to provide geographical positions
 * for <i>each</i> pixel. Unlike the {@link TiePointGeoCoding}</p>, which uses sub-sampled {@link TiePointGrid tie-point grids},
 * the  <code>PixelGeoCoding</code> class uses {@link Band bands}.</p>
 * <p/>
 * <p>This class is especially useful for high accuracy geo-coding, e.g. if geographical positions are computed for each pixel
 * by an upstream orthorectification.</p>
 * <p/>
 * <p>While the implementation of the {@link #getGeoPos(PixelPos, GeoPos)} is straight forward,
 * the {@link #getPixelPos(GeoPos, PixelPos)} uses two different search algorithms in order to
 * find the corresponding geo-position for a given pixel:
 * <ol>
 * <li>Search an N x N window around an estimated pixel position using the geo-coding of the source product (if any) or</li>
 * <li>perform a quad-tree search if the source product has no geo-coding.</li>
 * </ol></p>
 * <p/>
 * <p><i>Use instances of this class with care: The constructor fully loads the data given by the latitudes and longitudes bands and
 * the valid mask (if any) into memory.</i></p>
 */
public class PixelGeoCoding extends AbstractGeoCoding {

    // TODO - (nf) make EPS for quad-tree search dependent on current scene
    private static final float EPS = 0.04F; // used by quad-tree search
    private static final boolean _trace = false;
    private static final float D2R = (float) (Math.PI / 180.0);

    private Boolean _crossingMeridianAt180;
    private final Band _latBand;
    private final Band _lonBand;
    private final String _validMask;
    private final int _searchRadius; // used by direct search only
    private GeoCoding _pixelPosEstimator;
    private PixelGrid _latGrid;
    private PixelGrid _lonGrid;
    private boolean initialized;

    /**
     * Constructs a new pixel-based geo-coding.
     * <p/>
     * <i>Use with care: In contrast to the other constructor this one loads the data not until first access to
     * {@link #getPixelPos(GeoPos, PixelPos)} or {@link #getGeoPos(PixelPos, GeoPos)}. </i>
     *
     * @param latBand      the band providing the latitudes
     * @param lonBand      the band providing the longitudes
     * @param validMask    the valid mask expression used to identify valid lat/lon pairs, e.g. "NOT l1_flags.DUPLICATED".
     *                     Can be <code>null</code> if a valid mask is not used.
     * @param searchRadius the search radius in pixels, shall depend on the actual spatial scene resolution,
     *                     e.g. for 300 meter pixels a search radius of 5 is a good choice. This parameter is ignored
     *                     if the source product is not geo-coded.
     */
    public PixelGeoCoding(final Band latBand, final Band lonBand, final String validMask, final int searchRadius) {
        Guardian.assertNotNull("latBand", latBand);
        Guardian.assertNotNull("lonBand", lonBand);
        if (latBand.getProduct() == null) {
            throw new IllegalArgumentException("latBand.getProduct() == null");
        }
        if (lonBand.getProduct() == null) {
            throw new IllegalArgumentException("lonBand.getProduct() == null");
        }
        // Note that if two bands are of the same product, they also have the same raster size
        if (latBand.getProduct() != lonBand.getProduct()) {
            throw new IllegalArgumentException("latBand.getProduct() != lonBand.getProduct()");
        }
        if (latBand.getProduct().getSceneRasterWidth() < 2 || latBand.getProduct().getSceneRasterHeight() < 2) {
            throw new IllegalArgumentException(
                    "latBand.getProduct().getSceneRasterWidth() < 2 || latBand.getProduct().getSceneRasterHeight() < 2");
        }
        if (searchRadius <= 0) {
            throw new IllegalArgumentException("searchRadius <= 0");
        }
        _latBand = latBand;
        _lonBand = lonBand;
        _validMask = validMask;
        _searchRadius = searchRadius;
        _pixelPosEstimator = latBand.getProduct().getGeoCoding();
        if (_pixelPosEstimator != null) {
            _crossingMeridianAt180 = _pixelPosEstimator.isCrossingMeridianAt180();
        }
        initialized = false;
    }

    /**
     * Constructs a new pixel-based geo-coding.
     * <p/>
     * <p><i>Use with care: This constructor fully loads the data given by the latitudes and longitudes bands and
     * the valid mask (if any) into memory.</i></p>
     *
     * @param latBand      the band providing the latitudes
     * @param lonBand      the band providing the longitudes
     * @param validMask    the valid mask expression used to identify valid lat/lon pairs, e.g. "NOT l1_flags.DUPLICATED".
     *                     Can be <code>null</code> if a valid mask is not used.
     * @param searchRadius the search radius in pixels, shall depend on the actual spatial scene resolution,
     *                     e.g. for 300 meter pixels a search radius of 5 is a good choice. This parameter is ignored
     *                     if the source product is not geo-coded.
     * @param pm           a monitor to inform the user about progress
     *
     * @throws IOException if an I/O error occurs while additional data is loaded from the source product
     */
    public PixelGeoCoding(final Band latBand, final Band lonBand, final String validMask, final int searchRadius,
                          ProgressMonitor pm) throws IOException {
        this(latBand, lonBand, validMask, searchRadius);
        initData(latBand, lonBand, validMask, pm);
        initialized = true;
    }

    private void initData(final Band latBand, final Band lonBand,
                          final String validMaskExpr, ProgressMonitor pm) throws IOException {
        try {
            pm.beginTask("Preparing data for pixel based geo-coding...", 4);
            _latGrid = PixelGrid.create(latBand, SubProgressMonitor.create(pm, 1));
            _lonGrid = PixelGrid.create(lonBand, SubProgressMonitor.create(pm, 1));
            if (validMaskExpr != null && validMaskExpr.trim().length() > 0) {
                final BitRaster validMask = latBand.getProduct().createValidMask(validMaskExpr,
                                                                                 SubProgressMonitor.create(pm, 1));
                fillInvalidGaps(_latGrid.getRasterWidth(),
                                _latGrid.getRasterHeight(),
                                new RasterDataNode.ValidMaskValidator(_latGrid.getRasterHeight(), 0, validMask),
                                (float[]) _latGrid.getDataElems(),
                                (float[]) _lonGrid.getDataElems(), SubProgressMonitor.create(pm, 1));
            }
        } finally {
            pm.done();
        }
    }

    /**
     * <p>Fills the gaps in the given latitude and longitude data buffers.
     * The method shall fill in reasonable a latitude and longitude value at all positions where
     * {@link IndexValidator#validateIndex(int) validator.validateIndex(pixelIndex)} returns false.</p>
     * <p/>
     * <p>The default implementation uses the underlying {@link #getPixelPosEstimator() estimator} (if any)
     * to find default values for the gaps.</p>
     *
     * @param w         the raster width
     * @param h         the raster height
     * @param validator the pixel validator, never null
     * @param latElems  the latitude data buffer in row-major order
     * @param lonElems  the longitude data buffer in row-major order
     * @param pm        a monitor to inform the user about progress
     */
    protected void fillInvalidGaps(final int w,
                                   final int h,
                                   final IndexValidator validator,
                                   final float[] latElems,
                                   final float[] lonElems, ProgressMonitor pm) {
        if (_pixelPosEstimator != null) {
            try {
                pm.beginTask("Filling invalid pixel gaps", h);
                final PixelPos pixelPos = new PixelPos();
                GeoPos geoPos = new GeoPos();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int i = y * w + x;
                        if (!validator.validateIndex(i)) {
                            pixelPos.x = x;
                            pixelPos.y = y;
                            geoPos = _pixelPosEstimator.getGeoPos(pixelPos, geoPos);
                            latElems[i] = geoPos.lat;
                            lonElems[i] = geoPos.lon;
                        }
                    }
                    pm.worked(1);
                }
            } finally {
                pm.done();
            }
        }
    }

    /**
     * Computes an estimation of the memory required to create an instance of this class for the given product.
     * The estimation is returned in bytes.
     *
     * @return an estimation of the required memory in bytes
     */
    public static long getRequiredMemory(Product product, boolean usesValidMask) {
        final GeoCoding geoCoding = product.getGeoCoding();
        if (geoCoding == null) {
            return 0;
        }
        final long sizeofFloat = 4;
        final long pixelCount = product.getSceneRasterWidth() * product.getSceneRasterHeight();
        // lat + lon band converted to 32-bit float tie-point data
        long size = 2 * sizeofFloat * pixelCount;
        if (geoCoding.isCrossingMeridianAt180()) {
            // additional 32-bit float sine and cosine grids for to lon grid
            size += 2 * sizeofFloat * pixelCount;
        }
        if (usesValidMask) {
            // additional 1-bit data mask
            size += pixelCount / 8;
        }
        return size;
    }

    public Band getLatBand() {
        return _latBand;
    }

    public Band getLonBand() {
        return _lonBand;
    }

    public String getValidMask() {
        return _validMask;
    }

    /**
     * Gets the underlying geo-coding used as pixel position estimator.
     *
     * @return the underlying delegate geo-coding, can be null
     */
    public GeoCoding getPixelPosEstimator() {
        return _pixelPosEstimator;
    }

    /**
     * Gets the search radius used by this geo-coding.
     *
     * @return the search radius in pixels
     */
    public int getSearchRadius() {
        return _searchRadius;
    }

    /**
     * Checks whether or not the longitudes of this geo-coding cross the +/- 180 degree meridian.
     *
     * @return <code>true</code>, if so
     */
    @Override
    public boolean isCrossingMeridianAt180() {
        if (_crossingMeridianAt180 == null) {
            _crossingMeridianAt180 = false;
            final PixelPos[] pixelPoses = ProductUtils.createPixelBoundary(_lonBand, null, 1);
            try {
                float[] firstLonValue = new float[1];
                _lonBand.readPixels(0, 0, 1, 1, firstLonValue);
                float[] secondLonValue = new float[1];
                for (int i = 1; i < pixelPoses.length; i++) {
                    final PixelPos pixelPos = pixelPoses[i];
                    _lonBand.readPixels((int) pixelPos.x, (int) pixelPos.y, 1, 1, secondLonValue);
                    if (Math.abs(firstLonValue[0] - secondLonValue[0]) > 180) {
                        _crossingMeridianAt180 = true;
                        break;
                    }
                    firstLonValue[0] = secondLonValue[0];
                }
            } catch (IOException e) {
                throw new IllegalStateException("raster data is not readable", e);
            }
        }
        return _crossingMeridianAt180;
    }

    /**
     * Checks whether or not this geo-coding can determine the pixel position from a geodetic position.
     *
     * @return <code>true</code>, if so
     */
    @Override
    public boolean canGetPixelPos() {
        return true;
    }

    /**
     * Checks whether or not this geo-coding can determine the geodetic position from a pixel position.
     *
     * @return <code>true</code>, if so
     */
    @Override
    public boolean canGetGeoPos() {
        return true;
    }

    /**
     * Returns the pixel co-ordinates as x/y for a given geographical position given as lat/lon.
     *
     * @param geoPos   the geographical position as lat/lon.
     * @param pixelPos an instance of <code>Point</code> to be used as retun value. If this parameter is
     *                 <code>null</code>, the method creates a new instance which it then returns.
     *
     * @return the pixel co-ordinates as x/y
     */
    @Override
    public PixelPos getPixelPos(final GeoPos geoPos, PixelPos pixelPos) {
        initialize();
        if (pixelPos == null) {
            pixelPos = new PixelPos();
        }
        if (geoPos.isValid()) {
            if (_pixelPosEstimator != null) {
                getPixelPosUsingEstimator(geoPos, pixelPos);
            } else {
                getPixelPosUsingQuadTreeSearch(geoPos, pixelPos);
            }
        } else {
            pixelPos.setInvalid();
        }
        return pixelPos;
    }

    /**
     * Returns the pixel co-ordinates as x/y for a given geographical position given as lat/lon.
     *
     * @param geoPos   the geographical position as lat/lon.
     * @param pixelPos the return value.
     */
    public void getPixelPosUsingEstimator(final GeoPos geoPos, PixelPos pixelPos) {
        initialize();

        pixelPos = _pixelPosEstimator.getPixelPos(geoPos, pixelPos);
        if (!pixelPos.isValid()) {
            getPixelPosUsingQuadTreeSearch(geoPos, pixelPos);
            return;
        }
        final int x0 = (int) Math.floor(pixelPos.x);
        final int y0 = (int) Math.floor(pixelPos.y);
        final int rasterWidth = _latGrid.getSceneRasterWidth();
        final int rasterHeight = _latGrid.getSceneRasterHeight();
        if (x0 >= 0 && x0 < rasterWidth && y0 >= 0 && y0 < rasterHeight) {
            int bestX = -1;
            int bestY = -1;
            int x1 = x0 - _searchRadius;
            int y1 = y0 - _searchRadius;
            int x2 = x0 + _searchRadius;
            int y2 = y0 + _searchRadius;
            x1 = Math.max(x1, 0);
            y1 = Math.max(y1, 0);
            x2 = Math.min(x2, rasterWidth - 1);
            y2 = Math.min(y2, rasterHeight - 1);

            final float[] latArray = (float[]) _latGrid.getRasterData().getElems();
            final float[] lonArray = (float[]) _lonGrid.getRasterData().getElems();
            final float lat0 = geoPos.lat;
            final float lon0 = geoPos.lon;
            int bestCount = 0;


            int i = rasterWidth * y0 + x0;
            float lat = latArray[i];
            float lon = lonArray[i];
            float r = (float) Math.cos(lat * D2R);
            float dlat = Math.abs(lat - lat0);
            float dlon = r * lonDiff(lon, lon0);
            float delta, minDelta = dlat * dlat + dlon * dlon;

            for (int y = y1; y <= y2; y++) {
                for (int x = x1; x <= x2; x++) {
                    if (!(x == x0 && y == y0)) {
                        i = rasterWidth * y + x;
                        lat = latArray[i];
                        lon = lonArray[i];
                        dlat = Math.abs(lat - lat0);
                        dlon = r * lonDiff(lon, lon0);
                        delta = dlat * dlat + dlon * dlon;
                        if (delta < minDelta) {
                            minDelta = delta;
                            bestX = x;
                            bestY = y;
                            bestCount++;
                        }
                    }
                }
            }

            if (Debug.isEnabled()) {
                // trace(x0, y0, bestX, bestY, bestCount);
            }

            if (bestCount > 0 && (bestX != x0 || bestY != y0)) {
                pixelPos.setLocation(bestX + 0.5f, bestY + 0.5f);
            }
        }
    }

    /**
     * Returns the pixel co-ordinates as x/y for a given geographical position given as lat/lon.
     * This algorithm
     *
     * @param geoPos   the geographical position as lat/lon.
     * @param pixelPos the retun value
     */
    public void getPixelPosUsingQuadTreeSearch(final GeoPos geoPos, PixelPos pixelPos) {
        initialize();

        final Result result = new Result();
        final int rasterWidth = _latGrid.getSceneRasterWidth();
        final int rasterHeight = _latGrid.getSceneRasterHeight();

        boolean pixelFound = quadTreeSearch(0,
                                            geoPos.lat, geoPos.lon,
                                            0, 0,
                                            rasterWidth,
                                            rasterHeight,
                                            result);

        if (pixelFound) {
            pixelPos.setLocation(result.x + 0.5f, result.y + 0.5f);
        } else {
            pixelPos.setInvalid();
        }
    }

    private synchronized void initialize() {
        if (!initialized) {
            try {
                initData(_latBand, _lonBand, _validMask, ProgressMonitor.NULL);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to initialse data for pixel geo-coding", e);
            }
            initialized = true;
        }
    }

    /**
     * Returns the latitude and longitude value for a given pixel co-ordinate.
     *
     * @param pixelPos the pixel's co-ordinates given as x,y
     * @param geoPos   an instance of <code>GeoPos</code> to be used as retun value. If this parameter is
     *                 <code>null</code>, the method creates a new instance which it then returns.
     *
     * @return the geographical position as lat/lon.
     */
    @Override
    public GeoPos getGeoPos(final PixelPos pixelPos, GeoPos geoPos) {
        initialize();
        if (geoPos == null) {
            geoPos = new GeoPos();
        }

        if (pixelPos.isValid()) {
            final int x0 = (int) Math.floor(pixelPos.x);
            final int y0 = (int) Math.floor(pixelPos.y);
            final int rasterWidth = _latGrid.getSceneRasterWidth();
            final int rasterHeight = _latGrid.getSceneRasterHeight();
            if (x0 >= 0 && x0 < rasterWidth && y0 >= 0 && y0 < rasterHeight) {
                final float lat = _latGrid.getPixelFloat(x0, y0);
                final float lon = _lonGrid.getPixelFloat(x0, y0);
                geoPos.setLocation(lat, lon);
                return geoPos;
            }
        }

        if (_pixelPosEstimator != null) {
            return _pixelPosEstimator.getGeoPos(pixelPos, geoPos);
        }

        geoPos.setInvalid();
        return geoPos;
    }

    /**
     * Releases all of the resources used by this object instance and all of its owned children. Its primary use is to
     * allow the garbage collector to perform a vanilla job.
     * <p/>
     * <p>This method should be called only if it is for sure that this object instance will never be used again. The
     * results of referencing an instance of this class after a call to <code>dispose()</code> are undefined.
     */
    @Override
    public synchronized void dispose() {
        if (_latGrid != null) {
            _latGrid.dispose();
            _latGrid = null;

            _lonGrid.dispose();
            _lonGrid = null;

            // Don't dispose the estimator, it is not our's!
            _pixelPosEstimator = null;
        }
    }

    private boolean quadTreeSearch(final int depth,
                                   final float lat,
                                   final float lon,
                                   final int x, final int y,
                                   final int w, final int h,
                                   final Result result) {
        if (w < 2 || h < 2) {
            return false;
        }

        final int x1 = x;
        final int x2 = x1 + w - 1;

        final int y1 = y;
        final int y2 = y1 + h - 1;

        final float[] latArray = (float[]) _latGrid.getRasterData().getElems();
        final float[] lonArray = (float[]) _lonGrid.getRasterData().getElems();
        final int lineWidth = _latGrid.getSceneRasterWidth();

        final int lineOffset1 = y1 * lineWidth;
        final int lineOffset2 = y2 * lineWidth;
        final float lat0 = latArray[x1 + lineOffset1];
        final float lat1 = latArray[x1 + lineOffset2];
        final float lat2 = latArray[x2 + lineOffset1];
        final float lat3 = latArray[x2 + lineOffset2];

        // todo - solve 180° longitude problem here
        final float lon0 = lonArray[x1 + lineOffset1];
        final float lon1 = lonArray[x1 + lineOffset2];
        final float lon2 = lonArray[x2 + lineOffset1];
        final float lon3 = lonArray[x2 + lineOffset2];

        final float epsL = EPS;
        final float latMin = min(lat0, min(lat1, min(lat2, lat3))) - epsL;
        final float latMax = max(lat0, max(lat1, max(lat2, lat3))) + epsL;
        final float lonMin = min(lon0, min(lon1, min(lon2, lon3))) - epsL;
        final float lonMax = max(lon0, max(lon1, max(lon2, lon3))) + epsL;

        boolean pixelFound = false;
        final boolean definitelyOutside = lat < latMin || lat > latMax || lon < lonMin || lon > lonMax;
        if (!definitelyOutside) {
            if (w == 2 && h == 2) {
                final float f = (float) Math.cos(lat * D2R);
                if (result.update(x1, y1, sqr(lat - lat0, f * (lon - lon0)))) {
                    pixelFound = true;
                }
                if (result.update(x1, y2, sqr(lat - lat1, f * (lon - lon1)))) {
                    pixelFound = true;
                }
                if (result.update(x2, y1, sqr(lat - lat2, f * (lon - lon2)))) {
                    pixelFound = true;
                }
                if (result.update(x2, y2, sqr(lat - lat3, f * (lon - lon3)))) {
                    pixelFound = true;
                }
            } else if (w >= 2 && h >= 2) {
                pixelFound = quadTreeRecursion(depth, lat, lon, x1, y1, w, h, result);
            }
        }

        if (_trace) {
            for (int i = 0; i < depth; i++) {
                System.out.print("  ");
            }
            System.out.println(
                    depth + ": (" + x + "," + y + ") (" + w + "," + h + ") " + definitelyOutside + "  " + pixelFound);
        }
        return pixelFound;
    }


    private boolean quadTreeRecursion(final int depth,
                                      final float lat, final float lon,
                                      final int i, final int j,
                                      final int w, final int h,
                                      final Result result) {
        int w2 = w >> 1;
        int h2 = h >> 1;
        final int i2 = i + w2;
        final int j2 = j + h2;
        final int w2r = w - w2;
        final int h2r = h - h2;

        if (w2 < 2) {
            w2 = 2;
        }

        if (h2 < 2) {
            h2 = 2;
        }

        final boolean b1 = quadTreeSearch(depth + 1, lat, lon, i, j, w2, h2, result);
        final boolean b2 = quadTreeSearch(depth + 1, lat, lon, i, j2, w2, h2r, result);
        final boolean b3 = quadTreeSearch(depth + 1, lat, lon, i2, j, w2r, h2, result);
        final boolean b4 = quadTreeSearch(depth + 1, lat, lon, i2, j2, w2r, h2r, result);

        return b1 || b2 || b3 || b4;
    }


    private static float min(final float a, final float b) {
        return (a <= b) ? a : b;
    }

    private static float max(final float a, final float b) {
        return (a >= b) ? a : b;
    }

    private static float sqr(final float dx, final float dy) {
        return dx * dx + dy * dy;
    }

    /*
     * Computes the absolute and smaller difference for two angles.
     * @param a1 the first angle in the degrees (-180 <= a1 <= 180)
     * @param a2 the second angle in degrees (-180 <= a2 <= 180)
     * @return the difference between 0 and 180 degrees
     */
    private static float lonDiff(float a1, float a2) {
        float d = a1 - a2;
        if (d < 0.0f) {
            d = -d;
        }
        if (d > 180.0f) {
            d = 360.0f - d;
        }
        return d;
    }

    // todo - (nf) do not delete this method, it could be used later, if we want to determine x,y fractions
    private static boolean getPixelPos(final float lat, final float lon,
                                       final float[] lata, final float[] lona,
                                       final int[] xa, final int[] ya,
                                       final PixelPos pixelPos) {
        final Matrix mA = new Matrix(3, 3);
        mA.set(0, 0, 1.0);
        mA.set(1, 0, 1.0);
        mA.set(2, 0, 1.0);
        mA.set(0, 1, lata[0]);
        mA.set(1, 1, lata[1]);
        mA.set(2, 1, lata[2]);
        mA.set(0, 2, lona[0]);
        mA.set(1, 2, lona[1]);
        mA.set(2, 2, lona[2]);
        final LUDecomposition decomp = new LUDecomposition(mA);

        final Matrix mB = new Matrix(3, 1);

        mB.set(0, 0, ya[0] + 0.5);
        mB.set(1, 0, ya[1] + 0.5);
        mB.set(2, 0, ya[2] + 0.5);
        Exception err = null;

        Matrix mY = null;
        try {
            mY = decomp.solve(mB);
        } catch (Exception e) {
            System.out.println("y1 = " + ya[0] + ", " +
                               "y2 = " + ya[1] + ", " +
                               "y3 = " + ya[2] + "");
            err = e;

        }

        mB.set(0, 0, xa[0] + 0.5);
        mB.set(1, 0, xa[1] + 0.5);
        mB.set(2, 0, xa[2] + 0.5);
        Matrix mX = null;
        try {
            mX = decomp.solve(mB);
        } catch (Exception e) {
            System.out.println("x1 = " + xa[0] + ", " +
                               "x2 = " + xa[1] + ", " +
                               "x3 = " + xa[2] + "\n");
            err = e;
        }

        if (err != null) {
            return false;
        }


        final float fx = (float) (mX.get(0, 0) + mX.get(1, 0) * lat + mX.get(2, 0) * lon);
        final float fy = (float) (mY.get(0, 0) + mY.get(1, 0) * lat + mY.get(2, 0) * lon);

        pixelPos.setLocation(fx, fy);
        return true;
    }

    private void trace(final int x0, final int y0, int bestX, int bestY, int bestCount) {
        if (bestCount > 0) {
            int dx = bestX - x0;
            int dy = bestY - y0;
            if (Math.abs(dx) >= _searchRadius || Math.abs(dy) >= _searchRadius) {
                Debug.trace("WARNING: search radius reached at " +
                            "(x0 = " + x0 + ", y0 = " + y0 + "), " +
                            "(dx = " + dx + ", dy = " + dy + "), " +
                            "#best = " + bestCount);
            }
        } else {
            Debug.trace("WARNING: no better pixel found at " +
                        "(x0 = " + x0 + ", y0 = " + y0 + ")");
        }
    }

    /**
     * Transfers the geo-coding of the {@link Scene srcScene} to the {@link Scene destScene} with respect to the given
     * {@link org.esa.beam.framework.dataio.ProductSubsetDef subsetDef}.
     *
     * @param srcScene  the source scene
     * @param destScene the destination scene
     * @param subsetDef the definition of the subset, may be <code>null</code>
     *
     * @return true, if the geo-coding could be transferred.
     */
    @Override
    public boolean transferGeoCoding(final Scene srcScene, final Scene destScene, final ProductSubsetDef subsetDef) {
        final String latBandName = getLatBand().getName();
        final String lonBandName = getLonBand().getName();
        final Band latBand = destScene.getProduct().getBand(latBandName);
        final Band lonBand = destScene.getProduct().getBand(lonBandName);
        if (_pixelPosEstimator instanceof AbstractGeoCoding) {
            AbstractGeoCoding origGeoCoding = (AbstractGeoCoding) _pixelPosEstimator;
            origGeoCoding.transferGeoCoding(srcScene, destScene, subsetDef);
        }
        if (latBand != null && lonBand != null) {
            destScene.setGeoCoding(new PixelGeoCoding(latBand, lonBand,
                                                      getValidMask(),
                                                      getSearchRadius()));
        }
        return false;
    }

    private static class PixelGrid extends TiePointGrid {

        /**
         * Constructs a new <code>TiePointGrid</code> with the given tie point grid properties.
         *
         * @param name         the name of the new object
         * @param gridWidth    the width of the tie-point grid in pixels
         * @param gridHeight   the height of the tie-point grid in pixels
         * @param offsetX      the X co-ordinate of the first (upper-left) tie-point in pixels
         * @param offsetY      the Y co-ordinate of the first (upper-left) tie-point in pixels
         * @param subSamplingX the sub-sampling in X-direction given in the pixel co-ordinates of the data product to which
         *                     this tie-pint grid belongs to. Must not be less than one.
         * @param subSamplingY the sub-sampling in X-direction given in the pixel co-ordinates of the data product to which
         *                     this tie-pint grid belongs to. Must not be less than one.
         * @param tiePoints    the tie-point data values, must be an array of the size <code>gridWidth * gridHeight</code>
         */
        private PixelGrid(final Product p,
                          final String name,
                          final int gridWidth, final int gridHeight,
                          final float offsetX, final float offsetY,
                          final float subSamplingX, final float subSamplingY,
                          final float[] tiePoints) {
            super(name, gridWidth, gridHeight, offsetX, offsetY, subSamplingX, subSamplingY, tiePoints, false);
            // make this grid a component of the product without actually adding it to the product
            setOwner(p);
        }

        private static PixelGrid create(final Band b, ProgressMonitor pm) throws IOException {
            final int w = b.getRasterWidth();
            final int h = b.getRasterHeight();
            final float[] pixels = new float[w * h];
            b.readPixels(0, 0, w, h, pixels, pm);
            return new PixelGrid(b.getProduct(), b.getName(), w, h, 0.5f, 0.5f, 1.0f, 1.0f, pixels);
        }
    }

    private static class Result {

        public static final float INVALID = Float.MAX_VALUE;

        private int x;
        private int y;
        private float delta;

        private Result() {
            delta = INVALID;
        }

        public final boolean update(final int x, final int y, final float delta) {
            final boolean b = delta < this.delta;
            if (b) {
                this.x = x;
                this.y = y;
                this.delta = delta;
            }
            return b;
        }

        @Override
        public String toString() {
            return "Result[" + x + ", " + y + ", " + delta + "]";
        }
    }

    /**
     * Gets the datum, the reference point or surface against which {@link GeoPos} measurements are made.
     *
     * @return the datum
     */
    @Override
    public Datum getDatum() {
        if (_pixelPosEstimator != null) {
            return _pixelPosEstimator.getDatum();
        }
        return Datum.WGS_84;
    }

}
