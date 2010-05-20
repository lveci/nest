package org.esa.nest.db;

import org.esa.beam.util.io.FileUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.util.XMLSupport;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Vector;

/**

 */
public class AOI {

    private final File aoiFile;
    private String name = "aoi";
    private String inputFolder = "";
    private String outputFolder = "";
    private String processingGraph = "";
    private String lastProcessed = "";

    public AOI(final File file) {
        this.aoiFile = file;
        if(!aoiFile.exists() || !load(aoiFile)) {
            this.name = FileUtils.getFilenameWithoutExtension(file);
            this.inputFolder = AOIManager.getLastInputPath();
            this.outputFolder = AOIManager.getLastOutputPath();
            this.processingGraph = AOIManager.getLastGraphPath();
        }
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

    public String getInputFolder() {
        return inputFolder;
    }

    public void setInputFolder(final String file) {
        inputFolder = file;
    }

    public String getOutputFolder() {
        return outputFolder;
    }

    public void setOutputFolder(final String file) {
        outputFolder = file;
    }

    public String getProcessingGraph() {
        return processingGraph;
    }

    public void setProcessingGraph(final String file) {
        processingGraph = file;
    }

    public String getLastProcessed() {
        return lastProcessed;
    }

    public void setLastProcessed(final String date) {
        lastProcessed = date;
    }

    public void save() {
        final Element root = new Element("AOI");
        root.setAttribute("name", name);
        final Document doc = new Document(root);

        final Element elem = new Element("param");
        elem.setAttribute("inputFolder", inputFolder);
        elem.setAttribute("outputFolder", outputFolder);
        elem.setAttribute("graph", processingGraph);
        elem.setAttribute("lastProcessed", lastProcessed);

        root.addContent(elem);

        XMLSupport.SaveXML(doc, aoiFile.getAbsolutePath());
    }

    private boolean load(final File file) {
        org.jdom.Document doc;
        try {
            doc = XMLSupport.LoadXML(file.getAbsolutePath());

            final Element root = doc.getRootElement();
            final Attribute nameAttrib = root.getAttribute("name");
            if(nameAttrib != null)
                this.name = nameAttrib.getValue();

            final List children = root.getContent();
            for (Object aChild : children) {
                if (aChild instanceof Element) {
                    final Element child = (Element) aChild;
                    if(child.getName().equals("param")) {
                        inputFolder = getAttrib(child, "inputFolder");
                        outputFolder = getAttrib(child, "outputFolder");
                        processingGraph = getAttrib(child, "graph");
                        lastProcessed = getAttrib(child, "lastProcessed");
                    }
                }
            }
        } catch(IOException e) {
            VisatApp.getApp().showErrorDialog(e.getMessage());
            return false;
        }
        return true;
    }

    private static String getAttrib(final Element elem, final String tag) {
        final Attribute attrib = elem.getAttribute(tag);
        if(attrib != null)
            return attrib.getValue();
        return "";
    }
}
