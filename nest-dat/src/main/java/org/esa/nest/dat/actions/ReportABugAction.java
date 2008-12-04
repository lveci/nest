package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.util.StringUtils;

import java.awt.*;
import java.net.URI;
import java.io.IOException;
import java.util.*;

/**
 * This action emails a problem to Array
 *
 */
public class ReportABugAction extends ExecCommand {
    private static final String PR_EMAIL = "mailto:nest_pr@array.ca";

    /**
     * Invoked when a command action is performed.
     *
     * @param event the command event.
     */
    @Override
    public void actionPerformed(CommandEvent event) {

        final Desktop desktop = Desktop.getDesktop();
        final String mail = PR_EMAIL + "?subject=NEST-Problem-Report&body=Description:%0A%0A%0A%0A";
        final String sysInfo = getSystemInfo();

        final String longmail = mail + "SysInfo:%0A" + sysInfo;

        try {
            desktop.mail(URI.create(longmail));
        } catch (Exception e) {
            e.printStackTrace();
            try {
                desktop.mail(URI.create(mail));
            } catch(IOException ex) {
                //
            }
        }
    }

    private static String getSystemInfo() {

        String sysInfoStr="";
        Properties sysProps = null;
        try {
            sysProps = System.getProperties();
        } catch (RuntimeException e) {
            //
        }
        if (sysProps != null) {
            String[] names = new String[sysProps.size()];
            Enumeration<?> e = sysProps.propertyNames();
            for (int i = 0; i < names.length; i++) {
                names[i] = (String) e.nextElement();
            }
            Arrays.sort(names);
            for (String name : names) {
                if(name.equals("java.class.path") || name.equals("java.library.path") || name.contains("vendor"))
                    continue;

                if(name.startsWith("beam") || name.startsWith("nest") || name.startsWith("ceres")
                   || name.startsWith("os") || name.startsWith("java")) {
                    
                    String value = sysProps.getProperty(name);
                    sysInfoStr += name + "=" + value + "%0A";
                }
            }
        }

        return StringUtils.createValidName(sysInfoStr.trim(),
                new char[]{'_', '-', '.','=','/','@','~','%','*','(',')','+','!','#','$','^','&',':','<','>','|'},
                //new char[]{'_', '-', '.', '%','=' },
                '_');
    }
}