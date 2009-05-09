package org.esa.nest.dataio;

import org.esa.nest.util.DatUtils;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.util.MathUtils;
import org.esa.beam.framework.gpf.OperatorException;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.FileImageInputStream;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.Arrays;

import java.io.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Feb 26, 2008
 * To change this template use File | Settings | File Templates.
 */
public final class PrareOrbitReader {

    private DataInputStream in = null;

    private DataSetIdentificationRecord dataSetIdentificationRecord;
    private DataHeaderRecord dataHeaderRecord;
    private QualityParameterRecord[] qualityParameterRecords;

    private OrbitVector[] orbitVectors = null;
    private double[] recordTimes = null;
    private int numOfTrajectoryRecords;
    private int numOfQualityParameterRecords;

    private static final int sizeOfDataSetIdentificationRecord = 130;
    private static final int sizeOfDataHeaderRecord = 130;
    private static final int sizeOfTrajectoryRecord = 130;
    private static final int sizeOfQualityParameterRecord = 130;
    private static final int maxNumOfQualityParameterRecords = 20;

    private static final double millimeterToMeter = 0.001;
    private static final double microMeterToMeter = 0.000001;
    private static final double microSecondToSecond = 0.000001;
    private static final double secondToDay = 1.0 / (24*3600);

    /**
     * Read Data Set Identification Record and Data Header Record from the orbit file.
     * @param file The orbit file.
     * @throws IOException The exceptions.
     */
    public void readOrbitHeader(File file) throws IOException {

        BufferedReader reader = getBufferedReader(file);

        readDataSetIdentificationRecord(reader);

        readDataHeaderRecord(reader);

        reader.close();
    }

    /**
     * Get buffered reader for given file ASCII.
     * @param file The file.
     * @return The reader.
     */
    private BufferedReader getBufferedReader(File file) {

        String fileName = file.getAbsolutePath();
        FileInputStream stream;
        try {
            stream = new FileInputStream(fileName);
        } catch(FileNotFoundException e) {
            throw new OperatorException("File not found: " + fileName);
        }

        return new BufferedReader(new InputStreamReader(stream));
    }

    /**
     * Read Data Set Identification Record.
     * @param reader The buffered reeader.
     * @throws IOException The exceptions.
     */
    private void readDataSetIdentificationRecord(BufferedReader reader) throws IOException {

        char[] recKey = new char[6];
        char[] prodID = new char[15];
        char[] datTyp = new char[6];

        reader.read(recKey, 0, 6);
        reader.read(prodID, 0, 15);
        reader.read(datTyp, 0, 6);
        reader.skip(103);

        dataSetIdentificationRecord = new DataSetIdentificationRecord();
        dataSetIdentificationRecord.recKey = new String(recKey);
        dataSetIdentificationRecord.prodID = new String(prodID);
        dataSetIdentificationRecord.datTyp = new String(datTyp);
    }

    /**
     * Read Data Header Record.
     * @param reader The buffered reeader.
     * @throws IOException The exceptions.
     */
    private void readDataHeaderRecord(BufferedReader reader) throws IOException {

        char[] recKey = new char[6];
        char[] start = new char[6]; // 0.1 days
        char[] end = new char[6];   // 0.1 days
        char[] obsTyp = new char[6];
        char[] obsLev = new char[6];
        char[] modID = new char[2]; // 0, 1, 2
        char[] relID = new char[2];
        char[] rmsFit = new char[4]; // mm
        char[] sigPos = new char[4]; // mm
        char[] sigVel = new char[4]; // micro meter / s
        char[] qualit = new char[1];
        char[] tdtUtc = new char[5]; // s
        char[] cmmnt = new char[78];

        reader.read(recKey, 0, 6);
        reader.read(start, 0, 6);
        reader.read(end, 0, 6);
        reader.read(obsTyp, 0, 6);
        reader.read(obsLev, 0, 6);
        reader.read(modID, 0, 2);
        reader.read(relID, 0, 2);
        reader.read(rmsFit, 0, 4);
        reader.read(sigPos, 0, 4);
        reader.read(sigVel, 0, 4);
        reader.read(qualit, 0, 1);
        reader.read(tdtUtc, 0, 5);
        reader.read(cmmnt, 0, 78);

        dataHeaderRecord = new DataHeaderRecord();
        dataHeaderRecord.recKey = new String(recKey);
        dataHeaderRecord.start = Float.parseFloat(new String(start).trim());
        dataHeaderRecord.end = Float.parseFloat(new String(end).trim());
        dataHeaderRecord.obsTyp = new String(obsTyp);
        dataHeaderRecord.obsLev = new String(obsLev);
        dataHeaderRecord.modID = Integer.parseInt(new String(modID).trim());
        dataHeaderRecord.relID = Integer.parseInt(new String(relID).trim());
        dataHeaderRecord.rmsFit = Integer.parseInt(new String(rmsFit).trim());
        dataHeaderRecord.sigPos = Integer.parseInt(new String(sigPos).trim());
        dataHeaderRecord.sigVel = Integer.parseInt(new String(sigVel).trim());
        dataHeaderRecord.qualit = Integer.parseInt(new String(qualit).trim());
        dataHeaderRecord.tdtUtc = Float.parseFloat(new String(tdtUtc).trim());
        dataHeaderRecord.cmmnt = new String(cmmnt);
    }

