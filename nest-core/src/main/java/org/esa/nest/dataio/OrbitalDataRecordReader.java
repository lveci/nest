package org.esa.nest.dataio;

import org.esa.nest.util.DatUtils;

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
            in = new DataInputStream(new BufferedInputStream(DatUtils.getResourceAsStream(path)));
        } catch(Exception e) {
            in = null;
            return false;
        } 
        return true;
    }


    void parseHeader1() {
        if(in == null) return;
        
        try {
            // Product specifier ('@ODR' or 'xODR').
            productSpecifier = readAn(4);
            // Satellite name.
            satelliteName = readAn(8);
            // Advised start of the data arc (UTC secs past 1.0 Jan 1985)
            arcStart = in.readInt();
        } catch (IOException e) {

            System.out.print(e);
        }
    }

    void parseHeader2() {
        if(in == null) return;

        try {
            // Length of the repeat cycle in 10^-3 days.
            lengthOfRepeatCycle = in.readInt();
            // Arc number. This number refers to the orbital solution arcs as processed by DUT/DEOS.
            arcNumber = in.readInt();
            // Number of data records following the two headers.
            numRecords = in.readInt();

            // Version ID
            //Field number 4 of the second header record may contain a positive number
            //which refers to the list below.
            //
            // ID  Explanation
            //-------------------------------------------------------------------------------
            //  1  Orbits generated 'after the event'. Though generated with the operational
            //     software, they do not contain predictions. The gravity model used is
            //     GEM-T2.
            //  2  As 1. The gravity model used is PGS4591.
            //  3  Orbits generated with operational software in operational mode. Thus they
            //     also contain predictions. The gravity model used is GEM-T2.
            //  4  As 3. The gravity model used is PGS4591.
            //  5. Orbits generated with enhanced operational software (Version 920110),
            //     which is able to integrate over small orbital maneuvers. Thus predictions
            //     are accurate in case the magnitude of the orbital maneuver was know in
            //     advance. GEM-T2 model.
            //  6. As 5. With the PGS4591 gravity model.
            //  7. Orbits generated at 5 minute intervals. GEM-T2 model.
            //  9. Orbits generated with enhanced operational software (Version 920110),
            //     which is able to integrate over small orbital maneuvers.
            //     These arcs do not contain predictions. The 5.5-day arcs cover data
            //     until the end. GEM-T2 model.
            // 10. As 9. The gravity model used is PGS4591.
            //105. As 5. But with predictions removed. Arcs cut at 5.5 days.
            //201. 5.5-day arcs. No predictions. JGM-1 gravity model. Geodyn II version 9208.
            //     SLR-only solution.
            //202. 5.5-day arcs. No predictions. JGM-1 gravity model. Geodyn II version 9208.
            //     Solution based on SLR and altimeter tracking data.
            //301. JGM-2 gravity model, improved station coordinates, new GM.
            //     SLR-only solution.
            //302. Operational. JGM-3 gravity model, SLR-only solution.
            //303. as 301, altimeter (IGDR) xover data included as tracking data.
            //322. Precise, JGM-3 gravity model, Quick-look SLR, OPR single satellite xovers
            //304. JGM-3 gravity field, Quick-look SLR, OPR single satellite xovers
            //     DUT(LSC)95L03 coordinate solution.
            //305. as 404. Adds also OPR altimetric height above a seasonal mean sea
            //             surface as tracking data. 6-hourly drag parameters.
            //323. as 304.
            //324. as 304. Includes also ERS-1/2 dual satellite xovers. 6-hourly drag
            //             parameters.
            //404. as 304. DGM-E04 gravity field model used instead of JGM-3
            //405. as 305. DGM-E04 gravity field model used instead of JGM-3
            //424. as 324. DGM-E04 gravity field model used instead of JGM-3
            //504. as 304. EGM96 gravity field model used instead of JGM-3
            //505. as 305. EGM96 gravity field model used instead of JGM-3
            //524. as 324. EGM96 gravity field model used instead of JGM-3
            version = in.readInt();
            
        } catch (IOException e) {

            System.out.print(e);
        }
    }

    OrbitDataRecord parseDataRecord() {
        OrbitDataRecord data = new OrbitDataRecord();

        try {
            data.time = in.readInt();
            
            //latitude
            //@ODR: in microdegrees.
            //xODR: in 0.1 microdegrees.
            data.latitude = in.readInt();

            //longitude
            //@ODR: in microdegrees, interval 0 to 360 degrees.
	        //xODR: in 0.1 microdegrees, interval -180 to 180 degrees.
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
