package org.jdoris.core.filtering;

import org.apache.log4j.Logger;
import org.jblas.ComplexDouble;
import org.jblas.ComplexDoubleMatrix;
import org.jblas.DoubleMatrix;
import org.jdoris.core.Window;
import org.jdoris.core.todo_classes.todo_classes;
import org.jdoris.core.utils.LinearAlgebraUtils;
import org.jdoris.core.utils.SarUtils;
import org.jdoris.core.utils.SpectralUtils;

import static org.jblas.MatrixFunctions.pow;

public class PhaseFiter {

    static Logger logger = Logger.getLogger(PhaseFiter.class.getName());


    //TODO: make template classes for generalInput, operatorInput, and ProductMetadata class

    /**
     * phasefilter: OPERATOR prototype METHOD
     * goldsteins method, see routine goldstein and smooth.
     * After Goldstein and Werner, Radar interferogram filtering
     * for geophysical applications. GRL 25-21 pp 4035-4038, 1998.
     * and: ESA Florence 1997, vol2, pp969-972, Goldstein & Werner
     * "Radar ice motion interferometry".
     */
    public static void phasefilter(
            final todo_classes.inputgeneral generalinput,
            final todo_classes.productinfo interferogram,
            final todo_classes.input_filtphase filtphaseinput) {
    }

    /**
     * phasefilterspectral: OPERATOR method
     * loop over whole file and multiply spectrum of interferogram
     * with kernel specified in input file.
     */
    public static void phasefilterspectral(
            final todo_classes.inputgeneral generalinput,
            final todo_classes.productinfo interferogram,
            final todo_classes.input_filtphase filtphaseinput) {
    }

    /**
     * spatialphasefilt : OPERATOR prototype METHOD
     * For the first block the part [0:OVERLAP-1] is set to 0.
     * For the last block the part [NPIX-1-OVERLAP:NPIX-1] is 0.
     */
    @Deprecated
    public static void spatialphasefilt(
            final todo_classes.inputgeneral generalinput,
            final todo_classes.productinfo interferogram,
            final todo_classes.input_filtphase filtphaseinput) {
    }


