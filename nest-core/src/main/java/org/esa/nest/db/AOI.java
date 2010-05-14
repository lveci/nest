package org.esa.nest.db;

import org.esa.beam.util.io.FileUtils;

import java.io.File;

/**

 */
public class AOI {

    private final File aoiFile;
    private String name;
    private File inputFolder;
    private File outputFolder;
    private File processingGraph;

    public AOI(final File file) {
        this.aoiFile = file;
        this.name = FileUtils.getFilenameWithoutExtension(file);
    }

    public File getFile() {
        return aoiFile;
    }

    public String getName() {
        return name;
    }

    public void setName(final String n) {
        name = n;
    }

    public File getInputFolder() {
        return inputFolder;
    }

    public void setInputFolder(final File file) {
        inputFolder = file;
    }

    public File getOutputFolder() {
        return outputFolder;
    }

    public void setOutputFolder(final File file) {
        outputFolder = file;
    }

    public File getProcessingGraph() {
        return processingGraph;
    }

    public void setProcessingGraph(final File file) {
        processingGraph = file;
    }
}
