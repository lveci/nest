package org.esa.nest.dataio.dem.srtm3_geotiff;

import org.esa.beam.util.Guardian;
import org.esa.beam.util.io.FileUtils;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.dataio.dem.ace.ACEElevationModelDescriptor;
import org.esa.nest.util.ftpUtils;
import org.esa.nest.util.DatUtils;
import org.esa.nest.util.Settings;
import org.apache.commons.net.ftp.FTPFile;

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
    private boolean remoteFileExists = true;
    private SRTM3GeoTiffElevationTile tile = null;
    private ftpUtils ftp = null;
    private FTPFile[] remoteFileList = null;

    private static final String remoteFTP = Settings.instance().get("srtm3GeoTiffDEM_FTP");
    private static final String remotePath = getPath("srtm3GeoTiffDEM_remotePath");

    public SRTM3GeoTiffFile(SRTM3GeoTiffElevationModel model, File localFile, ProductReader reader) {
        this.demModel = model;
        this.localFile = localFile;
        this.productReader = reader;

        if (localFile.exists() && localFile.isFile()) {
            localFileExists = true;
        }
    }

    public void dispose() {
        try {
            if(ftp != null)
                ftp.disconnect();
            ftp = null;
        } catch(Exception e) {
            //
        }
    }

    private static String getPath(String tag) {
        String path = Settings.instance().get(tag);
        path = path.replace("\\", "/");
        if(!path.endsWith("/"))
            path += "/";
        return path;
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
                    if(remoteFileExists && getRemoteFile()) {
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
            if(ftp == null) {
                ftp = new ftpUtils(remoteFTP);

                remoteFileList = ftp.getRemoteFileList(remotePath);
            }

            if(remoteFileList == null)
                throw new IOException("Unable to get remote file list");

            final String remoteFileName = localFile.getName();
            final long fileSize = ftpUtils.getFileSize(remoteFileList, remoteFileName);
            
            final ftpUtils.FTPError result = ftp.retrieveFile(remotePath + remoteFileName, localFile, fileSize);
            if(result == ftpUtils.FTPError.OK) {
                return true;
            } else {
                if(result == ftpUtils.FTPError.FILE_NOT_FOUND)
                    remoteFileExists = false;
                localFile.delete();
            }

            return false;
        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
        return false;
    }

    private File getFileFromZip(final File dataFile) throws IOException {
        final String ext = FileUtils.getExtension(dataFile.getName());
        if (ext.equalsIgnoreCase(".zip")) {
            final String baseName = FileUtils.getFilenameWithoutExtension(dataFile.getName()) + ".tif";
            final File newFile = new File(DatUtils.getApplicationUserTempDataDir(), baseName);
            if(newFile.exists())
                return newFile;

            final ZipFile zipFile = new ZipFile(dataFile);
            final FileOutputStream fileoutputstream = new FileOutputStream(newFile);
            try {
                final ZipEntry zipEntry = zipFile.getEntry(baseName);
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
            } finally {
                zipFile.close();
                fileoutputstream.close();
            }
        }
        return dataFile;
    }
}