    /**
     * phasefilter goldstein
     * Input is matrix of SIZE (e.g. 32) lines, and N range pixels.
     * Filtered OUTPUT is same size as input block.
     * Because of overlap, only write to disk in calling routine
     * part (in matrix coord.) [OVERLAP:SIZE-OVERLAP-1]
     * <p/>
     * Smoothing of the amplitude of the spectrum is performed by
     * spatial convolution with a block kernel of size 2*SMOOTH+1.
     * (Which is done by FFT's). e.g. a spatial moving average with
     * kernel (1d) k=[1 1 1 1 1]/5; kernel2d = transpose(k)*k.
     * Blocks in range direction.
     * <p/>
     * After Goldstein and Werner, Radar interferogram filtering
     * for geophysical applications. GRL 25-21 pp 4035-4038, 1998.
     * and: ESA Florence 1997, vol2, pp969-972, Goldstein & Werner
     * "Radar ice motion interferometry".
     */
    public static ComplexDoubleMatrix goldstein(
            final ComplexDoubleMatrix complexIfg,
            final float alpha,
            final int overlap,
            final DoubleMatrix smoothKernel) { // lying down

        boolean checkIndicesScalling = false;
        boolean checkIndexOnly = false;

        if (checkIndicesScalling) {

            return complexIfg;

        } else {
            // ______ Allocate output matrix ______
            final int size = complexIfg.rows;
            final int npix = complexIfg.columns;

            ComplexDoubleMatrix filteredCplxIfg = new ComplexDoubleMatrix(size, npix); // output

            // ______ Get block from buffer ______
            final int numOut = size - (2 * overlap);       // number of output pixels
            int cIfgPixLo = 0;                      // index in CINT to get 1st block
            int cIfgPixHi = size - 1;                 // index in CINT to get 1st block
            int outBlockPixLo = 0;                      // index in BLOCK (only 1st block)
            int outBlockPixHi = size - 1 - overlap;         // index in BLOCK (except last block)
            int outPixLo = outBlockPixLo;          // index in FILTERED (1st block)
            int outPixHi = outBlockPixHi;          // index in FILTERED
            boolean lastBlockDone = false;                  // only just started...

            // note that int floors division
            int smooth = smoothKernel.columns / 2;   // half block size, odd kernel
            boolean doSmooth = (smooth != 0);
            logger.debug("SMOOTH flag: " + smooth);  // problem with uint<0 index in smoothkernel

            // use FFT's for convolution with smoothkernel
            // this could also be done static, or in the calling routine
            // KERNEL2D is FFT2 of even kernel (no imag part after fft!)
            ComplexDoubleMatrix kernel2D = null;
            if (doSmooth) {
                ComplexDoubleMatrix kernel1D = new ComplexDoubleMatrix(1, size);             // init to zeros
                for (int ii = -smooth; ii <= smooth; ++ii) {// 1d kernel function of block

                    //kernel(0,(ii+SIZE)%SIZE) = smoothkernel(0,ii-SMOOTH);
                    // e.g.: [30,31,0,1,2] <--> [0,1,2,3,4]
                    int tmpValue_1 = (ii + size) % size;
                    int tmpValue_2 = ii + smooth;// used to be ii-SMOOTH: wrong
                    logger.debug("tmp1: " + tmpValue_1 + "; tmp2: " + tmpValue_2);
                    kernel1D.put(0, tmpValue_1, new ComplexDouble(smoothKernel.get(0, tmpValue_2), 0.0));
                }

                kernel2D = LinearAlgebraUtils.matTxmat(kernel1D, kernel1D);
                SpectralUtils.fft2D_inplace(kernel2D);  // should be real sinc
            }
            logger.debug("kernel created for smoothing spectrum");

            // ====== Loop forever, stop after lastblockdone ======
            for (; ;)      //forever, like in c!
            {
                if (cIfgPixHi >= npix - 1)                      // check if we are doing the last block
                {
                    lastBlockDone = true;
                    cIfgPixHi = npix - 1;                   // prevent reading after file
                    cIfgPixLo = cIfgPixHi - size + 1;         // but make sure SIZE pixels are read
                    outPixHi = cIfgPixHi;                // index in FILTERED 2b written
                    outBlockPixHi = size - 1;                   // write all to the end
                    outBlockPixLo = outBlockPixHi - (outPixHi - outPixLo + 1) + 1;
                }
                Window winCIfg = new Window(0, size - 1, cIfgPixLo, cIfgPixHi);
                Window winBlock = new Window(0, size - 1, outBlockPixLo, outBlockPixHi);
                Window winFiltered = new Window(0, size - 1, outPixLo, outPixHi);

                // Construct BLOCK as part of CINT
                ComplexDoubleMatrix BLOCK = new ComplexDoubleMatrix((int) winCIfg.lines(), (int) winCIfg.pixels());
                LinearAlgebraUtils.setdata(BLOCK, complexIfg, winCIfg);

                if (checkIndexOnly) {

                    // Get spectrum/amplitude/smooth/filter ______
                    SpectralUtils.fft2D_inplace(BLOCK);
                    DoubleMatrix AMPLITUDE = SarUtils.magnitude(BLOCK);

                    // ______ use FFT's for convolution with rect ______
                    if (doSmooth == true)
                        AMPLITUDE = smooth(AMPLITUDE, kernel2D);

                    double maxamplitude = AMPLITUDE.max();

                    if (maxamplitude > 1e-20) //?
                    {
                        AMPLITUDE.div(maxamplitude);
                        pow(AMPLITUDE, alpha);
                        BLOCK.mmul(new ComplexDoubleMatrix(AMPLITUDE));           // weight spectrum
                    } else {
                        logger.warn("no filtering, maxamplitude<1e-20, zeros in this block?");
                    }

                    SpectralUtils.invfft2D_inplace(BLOCK);

                }

                // ______ Set correct part that is filtered in output matrix ______
                LinearAlgebraUtils.setdata(filteredCplxIfg, winFiltered, BLOCK, winBlock);

                // ______ Exit if finished ______
                if (lastBlockDone)
                    return filteredCplxIfg;                  // return

                // ______ Update indexes in matrices, will be corrected for last block ______
                cIfgPixLo += numOut;             // next block
                cIfgPixHi += numOut;             // next block
                outBlockPixLo = overlap;            // index in block, valid for all middle blocks
                outPixLo = outPixHi + 1;         // index in FILTERED, next range line
                outPixHi = outPixLo + numOut - 1;  // index in FILTERED

            } // for all blocks in this buffer


        }

    }

