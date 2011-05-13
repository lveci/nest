package org.jdoris.core.utils;

import org.jblas.ComplexDoubleMatrix;
import org.jblas.DoubleMatrix;
import org.jdoris.core.Window;
import org.jdoris.core.io.FlatBinaryDouble;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;

import static org.jblas.MatrixFunctions.cos;
import static org.jblas.MatrixFunctions.sin;

public class SarUtilsTest {

    static int nRows = 128;
    static int nCols = 256;

    static DoubleMatrix phaseMatrix;
    static DoubleMatrix magMatrix;
    static ComplexDoubleMatrix cplxData;
    static ComplexDoubleMatrix temp2;
    static ComplexDoubleMatrix temp1;
    private static final double DELTA = 1e-08;

    // TODO: make unit tests more systematic and robust, setdata tests are _leaking_

    @Before
    public void setUpMultilookTestData() {
        // TODO: refactor to simulation package
        // multilook an array with a noisy phase trend (10 fringes),
        int numFringes = 10;
        phaseMatrix = MathUtils.ramp(nRows, nCols).muli(2 * Math.PI).muli(numFringes);//.add(DoubleMatrix.randn(nRows, nCols).mmul(0.25 * Math.PI));
        magMatrix = DoubleMatrix.ones(nRows, nCols);//.add(DoubleMatrix.randn(nRows, nCols).mmul(0.5));

        temp1 = new ComplexDoubleMatrix(magMatrix);//, DoubleMatrix.zeros(nRows, nCols));
        temp2 = new ComplexDoubleMatrix(cos(phaseMatrix), sin(phaseMatrix));
        cplxData = temp1.mul(temp2);

    }

    @Test
    public void testMultilook() throws Exception {

        // TODO: make test files someplace reasonable
        ComplexDoubleMatrix cplxData_ml_ACTUAL = SarUtils.multilook(cplxData, 5, 5);
        ComplexDoubleMatrix cplxData_ml_EXPECTED = readCplxData("test/simulation_mlook55_testdata.out", cplxData_ml_ACTUAL.rows, cplxData_ml_ACTUAL.columns);
        Assert.assertEquals(cplxData_ml_EXPECTED, cplxData_ml_ACTUAL);
    }

    @Test
    public void testOversample12() throws Exception {
        ComplexDoubleMatrix cplxData_ovsmp_12_ACTUAL = SarUtils.oversample(cplxData, 1, 2);
        ComplexDoubleMatrix cplxData_ovsmp_12_EXPECTED = readCplxData("test/simulation_ovsmp12_testdata.out", cplxData_ovsmp_12_ACTUAL.rows, cplxData_ovsmp_12_ACTUAL.columns);
        Assert.assertEquals(cplxData_ovsmp_12_EXPECTED, cplxData_ovsmp_12_ACTUAL);
//        System.out.println("cplxData_ovsmp_22_ACTUAL.toString() = " + cplxData_ovsmp_12_ACTUAL.toString());
//        System.out.println("cplxData_ovsmp_22_EXPECTED.toString() = " + cplxData_ovsmp_12_EXPECTED.toString());

    }

    @Test
    public void testOversample21() throws Exception {
        ComplexDoubleMatrix cplxData_ovsmp_21_ACTUAL = SarUtils.oversample(cplxData, 2, 1);
        ComplexDoubleMatrix cplxData_ovsmp_21_EXPECTED = readCplxData("test/simulation_ovsmp21_testdata.out", cplxData_ovsmp_21_ACTUAL.rows, cplxData_ovsmp_21_ACTUAL.columns);
        Assert.assertEquals(cplxData_ovsmp_21_EXPECTED, cplxData_ovsmp_21_ACTUAL);
    }

    @Test
    public void testOversample22() throws Exception {
//        long startTime = System.currentTimeMillis();
        ComplexDoubleMatrix cplxData_ovsmp_22_ACTUAL = SarUtils.oversample(cplxData, 2, 2);
//        long endTime = System.currentTimeMillis();
//        System.out.println("endTime = " + ((endTime - startTime)));
        ComplexDoubleMatrix cplxData_ovsmp_22_EXPECTED = readCplxData("test/simulation_ovsmp22_testdata.out", cplxData_ovsmp_22_ACTUAL.rows, cplxData_ovsmp_22_ACTUAL.columns);
        Assert.assertEquals(cplxData_ovsmp_22_EXPECTED, cplxData_ovsmp_22_ACTUAL);
    }

    @Test
    public void testOversample33() throws Exception {
//        long startTime = System.currentTimeMillis();
        ComplexDoubleMatrix cplxData_ovsmp_33_ACTUAL = SarUtils.oversample(cplxData, 3, 3);
//        long endTime = System.currentTimeMillis();
//        System.out.println("endTime = " + ((endTime - startTime)));
        ComplexDoubleMatrix cplxData_ovsmp_33_EXPECTED = readCplxData("test/simulation_ovsmp33_testdata.out", cplxData_ovsmp_33_ACTUAL.rows, cplxData_ovsmp_33_ACTUAL.columns);
        Assert.assertEquals(cplxData_ovsmp_33_EXPECTED, cplxData_ovsmp_33_ACTUAL);
    }

    private ComplexDoubleMatrix readCplxData(String fileName, int rows, int columns) throws FileNotFoundException {
        FlatBinaryDouble inRealFile = new FlatBinaryDouble();
        inRealFile.setFile(new File(fileName));
        inRealFile.setDataWindow(new Window(0, rows, 0, 2 * columns));
        inRealFile.setInStream();
        inRealFile.readFromStream();

        // TODO: very slow!
        // real data
        DoubleMatrix realData = new DoubleMatrix(rows, columns);
        int cnt;
        for (int i = 0; i < rows; i++) {
            cnt = 0;
            for (int j = 0; j < 2 * columns; j = j + 2) {
                realData.put(i, cnt, inRealFile.data[i][j]);
                cnt++;
            }
        }

        DoubleMatrix cplxData = new DoubleMatrix(rows, columns);
        for (int i = 0; i < rows; i++) {
            cnt = 0;
            for (int j = 1; j < 2 * columns; j = j + 2) {
                cplxData.put(i, cnt, inRealFile.data[i][j]);
                cnt++;
            }
        }

        return new ComplexDoubleMatrix(realData,cplxData);
    }

    //    @Test
//    public void testIntensity() throws Exception {
//
//    }
//
//    @Test
//    public void testMagnitude() throws Exception {
//
//    }
//
    @Test
    public void testCoherence() throws Exception {

        for (int winAz = 1; winAz <= 20; winAz++) {
            for (int winRg = 1; winRg <= 20; winRg++) {
                DoubleMatrix coherence_ACTUAL = SarUtils.coherence(cplxData, cplxData, winAz, winRg);
            }
        }

    }

}
