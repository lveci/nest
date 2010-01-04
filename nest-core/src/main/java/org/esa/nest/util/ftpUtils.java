package org.esa.nest.util;

import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.esa.beam.visat.VisatApp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


public final class ftpUtils {

    private final FTPClient ftpClient = new FTPClient();

    public enum FTPError { FILE_NOT_FOUND, OK, READ_ERROR }

    public ftpUtils(String server) throws IOException {
        this(server, "anonymous", "anonymous");
    }

    private ftpUtils(String server, String user, String password) throws IOException {
        ftpClient.connect(server);
        ftpClient.login(user, password);

        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        ftpClient.setDataTimeout(30000);
    }

    public void disconnect() throws IOException {
        ftpClient.disconnect();
    }

    public FTPError retrieveFile(String remotePath, File localFile, long fileSize) {
        FileOutputStream fos = null;
        InputStream fis = null;
        try {
            System.out.println("ftp retrieving "+remotePath);
            
            fis = ftpClient.retrieveFileStream(remotePath);
            if(fis == null) {
                final int code = ftpClient.getReplyCode();
                System.out.println("error code:"+code + " on " + remotePath);
                if(code == 550)
                    return FTPError.FILE_NOT_FOUND;
                else
                    return FTPError.READ_ERROR;
            }

            fos = new FileOutputStream(localFile.getAbsolutePath());

            final VisatApp visatApp = VisatApp.getApp();

            if(false) {//visatApp != null) {
                if(!readFile(fis, fos, localFile.getName(), fileSize)) {
                    return FTPError.READ_ERROR;    
                }
            } else {
                final int size = 8192;
                final byte[] buf = new byte[size];
                int n;
                int total = 0, lastPct = 0;
                while ((n = fis.read(buf, 0, size)) > -1)  {
                    fos.write(buf, 0, n);
                    if(visatApp != null) {
                        total += n;
                        final int pct = (int)((total/(float)fileSize) * 100);
                        if(pct >= lastPct + 10) {
                            visatApp.setStatusBarMessage("Downloading "+localFile.getName()+"... "+pct+"%");
                            lastPct = pct;
                        }
                    }
                }
                visatApp.setStatusBarMessage("");
            }

            ftpClient.completePendingCommand();
            return FTPError.OK;

        } catch (Exception e) {
            System.out.println(e.getMessage());
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

    private static boolean readFile(final InputStream fis, final FileOutputStream fos,
                                 final String fileName, final long fileSize) throws Exception {

        final ProgressMonitorSwingWorker worker = new ProgressMonitorSwingWorker(VisatApp.getApp().getMainFrame(), "Downloading...") {
            @Override
            protected Object doInBackground(com.bc.ceres.core.ProgressMonitor pm) throws Exception {
                final int size = 4096;
                final byte[] buf = new byte[size];

                pm.beginTask("Downloading "+fileName, (int)(fileSize/size) + 200);
                try {
                    int n;
                    while ((n = fis.read(buf, 0, size)) > -1) {
                        fos.write(buf, 0, n);

                        pm.worked(1);
                    }
                } catch(Exception e) {
                    System.out.println(e.getMessage());
                    return false;
                } finally {
                    pm.done();
                }
                return true;
            }
        };
        worker.executeWithBlocking();

        return (Boolean)worker.get();
    }

    public FTPFile[] getRemoteFileList(String path) throws IOException {
        return ftpClient.listFiles(path);
    }

    public static String getPathFromSettings(String tag) {
        String path = Settings.instance().get(tag);
        path = path.replace("\\", "/");
        if(!path.endsWith("/"))
            path += "/";
        return path;
    }

    public static boolean testFTP(String remoteFTP, String remotePath) {
        try {
            final ftpUtils ftp = new ftpUtils(remoteFTP);

            final FTPFile[] remoteFileList = ftp.getRemoteFileList(remotePath);
            ftp.disconnect();

            return (remoteFileList != null);

        } catch(Exception e) {
            final String errMsg = "Error connecting via FTP: "+e.getMessage();
            System.out.println(errMsg);
            if(VisatApp.getApp() != null) {
                VisatApp.getApp().showErrorDialog(errMsg);
            }
        }
        return false;
    }
}