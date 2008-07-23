package org.esa.nest.dat.toolviews.Projects;

import org.esa.nest.util.XMLSupport;
import org.esa.nest.util.DatUtils;
import org.esa.nest.dat.dialogs.PromptDialog;
import org.esa.nest.dat.dialogs.StackDialog;
import org.esa.nest.dat.plugins.GraphBuilderDialog;
import org.esa.nest.dat.DatContext;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.framework.ui.product.ProductTreeListener;
import org.esa.beam.framework.ui.BasicApp;
import org.esa.beam.framework.ui.ModelessDialog;
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

    void notifyEvent() {
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
                else if(!newProjFolder.isCreatedByUser())
                    projSubFolder.removeSubFolder(newProjFolder);
            } else {
                boolean found = false;
                ProjectSubFolder.FolderType folderType = projSubFolder.getFolderType();
                if(folderType == ProjectSubFolder.FolderType.PRODUCT) {
                    ProductReader reader = ProductIO.getProductReaderForFile(f);
                    found = reader != null;
                } else if(folderType == ProjectSubFolder.FolderType.STACK ||
                        folderType == ProjectSubFolder.FolderType.GRAPH) {
                    found = f.getName().toLowerCase().endsWith(".xml");
                }

                if(found) {
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

    protected void initProject(File file) {
        if(productTreeListener == null && VisatApp.getApp() != null) {
            productTreeListener = new Project.ProjectPTL();
            VisatApp.getApp().addProductTreeListener(productTreeListener);
        }

        projectFile = file;
        projectFolder = file.getParentFile();

        projectSubFolders = new ProjectSubFolder(projectFolder, getProjectName(), false,
                                                ProjectSubFolder.FolderType.ROOT);
        projectSubFolders.setRemoveable(false);

        ProjectSubFolder stacksFolder = new ProjectSubFolder(
                new File(projectFolder, "Stacks"), "Stacks", true, ProjectSubFolder.FolderType.STACK);
        projectSubFolders.addSubFolder(stacksFolder);
        stacksFolder.setRemoveable(false);

        ProjectSubFolder graphsFolder = new ProjectSubFolder(
                new File(projectFolder, "Graphs"), "Graphs", true, ProjectSubFolder.FolderType.GRAPH);
        projectSubFolders.addSubFolder(graphsFolder);
        graphsFolder.setRemoveable(false);

        ProjectSubFolder importedFolder = projectSubFolders.addSubFolder("Imported Products");
        importedFolder.setRemoveable(false);

        ProjectSubFolder processedFolder = new ProjectSubFolder(
                new File(projectFolder, "Processed Products"), "Processed Products", true,
                ProjectSubFolder.FolderType.PRODUCT);
        projectSubFolders.addSubFolder(processedFolder);
        processedFolder.setRemoveable(false);

        refreshProjectTree();

        ProjectSubFolder newFolder = processedFolder.addSubFolder("Calibrated Products");
        newFolder.setCreatedByUser(true);
        newFolder = processedFolder.addSubFolder("Coregistered Products");
        newFolder.setCreatedByUser(true);
        //newFolder = processedFolder.addSubFolder("Orthorectified Products");
        //newFolder.setCreatedByUser(true);

        if(VisatApp.getApp() != null)
            VisatApp.getApp().getPreferences().setPropertyString(
                BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, processedFolder.getPath().getAbsolutePath());
    }

    private void addImportedProduct(Product product) {
        if(projectSubFolders.containsFile(product.getFileLocation()))
            return;
        
        refreshProjectTree();
        if(projectSubFolders.containsFile(product.getFileLocation()))
            return;

        ProjectSubFolder importFolder = projectSubFolders.addSubFolder("Imported Products");
        ProjectSubFolder destFolder = importFolder;
        String[] formats = product.getProductReader().getReaderPlugIn().getFormatNames();
        if(formats.length > 0)
            destFolder = importFolder.addSubFolder(formats[0]);

        destFolder.addFile(product.getFileLocation());
    }

    public void refreshProjectTree() {
        ProjectSubFolder stacksFolder = projectSubFolders.findFolder("Stacks");
        findSubFolders(stacksFolder.getPath(), stacksFolder);
        ProjectSubFolder graphsFolder = projectSubFolders.findFolder("Graphs");
        findSubFolders(graphsFolder.getPath(), graphsFolder);
        ProjectSubFolder processedFolder = projectSubFolders.findFolder("Processed Products");
        findSubFolders(processedFolder.getPath(), processedFolder);
        notifyEvent();
    }

    public void createNewFolder(ProjectSubFolder subFolder) {
        PromptDialog dlg = new PromptDialog("New Folder", "Name", "");
        dlg.show();
        if(dlg.IsOK()) {
            ProjectSubFolder newFolder = subFolder.addSubFolder(dlg.getValue());
            newFolder.setCreatedByUser(true);
            notifyEvent();
        }
    }

    public void createNewStack(ProjectSubFolder subFolder) {
        StackDialog dlg = new StackDialog("New Stack", "Name", "");
        dlg.show();
        if(dlg.IsOK()) {
            //ProjectSubFolder newFolder = subFolder.addSubFolder(dlg.getValue());
            //newFolder.setCreatedByUser(true);
            notifyEvent();
        }
    }

    public static void createNewGraph(ProjectSubFolder subFolder) {
        ModelessDialog dialog = new GraphBuilderDialog(new DatContext(""), "Graph Builder", null);
        dialog.show();
    }

    public static void openFile(ProjectSubFolder parentFolder, File file) {
        if(parentFolder.getFolderType() == ProjectSubFolder.FolderType.STACK) {

        } else if(parentFolder.getFolderType() == ProjectSubFolder.FolderType.GRAPH) {
            GraphBuilderDialog dialog = new GraphBuilderDialog(new DatContext(""), "Graph Builder", null);
            dialog.show();
            dialog.LoadGraph(file);
        } else if(parentFolder.getFolderType() == ProjectSubFolder.FolderType.PRODUCT) {
            VisatApp.getApp().openProduct(file);
        }
    }

    public void deleteFolder(ProjectSubFolder parentFolder, ProjectSubFolder subFolder) {
        parentFolder.removeSubFolder(subFolder);
        notifyEvent();
    }

    public void renameFolder(ProjectSubFolder subFolder) {
        PromptDialog dlg = new PromptDialog("Rename Folder", "Name", "");
        dlg.show();
        if(dlg.IsOK()) {
            subFolder.renameTo(dlg.getValue());
            notifyEvent();
        }
    }

    public void removeFile(ProjectSubFolder parentFolder, File file) {
        parentFolder.removeFile(file);
        if(parentFolder.getFolderType() == ProjectSubFolder.FolderType.STACK ||
           parentFolder.getFolderType() == ProjectSubFolder.FolderType.GRAPH)
            file.delete();

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
