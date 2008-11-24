package org.esa.nest.gpf;

import junit.framework.TestCase;
import org.junit.Test;

import shared.array.ComplexArray;
import shared.array.RealArray;
import shared.fft.JavaFFTService;
import shared.util.Arithmetic;
import shared.test.Tests;
import java.util.Arrays;

public class TestSST extends TestCase {


    @Override
    protected void setUp() throws Exception {
       
    }

    @Override
    protected void tearDown() throws Exception {
    }


        /**
         * Tests {@link ComplexArray#fft()} and {@link RealArray#rfft()} in one dimension.
         */
        @Test
        public void testOneDimensionalFFT() {

                ComplexArray a = new ComplexArray(new double[] {
                //
                                1, 1, 2, 2, 3, 3 //
                                } //
                );

                ComplexArray b = new ComplexArray(new double[] {
                //
                                3, 3, 2, 2, 1, 1 //
                                } //
                );

                ComplexArray expected = new ComplexArray(new double[] {
                //
                                0, 22, 0, 22, 0, 28 //
                                } //
                );

                assertTrue(Tests.equals( //
                                a.fft().eMul(b.fft()).ifft().values(), expected.values()));

                RealArray aReal = new RealArray(new double[] {
                //
                                1, 2, 3, 4, 5, 6, 7, 8, 9, //
                                10, 11, 12, 13, 14, 15, 16, 17, 18, //
                                19, 20, 21, 22, 23, 24, 25, 26, 27 //
                                } //
                );

                RealArray bReal = new RealArray(new double[] {
                //
                                1, 2, 3, 0, 0, 0, 0, 0, 0, //
                                0, 0, 0, 0, 0, 0, 0, 0, 0, //
                                0, 0, 0, 0, 0, 0, 0, 0, 0 //
                                } //
                );

                RealArray expectedReal = new RealArray(new double[] {
                //
                                133, 85, 10, 16, 22, 28, 34, 40, 46, //
                                52, 58, 64, 70, 76, 82, 88, 94, 100, //
                                106, 112, 118, 124, 130, 136, 142, 148, 154 //
                                } //
                );

                assertTrue(Tests.equals( //
                                aReal.rfft().eMul(bReal.rfft()).rifft().values(), expectedReal.values()));
        }

        /**
         * Tests {@link ComplexArray#fft()} and {@link RealArray#rfft()} in two dimensions.
         */
        @Test
        public void testTwoDimensionalFFT() {

                RealArray a = new RealArray(new double[] {
                //
                                0, 1, 2, 3, 4, 5, 6, //
                                7, 8, 9, 10, 11, 12, 13, //
                                14, 15, 16, 17, 18, 19, 20, //
                                21, 22, 23, 24, 25, 26, 27, //
                                28, 29, 30, 31, 32, 33, 34, //
                                35, 36, 37, 38, 39, 40, 41, //
                                42, 43, 44, 45, 46, 47, 48 //
                                }, //
                                7, 7 //
                );

                RealArray b = new RealArray(new double[] {
                //
                                -1, 2, 2, -1, 0, 0, 0, //
                                0, 1, 1, -4, 0, 0, 0, //
                                0, 0, 1, -2, 0, 0, 0, //
                                0, 0, 0, 1, 0, 0, 0, //
                                0, 0, 0, 0, 0, 0, 0, //
                                0, 0, 0, 0, 0, 0, 0, //
                                0, 0, 0, 0, 0, 0, 0 //
                                }, //
                                7, 7 //
                );

                RealArray expected = new RealArray(new double[] {
                //
                                -14, -14, -14, -14, 28, 0, -21, //
                                -14, -14, -14, -14, 28, 0, -21, //
                                -14, -14, -14, -14, 28, 0, -21, //
                                -14, -14, -14, -14, 28, 0, -21, //
                                -63, -63, -63, -63, -21, -49, -70, //
                                -14, -14, -14, -14, 28, 0, -21, //
                                84, 84, 84, 84, 126, 98, 77 //
                                }, //
                                7, 7 //
                );

                assertTrue(Tests.equals( //
                                ((a.rfft()).eMul(b.rfft().uConj())).rifft().values(), expected.values()));

                assertTrue(Tests.equals( //
                                ((a.tocRe().fft()).eMul(b.tocRe().fft().uConj())).ifft().torRe().values(), //
                                expected.values()));
        }