    /**
     * phasefilter buffer by spatial conv. with kernel.
     * Input is matrix of SIZE (e.g. 256) lines, and N range pixels.
     * Filtered OUTPUT is same size as input block.
     * Because of overlap, only write to disk in calling routine
     * part (in matrix coord.) [OVERLAP:SIZE-OVERLAP-1]
     * (in line direction)
     * spatial convolution with a kernel function, such as a block
     * function 111 (1D) (By FFT's).
     * Processing is done in blocks in range direction.
     * For the first block the part [0:OVERLAP-1] is set to 0.
     * For the last block the part [NPIX-1-OVERLAP:NPIX-1] is 0.
     * <p/>
     * Input:
     * - matrix to be filtered of blocklines * numpixs
     * - kernel2d: fft2 of 2d spatial kernel.
     * - overlap: half of the kernel size, e.g., 1 for 111.
     * Output:
     * - filtered matrix.
     * ifft2d(BLOCK .* KERNEL2D) is returned, so if required for
     * non symmetrical kernel, offer the conj(KERNEL2D)!
     */
    public static ComplexDoubleMatrix convbuffer(
            final ComplexDoubleMatrix CINT,
            final ComplexDoubleMatrix KERNEL2D,
            final int OVERLAP) {         // overlap in column direction

        // Allocate output matrix
        int SIZE = CINT.rows;
        int NPIX = CINT.columns;
        ComplexDoubleMatrix FILTERED = new ComplexDoubleMatrix(SIZE, NPIX);          // allocate output (==0)

        // ______ Get block from buffer ______
        int numout = SIZE - (2 * OVERLAP);       // number of output pixels per block
        int cintpixlo = 0;                      // index in CINT to get 1st block
        int cintpixhi = SIZE - 1;                 // index in CINT to get 1st block
        //int32 outblockpixlo = 0;                    // index in BLOCK (only 1st block)
        int outblockpixlo = OVERLAP;                // index in block
        int outblockpixhi = SIZE - 1 - OVERLAP;         // index in BLOCK (except last block)
        int outpixlo = outblockpixlo;          // index in FILTERED (1st block)
        int outpixhi = outblockpixhi;          // index in FILTERED
        boolean lastblockdone = false;                  // only just started...


        // Loop forever, stop after lastblockdone
        for (; ;)      //forever
        {
            if (cintpixhi >= NPIX - 1)                      // check if we are doing the last block
            {
                lastblockdone = true;
                cintpixhi = NPIX - 1;                   // prevent reading after file
                cintpixlo = cintpixhi - SIZE + 1;         // but make sure SIZE pixels are read
                // leave last few==0
                outpixhi = NPIX - 1 - OVERLAP;           // index in FILTERED 2b written
                //outblockpixhi = SIZE-1;                 // write all to the end
                outblockpixlo = outblockpixhi - (outpixhi - outpixlo + 1) + 1;
            }
            Window wincint = new Window(0, SIZE - 1, cintpixlo, cintpixhi);
            Window winblock = new Window(0, SIZE - 1, outblockpixlo, outblockpixhi);
            Window winfiltered = new Window(0, SIZE - 1, outpixlo, outpixhi);

            // Construct BLOCK as part of CINT ______
            ComplexDoubleMatrix BLOCK = new ComplexDoubleMatrix((int) wincint.lines(), (int) wincint.pixels());
            LinearAlgebraUtils.setdata(BLOCK, CINT, wincint);

            // ______ Set correct part that is filtered in output matrix ______
            LinearAlgebraUtils.setdata(FILTERED, winfiltered, BLOCK, winblock);

            // ______ Exit if finished ______
            if (lastblockdone)
                return FILTERED;                  // return

            // ______ Update indexes in matrices, will be corrected for last block ______
            cintpixlo += numout;             // next block
            cintpixhi += numout;             // next block
            outpixlo = outpixhi + 1;         // index in FILTERED, next range line
            outpixhi = outpixlo + numout - 1;  // index in FILTERED

        } // for all blocks in this buffer

    } // END convbuffer


