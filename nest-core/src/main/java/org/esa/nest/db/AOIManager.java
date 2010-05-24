package org.esa.nest.db;

import org.esa.beam.visat.VisatApp;
import org.esa.nest.util.ResourceUtils;

import java.io.File;
import java.util.ArrayList;

/**

 */
public class AOIManager {

    private final ArrayList<AOI> aoiList = new ArrayList<AOI>(5);
    public final static String LAST_INPUT_PATH = "aoi.last_input_path";
    public final static String LAST_OUTPUT_PATH = "aoi.last_output_path";
    public final static String LAST_GRAPH_PATH = "aoi.last_graph_path";

    public AOI[] getAOIList() {
        return aoiList.toArray(new AOI[aoiList.size()]);
    }

    public AOI createAOI(final File aoiFile) {
        final AOI aoi = new AOI(aoiFile);
        aoiList.add(aoi);
        return aoi;
    }

    public void removeAOI(final AOI aoi) {
        aoiList.remove(aoi);
    }

    public File getNewAOIFile() {
        return new File(getAOIFolder(), "aoi_"+(aoiList.size() + 1));
    }

    public AOI getAOIAt(final int index) {
        return aoiList.get(index);
    }

    public static File getAOIFolder() {
        final String homeUrl = System.getProperty(ResourceUtils.getContextID()+".home", ".");
        final File aoiFolder = new File(homeUrl, File.separator + "aoi");
        if(!aoiFolder.exists())
            aoiFolder.mkdirs();
        return aoiFolder;
    }

    public static String getLastInputPath() {
        return VisatApp.getApp().getPreferences().getPropertyString(LAST_INPUT_PATH, "");
    }

    public static void setLastInputPath(final String path) {
        VisatApp.getApp().getPreferences().setPropertyString(LAST_INPUT_PATH, path);
    }

    public static String getLastOutputPath() {
        return VisatApp.getApp().getPreferences().getPropertyString(LAST_OUTPUT_PATH, "");
    }

    public static void setLastOutputPath(final String path) {
        VisatApp.getApp().getPreferences().setPropertyString(LAST_OUTPUT_PATH, path);
    }

    public static String getLastGraphPath() {
        return VisatApp.getApp().getPreferences().getPropertyString(LAST_GRAPH_PATH,
                ResourceUtils.getGraphFolder("").getAbsolutePath());
    }

    public static void setLastGraphPath(final String path) {
        VisatApp.getApp().getPreferences().setPropertyString(LAST_GRAPH_PATH, path);
    }
}
