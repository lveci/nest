/*
 * Copyright (C) 2011 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.dataio.dem.aster;

import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.io.FileUtils;
import org.esa.nest.dataio.dem.BaseElevationTile;
import org.esa.nest.dataio.dem.ElevationFile;
import org.esa.nest.util.ResourceUtils;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Holds information about a dem file.
 */
public final class AsterFile extends ElevationFile {

    private final AsterElevationModel demModel;
    private File localFile;
    private final ProductReader productReader;
    private boolean localFileExists = false;
    private boolean errorInLocalFile = false;
    private BaseElevationTile tile = null;
    private final static boolean unrecoverableError = false;
    private final static File appTmpDir = ResourceUtils.getApplicationUserTempDataDir();

    public AsterFile(final AsterElevationModel model, final File localFile, final ProductReader reader) {
        super(localFile, reader);
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

    protected String getRemoteFTP() {
        return null;
    }

    protected String getRemotePath() {
        return null;    
    }

    protected boolean getRemoteFile() throws IOException {
        return false;
    }

    public String getFileName() {
        return localFile.getName();
    }

    public BaseElevationTile getTile() throws IOException {
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
                } else {
                    final String name = FileUtils.getFilenameWithoutExtension(localFile.getName());
                    // check for version 2
                    final String v2Name = name.replace("ASTGTM", "ASTGTM2");
                    final File v2File = new File(localFile.getParentFile(), v2Name+".zip");
                    if(v2File.exists()) {
                        localFile = v2File;
                        localFileExists = true;
                    } else {
                        // check if unzipped
                        final File unzipFile = new File(localFile.getParentFile(), name+"_dem.tif");
                        if(unzipFile.exists()) {
                            localFile = unzipFile;
                            localFileExists = true;
                        } else {
                            final File v2UnzipFile = new File(localFile.getParentFile(), v2Name+"_dem.tif");
                            if(v2UnzipFile.exists()) {
                                localFile = v2UnzipFile;
                                localFileExists = true;
                            }
                        }
                    }
                }
            }
            if(localFileExists) {
                final File dataFile = getFileFromZip(localFile);
                if(dataFile != null) {
                    final Product product = productReader.readProductNodes(dataFile, null);
                    if(product != null) {
                        tile = new BaseElevationTile(demModel, product);
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
            BufferedOutputStream fileoutputstream = null;
            try {
                zipFile = new ZipFile(dataFile);
                fileoutputstream = new BufferedOutputStream(new FileOutputStream(newFile));

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