
package org.esa.nest.dat.actions.importbrowser.model;

import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductIOPlugInManager;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;

import java.io.File;
import java.io.FileFilter;
import java.util.Iterator;

public class RepositoryScanner {


    public static void collectEntries(final Repository repository) {
        final File baseDir = repository.getBaseDir();
        final File[] productFiles = baseDir.listFiles(new ProductFileFilter());
        if (productFiles != null) {
            for (final File productFile : productFiles) {
                final RepositoryEntry repositoryEntry = new RepositoryEntry(productFile.getPath());
                repository.addEntry(repositoryEntry);
            }
        }
    }

    public static class ProductFileFilter implements FileFilter {

        final String[] skipExt = { ".txt", ".brs", ".ps", ".xsd" };
        final String[] passExt = { ".n1", ".e1", ".e2", ".tif", ".dim", ".hdf", ".h5", ".nc" };
        final String[] passPrefix = { "vol-alp", "vdf" };

        public boolean accept(final File file) {
            if(file.isDirectory())
                return false;
            final String name = file.getName().toLowerCase();
            for(String ext : skipExt) {
                if(name.endsWith(ext))
                    return false;
            }
            for(String ext : passExt) {
                if(name.endsWith(ext))
                    return true;
            }
            for(String ext : passPrefix) {
                if(name.startsWith(ext))
                    return true;
            }
            
            final Iterator it = ProductIOPlugInManager.getInstance().getAllReaderPlugIns();

            while (it.hasNext()) {
                final ProductReaderPlugIn plugIn = (ProductReaderPlugIn) it.next();

                if (plugIn.getDecodeQualification(file) != DecodeQualification.UNABLE) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class DirectoryFileFilter implements FileFilter {

        public boolean accept(final File file) {
            return file.isDirectory() && !file.getName().endsWith(".data");
        }
    }
}
