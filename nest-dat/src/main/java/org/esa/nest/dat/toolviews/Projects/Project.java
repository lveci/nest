package org.esa.nest.dat.toolviews.Projects;

import org.esa.nest.util.XMLSupport;
import org.esa.nest.util.DatUtils;
import org.esa.nest.dat.dialogs.ProductSetDialog;
import org.esa.nest.dat.plugins.graphbuilder.GraphBuilderDialog;
import org.esa.nest.dat.DatContext;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.dialogs.PromptDialog;
import org.esa.beam.framework.ui.product.ProductTreeListener;
import org.esa.beam.framework.ui.BasicApp;
import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.ExecCommand;
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

import javax.swing.*;

/**
 * A Project helps to organize your data by storing all your work in one folder.
 * User: lveci
 * Date: Jan 23, 2008
 */
public class Project extends Observable {

    static private Project _instance = null;

    private File projectFolder;
    private File projectFile;
    private ProjectPTL productTreeListener;
    private ProjectSubFolder projectSubFolders;
    private final static boolean SAVE_PROJECT = true;

    /**
    * @return The unique instance of this class.
    */
    static public Project instance() {
        if(null == _instance) {
            _instance = new Project();
        }
        return _instance;
    }

    void notifyEvent(boolean saveProject) {
        setChanged();
        notifyObservers();
        clearChanged();
        if(saveProject)
            SaveProject();
    }

    public void showProjectsView() {
        final ExecCommand command = VisatApp.getApp().getCommandManager().
                getExecCommand("org.esa.nest.dat.toolviews.Projects.ProjectsToolView.showCmd");
        command.execute();
    }

    public void CreateNewProject() {
        File file = DatUtils.GetFilePath("Create Project", "xml", "xml", "Project File", true);

        if(file != null) {
            String prjName = file.getName();
            String folderName = prjName.substring(0, prjName.lastIndexOf('.'));
            File prjFolder = new File(file.getParentFile(), folderName);
            if(!prjFolder.exists())
                prjFolder.mkdir();
            File newProjectFile = new File(prjFolder, prjName);

            initProject(newProjectFile);
            addExistingOpenedProducts();
            notifyEvent(SAVE_PROJECT);
            showProjectsView();
        }
    }

