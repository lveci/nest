
package org.esa.nest.dataio.dem.ace2_5min;

import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import com.bc.io.FileDownloader;
import com.bc.io.FileUnpacker;
import org.esa.beam.framework.dataop.dem.AbstractElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.util.Settings;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class ACE2_5MinElevationModelDescriptor extends AbstractElevationModelDescriptor {

    public static final String NAME = "ACE2_5Min";
    public static final String DB_FILE_SUFFIX = "_5M.ACE2";
    public static final String ARCHIVE_URL_PATH = "http://nest.s3.amazonaws.com/data/5M_HEIGHTS.zip";
    public static final int NUM_X_TILES = 24;
    public static final int NUM_Y_TILES = 12;
    public static final int DEGREE_RES = 15;
    public static final int PIXEL_RES = 180;
    public static final int NO_DATA_VALUE = -500;
    public static final int RASTER_WIDTH = NUM_X_TILES * PIXEL_RES;
    public static final int RASTER_HEIGHT = NUM_Y_TILES * PIXEL_RES;
    public static final Datum DATUM = Datum.WGS_84;

    private File aceDemInstallDir = null;

    public ACE2_5MinElevationModelDescriptor() {
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
        if(aceDemInstallDir == null) {
            final String path = Settings.instance().get("DEM/ace2_5MinDEMDataPath");
            aceDemInstallDir = new File(path);
        }
        return aceDemInstallDir;
    }

    public boolean isDemInstalled() {
        final File file = getTileFile(-180, -90);   // todo (nf) - check all tiles
        return file.canRead();
    }

    public URL getDemArchiveUrl() {
        try {
            return new URL(ARCHIVE_URL_PATH);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("MalformedURLException not expected: " + ARCHIVE_URL_PATH);
        }
    }

    @Deprecated
    public ElevationModel createDem() {
        try {
            if(!isDemInstalled()) {
                installDemFiles(null);
            }
            return new ACE2_5MinElevationModel(this, Resampling.NEAREST_NEIGHBOUR);
        } catch (IOException e) {
            return null;
        }
    }

    public ElevationModel createDem(Resampling resamplingMethod) {
        try {
            if(!isDemInstalled()) {
                installDemFiles(null);
            }
            return new ACE2_5MinElevationModel(this, resamplingMethod);
        } catch (IOException e) {
            return null;
        }
    }

    public File getTileFile(int minLon, int minLat) {
        return new File(getDemInstallDir(), createTileFilename(minLat, minLon));
    }

    public static String createTileFilename(int minLat, int minLon) {
        String latString = minLat < 0 ? Math.abs(minLat) + "S" : minLat + "N";
        while (latString.length() < 3) {
            latString = '0' + latString;
        }
        String lonString = minLon < 0 ? Math.abs(minLon) + "W" : minLon + "E";
        while (lonString.length() < 4) {
            lonString = '0' + lonString;
        }
        return latString + lonString + DB_FILE_SUFFIX;
    }

    @Override
    public synchronized boolean installDemFiles(Object uiComponent) {
        if (isDemInstalled()) {
            return true;
        }
        if (isInstallingDem()) {
            return true;
        }
        final Component parent = uiComponent instanceof Component ? (Component) uiComponent : null;

        final File demInstallDir = getDemInstallDir();
        if (!demInstallDir.exists()) {
            final boolean success = demInstallDir.mkdirs();
            if (!success) {
                return false;
            }
        }

        try {
            final VisatApp visatApp = VisatApp.getApp();
            if(visatApp != null) {
                visatApp.setStatusBarMessage("Downloading ACE2 5Min DEM...");
            }
            if(false) {//visatApp != null) {
                installWithProgressMonitor(parent);
            } else {
                final File archiveFile = FileDownloader.downloadFile(getDemArchiveUrl(), demInstallDir, parent);
                FileUnpacker.unpackZip(archiveFile, demInstallDir, parent);
                archiveFile.delete();
            }
            if(visatApp != null) {
                visatApp.setStatusBarMessage("");
            }
        } catch(Exception e) {
            return false;
        }
        return true;
    }

    private void installWithProgressMonitor(final Component parent) {
        final ProgressMonitorSwingWorker worker = new ProgressMonitorSwingWorker(VisatApp.getApp().getMainFrame(),
                "Installing Ace2 5min DEM...") {
            @Override
            protected Object doInBackground(com.bc.ceres.core.ProgressMonitor pm) throws Exception {

                pm.beginTask("Installing Ace2 5min DEM", 3);
                try {
                    final URL archiveUrl = getDemArchiveUrl();
                    final File demInstallDir = getDemInstallDir();

                    final File archiveFile = FileDownloader.downloadFile(archiveUrl, demInstallDir, parent);
                    pm.worked(1);
                    FileUnpacker.unpackZip(archiveFile, demInstallDir, parent);
                    pm.worked(1);
                    archiveFile.delete();
                    pm.worked(1);
                } catch(Exception e) {
                    System.out.println(e.getMessage());
                    return false;
                } finally {
                    pm.done();
                }
                return true;
            }
        };
        worker.executeWithBlocking();
    }
}