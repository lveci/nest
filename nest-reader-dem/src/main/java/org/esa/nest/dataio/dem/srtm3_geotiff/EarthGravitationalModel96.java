
package org.esa.nest.dataio.dem.srtm3_geotiff;

import org.esa.beam.framework.dataop.dem.AbstractElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.util.SystemUtils;
import org.esa.nest.util.Settings;
import org.esa.nest.util.MathUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.StringTokenizer;

/**
 *               "WW15MGH.GRD"
 *
 * This file contains 1038961 point values in grid form.  The first row of the file is the "header" of the file
 * and shows the south, north, west, and east limits of the file followed by the grid spacing in n-s and e-w.
 * All values in the "header" are in DECIMAL DEGREES.
 *
 * The geoid undulation grid is computed at 15 arc minute spacings in north/south and east/west with the new
 * "EGM96" spherical harmonic potential coefficient set complete to degree and order 360 and a geoid height
 * correction value computed from a set of spherical harmonic coefficients ("CORRCOEF"), also to degree and
 * order 360.  The file is arranged from north to south, west to east (i.e., the data after the header is
 * the north most latitude band and is ordered from west to east).
 *
 * The coverage of this file is:
 *
 *                90.00 N  +------------------+
 *                         |                  |
 *                         | 15' spacing N/S  |
 *                         |                  |
 *                         |                  |
 *                         | 15' spacing E/W  |
 *                         |                  |
 *               -90.00 N  +------------------+
 *                        0.00 E           360.00 E
 */

public class EarthGravitationalModel96 {

    private static final String NAME = "ww15mgh.grd";
    private static final int NUM_LATS = 481; // 120*4 + 1  (cover 60 degree to -60 degree)
    private static final int NUM_LONS = 1441; // 360*4 + 1 (cover 0 degree to 360 degree)
    private static final int NUM_CHAR_PER_NORMAL_LINE = 74;
    private static final int NUM_CHAR_PER_SHORT_LINE = 11;
    private static final int NUM_CHAR_PER_EMPTY_LINE = 1;
    private static final int BLOCK_HEIGHT = 20;
    private static final int NUM_OF_BLOCKS_PER_LAT = 9;

    private float[][] egm = new float[NUM_LATS][NUM_LONS];

    public EarthGravitationalModel96() {

        // get absolute file path
        final String filePath = Settings.instance().get("AuxData/egm96AuxDataPath");
        final String fileName = filePath + File.separator + NAME;

        // get reader
        FileInputStream stream;
        try {
            stream = new FileInputStream(fileName);
        } catch(FileNotFoundException e) {
            throw new OperatorException("File not found: " + fileName);
        }

        final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        // read data from file and save them in 2-D array
        String line = "";
        StringTokenizer st;
        int rowIdx = 0;
        int colIdx = 0;
        try {
            // skip the 1st 120 lat lines line (90 deg to 61 deg 45 min)
            final int numLatLinesToSkip = 120; // (90 - 61 + 1)*4;
            final int numCharInHeader = NUM_CHAR_PER_NORMAL_LINE + NUM_CHAR_PER_EMPTY_LINE;
            final int numCharInEachLatLine = NUM_OF_BLOCKS_PER_LAT * BLOCK_HEIGHT * NUM_CHAR_PER_NORMAL_LINE +
                                             (NUM_OF_BLOCKS_PER_LAT + 1) * NUM_CHAR_PER_EMPTY_LINE +
                                             NUM_CHAR_PER_SHORT_LINE;

            final int totalCharToSkip = numCharInHeader + numCharInEachLatLine * numLatLinesToSkip;
            reader.skip(totalCharToSkip);

            // get the lat lines from 60 deg to -60 deg 45 min
            final int numLinesInEachLatLine = NUM_OF_BLOCKS_PER_LAT * (BLOCK_HEIGHT + 1) + 2;
            final int numLinesToRead = NUM_LATS * numLinesInEachLatLine;
            int linesRead = 0;
            for (int i = 0; i < numLinesToRead; i++) {

                line = reader.readLine();
                linesRead++;
                if (!line.equals("")) {
                    st = new StringTokenizer(line);
                    final int numCols = st.countTokens();
                    for (int j = 0; j < numCols; j++) {
                        egm[rowIdx][colIdx] = Float.parseFloat(st.nextToken());
                        colIdx++;
                    }
                }

                if (linesRead % numLinesInEachLatLine == 0) {
                    rowIdx++;
                    colIdx = 0;
                }
            }

            reader.close();
            stream.close();

        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }

    public float getEGM(double lat, double lon) {

        final double r = (60 - lat) / 0.25;
        final double c = (lon < 0? lon + 360 : lon)/ 0.25;

        final int r0 = (int)r;
        final int c0 = (int)c;
        final int r1 = Math.min(r0 + 1, NUM_LATS - 1);
        final int c1 = Math.min(c0 + 1, NUM_LONS - 1);

        final double n00 = egm[r0][c0];
        final double n01 = egm[r0][c1];
        final double n10 = egm[r1][c0];
        final double n11 = egm[r1][c1];

        final double dRow = r - r0;
        final double dCol = c - c0;

        return (float)MathUtils.interpolationBiLinear(n00, n01, n10, n11, dCol, dRow);
    }
}