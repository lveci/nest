package org.esa.nest.util;

import junit.framework.TestCase;


/**
 * MathUtils Tester.
 *
 * @author lveci
 */
public class TestMathUtils extends TestCase {

    public TestMathUtils(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testHanning() {
        int windowLength = 5;
        double w0 = MathUtils.hanning(-2.0, windowLength);
        double w1 = MathUtils.hanning(-1.0, windowLength);
        double w2 = MathUtils.hanning( 0.0, windowLength);
        double w3 = MathUtils.hanning( 1.0, windowLength);
        double w4 = MathUtils.hanning( 2.0, windowLength);
        assertTrue(Double.compare(w0, 0.2500000000000001) == 0);
        assertTrue(Double.compare(w1, 0.75) == 0);
        assertTrue(Double.compare(w2, 1.0) == 0);
        assertTrue(Double.compare(w3, 0.75) == 0);
        assertTrue(Double.compare(w4, 0.2500000000000001) == 0);
    }

    public void testInterpolationSinc() {

        double y0 = (-2.0 - 0.3)*(-2.0 - 0.3);
        double y1 = (-1.0 - 0.3)*(-1.0 - 0.3);
        double y2 = (0.0 - 0.3)*(0.0 - 0.3);
        double y3 = (1.0 - 0.3)*(1.0 - 0.3);
        double y4 = (2.0 - 0.3)*(2.0 - 0.3);
        double mu = 0.3;
        double y = MathUtils.interpolationSinc(y0, y1, y2, y3, y4, mu);

        double yExpected = -0.06751353045007912;
        assertTrue(Double.compare(y, yExpected) == 0);
    }
}