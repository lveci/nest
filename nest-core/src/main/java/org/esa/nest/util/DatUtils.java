package org.esa.nest.util;

import org.esa.beam.util.io.BeamFileFilter;
import org.esa.beam.util.io.FileUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.util.SystemUtils;

import javax.swing.*;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileInputStream;

import com.bc.ceres.core.runtime.internal.RuntimeActivator;

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

    public static File GetFilePath(String title, String formatName, String extension, String description,
                                     boolean isSave) {
        BeamFileFilter xmlFilter = new BeamFileFilter(formatName, extension, description);
        File file;
        if (isSave)
            file = VisatApp.getApp().showFileSaveDialog(title, false, xmlFilter, '.' + extension, description);
        else
            file = VisatApp.getApp().showFileOpenDialog(title, false, xmlFilter, extension);
        if (file == null) {
            return null;
        }

        return FileUtils.ensureExtension(file, extension);
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

    public static File getResourceAsFile(final String filename, Class theClass) throws IOException {

        // load from disk
        java.net.URL resURL = theClass.getClassLoader().getResource(filename);
        if (resURL != null) {
            return new File(resURL.getPath());
        }

        return null;
    }


    /**
     * Optionally creates and returns the current user's application data directory.
     *
     * @param force if true, the directory will be created if it didn't exist before
     * @return the current user's application data directory
     */
    public static File getApplicationUserDir(boolean force) {
        String contextId = null;
        if (RuntimeActivator.getInstance() != null
                && RuntimeActivator.getInstance().getModuleContext() != null) {
            contextId = RuntimeActivator.getInstance().getModuleContext().getRuntimeConfig().getContextId();
        }
        if (contextId == null) {
            contextId = System.getProperty("ceres.context", "nest");
        }
        final File dir = new File(SystemUtils.getUserHomeDir(), '.' + contextId);
        if (force && !dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }


    public static File findSystemFile(String filename)
    {
        // check userhome/.nest first
        File appHomePath = DatUtils.getApplicationUserDir(false);
        String filePath = appHomePath.getAbsolutePath()
                + File.separator + filename;
        
        File outFile = new File(filePath);
        if(outFile.exists())
            return outFile;

        // next check config folder
        String homeDir = System.getProperty("nest.home");
        if (homeDir != null && homeDir.length() > 0) {
            File homeDirFile = new File(homeDir);

            String homeDirStr = homeDirFile.getAbsolutePath();
            String settingsfilePath = homeDirStr + File.separator + "config" + File.separator + filename;

            File outFile2 = new File(settingsfilePath);
            if(outFile2.exists())
                return outFile2;

            int idx = homeDirStr.lastIndexOf(File.separator);
            settingsfilePath = homeDirStr.substring(0, idx) + File.separator + "config" + File.separator + filename;

            File outFile3 = new File(settingsfilePath);
            if(outFile3.exists())
                return outFile3;
        }

        return findHomeFolder(File.separator + "config" + File.separator + filename);
    }

    public static File findHomeFolder(String filename)
    {
        File homePath = SystemUtils.getBeamHomeDir();
        String homePathStr = homePath.getAbsolutePath();
        if(homePathStr.endsWith(".") && homePathStr.length() > 1)
            homePathStr = homePathStr.substring(0, homePathStr.lastIndexOf(File.separator));
        String settingsfilePath = homePathStr + filename;

        File outFile4 = new File(settingsfilePath);
        if(outFile4.exists())
            return outFile4;

        settingsfilePath = homePathStr.substring(0, homePathStr.lastIndexOf(File.separator)) + filename;

        File outFile5 = new File(settingsfilePath);
        if(outFile5.exists())
            return outFile5;

        settingsfilePath = homePathStr.substring(0, homePathStr.lastIndexOf(File.separator)) + File.separator + "beam" + filename;

        File outFile6 = new File(settingsfilePath);
        if(outFile6.exists())
            return outFile6;

        return null;
    }
}
