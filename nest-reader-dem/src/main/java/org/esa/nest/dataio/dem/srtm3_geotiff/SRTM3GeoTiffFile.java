package org.esa.nest.dataio.dem.srtm3_geotiff;

import org.esa.beam.util.Guardian;
import org.esa.beam.util.io.FileUtils;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.dataio.dem.ace.ACEElevationModelDescriptor;
import org.esa.nest.util.ftpUtils;

import javax.imageio.stream.FileCacheImageInputStream;
import java.io.*;
import java.text.ParseException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Holds information about a dem file.
 */
public class SRTM3GeoTiffFile {

    private final SRTM3GeoTiffElevationModel demModel;
    private final File localFile;
    private final ProductReader productReader;
    private boolean localFileExists = false;
    private SRTM3GeoTiffElevationTile tile = null;

    private static final String remotePath = "/SRTM_v41/SRTM_Data_GeoTIFF/";

    public SRTM3GeoTiffFile(SRTM3GeoTiffElevationModel model, File localFile, ProductReader reader) {
        this.demModel = model;
        this.localFile = localFile;
        this.productReader = reader;

        if (localFile.exists() && localFile.isFile()) {
            localFileExists = true;
        }
    }

    public String getFileName() {
        return localFile.getName();
    }

    public SRTM3GeoTiffElevationTile getTile() {
        if(tile == null) {
            try {
                if(localFileExists) {
                    final Product product = productReader.readProductNodes(getFileFromZip(localFile), null);
                    tile = new SRTM3GeoTiffElevationTile(demModel, product);
                } else {
                    if(getRemoteFile()) {
                        final Product product = productReader.readProductNodes(getFileFromZip(localFile), null);
                        tile = new SRTM3GeoTiffElevationTile(demModel, product);
                    }
                }
            } catch(Exception e) {
                System.out.println(e.getMessage());
                tile = null;
            }
        }
        return tile;
    }

    private boolean getRemoteFile() {
        try {
            final ftpUtils ftp = new ftpUtils("srtm.csi.cgiar.org");

            final String fileName = localFile.getName();
            final boolean result = ftp.retrieveFile(remotePath + fileName, localFile.getAbsolutePath());
            if(!result) {
                localFile.delete();
            }

            ftp.disconnect();
            return result;
        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
        return false;
    }

    private static File getFileFromZip(final File dataFile) throws IOException {
        final String ext = FileUtils.getExtension(dataFile.getName());
        if (ext.equalsIgnoreCase(".zip")) {
            final String baseName = FileUtils.getFilenameWithoutExtension(dataFile.getName()) + ".tif";
            final ZipFile zipFile = new ZipFile(dataFile);

            final File newFile = new File(dataFile.getParentFile(), baseName);
            final FileOutputStream fileoutputstream = new FileOutputStream(newFile);
            try {
                //final ZipEntry zipEntry = getZipEntryIgnoreCase(zipFile, baseName);
                final ZipEntry zipEntry = zipFile.getEntry(baseName);
                if (zipEntry == null) {
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
            } finally {
                zipFile.close();
                fileoutputstream.close();
            }
        }
        return dataFile;
    }      

    private static ZipEntry getZipEntryIgnoreCase(final ZipFile zipFile, final String name) {
        final Enumeration enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            final ZipEntry zipEntry = (ZipEntry) enumeration.nextElement();
            if (zipEntry.getName().equalsIgnoreCase(name)) {
                return zipEntry;
            }
        }
        return null;
    }
}