package org.esa.nest.db;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.util.TestUtils;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Scans folders for products to add or update into the database
 */
public final class DBScanner extends SwingWorker {
    private final ProductDB db;

    private final File baseDir;
    private final boolean doRecursive;
    private final boolean generateQuicklooks;
    private final com.bc.ceres.core.ProgressMonitor pm;
    private final ArrayList<DBScannerListener> listenerList = new ArrayList<DBScannerListener>(1);

    public DBScanner(final ProductDB database, final File baseDir, final boolean doRecursive,
                     final boolean doQuicklooks, final com.bc.ceres.core.ProgressMonitor pm) {
        this.db = database;
        this.pm = pm;
        this.baseDir = baseDir;
        this.doRecursive = doRecursive;
        this.generateQuicklooks = doQuicklooks;
    }

    public void addListener(final DBScannerListener listener) {
        if (!listenerList.contains(listener)) {
            listenerList.add(listener);
        }
    }

    public void removeListener(final DBScannerListener listener) {
        listenerList.remove(listener);
    }

    private void notifyMSG(final DBScannerListener.MSG msg) {
        for (final DBScannerListener listener : listenerList) {
            listener.notifyMSG(msg);
        }
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        final ArrayList<File> dirList = new ArrayList<File>();
        dirList.add(baseDir);
        if (doRecursive) {
            final File[] subDirs = collectAllSubDirs(baseDir);
            dirList.addAll(Arrays.asList(subDirs));
        }

        final ArrayList<File> fileList = new ArrayList<File>();
        for(File file : dirList) {
            fileList.addAll(Arrays.asList(file.listFiles()));
        }

        final ArrayList<File> qlProductFiles = new ArrayList<File>();
        final ArrayList<Integer> qlIDs = new ArrayList<Integer>();

        final int total = fileList.size();
        pm.beginTask("Scanning Files...", total);
        int i=0;
        try {
            for(File file : fileList) {
                ++i;
                pm.setTaskName("Scanning Files... "+i+" of "+total);
                pm.worked(1);

                if(!file.isDirectory()) {
                    if(pm.isCanceled())
                        break;
                    if(TestUtils.isNotProduct(file))
                        continue;

                    // check if already exists in db
                    final ProductEntry existingEntry = db.getProductEntry(file);
                    if(existingEntry != null) {
                        // check for missing quicklook
                        if(!existingEntry.quickLookExists()) {
                            qlProductFiles.add(file);
                            qlIDs.add(existingEntry.getId());
                        }
                        existingEntry.dispose();
                        continue;
                    }

                    try {
                        final ProductReader reader = ProductIO.getProductReaderForFile(file);
                        if(reader != null) {
                            final Product sourceProduct = reader.readProductNodes(file, null);
                            if(sourceProduct != null) {
                                final ProductEntry entry = db.saveProduct(sourceProduct);
                                if(!entry.quickLookExists()) {
                                    qlProductFiles.add(file);
                                    qlIDs.add(entry.getId());
                                }
                                sourceProduct.dispose();
                                entry.dispose();
                            }
                        } else {
                            System.out.println("No reader for "+file.getAbsolutePath());
                        }
                    } catch(Exception e) {
                        System.out.println("Unable to read "+file.getAbsolutePath()+"\n"+e.getMessage());
                    }
                }
            }

            db.cleanUpRemovedProducts();

            notifyMSG(DBScannerListener.MSG.FOLDERS_SCANNED);

            if(generateQuicklooks) {
                final int numQL = qlProductFiles.size();
                pm.beginTask("Generating Quicklooks...", numQL);
                for(int j=0; j < numQL; ++j) {
                    pm.setTaskName("Generating Quicklook... "+(j+1)+" of "+numQL);
                    pm.worked(1);
                    if(pm.isCanceled())
                        break;
    
                    final File file = qlProductFiles.get(j);
                    try {
                        final ProductReader reader = ProductIO.getProductReaderForFile(file);
                        if(reader != null) {
                            final Product sourceProduct = reader.readProductNodes(file, null);
                            if(sourceProduct != null) {
                                QuickLookGenerator.createQuickLook(qlIDs.get(j), sourceProduct);
                                notifyMSG(DBScannerListener.MSG.QUICK_LOOK_GENERATED);
                                sourceProduct.dispose();
                            }
                        }
                    } catch(Exception e) {
                        System.out.println("QL Unable to read "+file.getAbsolutePath()+"\n"+e.getMessage());
                    }
                }
            }
            pm.setTaskName("");

        } catch(Exception e) {
            System.out.println("Scanning Exception\n"+e.getMessage());
        } finally {
            pm.done();
        }
        return true;
    }

    @Override
    public void done() {
        notifyMSG(DBScannerListener.MSG.DONE);
    }

    private File[] collectAllSubDirs(final File dir) {
        final ArrayList<File> dirList = new ArrayList<File>();
        final DirectoryFileFilter dirFilter = new DirectoryFileFilter();

        final File[] subDirs = dir.listFiles(dirFilter);
        for (final File subDir : subDirs) {
            dirList.add(subDir);
            final File[] dirs = collectAllSubDirs(subDir);
            dirList.addAll(Arrays.asList(dirs));
        }
        return dirList.toArray(new File[dirList.size()]);
    }

    private static class DirectoryFileFilter implements java.io.FileFilter {

        final static String[] skip = { "annotation", "auxraster", "auxfiles", "imagedata", "preview", "support", "schemas" };

        public boolean accept(final File file) {
            if(!file.isDirectory()) return false;
            final String name = file.getName().toLowerCase();
            if(name.endsWith(".data"))
                return false;
            for(String ext : skip) {
                if(name.equalsIgnoreCase(ext))
                    return false;
            }
            return true;
        }
    }

    public interface DBScannerListener {

        public enum MSG { DONE, FOLDERS_SCANNED, QUICK_LOOK_GENERATED }

        public void notifyMSG(MSG msg);
    }
}
