package org.esa.nest.dat.toolviews.Projects;

import org.jdom.Element;
import org.jdom.Attribute;

import java.io.File;
import java.util.Vector;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
* User: lveci
* Date: Jul 2, 2008
* To change this template use File | Settings | File Templates.
*/
public class ProjectSubFolder {

    private String folderName;
    private File path;
    private Vector<File> fileList = new Vector<File>(20);
    private Vector<ProjectSubFolder> subFolders = new Vector<ProjectSubFolder>(10);
    private boolean removeable = true;
    private boolean physical = false;
    private boolean createdByUser = false;
    private FolderType folderType;

    enum FolderType { ROOT, STACK, GRAPH, PRODUCT }

    ProjectSubFolder(File newPath, String name, boolean isPhysical, FolderType type) {
        path = newPath;
        folderName = name;
        physical = isPhysical;
        folderType = type;

        if(physical && !path.exists())
            path.mkdir();
    }

    void setCreatedByUser(boolean flag) {
        createdByUser = flag;
    }

    boolean isCreatedByUser() {
        return createdByUser;
    }

    FolderType getFolderType() {
        return folderType;
    }

    void setRemoveable(boolean flag) {
        removeable = flag;
    }

    public boolean canBeRemoved() {
        return removeable;
    }

    void setPhysical(boolean flag) {
        physical = flag;
    }

    boolean isPhysical() {
        return physical;
    }

    public String getName() {
        return folderName;
    }

    public File getPath() {
        return path;
    }

    void clear() {
        fileList.clear();
        subFolders.clear();
    }

    void addFile(File file) {
        if(!fileList.contains(file))
            fileList.add(file);
    }

    ProjectSubFolder addSubFolder(String name) {
        ProjectSubFolder newFolder = findFolder(name);
        if(newFolder != null)
            return newFolder;

        newFolder = new ProjectSubFolder(new File(path, name), name, physical, folderType);
        subFolders.add(newFolder);
        return newFolder;
    }

    ProjectSubFolder addSubFolder(ProjectSubFolder newFolder) {
        if(findFolder(newFolder.getName()) != null)
            return newFolder;

        newFolder.setPhysical(physical);
        if(physical) {
            if(!newFolder.getPath().exists())
                newFolder.getPath().mkdir();
        }
        subFolders.add(newFolder);
        return newFolder;
    }

    void removeSubFolder(ProjectSubFolder subFolder) {
        subFolders.remove(subFolder);
    }

    void removeFile(File file) {
        fileList.remove(file);
    }

    public ProjectSubFolder findFolder(String name) {
        for(int i=0; i < subFolders.size(); ++i) {
            ProjectSubFolder folder = subFolders.elementAt(i);
            if(folder.getName().equals(name))
                return folder;
        }
        return null;
    }

    public boolean containsFile(File file) {
        if(fileList.contains(file)) {
            return true;
        }
        for(int i=0; i < subFolders.size(); ++i) {
            ProjectSubFolder folder = subFolders.elementAt(i);
            if(folder.containsFile(file))
                return true;
        }
        return false;
    }

    public Vector getSubFolders() {
        return subFolders;
    }

    public Vector getFileList() {
        return fileList;
    }

    public Element toXML() {
        Element elem = new Element("subFolder");
        elem.setAttribute("name", folderName);
        if(createdByUser)
            elem.setAttribute("user", "true");

        for(int i=0; i < subFolders.size(); ++i) {
            ProjectSubFolder sub = subFolders.elementAt(i);
            Element subElem = sub.toXML();
            elem.addContent(subElem);
        }

        for(int i=0; i < fileList.size(); ++i) {
            File file = fileList.elementAt(i);
            Element fileElem = new Element("product");
            fileElem.setAttribute("path", file.getAbsolutePath());
            elem.addContent(fileElem);
        }

        return elem;
    }

    public void fromXML(Element elem, Vector folderList, Vector prodList) {
        List children = elem.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                Element child = (Element) aChild;
                if(child.getName().equals("subFolder")) {
                    Attribute attrib = child.getAttribute("name");
                    ProjectSubFolder subFolder = addSubFolder(attrib.getValue());
                    Attribute attribUser = child.getAttribute("user");
                    if(attribUser != null && attrib.getValue().equals("true"))
                        createdByUser = true;
                    subFolder.fromXML(child, folderList, prodList);
                } else if(child.getName().equals("product")) {
                    Attribute attrib = child.getAttribute("path");

                    File file = new File(attrib.getValue());
                    if (file.exists()) {
                        folderList.add(this);
                        prodList.add(file);
                    }
                }
            }
        }
    }
}
