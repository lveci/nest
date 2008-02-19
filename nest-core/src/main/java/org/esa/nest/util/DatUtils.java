package org.esa.nest.util;

import org.esa.beam.util.io.BeamFileFilter;
import org.esa.beam.util.io.FileUtils;
import org.esa.beam.visat.SharedApp;

import javax.swing.*;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileInputStream;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jan 29, 2008
 * Time: 2:18:16 PM
 * To change this template use File | Settings | File Templates.
 */
public class DatUtils {


    public static ImageIcon LoadIcon(String path) {
        java.net.URL imageURL = DatUtils.class.getClassLoader().getResource(path);
        if (imageURL == null) return null;
        return new ImageIcon(imageURL);
    }

    public static String GetFilePath(String title, String formatName, String extension, String description,
                                     boolean isSave) {
        BeamFileFilter xmlFilter = new BeamFileFilter(formatName, extension, description);
        File file;
        if (isSave)
            file = SharedApp.instance().getApp().showFileSaveDialog(title, false, xmlFilter, '.' + extension, description);
        else
            file = SharedApp.instance().getApp().showFileOpenDialog(title, false, xmlFilter, extension);
        if (file == null) {
            return null;
        }

        file = FileUtils.ensureExtension(file, extension);
        return file.getAbsolutePath();
    }

     public static InputStream getResourceAsStream(final String filename) throws IOException {
        // Try to load resource from jar
        InputStream stream = ClassLoader.getSystemResourceAsStream(filename);
        if (stream != null) return stream;

        // If not found in jar, then load from disk
        java.net.URL resURL = DatUtils.class.getClassLoader().getResource(filename);
        if (resURL != null) {
            return new FileInputStream(resURL.getPath());
        }

        return new FileInputStream(filename);
    }

}
