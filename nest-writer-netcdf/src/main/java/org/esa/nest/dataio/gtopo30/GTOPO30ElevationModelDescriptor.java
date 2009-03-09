package org.esa.nest.dataio.gtopo30;

import org.esa.beam.framework.dataop.dem.AbstractElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.SystemUtils;
import org.esa.nest.util.Settings;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class GTOPO30ElevationModelDescriptor extends AbstractElevationModelDescriptor {

    public static final String NAME = "GTOPO30";
    public static final String DB_FILE_SUFFIX = ".DEM";
    public static final String ARCHIVE_URL_PATH = SystemUtils.BEAM_HOME_PAGE + "data/ACE.zip";
    public static final int NUM_X_TILES = 24;
    public static final int NUM_Y_TILES = 12;
    public static final int DEGREE_RES = 15;
    public static final int PIXEL_RES_X = 4800;
    public static final int PIXEL_RES_Y = 6000;
    public static final int NO_DATA_VALUE = -500;
    public static final int RASTER_WIDTH = NUM_X_TILES * PIXEL_RES_X;
    public static final int RASTER_HEIGHT = NUM_Y_TILES * PIXEL_RES_Y;
    public static final Datum DATUM = Datum.WGS_84;

    private File aceDemInstallDir = null;

    public GTOPO30ElevationModelDescriptor() {
    }

    public String getName() {
        return NAME;
    }

    public Datum getDatum() {
        return DATUM;
    }

    public float getNoDataValue() {
        return NO_DATA_VALUE;
    }

    @Override
    public File getDemInstallDir() {
        if (aceDemInstallDir == null) {
            final String path = Settings.instance().get("gtopo30DEMDataPath");
            aceDemInstallDir = new File(path);
        }
        return aceDemInstallDir;
    }

    public boolean isDemInstalled() {
        final File file = getTileFile(-180, -60);
        return file.canRead();
    }

    public URL getDemArchiveUrl() {
        try {
            return new URL(ARCHIVE_URL_PATH);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("MalformedURLException not expected: " + ARCHIVE_URL_PATH);
        }
    }

    public ElevationModel createDem() {
        try {
            return new GTOPO30ElevationModel(this);
        } catch (IOException e) {
            return null;
        }
    }

    public File getTileFile(int minLon, int minLat) {
        return new File(getDemInstallDir(), createTileFilename(minLat, minLon));
    }

    public static String createTileFilename(int minLat, int minLon) {
        String latString = minLat < 0 ? "S" + Math.abs(minLat) : "N" +minLat;
        while (latString.length() < 3) {
            latString = '0' + latString;
        }
        String lonString = minLon < 0 ? "W" + Math.abs(minLon) : "E" + minLon;
        while (lonString.length() < 4) {
            lonString = '0' + lonString;
        }
        return lonString + latString + DB_FILE_SUFFIX;
    }

}