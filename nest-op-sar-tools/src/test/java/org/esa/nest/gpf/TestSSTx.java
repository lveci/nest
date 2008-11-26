package org.esa.nest.gpf;

import junit.framework.TestCase;
import org.junit.Test;
import org.junit.BeforeClass;

import shared.array.ComplexArray;
import shared.array.RealArray;
import shared.array.AbstractArray;
import shared.fft.JavaFFTService;
import shared.fft.ConvolutionCache;
import shared.util.Arithmetic;
import shared.util.Control;
import shared.util.Loader;
import shared.test.Tests;
import java.util.Arrays;

import sharedx.fftw.Plan;

public class TestSSTx extends TestCase {

         /**
         * The image size.
         */
        final public static int SIZE = 256;

        /**
         * The number of repetitions.
         */
        final public static int NREPS = 128;

        /**
         * The desired level of <a href="http://www.fftw.org/">FFTW3</a> precomputation.
         */
        final public static int MODE = Plan.FFTW_MEASURE;

    @Override
    protected void setUp() throws Exception {
        System.loadLibrary("sharedx");
    }

    @Override
    protected void tearDown() throws Exception {
    }


    public void testNative() throws Exception {  

       // Control.checkTrue(AbstractArray.OpKernel.useNative() && AbstractArray.FFTService.useProvider(),
       //                         "Could not link native layer");
    }

    @BeforeClass
        final public static void initClass() {

                final String modeStr;

                switch (MODE) {

                case Plan.FFTW_ESTIMATE:
                        modeStr = "estimate";
                        break;

                case Plan.FFTW_MEASURE:
                        modeStr = "measure";
                        break;

                case Plan.FFTW_PATIENT:
                        modeStr = "patient";
                        break;

                case Plan.FFTW_EXHAUSTIVE:
                        modeStr = "exhaustive";
                        break;

                default:
                        throw new AssertionError();
                }

                //AbstractArray.OpKernel.useNative();
                AbstractArray.FFTService.setHint("mode", modeStr);

                ComplexArray tmp = new ComplexArray(SIZE, SIZE, 2);

                for (int i = 0; i < 64; i++) {
                        tmp.fft().ifft();
                }
        }

        @Test
        public void testConvolve() {
                initClass();
            
                ComplexArray kernel = new ComplexArray(SIZE, SIZE, 2);
                ComplexArray im = new ComplexArray(SIZE, SIZE, 2);

                ConvolutionCache cc = ConvolutionCache.getInstance();

                for (int i = 0; i < NREPS; i++) {
                        im.eMul(cc.get(kernel, im.dimensions())).ifft();
                }
        }

}