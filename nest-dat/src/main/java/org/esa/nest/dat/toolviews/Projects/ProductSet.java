package org.esa.nest.dat.toolviews.Projects;

import org.jdom.Element;
import org.jdom.Document;
import org.jdom.Attribute;
import org.esa.nest.util.XMLSupport;
import org.esa.nest.dat.dialogs.ProductSetDialog;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductIO;

import java.io.File;
import java.io.IOException;
import java.util.Vector;
import java.util.List;

/**
 * Defines a list of Products
 * User: lveci
 * Date: Aug 20, 2008
 */
public class ProductSet {

    Vector<File> fileList = new Vector<File>(10);
    File productSetFile;
    File productSetFolder;
    String name;

    ProductSet(File path) {

        productSetFolder = path.getParentFile();
        setName(path.getName());
    }

    public Vector<File> getFileList() {
        return fileList;
    }

    public void setFileList(Vector<File> inFileList) {
        fileList = inFileList;
    }

    public File getFile() {
        return productSetFile;
    }

    public boolean addProduct(File file) {
        ProductReader reader = ProductIO.getProductReaderForFile(file);
        if (reader != null) {
            fileList.add(file);
            return true;
        }
        return false;
    }

    public void setName(String newName) {
        if(name==null || !name.equals(newName)) {
            if(productSetFile != null && productSetFile.exists())
                productSetFile.delete();
            if(!newName.toLowerCase().endsWith(".xml"))
                newName += ".xml";
            name = newName;
            productSetFile = new File(productSetFolder, name);
        }
    }

    public String getName() {
        return name;
    }

    public void Save() {

        Element root = new Element("ProjectSet");
        Document doc = new Document(root);

        for(int i=0; i < fileList.size(); ++i) {
            File file = fileList.elementAt(i);
            Element fileElem = new Element("product");
            fileElem.setAttribute("path", file.getAbsolutePath());
            root.addContent(fileElem);
        }

        XMLSupport.SaveXML(doc, productSetFile.getAbsolutePath());
    }

    public boolean Load(File file) {

        if(!file.exists())
            return false;
        org.jdom.Document doc;
        try {
            doc = XMLSupport.LoadXML(file.getAbsolutePath());
        } catch(IOException e) {
            VisatApp.getApp().showErrorDialog(e.getMessage());
            return false;
        }

        fileList = new Vector<File>(10);
        Element root = doc.getRootElement();

        List children = root.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                Element child = (Element) aChild;
                if(child.getName().equals("product")) {
                    Attribute attrib = child.getAttribute("path");
                    fileList.add(new File(attrib.getValue()));
                }
            }
        }
        return true;
    }

    public static void OpenProductSet(File file) {
        ProductSet prodSet = new ProductSet(file);
        prodSet.Load(file);
        ProductSetDialog dlg = new ProductSetDialog("ProductSet", prodSet);
        dlg.show();
    }

    public static void AddProduct(File productSetFile, File inputFile) {
        ProductSet prodSet = new ProductSet(productSetFile);
        prodSet.Load(productSetFile);
        if(prodSet.addProduct(inputFile)) {
            ProductSetDialog dlg = new ProductSetDialog("ProductSet", prodSet);
            dlg.show();
        }
    }

    public static String GetListAsString(File productSetFile) {
        ProductSet prodSet = new ProductSet(productSetFile);
        prodSet.Load(productSetFile);

        StringBuilder listStr = new StringBuilder(256);
        Vector<File> fileList = prodSet.getFileList();
        for(int i=0; i < fileList.size(); ++i) {
            File file = fileList.elementAt(i);
            listStr.append(file.getAbsolutePath());
            listStr.append('\n');
        }

        return listStr.toString();
    }
}
