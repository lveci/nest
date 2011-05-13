package org.jdoris.core.filtering;

import org.apache.log4j.Logger;
import org.jblas.ComplexDoubleMatrix;
import org.jblas.DoubleMatrix;
import org.jdoris.core.SLCImage;
import org.jdoris.core.todo_classes.todo_classes;
import org.jdoris.core.utils.LinearAlgebraUtils;
import org.jdoris.core.utils.SpectralUtils;
import org.jdoris.core.utils.WeightWindows;

import static org.jblas.MatrixFunctions.pow;

/**
 * User: pmar@ppolabs.com
 * Date: 4/8/11
 * Time: 5:01 PM
 */
public class AzimuthFilter {

    static Logger logger = Logger.getLogger(AzimuthFilter.class.getName());

    /**
     * azimuthfilter
     * Loop over whole master and slave image and filter out
     * part of the spectrum that is not common.
     * Only do zero doppler freq. offset.
     * do not use a polynomial from header for now.
     * (next we will, but assume image are almost coreg. in range,
     * so f_dc polynomial can be eval. same)
     * Per block in azimuth [1024] use a certain overlap with the
     * next block so that same data is partially used for spectrum
     * (not sure if this is requried).
     * Filter is composed of: DE-hamming, RE-hamming (for correct
     * new size and center of the spectrum).
     * Trick in processor.c: First call routine as:
     * (generalinput,filtaziinput,master,slave)
     * in order to process the master, and then as:
     * (generalinput,filtaziinput,slave,master)
     * to filter the slave slc image.
     */
    public static void azimuthfilter(final todo_classes.inputgeneral generalinput,
                                     final todo_classes.input_filtazi fitaziinput,
                                     SLCImage master, // not const, fdc possibly reset here?
                                     SLCImage slave) {

    }


