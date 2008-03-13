package org.esa.nest.dataio;

import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Feb 26, 2008
 * To change this template use File | Settings | File Templates.
 */
public class OrbitalDataRecordReader {

    private DataInputStream in = null;

    // header 1
    private String productSpecifier;
    private String satelliteName;
    private int arcStart;
    // header 2
    private int lengthOfRepeatCycle;
    private int arcNumber;
    private int numRecords = 0;
    private int version;
    // data records
    private OrbitDataRecord[] dataRecords;

    boolean readOrbitFile(String path) {
        if(OpenOrbitFile(path)) {
            parseHeader1();
            parseHeader2();
            if(numRecords > 0) {
                dataRecords = new OrbitDataRecord[numRecords];
                for(int i=0; i < numRecords; ++i) {
                    dataRecords[i] = parseDataRecord();
                }
            }
            return true;
        }
        return false;
    }

    boolean OpenOrbitFile(String path) {

        try {
        in = new DataInputStream(new BufferedInputStream(new FileInputStream(path)));
        } catch(FileNotFoundException e) {
            in = null;
            return false;
        }
        return true;
    }


    void parseHeader1() {
        if(in == null) return;
        
        try {
            productSpecifier = readAn(4);
            satelliteName = readAn(8);
            arcStart = in.readInt();
        } catch (IOException e) {

            System.out.print(e);
        }
    }

    void parseHeader2() {
        if(in == null) return;

        try {
            lengthOfRepeatCycle = in.readInt();
            arcNumber = in.readInt();
            numRecords = in.readInt();
            version = in.readInt();
        } catch (IOException e) {

            System.out.print(e);
        }
    }

    OrbitDataRecord parseDataRecord() {
        OrbitDataRecord data = new OrbitDataRecord();

        try {
            data.time = in.readInt();
            data.latitude = in.readInt();
            data.longitude = in.readInt();
            data.heightOfCenterOfMass = in.readInt();
        } catch (IOException e) {

            System.out.print(e);
        }
        return data;
    }

    public String getProductSpecifier() {
        return productSpecifier;
    }

    public String getSatelliteName() {
        return satelliteName;
    }

    public int getArcStart() {
        return arcStart;
    }

    public int getLengthOfRepeatCycle() {
        return lengthOfRepeatCycle;
    }

    public int getArcNumber() {
        return arcNumber;
    }

    public int getNumRecords() {
        return numRecords;
    }

    public int getVersion() {
        return version;
    }

    public OrbitDataRecord[] getDataRecords() {
        return dataRecords;
    }

    String readAn(final int n) throws IOException {

        final byte[] bytes = new byte[n];
        final int bytesRead;

        bytesRead = in.read(bytes);

        if (bytesRead != n) {
            final String message = "Error parsing file: expecting " + n + " bytes but got " + bytesRead;
            throw new IOException(message);
        }
        return new String(bytes);
    }


    static class OrbitDataRecord {
        int time;
        int latitude;
        int longitude;
        int heightOfCenterOfMass;
    }

}