    /**
     * phasefilter spectral
     * Input is matrix of SIZE (e.g. 32) lines, and N range pixels.
     * Filtered OUTPUT is same size as input block.
     * Because of overlap, only write to disk in calling routine
     * part (in matrix coord.) [OVERLAP:SIZE-OVERLAP-1]
     * *
     * Filtering is performed by pointwise multiplication of the
     * spectrum per block by the KERNEL2D (input).
     * Blocks in range direction,
     */
    public static ComplexDoubleMatrix spectralfilt(
            final ComplexDoubleMatrix CINT,
            final ComplexDoubleMatrix KERNEL2D,
            final int OVERLAP) {


        // Allocate output matrix
        final int SIZE = CINT.rows;
        final int NPIX = CINT.columns;
        ComplexDoubleMatrix FILTERED = new ComplexDoubleMatrix(SIZE, NPIX);

        // ______ Get block from buffer ______
        final int numout = SIZE - (2 * OVERLAP);       // number of output pixels
        int cintpixlo = 0;                      // index in CINT to get 1st block
        int cintpixhi = SIZE - 1;                 // index in CINT to get 1st block
        int outblockpixlo = 0;                      // index in BLOCK (only 1st block)
        int outblockpixhi = SIZE - 1 - OVERLAP;         // index in BLOCK (except last block)
        int outpixlo = outblockpixlo;          // index in FILTERED (1st block)
        int outpixhi = outblockpixhi;          // index in FILTERED
        boolean lastblockdone = false;                  // only just started...


        // ====== Loop forever, stop after lastblockdone ======
        for (; ;)      //forever
        {
            if (cintpixhi >= NPIX - 1) {                      // check if we are doing the last block
                lastblockdone = true;
                cintpixhi = NPIX - 1;                   // prevent reading after file
                cintpixlo = cintpixhi - SIZE + 1;         // but make sure SIZE pixels are read
                outpixhi = cintpixhi;                // index in FILTERED 2b written
                outblockpixhi = SIZE - 1;                   // write all to the end
                outblockpixlo = outblockpixhi - (outpixhi - outpixlo + 1) + 1;
            }

            final Window wincint = new Window(0, SIZE - 1, cintpixlo, cintpixhi);
            final Window winblock = new Window(0, SIZE - 1, outblockpixlo, outblockpixhi);
            final Window winfiltered = new Window(0, SIZE - 1, outpixlo, outpixhi);

            // Construct BLOCK as part of CINT
            ComplexDoubleMatrix BLOCK = new ComplexDoubleMatrix((int) wincint.lines(), (int) wincint.pixels());
            LinearAlgebraUtils.setdata(BLOCK, CINT, wincint);

            // ______ Get spectrum/filter/ifft ______
            SpectralUtils.fft2D_inplace(BLOCK);
            BLOCK.mmul(KERNEL2D);                  // the filter...


            SpectralUtils.invfft2D_inplace(BLOCK);


            // Set correct part that is filtered in output matrix
            LinearAlgebraUtils.setdata(FILTERED, winfiltered, BLOCK, winblock);

            // Exit if finished ______
            if (lastblockdone)
                return FILTERED;                  // return

            // ______ Update indexes in matrices, will be corrected for last block ______
            cintpixlo += numout;             // next block
            cintpixhi += numout;             // next block
            outblockpixlo = OVERLAP;            // index in block, valid for all middle blocks
            outpixlo = outpixhi + 1;         // index in FILTERED, next range line
            outpixhi = outpixlo + numout - 1;  // index in FILTERED

        } // for all blocks in this buffer

    }

