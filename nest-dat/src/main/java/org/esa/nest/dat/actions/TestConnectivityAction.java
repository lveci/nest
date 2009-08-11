package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.util.Settings;
import org.esa.nest.util.ftpUtils;
import org.apache.commons.net.ftp.FTPFile;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

/**
 * This action test if FTP is working
 *
 */
public class TestConnectivityAction extends ExecCommand {

    private static final String remoteFTPSRTM = Settings.instance().get("DEM/srtm3GeoTiffDEM_FTP");
    private static final String remotePathSRTM = ftpUtils.getPathFromSettings("DEM/srtm3GeoTiffDEM_remotePath");

    private static final String delftFTP = Settings.instance().get("OrbitFiles/delftFTP");
    private static final String delftFTPPath = Settings.instance().get("OrbitFiles/delftFTP_ERS2_precise_remotePath");

    @Override
    public void actionPerformed(CommandEvent event) {

        boolean failed = false;
        String msg1 = "Connection to FTP "+ remoteFTPSRTM + remotePathSRTM;
        if(testFTP(remoteFTPSRTM, remotePathSRTM)) {
            msg1 += " PASSED";
        } else {
            msg1 += " FAILED";
            failed = true;
        }

        String msg2 = "Connection to FTP "+ delftFTP + delftFTPPath;
        if(testFTP(delftFTP, delftFTPPath)) {
            msg2 += " PASSED";
        } else {
            msg2 += " FAILED";
            failed = true;
        }

        String msg = msg1 +"\n" +msg2;
        if(failed) {
            msg += "\nPlease verify that all paths are correct in your $NEST_HOME/config/settings.xml";
            msg += "\nAlso verify that FTP is not blocked by your firewall";
        }
        VisatApp.getApp().showInfoDialog(msg, null);
    }

    private static boolean testFTP(String remoteFTP, String remotePath) {
        try {
            final ftpUtils ftp = new ftpUtils(remoteFTP);

            final FTPFile[] remoteFileList = ftp.getRemoteFileList(remotePath);
            ftp.disconnect();

            return (remoteFileList != null);

        } catch(Exception e) {
            VisatApp.getApp().showErrorDialog(e.getMessage());
        }
        return false;
    }
}