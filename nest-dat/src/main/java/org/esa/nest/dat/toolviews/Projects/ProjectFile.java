package org.esa.nest.dat.toolviews.Projects;

import java.io.File;

/**
 * Defines a File in a Project
* User: lveci
* Date: Aug 28, 2008
*/
public class ProjectFile {
    private final File file;
    private final String displayName;

    ProjectFile(File f, String name) {
        file = f;
        displayName = name.trim();
    }

    public File getFile() {
        return file;
    }

    public String getDisplayName() {
        return displayName;
    }
}
