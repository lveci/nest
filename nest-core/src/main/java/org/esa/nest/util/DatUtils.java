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
import java.net.URISyntaxException;

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

    public static void deleteDir(final File dir) {
        if (dir.isDirectory()) {
            for (String aChild : dir.list()) {
                deleteDir(new File(dir, aChild));
            }
        }
        dir.delete();
	}

    public static File GetFilePath(String title, String formatName, String extension, String description,
                                     boolean isSave) {
        BeamFileFilter fileFilter = null;
        if(!extension.isEmpty()) {
            fileFilter = new BeamFileFilter(formatName, extension, description);
        }
        File file;
        if (isSave)
            file = VisatApp.getApp().showFileSaveDialog(title, false, fileFilter, '.' + extension, description);
        else
            file = VisatApp.getApp().showFileOpenDialog(title, false, fileFilter);
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
            try {
                return new FileInputStream(resURL.toURI().getPath());
            } catch(URISyntaxException e) {
                //
            }
        }

        return new FileInputStream(filename);
    }

    public static InputStream getResourceAsStream(final String filename, Class theClass) throws IOException {
        // Try to load resource from jar
        InputStream stream = ClassLoader.getSystemResourceAsStream(filename);
        if (stream != null) return stream;

        // If not found in jar, then load from disk
        java.net.URL resURL = theClass.getClassLoader().getResource(filename);
        if (resURL != null) {
            try {
                return new FileInputStream(resURL.toURI().getPath());
            } catch(URISyntaxException e) {
                //
            }
        }

        return new FileInputStream(filename);
    }

    public static File getResourceAsFile(final String filename, Class theClass) throws IOException {

        try {
            // load from disk
            java.net.URL resURL = theClass.getClassLoader().getResource(filename);
            if (resURL != null) {
                return new File(resURL.getFile());
            }
        } catch(Exception e) {
            throw new IOException("Unable to open "+ filename);
        }
        throw new IOException("resURL==null Unable to open "+ filename);
    }


    /**
     * Optionally creates and returns the current user's application data directory.
     *
     * @param forceCreate if true, the directory will be created if it didn't exist before
     * @return the current user's application data directory
     */
    public static File getApplicationUserDir(boolean forceCreate) {
        String contextId = null;
        if (RuntimeActivator.getInstance() != null
                && RuntimeActivator.getInstance().getModuleContext() != null) {
            contextId = RuntimeActivator.getInstance().getModuleContext().getRuntimeConfig().getContextId();
        }
        if (contextId == null) {
            contextId = System.getProperty("ceres.context", "nest");
        }
        final File dir = new File(SystemUtils.getUserHomeDir(), '.' + contextId);
        if (forceCreate && !dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * get the temporary data folder in the user's application data directory
     * @return the temp folder
     */
    public static File getApplicationUserTempDataDir() {
        final File tmpDir = new File(DatUtils.getApplicationUserDir(true), "temp");
        if(!tmpDir.exists())
            tmpDir.mkdirs();
        return tmpDir;
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

        return findInHomeFolder(File.separator + "config" + File.separator + filename);
    }

    public static File findHomeFolder()
    {
        final String nestHome = System.getProperty("nest.home");
        File homePath;
        if(nestHome == null)
            homePath = SystemUtils.getBeamHomeDir();
        else
            homePath = new File(nestHome);
        String homePathStr = homePath.getAbsolutePath();
        if(homePathStr.endsWith(".") && homePathStr.length() > 1)
            homePathStr = homePathStr.substring(0, homePathStr.lastIndexOf(File.separator));
        return new File(homePathStr);
    }

    public static File findInHomeFolder(String filename)
    {
        File homePath = findHomeFolder();
        String homePathStr = homePath.getAbsolutePath();
        if(homePathStr.endsWith(".") && homePathStr.length() > 1)
            homePathStr = homePathStr.substring(0, homePathStr.lastIndexOf(File.separator));
        String filePath = homePathStr + filename;

        File outFile = new File(filePath);
        if(outFile.exists())
            return outFile;

        filePath = homePathStr.substring(0, homePathStr.lastIndexOf(File.separator)) + filename;

        File outFile2 = new File(filePath);
        if(outFile2.exists())
            return outFile2;

        filePath = homePathStr.substring(0, homePathStr.lastIndexOf(File.separator)) + File.separator + "beam" + filename;

        File outFile3 = new File(filePath);
        if(outFile3.exists())
            return outFile3;

        System.out.println("findInHomeFolder "+filename+ " not found in " + homePath);

        return null;
    }
}
