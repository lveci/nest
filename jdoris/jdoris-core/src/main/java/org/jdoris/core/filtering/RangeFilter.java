package org.jdoris.core.filtering;

import org.apache.log4j.Logger;
import org.jblas.ComplexDoubleMatrix;
import org.jblas.DoubleMatrix;
import org.jblas.ranges.IntervalRange;
import org.jdoris.core.Ellipsoid;
import org.jdoris.core.Orbit;
import org.jdoris.core.SLCImage;
import org.jdoris.core.todo_classes.todo_classes;
import org.jdoris.core.utils.*;

import static org.jblas.MatrixFunctions.pow;

public class RangeFilter {

    static Logger logger = Logger.getLogger(RangeFilter.class.getName());

    SLCImage masterMetaData;
    SLCImage slaveMetaData;

    ComplexDoubleMatrix masterCplxData;
    ComplexDoubleMatrix slaveCplxData;

    double meanSNR;
    double percentOfFiltered;


    //TODO: make template classes for generalInput, operatorInput, and ProductMetadata class
    public static void rangefilter(final todo_classes.inputgeneral input_gen,
                                   final SLCImage master,
                                   final SLCImage slave,
                                   final todo_classes.productinfo interferogram,
                                   final todo_classes.input_filtrange inputfiltrange) {
    }