        /**
         * Tests {@link ComplexArray#fft()} and {@link RealArray#rfft()} in three dimensions.
         */
        @Test
        public void testThreeDimensionalFFT() {

                RealArray a = new RealArray(new double[] {
                //
                                1, 4, 7, //
                                2, 5, 8, //
                                3, 6, 9, //
                                //
                                10, 13, 16, //
                                11, 14, 17, //
                                12, 15, 18, //
                                //
                                19, 22, 25, //
                                20, 23, 26, //
                                21, 24, 27 //
                                }, //
                                3, 3, 3 //
                );

                RealArray b = new RealArray(new double[] {
                //
                                -2, -2, 1, //
                                1, 1, 1, //
                                0, 0, 0, //
                                //
                                1, 0, 1, //
                                0, -2, 0, //
                                0, 0, 0, //
                                //
                                0, 0, 0, //
                                0, 0, 0, //
                                0, 0, 0 //
                                }, //
                                3, 3, 3 //
                );

                RealArray expected = new RealArray(new double[] {
                //
                                -7, 20, -7, //
                                -10, 17, -10, //
                                -10, 17, -10, //
                                //
                                -7, 20, -7, //
                                -10, 17, -10, //
                                -10, 17, -10, //
                                //
                                -7, 20, -7, //
                                -10, 17, -10, //
                                -10, 17, -10 //
                                }, //
                                3, 3, 3 //
                );

                assertTrue(Tests.equals( //
                                a.tocRe().fft().eMul(b.tocRe().fft()).ifft().torRe().values(), expected.values()));

                assertTrue(Tests.equals( //
                                a.rfft().eMul(b.rfft()).rifft().values(), expected.values()));
        }

        /**
         * Tests {@link ComplexArray#fftShift()}.
         */
        @Test
        public void testFFTShift() {

                ComplexArray a = new ComplexArray(new double[] {
                //
                                1, 0, 4, 0, 7, 0, 10, 0, //
                                2, 0, 5, 0, 8, 0, 11, 0, //
                                3, 0, 6, 0, 9, 0, 12, 0, //
                                //
                                13, 0, 16, 0, 19, 0, 22, 0, //
                                14, 0, 17, 0, 20, 0, 23, 0, //
                                15, 0, 18, 0, 21, 0, 24, 0, //
                                //
                                25, 0, 28, 0, 31, 0, 34, 0, //
                                26, 0, 29, 0, 32, 0, 35, 0, //
                                27, 0, 30, 0, 33, 0, 36, 0, //
                                //
                                37, 0, 40, 0, 43, 0, 46, 0, //
                                38, 0, 41, 0, 44, 0, 47, 0, //
                                39, 0, 42, 0, 45, 0, 48, 0, //
                                //
                                49, 0, 52, 0, 55, 0, 58, 0, //
                                50, 0, 53, 0, 56, 0, 59, 0, //
                                51, 0, 54, 0, 57, 0, 60, 0 //
                                }, //
                                5, 3, 4, 2 //
                );

                ComplexArray expected = new ComplexArray(new double[] {
                //
                                45, 0, 48, 0, 39, 0, 42, 0, //
                                43, 0, 46, 0, 37, 0, 40, 0, //
                                44, 0, 47, 0, 38, 0, 41, 0, //
                                //
                                57, 0, 60, 0, 51, 0, 54, 0, //
                                55, 0, 58, 0, 49, 0, 52, 0, //
                                56, 0, 59, 0, 50, 0, 53, 0, //
                                //
                                9, 0, 12, 0, 3, 0, 6, 0, //
                                7, 0, 10, 0, 1, 0, 4, 0, //
                                8, 0, 11, 0, 2, 0, 5, 0, //
                                //
                                21, 0, 24, 0, 15, 0, 18, 0, //
                                19, 0, 22, 0, 13, 0, 16, 0, //
                                20, 0, 23, 0, 14, 0, 17, 0, //
                                //
                                33, 0, 36, 0, 27, 0, 30, 0, //
                                31, 0, 34, 0, 25, 0, 28, 0, //
                                32, 0, 35, 0, 26, 0, 29, 0 //
                                }, //
                                5, 3, 4, 2 //
                );

                assertTrue(Arrays.equals( //
                                a.fftShift().values(), expected.values()));

                assertTrue(Arrays.equals( //
                                a.fftShift().ifftShift().values(), a.values()));
        }

        /**
         * Tests {@link shared.fft.JavaFFTService#reducedToFull(ComplexArray, int[])}.
         */
        @Test
        public void testReducedToFull() {

                JavaFFTService jfs = new JavaFFTService();

                int baseSize = 5;
                int ndims = 5;

                int[] dims = new int[ndims];

                for (int i = 0, n = 1 << ndims; i < n; i++) {

                        for (int j = 0; j < ndims; j++) {
                                dims[j] = baseSize + ((i >>> j) & 0x1);
                        }

                        RealArray arr = new RealArray(dims);
                        double[] values = arr.values();

                        for (int j = 0, m = values.length; j < m; j++) {
                                values[j] = Arithmetic.nextInt(2);
                        }

                        Tests.equals( //
                                        arr.tocRe().fft().values(), //
                                        jfs.reducedToFull(arr.rfft(), dims).values());
                }
        }
}