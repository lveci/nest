package org.esa.nest.util;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTP;

import java.io.IOException;
import java.io.FileOutputStream;
import java.io.File;


public class ftpUtils {

    private final FTPClient ftpClient = new FTPClient();

    public ftpUtils(String server) throws IOException {
        this(server, "anonymous", "anonymous");
    }

    public ftpUtils(String server, String user, String password) throws IOException {
        ftpClient.connect(server);
        ftpClient.login(user, password);
    }

    public void disconnect() throws IOException {
        ftpClient.disconnect();
    }

    public boolean retrieveFile(String remoteFileName, String localFileName) {
        FileOutputStream fos = null;
        boolean result = false;
        try {
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            fos = new FileOutputStream(localFileName);
            result = ftpClient.retrieveFile(remoteFileName, fos);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

  /*  public getFile() {
        final FTPClient f= new FTPClient();
            f.connect(server);
            f.login("anonymous", "anonymous");
            FTPListParseEngine engine = f.initiateListParsing(directory);

            while (engine.hasNext()) {
               FTPFile[] files = engine.getNext(25);  // "page size" you want
                f.retrieveFile()
               //do whatever you want with these files, display them, etc.
               //expensive FTPFile objects not created until needed.
            }

    }    */

}