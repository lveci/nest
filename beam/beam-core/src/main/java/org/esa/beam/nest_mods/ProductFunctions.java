package org.esa.beam.nest_mods;

import org.esa.beam.util.io.FileUtils;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.dataio.dimap.DimapProductConstants;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Apr 14, 2011
 * Time: 1:18:27 PM
 * To change this template use File | Settings | File Templates.
 */
public class ProductFunctions {
        //NESTMOD

    private static String[] validExtensions = {".dim",".n1",".e1",".e2",".h5",".tif"};

    private static final String[] nonValidExtensions = { "xsd", "xsl", "xls", "pdf", "txt", "doc", "ps", "db", "ief", "ord",
                                                   "tfw", "gif", "jpg", "jgw", "hdr", "self", "report", "raw", "tgz",
                                                   "log", "html", "htm", "png", "bmp", "ps", "aux", "ovr", "brs", "kml", "kmz",
                                                   "sav", "7z", "zip", "rrd", "lbl", "z", "gz", "exe", "bat", "sh", "rtf",
                                                   "prj", "dbf", "shx", "ace", "ace2", "tar"};
    private static final String[] nonValidprefixes = { "led", "trl", "tra_", "nul", "lea", "dat", "img", "imop", "sarl", "sart",
                                                 "dfas", "dfdn", "lut",
                                                 "readme", "l1b_iif", "dor_vor", "imagery_", "browse" };

    public static boolean isValidProduct(final File file) {
        final String ext = FileUtils.getExtension(file).toLowerCase();
        for(String str : validExtensions) {
            if(ext.equals(str)) {
                return true;
            }
        }
        // test with readers
        final ProductReader reader = ProductIO.getProductReaderForFile(file);
        if(reader != null)
            return true;

        return false;
    }

    /**
     * recursively scan a folder for valid files and put them in pathList
     * @param inputFolder starting folder
     * @param pathList list of products found
     */
    public static void scanForValidProducts(final File inputFolder, final ArrayList<String> pathList) {
        final ValidProductFileFilter dirFilter = new ValidProductFileFilter();
        final File[] files = inputFolder.listFiles(dirFilter);
        for(File file : files) {
            if(file.isDirectory()) {
                scanForValidProducts(file, pathList);
            } else if(isValidProduct(file)) {
                pathList.add(file.getAbsolutePath());
            }
        }
    }

    private static class ValidProductFileFilter implements java.io.FileFilter {

        final static String[] skip = { "annotation", "auxraster", "auxfiles", "imagedata", "preview", "support", "schemas" };

        public boolean accept(final File file) {
            final String name = file.getName().toLowerCase();
            if(file.isDirectory() && name.endsWith(DimapProductConstants.DIMAP_DATA_DIRECTORY_EXTENSION))
                return false;
            for(String ext : skip) {
                if(name.equalsIgnoreCase(ext))
                    return false;
            }
            for(String ext : nonValidExtensions) {
                if(name.endsWith(ext))
                    return false;
            }
            for(String pre : nonValidprefixes) {
                if(name.startsWith(pre))
                    return false;
            }
            return true;
        }
    }
}