    /**
     * Read complete CTS orbit data records from the orbit file.
     * @param file The PRARE orbit file.
     * @throws IOException The exceptions.
     */
    public void readOrbitData(File file) throws IOException {

        computeNumberOfRecords(file);

        orbitVectors = new OrbitVector[numOfTrajectoryRecords];

        recordTimes = new double[numOfTrajectoryRecords];

        final int numCharactersSkip = sizeOfDataSetIdentificationRecord + sizeOfDataHeaderRecord +
                                      numOfTrajectoryRecords * sizeOfTrajectoryRecord;

        BufferedReader reader = getBufferedReader(file);

        reader.skip((long)numCharactersSkip);

        for (int i = 0; i < numOfTrajectoryRecords; i++) {

            TrajectoryRecord dataRecord = readTrajectoryRecord(reader);

            orbitVectors[i] = new OrbitVector();
            // todo need to convert TDT time to UTC time
            orbitVectors[i].utcTime = (double)dataRecord.tTagD*0.1 +  0.5 +
                                      (double)dataRecord.tTagMs * microSecondToSecond * secondToDay;
            orbitVectors[i].xPos = (double)dataRecord.xSat * millimeterToMeter;
            orbitVectors[i].yPos = (double)dataRecord.ySat * millimeterToMeter;
            orbitVectors[i].zPos = (double)dataRecord.zSat * millimeterToMeter;
            orbitVectors[i].xVel = (double)dataRecord.xDSat * microMeterToMeter;
            orbitVectors[i].yVel = (double)dataRecord.yDSat * microMeterToMeter;
            orbitVectors[i].zVel = (double)dataRecord.zDSat * microMeterToMeter;

            recordTimes[i] = orbitVectors[i].utcTime;
        }

        qualityParameterRecords = new QualityParameterRecord[numOfQualityParameterRecords];
        for (int j = 0; j < numOfQualityParameterRecords; j++) {
            qualityParameterRecords[j] = readQualityParameterRecord(reader);
        }

        reader.close();
    }

    /**
     * Compute the number of Trajectory Records in the orbit file.
     * @param file The orbit file.
     * @throws IOException The exceptions.
     */
    private void computeNumberOfRecords(File file) throws IOException {

        int fileSize = (int)file.length();

        int numRecordsApprox = (fileSize - sizeOfDataSetIdentificationRecord - sizeOfDataHeaderRecord -
                maxNumOfQualityParameterRecords*sizeOfQualityParameterRecord) / (2*sizeOfTrajectoryRecord);

        int numCharactersSkip = sizeOfDataSetIdentificationRecord + sizeOfDataHeaderRecord +
                                numRecordsApprox * sizeOfTrajectoryRecord;

        BufferedReader reader = getBufferedReader(file);

        reader.skip((long)numCharactersSkip);

        char[] dataRecord = new char[sizeOfTrajectoryRecord];

        int k = 0;
        while (true) {
            reader.read(dataRecord, 0, sizeOfTrajectoryRecord);
            if (dataRecord[0] == 'S' && dataRecord[1] == 'T' && dataRecord[2] == 'T' &&
                dataRecord[3] == 'E' && dataRecord[4] == 'R' && dataRecord[5] == 'R') {
                break;
            }
            k++;
        }
        reader.close();

        numOfTrajectoryRecords = numRecordsApprox + k;

        numOfQualityParameterRecords = maxNumOfQualityParameterRecords - 2*k;
    }

