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
package org.esa.nest.dat.toolviews.Projects;

import com.thoughtworks.xstream.io.xml.xppdom.XppDom;
import org.esa.beam.framework.gpf.graph.Graph;
import org.esa.beam.framework.gpf.graph.GraphIO;

import java.io.File;
import java.io.FileReader;

/**
 * Defines a File in a Project
* User: lveci
* Date: Aug 28, 2008
*/
public class ProjectFile {
    private final File file;
    private final String displayName;
    private String tooltipText;
    private ProjectSubFolder.FolderType folderType;

    ProjectFile(File f, String name) {
        file = f;
        displayName = name.trim();
        tooltipText = displayName;
    }

    void setFolderType(ProjectSubFolder.FolderType folder) {
        folderType = folder;
        if(folderType == ProjectSubFolder.FolderType.GRAPH) {
            Graph graph = readGraph(file.getAbsolutePath());
            if(graph != null) {
                XppDom presXML = graph.getApplicationData("Presentation");
                if(presXML != null) {
                    XppDom descXML = presXML.getChild("Description");
                    if(descXML != null && descXML.getValue() != null) {
                        this.setToolTipText(descXML.getValue());
                    }
                }
            }
        }
    }

    private static Graph readGraph(String filepath) {
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(filepath);
            return GraphIO.read(fileReader);
        } catch(Exception e) {
            System.out.println(e.getMessage());
        } finally {
            try {
                if(fileReader != null)
                    fileReader.close();
            } catch(Exception e) {
                System.out.println(e.getMessage());
            }
        }
        return null;
    }

    public File getFile() {
        return file;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getToolTipText() {
        return tooltipText;
    }

    void setToolTipText(String text) {
        tooltipText = text;
    }
}
