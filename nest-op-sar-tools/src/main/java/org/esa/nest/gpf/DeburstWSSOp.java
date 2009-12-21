package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.datamodel.AbstractMetadata;

import java.awt.*;
import java.util.*;

/**
 * De-Burst a WSS product
 */
@OperatorMetadata(alias = "DeburstWSS",
        category = "SAR Tools",
        description="Debursts an ASAR WSS product")
public class DeburstWSSOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(valueSet = { SS1, SS2, SS3, SS4, SS5 }, defaultValue = SS1, label="Sub Swath:")
    private String subSwath = SS1;

    @Parameter(defaultValue = "true", label="Produce Intensities Only")
    private boolean produceIntensitiesOnly = true;
    @Parameter(defaultValue = "true", label="Mean Average Intensities")
    private boolean average = false;

    private final Vector<Integer> startLine = new Vector<Integer>(5);
    private static final double zeroThreshold = 1000;
    private static final double zeroThresholdSmall = 500;
    private LineTime[] lineTimes = null;
    private boolean lineTimesSorted = false;

    private final Map<Band, ComplexBand> bandMap = new HashMap<Band, ComplexBand>(5);

    private final static String SS1 = "SS1";
    private final static String SS2 = "SS2";
    private final static String SS3 = "SS3";
    private final static String SS4 = "SS4";
    private final static String SS5 = "SS5";

    private int subSwathBandNum;
    private int targetWidth;
    private int targetHeight;

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public DeburstWSSOp() {
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
            // check product type
            if (!sourceProduct.getProductType().equals("ASA_WSS_1P")) {
                throw new OperatorException("Source product is not an ASA_WSS_1P");
            }

            getSourceMetadata();

            targetProduct = new Product(sourceProduct.getName() + "_" + subSwath,
                    sourceProduct.getProductType(),
                    targetWidth,
                    targetHeight);

            targetProduct.setPreferredTileSize(targetWidth, 20);

            subSwathBandNum = getRealBandNumFromSubSwath(subSwath);
            final Band[] sourceBands = sourceProduct.getBands();

            if (produceIntensitiesOnly) {
                final Band tgtBand = targetProduct.addBand("Intensity_" + subSwath, ProductData.TYPE_FLOAT32);
                tgtBand.setUnit(Unit.INTENSITY);
                bandMap.put(tgtBand, new ComplexBand(sourceBands[subSwathBandNum], sourceBands[subSwathBandNum+1]));
            } else {
                final Band trgI = targetProduct.addBand("i_" +subSwath, sourceBands[subSwathBandNum].getDataType());
                trgI.setUnit(Unit.REAL);
                final Band trgQ = targetProduct.addBand("q_" +subSwath, sourceBands[subSwathBandNum+1].getDataType());
                trgQ.setUnit(Unit.IMAGINARY);
                bandMap.put(trgI, new ComplexBand(sourceBands[subSwathBandNum], sourceBands[subSwathBandNum+1]));
                ReaderUtils.createVirtualIntensityBand(targetProduct, trgI, trgQ, subSwath);
                ReaderUtils.createVirtualPhaseBand(targetProduct, trgI, trgQ, subSwath);
            }

            copyMetaData(sourceProduct.getMetadataRoot(), targetProduct.getMetadataRoot());
            ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
            ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
            ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
            targetProduct.setStartTime(sourceProduct.getStartTime());
            targetProduct.setEndTime(sourceProduct.getEndTime());

            // update the metadata with the affect of the processing
            updateTargetProductMetadata();
        } catch (Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Compute mean pixel spacing (in m).
     * @throws Exception The exception.
     */
    private void getSourceMetadata() throws Exception {
        final MetadataElement srcMetadataRoot = sourceProduct.getMetadataRoot();
        final MetadataElement mppRootElem = srcMetadataRoot.getElement("MAIN_PROCESSING_PARAMS_ADS");
        final MetadataElement mpp = mppRootElem.getElementAt(subSwathBandNum);

        targetHeight = mpp.getAttributeInt("num_output_lines") / 3;
        targetWidth = mpp.getAttributeInt("num_samples_per_line");


    }

    /**
     * Update metadata in the target product.
     */
    private void updateTargetProductMetadata() {

        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.num_output_lines, targetHeight);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.num_samples_per_line, targetWidth);

        final MetadataElement srcMetadataRoot = sourceProduct.getMetadataRoot();
        final MetadataElement mppRootElem = srcMetadataRoot.getElement("MAIN_PROCESSING_PARAMS_ADS");
        final MetadataElement mpp = mppRootElem.getElementAt(subSwathBandNum);

        absTgt.setAttributeUTC(AbstractMetadata.first_line_time,
                mpp.getAttributeUTC("first_zero_doppler_time", new ProductData.UTC(0)));
        absTgt.setAttributeUTC(AbstractMetadata.last_line_time,
                mpp.getAttributeUTC("last_zero_doppler_time", new ProductData.UTC(0)));
        absTgt.setAttributeDouble(AbstractMetadata.line_time_interval, mpp.getAttributeDouble(AbstractMetadata.line_time_interval));

    }

    private static int getRealBandNumFromSubSwath(final String subSwath) {
        if(subSwath.equals(SS1))
            return 0;
        else if(subSwath.equals(SS2))
            return 2;
        else if(subSwath.equals(SS3))
            return 4;
        else if(subSwath.equals(SS4))
            return 6;
        return 8;
    }

    private static void copyMetaData(final MetadataElement source, final MetadataElement target) {
        for (final MetadataElement element : source.getElements()) {
            if (!element.getName().equals("Image Record"))
                target.addElement(element.createDeepClone());
        }
        for (final MetadataAttribute attribute : source.getAttributes()) {
            target.addAttribute(attribute.createDeepClone());
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
   // @Override
   // public synchronized void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {

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
    public synchronized void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Tile targetTileI = null, targetTileQ = null, targetTileIntensity = null;
        final Rectangle targetRectangle = targetTile.getRectangle();
        //System.out.println("targetRect " + targetRectangle.x + " " + targetRectangle.y + " w "
        //        + targetRectangle.width + " h " + targetRectangle.height);

        try {
            ComplexBand cBand;
            if (produceIntensitiesOnly) {
                final Band tgtBand = targetProduct.getBandAt(0);
                targetTileIntensity = targetTile;//targetTiles.get(tgtBand);
                cBand = bandMap.get(tgtBand);
            } else {
                final Band tgtBandI = targetProduct.getBandAt(0);
                final Band tgtBandQ = targetProduct.getBandAt(1);
                targetTileI = targetTile;//targetTiles.get(tgtBandI);
                //targetTileQ = targetTiles.get(tgtBandQ);
                cBand = bandMap.get(tgtBandI);
            }

            if (!lineTimesSorted) {
                sortLineTimes(sourceProduct, cBand.i);
            }

            int targetLine = targetRectangle.y;
            final int maxX = targetRectangle.x + targetRectangle.width;

            //final double threshold = 0.000000139;
            final double threshold = 0.000000135;
            //final double threshold = 0.000000035;

            //int startLineForBand = 0;
            int i = 0;
            pm.beginTask("de-bursting WSS product", lineTimes.length-i);
            while (i < lineTimes.length) {
                pm.worked(1);
                if (lineTimes[i].visited) {
                    ++i;
                    continue;
                }

                final Vector<Integer> burstLines = new Vector<Integer>(4);
                burstLines.add(lineTimes[i].line);
                lineTimes[i].visited = true;
                //startLineForBand = i;

                int j = i + 1;
                while (j < lineTimes.length) {// && j < i + 10 && burstLines.size() < 3) {
                    if (lineTimes[j].visited) {
                        ++j;
                        continue;
                    }

                    final double diff = lineTimes[j].time - lineTimes[i].time;
                    if (diff < threshold) {
                        burstLines.add(lineTimes[j].line);
                        lineTimes[j].visited = true;
                    }
                    ++j;
                }
                ++i;

                //System.out.println(targetLine+" found "+ burstLines.size() + " burstlines");

                if (!burstLines.isEmpty()) {

                    final boolean ok = deburstTile(burstLines, targetLine, targetRectangle.x, maxX, cBand.i, cBand.q,
                            targetTileI, targetTileQ, targetTileIntensity, pm);
                    if(ok)
                        ++targetLine;
                }

                if(targetLine >= targetRectangle.y + targetRectangle.height)
                    break;
            }
            //startLine.set(index, startLineForBand);

            pm.done();

        } catch (Exception e) {
            System.out.print("WSSDeburst.computeTileStack " + e.toString());
        }
    }

    private synchronized void sortLineTimes(final Product srcProduct, final Band srcBand) {
        if(lineTimesSorted) return;

        final MetadataElement imgRecElem = srcProduct.getMetadataRoot().getElement("Image Record");
        final MetadataElement bandElem = imgRecElem.getElement(srcBand.getName());

        final MetadataAttribute attrib = bandElem.getAttribute("t");
        final double[] timeData = (double[])attrib.getData().getElems();
        lineTimes = new LineTime[timeData.length];

        for(int y=0; y < timeData.length; ++y) {
            lineTimes[y] = new LineTime(y, timeData[y]);
        }

        Arrays.sort(lineTimes, new LineTimeComparator());
        lineTimesSorted = true;
    }

    private synchronized boolean deburstTile(final Vector<Integer> burstLines, final int targetLine, final int startX, final int endX,
                              final Band srcBandI, final Band srcBandQ,
                              final Tile targetTileI, final Tile targetTileQ,
                              final Tile targetTileIntensity, final ProgressMonitor pm) {

        final Integer[] burstLineList = new Integer[burstLines.size()];
        burstLines.toArray(burstLineList);
        final int rowLength = endX - startX;
        final double[] peakLine = new double[rowLength];
        final double[] peakLineI = new double[rowLength];
        final double[] peakLineQ = new double[rowLength];
        final double[] sumLine = new double[rowLength];
        final int[] avgTotals = new int[rowLength];
        Arrays.fill(peakLine, -Float.MAX_VALUE);
        Arrays.fill(sumLine, 0.0);
        Arrays.fill(avgTotals, 0);
        double Ival, Qval, intensity;

        final Vector<short[]> srcDataListI = new Vector<short[]>(3);
        final Vector<short[]> srcDataListQ = new Vector<short[]>(3);

        try {
            // get all burst lines
            getBurstLines(burstLineList, srcBandI, srcBandQ, startX, endX, srcDataListI, srcDataListQ, pm);

            if(srcDataListI.isEmpty())
                return false;

            final int dataListSize = srcDataListI.size();
            // for all x peakpick or average from the bursts
            for (int x = startX, i = 0; x < endX; ++x, ++i) {

                for(int j=0; j < dataListSize; ++j) {

                    final short[] srcDataI = srcDataListI.get(j);
                    final short[] srcDataQ = srcDataListQ.get(j);

                    Ival = srcDataI[i];
                    Qval = srcDataQ[i];

                    intensity = (Ival * Ival) + (Qval * Qval);
                    if (intensity > peakLine[i]) {
                        peakLine[i] = intensity;
                        peakLineI[i] = Ival;
                        peakLineQ[i] = Qval;
                    }

                    if (average) {
                        if(!isInvalid(Ival, Qval, zeroThresholdSmall)) {
                            sumLine[i] += intensity;
                            avgTotals[i] += 1;
                        }// if(i > 0) {
                         //   addToAverage(i, srcDataI[i-1], srcDataQ[i-1], sumLine, avgTotals);
                        //} if(i < srcDataI.length-1) {
                        //    addToAverage(i, srcDataI[i+1], srcDataQ[i+1], sumLine, avgTotals);
                        //}
                    }
                }

                //System.out.println("y="+targetLine+" i="+i+" avgTotals="+avgTotals[i]);

                if(average && avgTotals[i] > 1)
                    sumLine[i] /= avgTotals[i];
            }

            if (produceIntensitiesOnly) {
                final ProductData data = targetTileIntensity.getDataBuffer();

                if (average) {

                    for (int x = startX, i = 0; x < endX; ++x, ++i) {
                        data.setElemDoubleAt(targetTileIntensity.getDataBufferIndex(x, targetLine), sumLine[i]);
                    }
                } else {
                    for (int x = startX, i = 0; x < endX; ++x, ++i) {
                        if(peakLine[i] == -Float.MAX_VALUE) {
                            peakLine[i] = 0;
                            //System.out.println("uninitPeak " + i + " at " + targetLine);
                        }
                        data.setElemDoubleAt(targetTileIntensity.getDataBufferIndex(x, targetLine), peakLine[i]);
                    }
                }
            } else {
                final ProductData rawDataI = targetTileI.getRawSamples();
                final ProductData rawDataQ = targetTileQ.getRawSamples();
                final int stride = targetLine * targetTileI.getWidth();

                for (int x = startX, i = 0; x < endX; ++x, ++i) {
                    if(peakLineI[i] == -Float.MAX_VALUE) {
                        peakLineI[i] = 0;
                        peakLineQ[i] = 0;
                    }
                    rawDataI.setElemDoubleAt(stride + x, peakLineI[i]);
                    rawDataQ.setElemDoubleAt(stride + x, peakLineQ[i]);
                }
            }
            return true;

        } catch (Exception e) {
            System.out.println("deburstTile2 " + e.toString());
        }
        return false;
    }

    private static void getBurstLines(final Integer[] burstLineList, final Band srcBandI, final Band srcBandQ,
                               final int startX, final int endX,
                               final Vector<short[]> srcDataListI, final Vector<short[]> srcDataListQ,
                               final ProgressMonitor pm) {
        Tile sourceRasterI, sourceRasterQ;
        final int srcBandHeight = srcBandI.getRasterHeight() - 1;
        final int srcBandWidth = srcBandI.getRasterWidth() - 1;

        for (Integer y : burstLineList) {
            if (y > srcBandHeight) continue;

            final Rectangle sourceRectangle = new Rectangle(startX, y, endX, 1);
            sourceRasterI = getSourceTile(srcBandI, sourceRectangle, pm);
            final short[] srcDataI = (short[]) sourceRasterI.getRawSamples().getElems();
            sourceRasterQ = getSourceTile(srcBandQ, sourceRectangle, pm);
            final short[] srcDataQ = (short[]) sourceRasterQ.getRawSamples().getElems();

       /*     int invalidCount = 0;
            int total = 0;
            final int max = Math.min(srcBandWidth, srcDataI.length);
            for(int i=500; i < max; i+= 50) {
                if(isInvalid(srcDataI[i], srcDataQ[i], zeroThreshold))
                    ++invalidCount;
                ++total;
            }
            if(invalidCount / (float)total > 0.4)  {
                //System.out.println("skipping " + y);
                continue;
            }           */

            srcDataListI.add(srcDataI);
            srcDataListQ.add(srcDataQ);
        }
    }

    private static void addToAverage(int i, double Ival, double Qval,
                                     final double[] sumLine, final int[] avgTotals) {
        if(!isInvalid(Ival, Qval, zeroThresholdSmall)) {
            sumLine[i] += (Ival * Ival) + (Qval * Qval);
            avgTotals[i] += 1;
        }
    }

    private static boolean isInvalid(final double i, final double q, final double threshold) {
        return i > -threshold && i < threshold && q > -threshold && q < threshold;
    }

    private final static class LineTime {
        boolean visited = false;
        final int line;
        final double time;

        LineTime(final int y, final double utc) {
            line = y;
            time = utc;
        }
    }

    private final static class LineTimeComparator implements Comparator<LineTime> {
        public int compare(LineTime a, LineTime b) {
            if (a.time < b.time) return -1;
            else if (a.time > b.time) return 1;
            return 0;
        }
    }

    private final static class ComplexBand {
        final Band i;
        final Band q;
        public ComplexBand(final Band iBand, final Band qBand) {
            i = iBand;
            q = qBand;
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
            super(DeburstWSSOp.class);
        }
    }
}