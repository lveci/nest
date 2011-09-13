package org.esa.nest.util;

import org.esa.beam.util.io.FileUtils;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.dataio.dimap.DimapProductConstants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**

 */
public class ProductFunctions {

    private static String[] validExtensions = {".dim",".n1",".e1",".e2",".h5"};
    final static String[] xmlPrefix = { "product", "tsx1_sar", "tsx2_sar", "tdx1_sar", "tdx2_sar" };

    private static final String[] nonValidExtensions = { "xsd", "xsl", "xls", "pdf", "txt", "doc", "ps", "db", "ief", "ord",
                                                   "tfw", "gif", "jpg", "jgw", "hdr", "self", "report", "raw", "tgz",
                                                   "log", "html", "htm", "png", "bmp", "ps", "aux", "ovr", "brs", "kml", "kmz",
                                                   "sav", "7z", "zip", "rrd", "lbl", "z", "gz", "exe", "bat", "sh", "rtf",
                                                   "prj", "dbf", "shx", "ace", "ace2", "tar", "tooldes"};
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
        if(ext.equals("xml")) {
            final String name = file.getName().toLowerCase();
            for(String str : xmlPrefix) {
                if(name.startsWith(str)) {
                    return true;
                }
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

    /**
     * any files (not folders) that could be products
     */
    public static class ValidProductFileFilter implements java.io.FileFilter {
        private final boolean includeFolders;

        public ValidProductFileFilter() {
            this.includeFolders = false;
        }

        public ValidProductFileFilter(final boolean includeFolders) {
            this.includeFolders = includeFolders;
        }

        public boolean accept(final File file) {
            if(file.isDirectory()) return includeFolders;
            final String name = file.getName().toLowerCase();
            for(String ext : validExtensions) {
                if(name.endsWith(ext)) {
                    return true;
                }
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

    /**
     * collect valid folders to scan
     */
    public static class DirectoryFileFilter implements java.io.FileFilter {

        final static String[] skip = { "annotation", "auxraster", "auxfiles", "imagedata", "preview", "support", "schemas" };

        public boolean accept(final File file) {
            if(!file.isDirectory()) return false;
            final String name = file.getName().toLowerCase();
            if(name.endsWith(DimapProductConstants.DIMAP_DATA_DIRECTORY_EXTENSION))
                return false;
            for(String ext : skip) {
                if(name.equalsIgnoreCase(ext))
                    return false;
            }
            return true;
        }
    }

    /**
     * Quickly return the product read by the right reader without testing many readers
     * @param file input file
     * @return the product
     * @throws IOException if can't be read
     */
    public static Product readCommonProductReader(final File file) throws IOException {
        final String filename = file.getName().toLowerCase();
        if(filename.endsWith("n1")) {
            return ProductIO.readProduct(file, "ENVISAT");
        } else if(filename.endsWith("e1") || filename.endsWith("e2")) {
            return ProductIO.readProduct(file, "ERS1/2");
        } else if(filename.endsWith("dim")) {
            return ProductIO.readProduct(file, "BEAM-DIMAP");
        } else if((filename.startsWith("TSX") || filename.startsWith("TDX")) && filename.endsWith("xml")) {
            return ProductIO.readProduct(file, "TerraSarX");
        } else if(filename.equals("product.xml")) {
            try {
                return ProductIO.readProduct(file, "RADARSAT-2");
            } catch(IOException e) {
                return ProductIO.readProduct(file, "RADARSAT-2 NITF");
            }
        }
        return null;
    }

    private static String[] elemsToKeep = { "Abstracted_Metadata", "MAIN_PROCESSING_PARAMS_ADS", "DSD", "SPH", "lutSigma" };

    public static void discardUnusedMetadata(final Product product) {
        final boolean dicardUnusedMetadata = Config.getConfigPropertyMap().getPropertyBool("discard.unused.metadata");
        if(dicardUnusedMetadata) {
            removeUnusedMetadata(product.getMetadataRoot());
        }
    }

    private static void removeUnusedMetadata(final MetadataElement root) {
        final MetadataElement[] elems = root.getElements();
        for(MetadataElement elem : elems) {
            final String name = elem.getName();
            boolean keep = false;
            for(String toKeep : elemsToKeep) {
                if(name.equals(toKeep)) {
                    keep = true;
                    break;
                }
            }
            if(!keep) {
                root.removeElement(elem);
                elem.dispose();
            }
        }
    }
}
