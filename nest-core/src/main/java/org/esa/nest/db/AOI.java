package org.esa.nest.db;

import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.util.io.FileUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.util.XMLSupport;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**

 */
public class AOI {

    private final File aoiFile;
    private String name = "aoi";
    private String inputFolder = "";
    private String outputFolder = "";
    private String processingGraph = "";
    private String lastProcessed = "";

    private GeoPos[] aoiPoints = new GeoPos[] {};
    private DBQuery slaveDBQuery = null;

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

    public void setAOIPoints(final GeoPos[] selectionBox) {
        aoiPoints = selectionBox;
    }

    public GeoPos[] getAOIPoints() {
        return aoiPoints;
    }

    public void setSlaveDBQuery(final DBQuery query) {
        slaveDBQuery = query;
    }

    public DBQuery getSlaveDBQuery() {
        return slaveDBQuery;
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

        final Element pntsElem = new Element("points");
        for(GeoPos pnt : aoiPoints) {
            final Element pntElem = new Element("point");
            pntElem.setAttribute("lat", String.valueOf(pnt.getLat()));
            pntElem.setAttribute("lon", String.valueOf(pnt.getLon()));
            pntsElem.addContent(pntElem);
        }
        root.addContent(pntsElem);

        if(slaveDBQuery != null) {
            root.addContent(slaveDBQuery.toXML());
        }

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

            final ArrayList<GeoPos> geoPosList = new ArrayList<GeoPos>();

            final List children = root.getContent();
            for (Object aChild : children) {
                if (aChild instanceof Element) {
                    final Element child = (Element) aChild;
                    if(child.getName().equals("param")) {
                        inputFolder = XMLSupport.getAttrib(child, "inputFolder");
                        outputFolder = XMLSupport.getAttrib(child, "outputFolder");
                        processingGraph = XMLSupport.getAttrib(child, "graph");
                        lastProcessed = XMLSupport.getAttrib(child, "lastProcessed");
                    } else if(child.getName().equals("points")) {
                        final List pntsList = child.getContent();
                        for (Object o : pntsList) {
                            if (o instanceof Element) {
                                final Element pntElem = (Element) o;
                                final String latStr = XMLSupport.getAttrib(pntElem, "lat");
                                final String lonStr = XMLSupport.getAttrib(pntElem, "lon");
                                if(!latStr.isEmpty() && !lonStr.isEmpty()) {
                                    final float lat = Float.parseFloat(latStr);
                                    final float lon = Float.parseFloat(lonStr);
                                    geoPosList.add(new GeoPos(lat, lon));
                                }
                            }
                        }
                    } else if(child.getName().equals(DBQuery.DB_QUERY)) {
                        slaveDBQuery = new DBQuery();
                        slaveDBQuery.fromXML(child);
                    }
                }
            }

            aoiPoints = geoPosList.toArray(new GeoPos[geoPosList.size()]);
        } catch(IOException e) {
            VisatApp.getApp().showErrorDialog(e.getMessage());
            return false;
        }
        return true;
    }
}
