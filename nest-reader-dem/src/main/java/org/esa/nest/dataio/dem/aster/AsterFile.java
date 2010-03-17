package org.esa.nest.dataio.dem.aster;

import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.io.FileUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.util.ResourceUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.Enumeration;
import java.awt.*;

import com.bc.io.FileUnpacker;

/**
 * Holds information about a dem file.
 */
public final class AsterFile {

    private final AsterElevationModel demModel;
    private final File localFile;
    private final ProductReader productReader;
    private boolean localFileExists = false;
    private boolean errorInLocalFile = false;
    private AsterElevationTile tile = null;
    private boolean unrecoverableError = false;
    private boolean checkOnceForUnzippedTiles = true; // look for bulk Tiles_ files
    private final static File appTmpDir = ResourceUtils.getApplicationUserTempDataDir();

    public AsterFile(AsterElevationModel model, File localFile, ProductReader reader) {
        this.demModel = model;
        this.localFile = localFile;
        this.productReader = reader;
    }

    public void dispose() {
        try {
            tile.dispose();
            tile = null;
        } catch(Exception e) {
            //
        }
    }

    public String getFileName() {
        return localFile.getName();
    }

    public AsterElevationTile getTile() throws IOException {
        if(tile == null) {
            getFile();
        }
        return tile;
    }

    private synchronized void getFile() throws IOException {
        try {
            if(tile != null) return;
            if(!localFileExists && !errorInLocalFile) {
                if (localFile.exists() && localFile.isFile() && localFile.length() > 0) {
                    localFileExists = true;
                }
            }
            if(localFileExists) {
                final File dataFile = getFileFromZip(localFile);
                if(dataFile != null) {
                    final Product product = productReader.readProductNodes(dataFile, null);
                    if(product != null) {
                        tile = new AsterElevationTile(demModel, product);
                    }
                }
            } else if(checkOnceForUnzippedTiles) {
                final File parentFolder = localFile.getParentFile();
                final File[] files = parentFolder.listFiles();
                Component component = null;
                if(VisatApp.getApp() != null) {
                    component = VisatApp.getApp().getApplicationWindow();
                }
                for(File f : files) {
                    if(f.getName().startsWith("Tiles_") && f.getName().endsWith(".zip")) {
                        FileUnpacker.unpackZip(f, parentFolder, component);
                        f.delete();
                    }
                }
                checkOnceForUnzippedTiles = false;
                if (localFile.exists() && localFile.isFile() && localFile.length() > 0) {
                    final File dataFile = getFileFromZip(localFile);
                    if(dataFile != null) {
                        final Product product = productReader.readProductNodes(dataFile, null);
                        if(product != null) {
                            tile = new AsterElevationTile(demModel, product);
                        }
                    }
                }
            }
            if(tile != null) {
                demModel.updateCache(tile);
                errorInLocalFile = false;
            } else {
                if(localFileExists) {
                    System.out.println("SRTM unable to reader product "+localFile.getAbsolutePath());
                }
                localFileExists = false;
                errorInLocalFile = true;
            }
        } catch(Exception e) {
            System.out.println(e.getMessage());
            tile = null;
            localFileExists = false;
            errorInLocalFile = true;
            if(unrecoverableError) {
                throw new IOException(e);
            }
        }
    }

    private File getFileFromZip(final File dataFile) throws IOException {
        final String ext = FileUtils.getExtension(dataFile.getName());
        if (ext.equalsIgnoreCase(".zip")) {
            final String baseName = FileUtils.getFilenameWithoutExtension(dataFile.getName());
            final String newFileName = baseName + "_dem.tif";
            final File newFile = new File(appTmpDir, newFileName);
            if(newFile.exists() && newFile.length() > 0)
                return newFile;

            ZipFile zipFile = null;
            FileOutputStream fileoutputstream = null;
            try {
                zipFile = new ZipFile(dataFile);
                fileoutputstream = new FileOutputStream(newFile);

                zipFile.entries();
                final ZipEntry zipEntry = zipFile.getEntry(baseName +'/'+ newFileName);
                if (zipEntry == null) {
                    localFileExists = false;
                    throw new IOException("Entry '" + baseName + "' not found in zip file.");
                }

                final int size = 8192;
                final byte[] buf = new byte[size];
                InputStream zipinputstream = zipFile.getInputStream(zipEntry);

                int n;
                while ((n = zipinputstream.read(buf, 0, size)) > -1)
                    fileoutputstream.write(buf, 0, n);

                return newFile;
            } catch(Exception e) {
                System.out.println(e.getMessage());
                dataFile.delete();
                return null;
            } finally {
                if(zipFile != null)
                    zipFile.close();
                if(fileoutputstream != null)
                    fileoutputstream.close();
            }
        }
        return dataFile;
    }
}