    /**
     * filterblock
     * Computes powerspectrum of complex interferogram.
     * (Product oversampled master. conj oversampled slave in range)
     * A peak in this spectrum corresponds to frequency shift.
     * The master and slave are LPF filtered for this shift.
     * <p/>
     * Optionally the oversampling can be turned off, since no use if only small baseline, and flat terrain.
     * <p/>
     * The powerspectrum can be weighted to give more influence to higher frequencies (conv. of 2 blocks, should be hamming).
     * <p/>
     * The peak is detected by taking a mean of nlmean lines (odd).
     * <p/>
     * Filtering is applied if the SNR (N*power peak / power rest) is above a user supplied threshold.
     * At LPF filtering of the master/slave a hamming window may be applied first to deweight, then to re-weight the spectrum
     * <p/>
     * Should filter based on zero terrain slope if below SNR,
     * but this requried knowledge of orbits, pixel,line coordinate
     * *
     * Input:
     * - MASTER: block of master, that will be filtered
     * - SLAVE:  block of slave, that will be filtered
     * Output:
     * - MASTER (SLAVE): filtered from indeces[0:numl-1]
     * (nlmean-1)/2 to numlines-(nlmean-1)/2-1
     */
    public void filterBlock(ComplexDoubleMatrix masterDataBlock, // updated
                            ComplexDoubleMatrix slaveDataBlock,  // updated
                            long nlmean,
                            float SNRthreshold,
                            float RSR, // in MHz
                            float RBW, // in MHz
                            float alphaHamming,
                            long ovsFactor,
                            boolean doWeightCorrelFlag,
                            double meanSNR, // returned
                            double percentNotFiltered) throws Exception { // returned


        final long numLines = masterDataBlock.rows;
        final long numPixs = masterDataBlock.columns;
        final long outputLines = numLines - nlmean + 1;
        final long firstLine = ((nlmean - 1) / 2);        // indices in matrix system
        final long lastLine = firstLine + outputLines - 1;
        final boolean doHammingFlag = (alphaHamming < 0.9999);
        // use oversampling before int. gen.
        final boolean doOversampleFlag = (ovsFactor != 1);
        int notFiltered = 0; // method counter

        // local variables
        DoubleMatrix inverseHamming = null;

        // sanity check on input paramaters
        if (!MathUtils.isOdd(nlmean)) {
            logger.error("nlMean has to be odd.");
            throw new IllegalArgumentException();
        }
        if (!MathUtils.isPower2(numPixs)) {
            logger.error("numPixels (FFT) has to be power of 2.");
            throw new IllegalArgumentException();
        }
        if (!MathUtils.isPower2(ovsFactor)) {
            logger.error("oversample factor (FFT) has to be power of 2.");
            throw new IllegalArgumentException();
        }
        if (slaveDataBlock.rows != numLines) {
            logger.error("slave not same size as master.");
            throw new IllegalArgumentException();
        }
        if (slaveDataBlock.columns != numPixs) {
            logger.error("slave not same size as master.");
            throw new IllegalArgumentException();
        }
        if (outputLines < 1) {
            logger.warn("no outputLines, continuing....");
        }

        // SHIFT PARAMETERS
        final double deltaF = RSR / numPixs;
        final double freq = -RSR / 2.;

        DoubleMatrix freqAxis = defineFreqAxis(numPixs, RSR);

        if (doHammingFlag) {
            inverseHamming = doHamming(RSR, RBW, alphaHamming, numPixs, freqAxis);
        }

        // COMPUTE CPLX IFG ON THE FLY -> power
        ComplexDoubleMatrix cplxIfg;
        if (doOversampleFlag) {
            cplxIfg = computeOvsIfg(masterDataBlock, slaveDataBlock, (int) ovsFactor);
        } else {
            cplxIfg = computeIfg(masterDataBlock, slaveDataBlock);
        }

        long fftLength = cplxIfg.columns;

        logger.debug("is real4 accurate enough?");// seems so

        SpectralUtils.fft_inplace(cplxIfg, 2);                          // cplxIfg = fft over rows
        DoubleMatrix power = SarUtils.intensity(cplxIfg);  // power   = cplxIfg.*conj(cplxIfg);

        // Use weighted correlation due to bias in normal definition
        // Actually better de-weight with autoconvoluted hamming.
        // No use a triangle for #points used for correlation estimation
        // not in combination with dooversample...
        if (doWeightCorrelFlag) {
            doWeightCorrel(RSR, RBW, numLines, numPixs, fftLength, power);
        }

        // Average power to reduce noise
        SpectralUtils.fft_inplace(masterDataBlock, 2); // fft.ing over rows
        SpectralUtils.fft_inplace(slaveDataBlock, 2);
        logger.trace("Took FFT over rows of master, slave.");

        DoubleMatrix nlMeanPower = computeNlMeanPower(nlmean, fftLength, power);

        long shift = 0;                          // returned by max
        long dummy = 0;                          // returned by max
        meanSNR = 0.;
        double meanShift = 0.;

        // Start actual filtering
        for (long outLine = firstLine; outLine <= lastLine; ++outLine) {

            // TODO: check algorithmically this step
            // 1x1 matrix ... ??
//            DoubleMatrix totalPowerMatrix = nlMeanPower.columnSums().get(0,0);
            double totalPower = nlMeanPower.columnSums().get(0, 0);

            // double maxvalue = max(nlmeanpower, dummy, shift);      // shift returned
            double maxValue = nlMeanPower.max();

            long lastShift = shift;     // use this if current shift not ok.
            double SNR = fftLength * (maxValue / (totalPower - maxValue));
            meanSNR += SNR;

            // TODO: encapsulate check for the shift direction
            // Check for negative shift
            boolean negShift = false;
            if (shift > (int) (fftLength / 2)) {
                shift = (int) fftLength - shift;
                lastShift = shift; // use this if current shift not OK.
                negShift = true;
            }

            // ______ Do actual filtering ______
            if (SNR < SNRthreshold) {
                notFiltered++;                                    // update counter
                shift = lastShift;
                logger.warn("using last shift for filter");
            }

            meanShift += shift;
            DoubleMatrix filter;

            if (doHammingFlag) {
                // Newhamming is scaled and centered around new mean
                // filter is fftshifted
                filter = WeightWindows.hamming(
                        freqAxis.subi(0.5 * shift * deltaF),
                        RBW - (shift * deltaF),
                        RSR, alphaHamming);
                filter.mmul(inverseHamming);
            } else { // no weighting of spectra
                // filter is fftshifted
                filter = WeightWindows.rect((freqAxis.subi(.5 * shift * deltaF)).divi((RBW - shift * deltaF)));
            }

            // Use freq. as returned by fft
            // Note that filter_s = fliplr(filter_m)
            // and that this is also valid after ifftshift
            SpectralUtils.ifftshift_inplace(filter);

            // ====== Actual spectral filtering ======
            // Decide which side to filter, may be dependent on definition of FFT??
            ComplexDoubleMatrix filterComplex = new ComplexDoubleMatrix(filter);
            if (!negShift) {
                LinearAlgebraUtils.dotmult(masterDataBlock.getRow((int) outLine), filterComplex);
                LinearAlgebraUtils.fliplr_inplace(filter);
                LinearAlgebraUtils.dotmult(slaveDataBlock.getRow((int) outLine), filterComplex);
            } else {
                LinearAlgebraUtils.dotmult(slaveDataBlock.getRow((int) outLine), filterComplex);
                LinearAlgebraUtils.fliplr_inplace(filter);
                LinearAlgebraUtils.dotmult(masterDataBlock.getRow((int) outLine), filterComplex);
            }

            // Update 'walking' mean
            if (outLine != lastLine) {
                DoubleMatrix line1 = power.getRow((int) (outLine - firstLine));
                DoubleMatrix lineN = power.getRow((int) (outLine - firstLine + nlmean));
                nlMeanPower.add(lineN.sub(line1));
            }

        } // loop over outLines

        // IFFT of spectrally filtered data, and return these
        SpectralUtils.fft_inplace(masterDataBlock, 2);
        SpectralUtils.fft_inplace(slaveDataBlock, 2);

        // Return these to main
        meanShift /= (outputLines - notFiltered);
        meanSNR /= outputLines;
        percentNotFiltered = 100. * (float) (notFiltered) / (float) outputLines;


        // Some info for this block
        final double meanFrFreq = meanShift * deltaF;    // Hz?
        logger.debug("mean SHIFT for block"
                + ": " + meanShift
                + " = " + meanFrFreq / 1e6 + " MHz (fringe freq.).");

        logger.debug("mean SNR for block: " + meanSNR);
        logger.debug("filtered for block"
                + ": " + (100.00 - percentNotFiltered) + "%");

        if (percentNotFiltered > 60.0) {
            logger.warn("more then 60% of signal filtered?!?");
        }

    }