    /**
     * Read one Trajectory Record from the orbit file.
     * @param reader The buffered reeader.
     * @return The Trajectory Record.
     * @throws IOException The exceptions.
     */
    private TrajectoryRecord readTrajectoryRecord(BufferedReader reader) throws IOException {

        char[] recKey = new char[6];
        char[] satID = new char[7];
        char[] orbTyp = new char[1];
        char[] tTagD = new char[6];   // 0.1 days
        char[] tTagMs = new char[11]; // micro seconds
        char[] xSat = new char[12];   // mm
        char[] ySat = new char[12];   // mm
        char[] zSat = new char[12];   // mm
        char[] xDSat = new char[11];  // micro seconds / s
        char[] yDSat = new char[11];  // micro seconds / s
        char[] zDSat = new char[11];  // micro seconds / s
        char[] roll = new char[6];    // 0.001 deg
        char[] pitch = new char[6];   // 0.001 deg
        char[] yaw = new char[6];     // 0.001 deg
        char[] ascArc = new char[2];
        char[] check = new char[3];
        char[] quali = new char[1];
        char[] radCor = new char[4];

        reader.read(recKey, 0, 6);
        reader.read(satID, 0, 7);
        reader.read(orbTyp, 0, 1);
        reader.read(tTagD, 0, 6);
        reader.read(tTagMs, 0, 11);
        reader.read(xSat, 0, 12);
        reader.read(ySat, 0, 12);
        reader.read(zSat, 0, 12);
        reader.read(xDSat, 0, 11);
        reader.read(yDSat, 0, 11);
        reader.read(zDSat, 0, 11);
        reader.read(roll, 0, 6);
        reader.read(pitch, 0, 6);
        reader.read(yaw, 0, 6);
        reader.read(ascArc, 0, 2);
        reader.read(check, 0, 3);
        reader.read(quali, 0, 1);
        reader.read(radCor, 0, 4);
        reader.skip(2);

        TrajectoryRecord trajectoryRecord = new TrajectoryRecord();
        trajectoryRecord.recKey = new String(recKey);
        trajectoryRecord.satID = Integer.parseInt(new String(satID).trim());
        trajectoryRecord.orbTyp = new String(orbTyp);
        trajectoryRecord.tTagD = Float.parseFloat(new String(tTagD).trim());
        trajectoryRecord.tTagMs = Long.parseLong(new String(tTagMs).trim());
        trajectoryRecord.xSat = Long.parseLong(new String(xSat).trim());
        trajectoryRecord.ySat = Long.parseLong(new String(ySat).trim());
        trajectoryRecord.zSat = Long.parseLong(new String(zSat).trim());
        trajectoryRecord.xDSat = Long.parseLong(new String(xDSat).trim());
        trajectoryRecord.yDSat = Long.parseLong(new String(yDSat).trim());
        trajectoryRecord.zDSat = Long.parseLong(new String(zDSat).trim());
        trajectoryRecord.roll = Float.parseFloat(new String(roll).trim());
        trajectoryRecord.pitch = Float.parseFloat(new String(pitch).trim());
        trajectoryRecord.yaw = Float.parseFloat(new String(yaw).trim());
        trajectoryRecord.ascArc = Integer.parseInt(new String(ascArc).trim());
        trajectoryRecord.check = Integer.parseInt(new String(check).trim());
        trajectoryRecord.quali = Integer.parseInt(new String(quali).trim());
        trajectoryRecord.radCor = Integer.parseInt(new String(radCor).trim());

        return trajectoryRecord;
    }

    /**
     * Read one Quality Parameter Record from the orbit file.
     * @param reader The buffered reeader.
     * @return The Quality Parameter Record.
     * @throws IOException The exceptions.
     */
    private QualityParameterRecord readQualityParameterRecord(BufferedReader reader) throws IOException {

        char[] recKey = new char[6];
        char[] qPName = new char[25];
        char[] qPValue = new char[10];
        char[] qPUnit = new char[26];
        char[] qPRefVal = new char[10];

        reader.read(recKey, 0, 6);
        reader.read(qPName, 0, 25);
        reader.skip(3);
        reader.read(qPValue, 0, 10);
        reader.read(qPUnit, 0, 26);
        reader.read(qPRefVal, 0, 10);
        reader.skip(50);

        QualityParameterRecord qualityParameterRecord = new QualityParameterRecord();
        qualityParameterRecord.recKey = new String(recKey);
        qualityParameterRecord.qPName = new String(qPName);
        qualityParameterRecord.qPValue = new String(qPValue);
        qualityParameterRecord.qPUnit = new String(qPUnit);
        qualityParameterRecord.qPRefVal = new String(qPRefVal);

        return qualityParameterRecord;
    }

    /**
     * Get the start time of the Arc in MJD (in days).
     * @return The start time.
     */
    public float getSensingStart() {
        // The start time is given as Julian days since 1.1.2000 12h in TDT.
        // Add 0.5 days to make it as Julian days since 1.1.2000 0h in TDT. 
        // todo need to convert TDT time to UTC time
        return dataHeaderRecord.start*0.1f + 0.5f; // 0.1 days to days
    }

    /**
     * Get the end time of the Arc in MJD (in days).
     * @return The end time.
     */
    public float getSensingStop() {
        // The end time is given as Julian days since 1.1.2000 12h in TDT.
        // Add 0.5 days to make it as Julian days since 1.1.2000 0h in TDT.
        // todo need to convert TDT time to UTC time
        return dataHeaderRecord.end*0.1f + 0.5f; // 0.1 days to days
    }

