package org.esa.nest.dat.toolviews.Projects;

import org.esa.nest.util.XMLSupport;
import org.esa.nest.util.DatUtils;
import org.esa.nest.dat.dialogs.PromptDialog;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.framework.ui.product.ProductTreeListener;
import org.esa.beam.framework.ui.BasicApp;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductIO;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Attribute;

import java.util.*;
import java.io.File;
import java.io.IOException;

import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jan 23, 2008
 * Time: 1:39:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class Project extends Observable {

    static private Project _instance = null;

    private File projectFolder;
    private File projectFile;
    private ProjectPTL productTreeListener;
    private ProjectSubFolder projectSubFolders;

    /**
    * @return The unique instance of this class.
    */
    static public Project instance() {
        if(null == _instance) {
            _instance = new Project();
        }
        return _instance;
    }

    private void notifyEvent() {
        setChanged();
        notifyObservers();
        clearChanged();
    }

    public void CreateNewProject() {
        File file = DatUtils.GetFilePath("Create Project", "XML", "xml", "Project File", true);
        if(file != null) {
            initProject(file);
            addExistingOpenedProducts();
            notifyEvent();
        }
    }

    private void addExistingOpenedProducts() {
        ProductManager prodman = VisatApp.getApp().getProductManager();
        int numProducts = prodman.getNumProducts();
        for(int i=0; i < numProducts; ++i) {
            addImportedProduct(prodman.getProductAt(i));
        }
    }

    private static boolean findSubFolders(File currentFolder, ProjectSubFolder projSubFolder) {
        File[] files = currentFolder.listFiles();
        boolean hasProducts = false;

        for(File f : files) {
            if(f.isDirectory()) {
                ProjectSubFolder newProjFolder = projSubFolder.addSubFolder(f.getName());

                if(findSubFolders(f, newProjFolder))
                    hasProducts = true;
                else 
                    projSubFolder.removeSubFolder(newProjFolder);
            } else {
                ProductReader reader = ProductIO.getProductReaderForFile(f);
                if (reader != null) {
                    projSubFolder.addFile(f);
                    hasProducts = true;
                }
            }
        }
        return hasProducts;
    }

    public File getProjectFolder() {
        return projectFolder;
    }

    public String getProjectName() {
        String name = projectFile.getName();
        if(name.endsWith(".xml"))
            return name.substring(0, name.length()-4);
        return name;
    }

    private void initProject(File file) {
        if(productTreeListener == null) {
            productTreeListener = new Project.ProjectPTL();
            VisatApp.getApp().addProductTreeListener(productTreeListener);
        }

        projectFile = file;
        projectFolder = file.getParentFile();

        projectSubFolders = new ProjectSubFolder(projectFolder, getProjectName());
        projectSubFolders.setRemoveable(false);
        ProjectSubFolder stacksFolder = projectSubFolders.addSubFolder("Stacks");
        stacksFolder.setRemoveable(false);
        ProjectSubFolder importedFolder = projectSubFolders.addSubFolder("Imported Products");
        importedFolder.setRemoveable(false);
        ProjectSubFolder processedFolder = projectSubFolders.addSubFolder("Processed Products");
        processedFolder.setRemoveable(false);
        processedFolder.setPhysical(true);
        processedFolder.addSubFolder("Calibrated Products");
        processedFolder.addSubFolder("Coregistered Products");
        processedFolder.addSubFolder("Orthorectified Products");

        findSubFolders(projectFolder, processedFolder);
    }

    private void addImportedProduct(Product product) {
        if(projectSubFolders.containsFile(product.getFileLocation()))
            return;
        ProjectSubFolder importFolder = projectSubFolders.addSubFolder("Imported Products");
        ProjectSubFolder destFolder = importFolder;
        String[] formats = product.getProductReader().getReaderPlugIn().getFormatNames();
        if(formats.length > 0)
            destFolder = importFolder.addSubFolder(formats[0]);

        destFolder.addFile(product.getFileLocation());
    }

    public void CreateNewFolder(ProjectSubFolder subFolder) {
        PromptDialog dlg = new PromptDialog("New Folder", "Name", "");
        dlg.show();
        if(dlg.IsOK()) {
            subFolder.addSubFolder(dlg.getValue());
            notifyEvent();
        }
    }

    public void DeleteFolder(ProjectSubFolder parentFolder, ProjectSubFolder subFolder) {
        parentFolder.removeSubFolder(subFolder);
        notifyEvent();
    }

    public void RemoveFile(ProjectSubFolder parentFolder, File file) {
        parentFolder.removeFile(file);
        notifyEvent();
    }

    public ProjectSubFolder getProjectSubFolders() {
        return projectSubFolders;
    }

    public void CloseProject() {
        projectSubFolders = null;
        notifyEvent();
    }

    public void SaveWorkSpace() {

    }

    public void SaveProjectAs() {
        File file = DatUtils.GetFilePath("Save Project", "XML", "xml", "Project File", true);
        if(file == null) return;
        
        projectFile = file;
        projectFolder = file.getParentFile();

        SaveProject();
    }

    public void SaveProject() {
        if(projectSubFolders == null)
            return;

        Element root = new Element("Project");
        root.setAttribute("name", getProjectName());
        Document doc = new Document(root);

        Vector subFolders = projectSubFolders.getSubFolders();
        for (Enumeration e = subFolders.elements(); e.hasMoreElements();)
        {
            ProjectSubFolder folder = (ProjectSubFolder)e.nextElement();
            Element elem = folder.toXML();
            root.addContent(elem);
        }

        XMLSupport.SaveXML(doc, projectFile.getAbsolutePath());
    }

    public void LoadProject() {

        File file = DatUtils.GetFilePath("Load Project", "XML", "xml", "Project File", false);
        if(file == null) return;

        initProject(file);

        org.jdom.Document doc;
        try {
            doc = XMLSupport.LoadXML(file.getAbsolutePath());
        } catch(IOException e) {
            VisatApp.getApp().showErrorDialog(e.getMessage());
            return;
        }

        Vector folderList = new Vector(30);
        Vector prodList = new Vector(50);

        Element root = doc.getRootElement();

        List children = root.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                Element child = (Element) aChild;
                if(child.getName().equals("subFolder")) {
                    Attribute attrib = child.getAttribute("name");
                    ProjectSubFolder subFolder = projectSubFolders.addSubFolder(attrib.getValue());
                    subFolder.fromXML(child, folderList, prodList);
                }
            }
        }

        loadProducts(folderList, prodList);

        VisatApp.getApp().getPreferences().setPropertyString(
                BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, projectFolder.getAbsolutePath());

        notifyEvent();
    }

    private static void loadProducts(final Vector folderList, final Vector prodList) {

        ProgressMonitorSwingWorker worker = new ProgressMonitorSwingWorker(VisatApp.getApp().getMainFrame(), "Opening Project") {
            @Override
            protected Object doInBackground(com.bc.ceres.core.ProgressMonitor pm) throws Exception {
                pm.beginTask("Opening Project...", prodList.size());
                try {
                    for(int i=0; i < prodList.size(); ++i) {

                        ProjectSubFolder subFolder = (ProjectSubFolder)folderList.get(i);
                        File prodFile = (File)prodList.get(i);

                        ProductReader reader = ProductIO.getProductReaderForFile(prodFile);
                        if (reader != null) {
                            try {
                                //Product product = reader.readProductNodes(prodFile, null);
                                subFolder.addFile(prodFile);
                            } catch(Exception e) {
                                VisatApp.getApp().showErrorDialog(e.getMessage());
                            }
                        }
                        pm.worked(1);
                    }
                } finally {
                    pm.done();
                }
                return null;
            }
        };
        worker.executeWithBlocking();
    }

    private class ProjectPTL implements ProductTreeListener {

        public ProjectPTL() {
        }

        public void productAdded(final Product product) {
            if(projectSubFolders == null) return;
            addImportedProduct(product);
            notifyEvent();
        }

        public void productRemoved(final Product product) {
            //if (getSelectedProduct() == product) {
            //    setSelectedProduct(product);
            //}
           // setProducts(VisatApp.getApp());
        }

        public void productSelected(final Product product, final int clickCount) {
            //setSelectedProduct(product);
        }

        public void metadataElementSelected(final MetadataElement group, final int clickCount) {
            //final Product product = group.getProduct();
            //setSelectedProduct(product);
        }

        public void tiePointGridSelected(final TiePointGrid tiePointGrid, final int clickCount) {
            //final Product product = tiePointGrid.getProduct();
            //setSelectedProduct(product);
        }

        public void bandSelected(final Band band, final int clickCount) {
            //final Product product = band.getProduct();
            //setSelectedProduct(product);
        }
    }

}
