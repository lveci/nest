package org.esa.nest.util;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.File;
import java.util.Map;

import org.apache.commons.net.ftp.FTPFile;


/**
 * FTPUtils Tester.
 *
 * @author lveci
 */
public class TestFTPUtils extends TestCase {

    public TestFTPUtils(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testConnect() throws IOException {
        final String server = "xftp.jrc.it";
        final String remotePath = "/pub/srtmV4/tiff/";
        final File localFile = new File("c:\\NESTDATA\\srtm_35_03.zip");

        final ftpUtils ftp = new ftpUtils(server);
        final Map<String, Long> fileSizeMap = ftpUtils.readRemoteFileList(ftp, server, remotePath);
        
        final String remoteFileName = localFile.getName();
        final Long fileSize = fileSizeMap.get(remoteFileName);

        final ftpUtils.FTPError result = ftp.retrieveFile(remotePath + remoteFileName, localFile, fileSize);
        assertTrue(result==ftpUtils.FTPError.OK);
    }

}