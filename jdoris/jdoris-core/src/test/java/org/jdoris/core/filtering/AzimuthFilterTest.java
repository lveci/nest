package org.jdoris.core.filtering;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import org.jblas.ComplexDoubleMatrix;
import org.junit.Test;

public class AzimuthFilterTest {


    // Temporrary Test
    @Test
    public void testTemp() throws Exception {

        ComplexDoubleMatrix A = new ComplexDoubleMatrix(1, 4);

        A.put(0, 0, 1);
        A.put(0, 1, 2);
        A.put(0, 2, 3);
        A.put(0, 3, 4);

        System.out.println("A.length = " + A.length);

        double[] temp = A.toDoubleArray();

        for (double v : temp) {
            System.out.println("v = " + v);
        }

        System.out.println("--- FFT ----------");

        DoubleFFT_1D fft1d = new DoubleFFT_1D(A.length);
        fft1d.complexForward(A.toDoubleArray());

        for (int i = 0; i < A.length; i++) {
            System.out.println("v = " + A.toDoubleArray()[i]);
        }

    }
}