    /**
     * azimuth filter per block
     * Input is matrix of SIZE (e.g. 1024) lines, and N range pixs.
     * Input is SLC of master. slave_info gives fDC polynomial
     * for slave + coarse offset. HAMMING is alpha for myhamming f.
     * Filtered OUTPUT is same size as input block.
     * Because of overlap (azimuth), only write to disk in calling
     * routine part (in matrix coord.) [OVERLAP:SIZE-OVERLAP-1]
     * = SIZE-(2*OVERLAP);  // number of output pixels
     * <p/>
     * Filtering is performed in the spectral domain
     * (1DFFT over azimuth for all columns at once)
     * Filter is different for each column due to shift in fd_c
     * doppler centroid frequency.
     */
    public ComplexDoubleMatrix filterBlock(
            final ComplexDoubleMatrix slcData,
            final SLCImage master, // PRF, BW, fd0
            final SLCImage slave,  // PRF, BW, fd0
            final double hamming) throws Exception {

        final long size = slcData.rows;     // fftlength
        final long nCols = slcData.columns; // width
        if (nCols != master.getCurrentWindow().pixels())
            logger.warn("this will crash, size input matrix not ok...");

        final boolean doHamming = (hamming < 0.9999);
        final double PRF = master.getPRF();               // pulse repetition freq. [Hz]
        final double ABW = master.getAzimuthBandwidth();  // azimuth band width [Hz]

        final float deltaF = (float) (PRF / size);
        final float freq = (float) (-PRF / 2.0);

        // Compute fDC_master, fDC_slave for all columns
        // Create axis to evaluate fDC polynomial for master/slave
        // fDC(column) = fdc_a0 + fDC_a1*(col/RSR) + fDC_a2*(col/RSR)^2
        // fDC = y = Ax
        // Capitals indicate matrices (FDC_M <-> fDC_m)
        logger.debug("Filtering data by evaluated polynomial fDC for each column.");

        DoubleMatrix xAxis = defineAxis(master.getCurrentWindow().pixlo, master.getCurrentWindow().pixhi, master.getRsr2x() / 2.0);
        DoubleMatrix fDC_Master = dopplerAxis(master, xAxis);

        // redefine xAxis with different scale factor
        xAxis = defineAxis(master.getCurrentWindow().pixlo, master.getCurrentWindow().pixhi, slave.getRsr2x() / 2.0);
        DoubleMatrix fDC_Slave = dopplerAxis(slave, xAxis);

        logger.debug("Dumping matrices fDC_m, fDC_s (__DEBUG defined)");
        logger.debug("fDC_m: " + fDC_Master.toString());
        logger.debug("fDC_s: " + fDC_Slave.toString());

        // Axis for filter in frequencies
        // TODO check, rather shift, test matlab... or wshift,1D over dim1
        // use fft properties to shift...
        DoubleMatrix freqAxis = new DoubleMatrix(1, (int) size);
        for (int i = 0; i < size; ++i)
            freqAxis.put(0, i, freq + (i * deltaF)); // [-fr:df:fr-df]

        DoubleMatrix filterVector; // filter per column
        DoubleMatrix filterMatrix = new DoubleMatrix((int) size, (int) nCols); // filter

        // design a filter
        double fDC_m;   // zero doppler freq. [Hz]
        double fDC_s;   // zero doppler freq. [Hz]
        double fDC_mean;// mean doppler centroid freq.
        double ABW_new; // new bandwidth > 1.0
        for (long i = 0; i < nCols; ++i) {

            fDC_m = fDC_Master.get(0, (int) i);
            fDC_s = fDC_Slave.get(0, (int) i);
            fDC_mean = 0.5 * (fDC_m + fDC_s);
            ABW_new = Math.max(1.0, 2.0 * (0.5 * ABW - Math.abs(fDC_m - fDC_mean)));

            if (doHamming) {
                // TODO: not a briliant implementation for per col.. cause wshift AND fftshift.
                // DE-weight spectrum at centered at fDC_m
                // spectrum should be periodic -> use of wshift
                DoubleMatrix inVerseHamming = invertHamming(WeightWindows.hamming(freqAxis, ABW, PRF, hamming), size);

                // Shift this circular by myshift pixels
                long myShift = (long) (Math.rint((size * fDC_m / PRF))); // round
                LinearAlgebraUtils.wshift_inplace(inVerseHamming, (int) -myShift);    // center at fDC_m

                // Newhamming is scaled and centered around new mean
                myShift = (long) (Math.rint((size * fDC_mean / PRF)));                   // round
                filterVector = WeightWindows.hamming(freqAxis, ABW_new, PRF, hamming); // fftshifted
                LinearAlgebraUtils.wshift_inplace(filterVector, (int) -myShift);                      // center at fDC_mean
                filterVector.mmuli(inVerseHamming);

            } else {       // no weighting, but center at fDC_mean, size ABW_new

                long myShift = (long) (Math.rint((size * fDC_mean / PRF)));          // round
                filterVector = WeightWindows.rect(freqAxis.divi((float) ABW_new)); // fftshifted
                LinearAlgebraUtils.wshift_inplace(filterVector, (int) -myShift);                  // center at fDC_mean

            }

            SpectralUtils.ifftshift_inplace(filterVector);           // fftsh works on data!
            filterMatrix.putColumn((int) i, filterVector);   // store filter Vector in filter Matrix

        } // foreach column


        // Filter slcdata
        ComplexDoubleMatrix slcDataFiltered = slcData.dup();
        SpectralUtils.fft_inplace(slcDataFiltered, 1);                         // fft foreach column
        slcDataFiltered.mmuli(new ComplexDoubleMatrix(filterMatrix));
        SpectralUtils.invfft_inplace(slcDataFiltered, 1);                        // ifft foreach column
        return slcDataFiltered;

    }

    private static DoubleMatrix invertHamming(DoubleMatrix hamming, long size) throws Exception {
        for (long ii = 0; ii < size; ++ii)
            hamming.put(0, (int) ii, (float) (1.0 / hamming.get(0, (int) ii)));
        return hamming;
    }

    // doppler progression of image over input axis
    private static DoubleMatrix dopplerAxis(SLCImage master, DoubleMatrix xAxis) {
        DoubleMatrix fDC_Master = xAxis.mul(master.getF_DC_a1());
        fDC_Master.addi(master.getF_DC_a0());
        fDC_Master.addi(pow(xAxis, 2).mmul(master.getF_DC_a2()));
        return fDC_Master;
    }

    private static DoubleMatrix defineAxis(long min, long max, double scale) {
        DoubleMatrix xAxis = new DoubleMatrix(1, (int) max);  // lying
        for (long i = min; i <= max; ++i)
            xAxis.put(0, (int) (i - min), i - 1.0);
        xAxis.divi(scale / 2.0);
        return xAxis;
    }

}
