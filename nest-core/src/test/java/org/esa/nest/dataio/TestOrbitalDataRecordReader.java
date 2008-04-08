package org.esa.nest.dataio;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.framework.TestCase;

/**
 * OrbitalDataRecordReader Tester.
 *
 * @author <Authors name>
 * @since <pre>02/26/2008</pre>
 * @version 1.0
 */
public class TestOrbitalDataRecordReader extends TestCase {

    String envisatOrbitFilePath = "org/esa/nest/data/envisat_ODR.051";
    String ers1OrbitFilePath = "org/esa/nest/data/ers1_ODR.051";
    String ers2OrbitFilePath = "org/esa/nest/data/ers2_ODR.051";

    public TestOrbitalDataRecordReader(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testOpenFile() {

        OrbitalDataRecordReader reader = new OrbitalDataRecordReader();

        assertTrue(reader.OpenOrbitFile(envisatOrbitFilePath));
    }

    public void testReadHeader() {

        OrbitalDataRecordReader reader = new OrbitalDataRecordReader();

        if(reader.OpenOrbitFile(envisatOrbitFilePath)) {

            reader.parseHeader1();
            reader.parseHeader2();
        }
    }

    public void testReadOrbitFile() {
        OrbitalDataRecordReader reader = new OrbitalDataRecordReader();
        reader.readOrbitFile(envisatOrbitFilePath);
    }

    public void testReadERS1OrbitFile() {
        OrbitalDataRecordReader reader = new OrbitalDataRecordReader();
        reader.readOrbitFile(ers1OrbitFilePath);
    }
    
    public void testReadERS2OrbitFile() {
        OrbitalDataRecordReader reader = new OrbitalDataRecordReader();
        reader.readOrbitFile(ers2OrbitFilePath);
    }
}
