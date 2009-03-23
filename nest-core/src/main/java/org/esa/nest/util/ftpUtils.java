package org.esa.nest.util;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPListParseEngine;
import org.apache.commons.net.ftp.FTPFile;
import org.esa.beam.visat.VisatApp;

import java.io.*;
import java.nio.Buffer;

import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;


public class ftpUtils {

    private final FTPClient ftpClient = new FTPClient();

    public enum FTPError { FILE_NOT_FOUND, OK, READ_ERROR }

    public ftpUtils(String server) throws IOException {
        this(server, "anonymous", "anonymous");
    }

    public ftpUtils(String server, String user, String password) throws IOException {
        ftpClient.connect(server);
        ftpClient.login(user, password);

        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        ftpClient.setDataTimeout(60000);
    }

    public void disconnect() throws IOException {
        ftpClient.disconnect();
    }

    public FTPError retrieveFile(String remotePath, File localFile, long fileSize) {
        FileOutputStream fos = null;
        InputStream fis = null;
        try {
            fis = ftpClient.retrieveFileStream(remotePath);
            if(fis == null) {
                final int code = ftpClient.getReplyCode();
                System.out.println("error code:"+code);
                return FTPError.FILE_NOT_FOUND;
            }

            fos = new FileOutputStream(localFile.getAbsolutePath());

            if(VisatApp.getApp() != null) {
                readFile(fis, fos, localFile.getName(), fileSize);
            } else {
                final int size = 1024;//8192;
                final byte[] buf = new byte[size];
                int n;
                while ((n = fis.read(buf, 0, size)) > -1)
                    fos.write(buf, 0, n);
            }

            ftpClient.completePendingCommand();
            return FTPError.OK;

        } catch (Exception e) {
            e.printStackTrace();
            return FTPError.READ_ERROR;
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static long getFileSize(FTPFile[] fileList, String remoteFileName) {
        for(FTPFile file : fileList) {
            if(file.getName().equalsIgnoreCase(remoteFileName)) {
                return file.getSize();
            }
        }
        return 0;
    }

    private static void readFile(final InputStream fis, final FileOutputStream fos,
                                 final String fileName, final long fileSize) {

        final ProgressMonitorSwingWorker worker = new ProgressMonitorSwingWorker(VisatApp.getApp().getMainFrame(), "Downloading...") {
            @Override
            protected Object doInBackground(com.bc.ceres.core.ProgressMonitor pm) throws Exception {
                final int size = 1024;//8192;
                final byte[] buf = new byte[size];

                pm.beginTask("Downloading "+fileName, (int)(fileSize/size) + 200);
                try {

                    int n;
                    while ((n = fis.read(buf, 0, size)) > -1) {
                        fos.write(buf, 0, n);

                        pm.worked(1);
                    }
                } finally {
                    pm.done();
                }
                return null;
            }
        };
        worker.executeWithBlocking();
    }

    public FTPFile[] getRemoteFileList(String path) throws IOException {
        return ftpClient.listFiles(path);
    }
}