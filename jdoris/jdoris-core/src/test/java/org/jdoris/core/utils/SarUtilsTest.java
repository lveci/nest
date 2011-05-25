package org.jdoris.core.utils;

import org.jblas.ComplexDoubleMatrix;
import org.jblas.DoubleMatrix;
import org.jblas.FloatMatrix;
import org.jdoris.core.simulation.Simulation;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteOrder;

import static org.jdoris.core.io.DataReader.*;

public class SarUtilsTest {

    static int nRows = 128;
    static int nCols = 256;

    static ComplexDoubleMatrix cplxData;

    private static final double DELTA_08 = 1e-08;
    private static final double DELTA_04 = 1e-04;

    private static final String testDataLocation = "test/";

    @Before
    public void setUpMultilookTestData() {

        final int numFringes = 10;
        final boolean noiseFlag = false;
        final double noiseLevel = 0;
        cplxData = Simulation.simulateIFG(nRows, nCols, numFringes, noiseFlag, noiseLevel);

    }

    @Test
    public void testMultilook() throws Exception {

        int[] mlookFactorAz = new int[]{1, 2, 5, 5};
        int[] mlookFactorRg = new int[]{2, 2, 1, 5};

        for (int i = 0; i < mlookFactorAz.length; i++) {
            int facAz = mlookFactorAz[i];
            int facRg = mlookFactorRg[i];

                // multilook
                ComplexDoubleMatrix cplxData_mlook_ACTUAL = SarUtils.multilook(cplxData, facAz, facRg);

                // read data
                String fileName = testDataLocation + "testdata_mlook_" + facAz + "_" + facRg + ".cr8";
                ComplexDoubleMatrix cplxData_mlook_EXPECTED = readCplxDoubleData(fileName,
                        cplxData_mlook_ACTUAL.rows, cplxData_mlook_ACTUAL.columns, ByteOrder.LITTLE_ENDIAN);

                // assertEqual
                Assert.assertArrayEquals(cplxData_mlook_EXPECTED.toDoubleArray(), cplxData_mlook_ACTUAL.toDoubleArray(), DELTA_08);

        }
    }

    @Test
    public void testOversample() throws Exception {

        int[] ovsmpFactorAz = new int[]{1, 2, 2, 3};
        int[] ovsmpFactorRg = new int[]{2, 1, 2, 3};

        for (int i = 0; i < ovsmpFactorAz.length; i++) {

            int facAz = ovsmpFactorAz[i];
            int facRg = ovsmpFactorRg[i];


            // oversample
            ComplexDoubleMatrix cplxData_ovsmp_ACTUAL = SarUtils.oversample(cplxData, facAz, facRg);

            // read data
            String fileName = testDataLocation + "testdata_ovsmp_" + facAz + "_" + facRg + ".cr8";
            ComplexDoubleMatrix cplxData_ovsmp_EXPECTED = readCplxDoubleData(fileName,
                    cplxData_ovsmp_ACTUAL.rows, cplxData_ovsmp_ACTUAL.columns, ByteOrder.LITTLE_ENDIAN);

            // assertEqual
            Assert.assertArrayEquals(cplxData_ovsmp_EXPECTED.toDoubleArray(), cplxData_ovsmp_ACTUAL.toDoubleArray(), DELTA_08);

        }

    }

    @Test
    public void testCoherence() throws Exception {

        // loop through tests
        int[] cohWinAz = new int[]{2, 10, 10, 20};
        int[] cohWinRg = new int[]{2, 2, 10, 4};

        for (int i = 0; i < cohWinAz.length; i++) {

            int winAz = cohWinAz[i];
            int winRg = cohWinRg[i];

            // get test data
            String fileTestDataName_1 = testDataLocation + "testdata_cplxinput_1_coh_" + winAz + "_" + winRg + "_"
                    + 126 + "_" + 512 + ".cr4.swap";
            String fileTestDataName_2 = testDataLocation + "testdata_cplxinput_2_coh_" + winAz + "_" + winRg + "_"
                    + 126 + "_" + 512 + ".cr4.swap";

            ComplexDoubleMatrix masterCplx = readCplxFloatData(fileTestDataName_1, 126, 512);
            ComplexDoubleMatrix slaveCplx = readCplxFloatData(fileTestDataName_2, 126, 512);

            // estimate coherence
            DoubleMatrix coh_ACTUAL = SarUtils.coherence(masterCplx, slaveCplx, winAz, winRg);

            int fileSizeRows = coh_ACTUAL.rows;
            int fileSizeCols = coh_ACTUAL.columns;

            // read EXPECTED data
            String fileName = testDataLocation + "testdata_coh_" + winAz + "_" + winRg + "_"
                    + fileSizeRows + "_" + fileSizeCols + ".cr4.swap";

            FloatMatrix coh_EXPECTED = readFloatData(fileName, fileSizeRows, fileSizeCols);

            // assertEqual
            Assert.assertArrayEquals(coh_EXPECTED.toArray(), coh_ACTUAL.toFloat().toArray(), (float) DELTA_04);

        }
    }

/*
    // declared after test as private
    @Test
    public void testCoherenceProduct() throws Exception {
        ComplexDouble power = new ComplexDouble(982009.812, 1363626);
        ComplexDouble sum = new ComplexDouble(-4075.92822, 798659.5);
        double p = power.real() * power.imag(); // 1.33909407e+12
        double norm_sum = Math.pow(sum.abs(), 2);
        double norm_sum_p = norm_sum / p;
        double sqrt_norm_sum_p = Math.sqrt(norm_sum_p);

        double productValue_ACTUAL = SarUtils.coherenceProduct(sum, power);

        Assert.assertEquals(sqrt_norm_sum_p, productValue_ACTUAL, DELTA_08);
    }
*/

}
