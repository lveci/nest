/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
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
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.util.PropertyMap;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.util.ResourceUtils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.FilenameFilter;
import java.net.URI;

/**
 * This action launches PolSARPro
 *
 */
public class LaunchPolsarProAction extends ExecCommand {

    private final static String PolsarProPathStr = "external.polsarpro.path";
    /**
     * Launches PolSARPro
     * Invoked when a command action is performed.
     *
     * @param event the command event.
     */
    @Override
    public void actionPerformed(CommandEvent event) {
        final PropertyMap pref = VisatApp.getApp().getPreferences();
        final String path = pref.getPropertyString(PolsarProPathStr);
        File polsarProFile = new File(path);
        
        if(!polsarProFile.exists()) {
            polsarProFile = findPolsarPro();
        }
        if(!polsarProFile.exists()) {
            // ask for location
        }
        if(polsarProFile.exists()) {
            externalExecute(polsarProFile);

            // save location
            pref.setPropertyString(PolsarProPathStr, polsarProFile.getAbsolutePath());
            VisatApp.getApp().savePreferences();
        } else {
            // report error
        }
    }

    private void externalExecute(File prog) {
        final File homeFolder = ResourceUtils.findHomeFolder();
        final File program = new File(homeFolder, "bin"+File.separator+"exec.bat");

        try {
            final String args = prog.getParent()+" "+"wish"+" "+prog.getName();

            System.out.println("Launching PolSARPro "+args);
            Runtime.getRuntime().exec(program.getAbsolutePath()+" "+args);
        } catch(Exception e) {
            VisatApp.getApp().showErrorDialog(e.getMessage());
        }
    }

    private static File findPolsarPro() {
        File progFiles = new File("C:\\Program Files (x86)");
        if(!progFiles.exists())
            progFiles = new File("C:\\Program Files");
        if(progFiles.exists()) {
            final File[] progs = progFiles.listFiles(new PolsarFileFilter());
            for(File prog : progs) {
                final File[] fileList = prog.listFiles(new PolsarFileFilter());
                for(File file : fileList) {
                    if(file.getName().toLowerCase().endsWith("tcl")) {
                        return file;
                    }
                }
            }
        }
        return new File("");
    }

    private static class PolsarFileFilter implements FilenameFilter {

        public boolean accept(File dir, String name) {

            return name.toLowerCase().startsWith("polsar");
        }
    }

}