    private void addExistingOpenedProducts() {
        ProductManager prodman = VisatApp.getApp().getProductManager();
        int numProducts = prodman.getNumProducts();
        for(int i=0; i < numProducts; ++i) {
            addProductLink(prodman.getProductAt(i));
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
                } else if(folderType == ProjectSubFolder.FolderType.PRODUCTSET ||
                        folderType == ProjectSubFolder.FolderType.GRAPH) {
                    found = f.getName().toLowerCase().endsWith(".xml");
                }

                if(found) {
                    projSubFolder.addFile(new ProjectFile(f, f.getName()));
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

        ProjectSubFolder productSetsFolder = new ProjectSubFolder(
                new File(projectFolder, "ProductSets"), "ProductSets", true, ProjectSubFolder.FolderType.PRODUCTSET);
        projectSubFolders.addSubFolder(productSetsFolder);
        productSetsFolder.setRemoveable(false);

        ProjectSubFolder graphsFolder = new ProjectSubFolder(
                new File(projectFolder, "Graphs"), "Graphs", true, ProjectSubFolder.FolderType.GRAPH);
        projectSubFolders.addSubFolder(graphsFolder);
        graphsFolder.setRemoveable(false);

        ProjectSubFolder productLinksFolder = projectSubFolders.addSubFolder("External Product Links");
        productLinksFolder.setRemoveable(false);
        productLinksFolder.setFolderType(ProjectSubFolder.FolderType.PRODUCT);

        ProjectSubFolder importedFolder = new ProjectSubFolder(
                new File(projectFolder, "Imported Products"), "Imported Products", true,
                ProjectSubFolder.FolderType.PRODUCT);
        projectSubFolders.addSubFolder(importedFolder);
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

        if(VisatApp.getApp() != null) {
            VisatApp.getApp().getPreferences().setPropertyString(
                BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, processedFolder.getPath().getAbsolutePath());
            VisatApp.getApp().updateState();
        }
    }

    private void addProductLink(Product product) {
        File productFile = product.getFileLocation();
        if(productFile == null)
            return;
        if(projectSubFolders.containsFile(productFile))
            return;
        
        refreshProjectTree();
        if(projectSubFolders.containsFile(productFile))
            return;

        ProjectSubFolder productLinksFolder = projectSubFolders.addSubFolder("External Product Links");
        ProjectSubFolder destFolder = productLinksFolder;
        String[] formats = product.getProductReader().getReaderPlugIn().getFormatNames();
        if(formats.length > 0)
            destFolder = productLinksFolder.addSubFolder(formats[0]);

        destFolder.addFile(new ProjectFile(productFile, product.getName()));
    }

    public void refreshProjectTree() {
        ProjectSubFolder productSetsFolder = projectSubFolders.findFolder("ProductSets");
        findSubFolders(productSetsFolder.getPath(), productSetsFolder);
        ProjectSubFolder graphsFolder = projectSubFolders.findFolder("Graphs");
        findSubFolders(graphsFolder.getPath(), graphsFolder);
        ProjectSubFolder importedFolder = projectSubFolders.findFolder("Imported Products");
        findSubFolders(importedFolder.getPath(), importedFolder);
        ProjectSubFolder processedFolder = projectSubFolders.findFolder("Processed Products");
        findSubFolders(processedFolder.getPath(), processedFolder);
    }

    public void createNewFolder(ProjectSubFolder subFolder) {
        PromptDialog dlg = new PromptDialog("New Folder", "Name", "");
        dlg.show();
        if(dlg.IsOK()) {
            ProjectSubFolder newFolder = subFolder.addSubFolder(dlg.getValue());
            newFolder.setCreatedByUser(true);
            if(subFolder == projectSubFolders || subFolder.isPhysical())
                newFolder.setPhysical(true);
            notifyEvent(SAVE_PROJECT);
        }
    }

    public void createNewProductSet(ProjectSubFolder subFolder) {
        String name = "ProductSet"+(subFolder.getFileList().size()+1);
        ProductSet prodSet = new ProductSet(new File(subFolder.getPath(), name));
        ProductSetDialog dlg = new ProductSetDialog("New ProductSet", prodSet);
        dlg.show();
        if(dlg.IsOK()) {
            subFolder.addFile(new ProjectFile(prodSet.getFile(), prodSet.getName()));
            notifyEvent(SAVE_PROJECT);
        }
    }

    public static void createNewGraph(ProjectSubFolder subFolder) {
        ModelessDialog dialog = new GraphBuilderDialog(new DatContext(""), "Graph Builder", "graph_builder");
        dialog.show();
    }

    public static void openFile(ProjectSubFolder parentFolder, File file) {
        if(parentFolder.getFolderType() == ProjectSubFolder.FolderType.PRODUCTSET) {
            ProductSet.OpenProductSet(file);
        } else if(parentFolder.getFolderType() == ProjectSubFolder.FolderType.GRAPH) {
            GraphBuilderDialog dialog = new GraphBuilderDialog(new DatContext(""), "Graph Builder", "graph_builder");
            dialog.show();
            dialog.LoadGraph(file);
        } else if(parentFolder.getFolderType() == ProjectSubFolder.FolderType.PRODUCT) {
            VisatApp.getApp().openProduct(file);
        }
    }

    public void importFile(ProjectSubFolder parentFolder, File prodFile) {
        if(parentFolder.getFolderType() == ProjectSubFolder.FolderType.PRODUCT) {

            ProductReader reader = ProductIO.getProductReaderForFile(prodFile);
            if (reader != null) {
                ProjectSubFolder importedFolder = projectSubFolders.findFolder("Imported Products");

                try {
                    final Product product = reader.readProductNodes(prodFile, null);
                    if(product != null) {

                        final File destFile = new File(importedFolder.getPath(), product.getName());
                        final SwingWorker worker = new SwingWorker() {

                            @Override
                            protected Object doInBackground() throws Exception {
                                VisatApp.getApp().writeProduct(product, destFile, "BEAM-DIMAP");
                                return null;
                            }

                            @Override
                            public void done() {
                                refreshProjectTree();
                                notifyEvent(SAVE_PROJECT);
                            }
                        };
                        worker.execute();

                    }
                } catch(Exception e) {
                    VisatApp.getApp().showErrorDialog(e.getMessage());
                }
            }
        }
    }

    public void ImportFileList(File[] productFilesToOpen) {
        if(!IsProjectOpen()) {
            CreateNewProject();
        }

        ProjectSubFolder importedFolder = projectSubFolders.findFolder("Imported Products");
        for(File f : productFilesToOpen) {
            importFile(importedFolder, f);
        }
    }

    public void deleteFolder(ProjectSubFolder parentFolder, ProjectSubFolder subFolder) {
        parentFolder.removeSubFolder(subFolder);
        notifyEvent(SAVE_PROJECT);
    }

    public void clearFolder(ProjectSubFolder subFolder) {
        subFolder.clear();
        notifyEvent(SAVE_PROJECT);
    }

    public void renameFolder(ProjectSubFolder subFolder) {
        PromptDialog dlg = new PromptDialog("Rename Folder", "Name", "");
        dlg.show();
        if(dlg.IsOK()) {
            subFolder.renameTo(dlg.getValue());
            notifyEvent(SAVE_PROJECT);
        }
    }

    public void removeFile(ProjectSubFolder parentFolder, File file) {
        parentFolder.removeFile(file);
        if(parentFolder.getFolderType() == ProjectSubFolder.FolderType.PRODUCTSET ||
           parentFolder.getFolderType() == ProjectSubFolder.FolderType.GRAPH)
            file.delete();
        else if(parentFolder.getFolderType() == ProjectSubFolder.FolderType.PRODUCT) {
            if(file.getName().endsWith(".dim")) {
                String pathStr = file.getAbsolutePath();
                File dataDir = new File(pathStr.substring(0, pathStr.length()-4) + ".data");
                if(dataDir.exists()) {
                    deleteDir(dataDir);
                    file.delete();
                }
            }
        }
        notifyEvent(SAVE_PROJECT);
    }

    private static void deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String aChild : children) {
                deleteDir(new File(dir, aChild));
            }
        }
        dir.delete();
	}

    public ProjectSubFolder getProjectSubFolders() {
        return projectSubFolders;
    }

    public void CloseProject() {
        projectSubFolders = null;
        notifyEvent(false);

        if(VisatApp.getApp() != null) {
            VisatApp.getApp().updateState();
        }
    }

    public boolean IsProjectOpen() {
        return projectSubFolders != null;
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

        Vector<ProjectSubFolder> folderList = new Vector<ProjectSubFolder>(30);
        Vector<ProjectFile> prodList = new Vector<ProjectFile>(50);

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

        notifyEvent(false);
        showProjectsView();
    }

    private static void loadProducts(final Vector<ProjectSubFolder> folderList,
                                     final Vector<ProjectFile> prodList) {

        ProgressMonitorSwingWorker worker = new ProgressMonitorSwingWorker(VisatApp.getApp().getMainFrame(), "Opening Project") {
            @Override
            protected Object doInBackground(com.bc.ceres.core.ProgressMonitor pm) throws Exception {
                pm.beginTask("Opening Project...", prodList.size());
                try {
                    for(int i=0; i < prodList.size(); ++i) {

                        ProjectSubFolder subFolder = folderList.get(i);
                        ProjectFile projFile = prodList.get(i);
                        File prodFile = projFile.getFile();

                        ProductReader reader = ProductIO.getProductReaderForFile(prodFile);
                        if (reader != null) {
                            try {
                                //Product product = reader.readProductNodes(prodFile, null);
                                subFolder.addFile(projFile);
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
            addProductLink(product);
            notifyEvent(SAVE_PROJECT);
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