    /**
     * Get Data Set Identification Record.
     * @return The record.
     */
    public DataSetIdentificationRecord getDataSetIdentificationRecord() {
        return dataSetIdentificationRecord;
    }

    /**
     * Get Data Header Record.
     * @return The record.
     */
    public DataHeaderRecord getDataHeaderRecord() {
        return dataHeaderRecord;
    }

    /**
     * Get Quality Parameter Record.
     * @param n The record index.
     * @return The record.
     */
    public QualityParameterRecord getQualityParameterRecord(int n) {
        return qualityParameterRecords[n];
    }

    /**
     * Get orbit vector.
     * @param n The vector index.
     * @return The orbit vector.
     */
    public OrbitVector getOrbitVector(int n) {
        return orbitVectors[n];
    }

    /**
     * Get orbit vector for given UTC time.
     * @param utc The UTC time.
     * @throws Exception for incorrect time.
     * @return The orbit vector.
     */
    public OrbitVector getOrbitVector(double utc) throws Exception {

        final int n = Arrays.binarySearch(recordTimes, utc);

        if (n >= 0) {
			return orbitVectors[n];
		}

		final int n2 = -n - 1;
        final int n0 = n2 - 2;
        final int n1 = n2 - 1;
        final int n3 = n2 + 1;

        if (n0 < 0 || n1 < 0 || n2 >= recordTimes.length || n3 >= recordTimes.length) {
            throw new Exception("Incorrect UTC time");
        }

        final double mu = (utc - recordTimes[n1]) / (recordTimes[n2] - recordTimes[n1]);

        OrbitVector orb = new OrbitVector();

        orb.utcTime = MathUtils.interpolationCubic(
            orbitVectors[n0].utcTime, orbitVectors[n1].utcTime, orbitVectors[n2].utcTime, orbitVectors[n3].utcTime, mu);

        orb.xPos = MathUtils.interpolationCubic(
            orbitVectors[n0].xPos, orbitVectors[n1].xPos, orbitVectors[n2].xPos, orbitVectors[n3].xPos, mu);

        orb.yPos = MathUtils.interpolationCubic(
            orbitVectors[n0].yPos, orbitVectors[n1].yPos, orbitVectors[n2].yPos, orbitVectors[n3].yPos, mu);

        orb.zPos = MathUtils.interpolationCubic(
            orbitVectors[n0].zPos, orbitVectors[n1].zPos, orbitVectors[n2].zPos, orbitVectors[n3].zPos, mu);

        orb.xVel = MathUtils.interpolationCubic(
            orbitVectors[n0].xVel, orbitVectors[n1].xVel, orbitVectors[n2].xVel, orbitVectors[n3].xVel, mu);

        orb.yVel = MathUtils.interpolationCubic(
            orbitVectors[n0].yVel, orbitVectors[n1].yVel, orbitVectors[n2].yVel, orbitVectors[n3].yVel, mu);

        orb.zVel = MathUtils.interpolationCubic(
            orbitVectors[n0].zVel, orbitVectors[n1].zVel, orbitVectors[n2].zVel, orbitVectors[n3].zVel, mu);

        return orb;
    }

    // PRC Data Set Identification Record
    public final static class DataSetIdentificationRecord {
        String recKey;
        String prodID;
        String datTyp;
    }

    // PRC Data Header Record
    public final static class DataHeaderRecord {
        String recKey;
        float start;  // 0.1 days
        float end;    // 0.1 days
        String obsTyp;
        String obsLev;
        int modID;    // 0, 1, 2
        int relID;
        int rmsFit;   // mm
        int sigPos;   // mm
        int sigVel;   // micro meter / s
        int qualit;
        float tdtUtc; // s
        String cmmnt;
    }

    // PRC Trajectory Record (Inertial Frame or Terrestrial Frame)
   public final  static class TrajectoryRecord {
        String recKey;
        int satID;
        String orbTyp;
        float tTagD; // 0.1 days
        long tTagMs;  // micro seconds
        long xSat;    // mm
        long ySat;    // mm
        long zSat;    // mm
        long xDSat;   // micro meters / s
        long yDSat;   // micro meters / s
        long zDSat;   // micro meters / s
        float roll;  // 0.001 deg
        float pitch; // 0.001 deg
        float yaw;   // 0.001 deg
        int ascArc;  // 0, 1
        int check;
        int quali;   // 0, 1
        int radCor;  // cm
    }

    // PRC Quality Parameter Record
    public final static class QualityParameterRecord {
        String recKey;
        String qPName;
        String qPValue;
        String qPUnit;
        String qPRefVal;
    }

    public final static class OrbitVector {
        public double utcTime = 0;
        public double xPos = 0;
        public double yPos = 0;
        public double zPos = 0;
        public double xVel = 0;
        public double yVel = 0;
        public double zVel = 0;
    }
}
