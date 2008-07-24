package org.esa.nest.dataio;

import junit.framework.TestCase;

/**
 * OrbitalDataRecordReader Tester.
 *
 * @author lveci
 */
public class TestOrbitalDataRecordReader extends TestCase {

    String envisatOrbitFilePath = "org/esa/nest/data/envisat_ODR.051";
    String ers1OrbitFilePath = "org/esa/nest/data/ers1_ODR.079";
    String ers2OrbitFilePath = "org/esa/nest/data/ers2_ODR.015";

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
        } else
            assertTrue(false);
    }

    public void testReadERS1OrbitFiles() {
        System.out.print("ERS1 ORD ");
        readOrbitFile(ers1OrbitFilePath);
    }

    public void testReadERS2OrbitFile() {
        System.out.print("ERS2 ORD ");
        readOrbitFile(ers2OrbitFilePath);
    }

    public void testReadEnvisatOrbitFile() {
        System.out.print("Envisat ORD ");
        readOrbitFile(envisatOrbitFilePath);
    }

    public static void readOrbitFile(String path) {
        OrbitalDataRecordReader reader = new OrbitalDataRecordReader();
        boolean res = reader.readOrbitFile(path);
        assertTrue(res);

        OrbitalDataRecordReader.OrbitDataRecord[] orbits = reader.getDataRecords();
        System.out.print("Num Orbits " + orbits.length);
        for(int i=0; i < 2; ++i) {
            System.out.print(" Orbit time " + orbits[i].time);
            System.out.print(" lat " + orbits[i].latitude);
            System.out.print(" lng " + orbits[i].longitude);
            System.out.print(" hgt " + orbits[i].heightOfCenterOfMass);
            System.out.println();
        }
    }

}