    private DoubleMatrix computeNlMeanPower(long nlmean, long fftLength, DoubleMatrix power) {
//        DoubleMatrix nlmeanpower = sum(power(0,nlmean-1, 0,fftlength-1),1);
        final IntervalRange rangeRows = new IntervalRange(0, (int) (nlmean - 1));
        final IntervalRange rangeColumns = new IntervalRange(0, (int) (fftLength - 1));
        return pow(power.get(rangeRows, rangeColumns), 2).rowSums();
    }

    private static void doWeightCorrel(final float RSR, final float RBW, final long numLines, final long numPixs, final long fftLength, DoubleMatrix data) {

        // TODO: refactor this call to use arrays instead of loops
        int j;
        int i;

        // weigth = numpoints in spectral convolution for fft squared for power...
        int indexNoPeak = (int) ((1. - (RBW / RSR)) * (float) (numPixs));
        for (j = 0; j < fftLength; ++j) {

            long nPnts = Math.abs(numPixs - j);
            double weight = (nPnts < indexNoPeak) ? Math.pow(numPixs, 2) : Math.pow(nPnts, 2); // ==zero

            for (i = 0; i < numLines; ++i) {
                data.put(i, j, data.get(i, j) / weight);
            }
        }

    }

    private static ComplexDoubleMatrix computeOvsIfg(final ComplexDoubleMatrix masterData, final ComplexDoubleMatrix slaveData,
                                                     final int ovsFactor) throws Exception {
        return computeIfg(SarUtils.oversample(masterData, 1, ovsFactor), SarUtils.oversample(slaveData, 1, ovsFactor));
    }

    private static ComplexDoubleMatrix computeIfg(final ComplexDoubleMatrix masterData, final ComplexDoubleMatrix slaveData) throws Exception {
        return LinearAlgebraUtils.dotmult(masterData, slaveData.conj());
    }


    private DoubleMatrix defineFreqAxis(final long numPixs, final double RSR) {
        final double deltaF = RSR / numPixs;
        final double freq = -RSR / 2.;
        DoubleMatrix freqAxis = new DoubleMatrix(1, (int) numPixs);
        for (int i = 0; i < numPixs; ++i) {
            freqAxis.put(0, i, freq + (i * deltaF));
        }
        return freqAxis;
    }

    private static DoubleMatrix doHamming(float RSR, float RBW, float alphaHamming, long numPixs, DoubleMatrix freqAxis) throws Exception {
        DoubleMatrix inverseHamming = WeightWindows.hamming(freqAxis, RBW, RSR, alphaHamming);
        for (int i = 0; i < numPixs; ++i)
            if (inverseHamming.get(0, i) != 0.)
                inverseHamming.put(0, i, 1. / inverseHamming.get(0, i));
        return inverseHamming;
    }

    // TODO: refactor InputEllips to "Ellipsoid" class of "org.esa.beam.framework.dataop.maptransf.Ellipsoid" and use GeoUtils of NEST;
    @Deprecated
    public static void rangefilterorbits(final todo_classes.inputgeneral generalinput,
                                         final todo_classes.input_filtrange inputfiltrange,
                                         final Ellipsoid ellips,
                                         final SLCImage master,
                                         final SLCImage slave,
                                         Orbit masterorbit,
                                         Orbit slaveorbit) {
    }


}