    /**
     * B = smooth(A,KERNEL)
     * (circular) spatial moving average with a (2N+1,2N+1) block.
     * See also matlab script smooth.m for some tests.
     * implementation as convolution with FFT's
     * input: KERNEL is the FFT of the kernel (block)
     */
    public static DoubleMatrix smooth(
            final DoubleMatrix A,
            final ComplexDoubleMatrix KERNEL2D) {

        ComplexDoubleMatrix DATA = new ComplexDoubleMatrix(A);      // or define fft(R4)
        SpectralUtils.fft2D_inplace(DATA);                                  // or define fft(R4)

        // ______ create kernel in calling routine, e.g., like ______
        // ______ Kernel has to be even! ______
        //const int32 L = A.lines();
        //const int32 P = A.pixels();
        //matrix<complr4> kernel(1,L);                        // init to zeros
        //for (register int32 ii=-N; ii<=N; ++ii)     // 1d kernel function of block
        //  kernel(0,(ii+L)%L) = 1./(2*N+1);
        //matrix<complr4> KERNEL2D = matTxmat(kernel,kernel);
        //fft2d(KERNEL2D);                            // should be real sinc

        DATA.mmuli(KERNEL2D);
        SpectralUtils.invfft2D_inplace(DATA);                   // convolution, but still complex...
        return DATA.real();                           // you know it is real only...
    }

    /**
     * B = smooth(A,blocksize)
     * (circular) spatial moving average with a (2N+1,2N+1) block.
     * See also matlab script smooth.m for some tests.
     */
    @Deprecated
    public static DoubleMatrix smoothSpace(
            final DoubleMatrix A,
            int N) {

        if (N == 0)
            return A;

        int L = A.rows;
        int P = A.columns;
        DoubleMatrix SMOOTH = new DoubleMatrix(L, P);            // init to zero...
        double sum = 0.;
        int indexii;
        double Nsmooth = (2 * N + 1) * (2 * N + 1);
        for (int i = 0; i < L; ++i) {
            for (int j = 0; j < P; ++j) {
                // Smooth this pixel
                for (int ii = -N; ii <= N; ++ii) {
                    indexii = (i + ii + L) % L;
                    for (int jj = -N; jj <= N; ++jj) {
                        sum += A.get(indexii, (j + jj + P) % P);
                    }
                }
                SMOOTH.put(i, j, sum / Nsmooth);
                sum = 0.;
            }
        }

        return SMOOTH;
    }

    // Do the same as smoothSpace but faster
    // some overhead due to conversion r4<->cr4
    public static DoubleMatrix smoothSpectral(
            final DoubleMatrix A,
            int N) {

        int L = A.rows;
        int P = A.columns;
        ComplexDoubleMatrix DATA = new ComplexDoubleMatrix(L, P); // init to zero...

        SpectralUtils.fft2D_inplace(DATA); // or define fft(R4)
        ComplexDoubleMatrix kernel = new ComplexDoubleMatrix(1, L); // init to zeros

        // design 1d kernel function of block
        for (int ii = -N; ii <= N; ++ii) {
            kernel.put(0, (ii + L) % L, new ComplexDouble(1.0 / (2 * N + 1), 0.0));
        }

        ComplexDoubleMatrix KERNEL2D = LinearAlgebraUtils.matTxmat(kernel, kernel);
        SpectralUtils.fft2D_inplace(KERNEL2D); // should be real sinc
        DATA.mmul(KERNEL2D); // no need for conj. with real fft...
        SpectralUtils.invfft2D_inplace(DATA);  // convolution, but still complex...
        return DATA.real(); // you know it is real only...

    }

}
