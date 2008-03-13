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

    String orbitFilePath = "P:\\nest\\nest\\ESA Data\\Orbits\\ODR.ENVISAT1\\eigen-cg03c\\ODR.051";

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

        assertTrue(reader.OpenOrbitFile(orbitFilePath));
    }

    public void testReadHeader() {

        OrbitalDataRecordReader reader = new OrbitalDataRecordReader();

        if(reader.OpenOrbitFile(orbitFilePath)) {

            reader.parseHeader1();
            reader.parseHeader2();
        }
    }

    public void testReadOrbitFile() {
        OrbitalDataRecordReader reader = new OrbitalDataRecordReader();
        reader.readOrbitFile(orbitFilePath);
    }
}
