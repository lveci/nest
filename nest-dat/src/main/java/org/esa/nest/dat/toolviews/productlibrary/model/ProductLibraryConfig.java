package org.esa.nest.dat.toolviews.productlibrary.model;

import org.esa.beam.util.Guardian;
import org.esa.beam.util.PropertyMap;
import org.esa.beam.visat.VisatApp;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Set;

/**
 * This class handles the configuration of the ProductGrabber.
 */
public class ProductLibraryConfig {

    private static final String REPOSITORIES_KEY = "productGrabber.repository.%1$d.dirPath";
    private static final String CURRENT_REPSOITORY_KEY = "productGrabber.repository.selected.dirPath";
    private static final String WINDOW_LOCATION_X_KEY = "productLibrary.window.locationX";
    private static final String WINDOW_LOCATION_Y_KEY = "productLibrary.window.locationY";
    private static final String WINDOW_WIDTH_KEY = "productLibrary.window.width";
    private static final String WINDOW_HEIGHT_KEY = "productLibrary.window.height";
    private static final String BASE_DIR = "BaseDir_";

    private final PropertyMap _properties;

    /**
     * Creates a new instance with the given {@link org.esa.beam.util.PropertyMap}.
     * The property map which is used to load and store the configuration.
     *
     * @param configuration the {@link org.esa.beam.util.PropertyMap}.
     */
    public ProductLibraryConfig(final PropertyMap configuration) {
        Guardian.assertNotNull("configuration", configuration);
        _properties = configuration;
    }

    /**
     * Sets the repositories.
     *
     * @param baseDir the repository base directory.
     */
    public void addBaseDir(final File baseDir) {
        _properties.setPropertyString(BASE_DIR+baseDir.getAbsolutePath(), baseDir.getAbsolutePath());
        VisatApp.getApp().savePreferences();
    }

    /**
     * removes the repositories.
     *
     * @param baseDir the repository base directory.
     */
    public void removeBaseDir(final File baseDir) {
        _properties.setPropertyString(BASE_DIR+baseDir.getAbsolutePath(), null);
        VisatApp.getApp().savePreferences();
    }

    /**
     * Retrieves the stored repositories.
     *
     * @return the stored repositories.
     */
    public File[] getBaseDirs() {
        final ArrayList<File> dirList = new ArrayList<File>();
        final Set keys = _properties.getProperties().keySet();
        for(Object o : keys) {
            if( o instanceof String) {
                final String key = (String)o;
                if(key.startsWith(BASE_DIR)) {
                    final String path = _properties.getPropertyString(key);
                    if(path != null) {
                        final File file = new File(path);
                        if(file.exists()) {
                            dirList.add(file);
                        }
                    }
                }
            }
        }
        return dirList.toArray(new File[dirList.size()]);
    }

    /**
     * Sets the window bounds of the ProductGrabber dialog.
     *
     * @param windowBounds the window bounds.
     */
    public void setWindowBounds(final Rectangle windowBounds) {
        _properties.setPropertyInt(WINDOW_LOCATION_X_KEY, windowBounds.x);
        _properties.setPropertyInt(WINDOW_LOCATION_Y_KEY, windowBounds.y);
        _properties.setPropertyInt(WINDOW_WIDTH_KEY, windowBounds.width);
        _properties.setPropertyInt(WINDOW_HEIGHT_KEY, windowBounds.height);
    }

    /**
     * Retrieves the window bounds of the ProductGrabber dialog.
     *
     * @return the window bounds.
     */
    public Rectangle getWindowBounds() {
        final int x = _properties.getPropertyInt(WINDOW_LOCATION_X_KEY, 50);
        final int y = _properties.getPropertyInt(WINDOW_LOCATION_Y_KEY, 50);
        final int width = _properties.getPropertyInt(WINDOW_WIDTH_KEY, 700);
        final int height = _properties.getPropertyInt(WINDOW_HEIGHT_KEY, 450);

        return new Rectangle(x, y, width, height);
    }

}