package org.esa.nest.dat.toolviews.Projects;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dat.dialogs.ProductSetDialog;
import org.esa.nest.util.XMLSupport;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines a list of Products
 * User: lveci
 * Date: Aug 20, 2008
 */
public final class ProductSet {

    private ArrayList<File> fileList = new ArrayList<File>(10);
    private File productSetFile = null;
    private final File productSetFolder;
    private String name = null;

    ProductSet(final File path) {

        productSetFolder = path.getParentFile();
        setName(path.getName());
    }

    public ArrayList<File> getFileList() {
        return fileList;
    }

    public void setFileList(final ArrayList<File> inFileList) {
        fileList = inFileList;
    }

    public File getFile() {
        return productSetFile;
    }

    public boolean addProduct(final File file) {
        final ProductReader reader = ProductIO.getProductReaderForFile(file);
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

        final Element root = new Element("ProjectSet");
        final Document doc = new Document(root);

        for(File file : fileList) {
            final Element fileElem = new Element("product");
            fileElem.setAttribute("path", file.getAbsolutePath());
            root.addContent(fileElem);
        }

        XMLSupport.SaveXML(doc, productSetFile.getAbsolutePath());
    }

    public boolean Load(final File file) {

        if(!file.exists())
            return false;
        org.jdom.Document doc;
        try {
            doc = XMLSupport.LoadXML(file.getAbsolutePath());
        } catch(IOException e) {
            VisatApp.getApp().showErrorDialog(e.getMessage());
            return false;
        }

        fileList = new ArrayList<File>(10);
        Element root = doc.getRootElement();

        final List children = root.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                final Element child = (Element) aChild;
                if(child.getName().equals("product")) {
                    final Attribute attrib = child.getAttribute("path");
                    fileList.add(new File(attrib.getValue()));
                }
            }
        }
        return true;
    }

    public static void OpenProductSet(File file) {
        final ProductSet prodSet = new ProductSet(file);
        prodSet.Load(file);
        final ProductSetDialog dlg = new ProductSetDialog("ProductSet", prodSet);
        dlg.show();
    }

    public static void AddProduct(File productSetFile, File inputFile) {
        final ProductSet prodSet = new ProductSet(productSetFile);
        prodSet.Load(productSetFile);
        if(prodSet.addProduct(inputFile)) {
            final ProductSetDialog dlg = new ProductSetDialog("ProductSet", prodSet);
            dlg.show();
        }
    }

    public static String GetListAsString(File productSetFile) {
        final ProductSet prodSet = new ProductSet(productSetFile);
        prodSet.Load(productSetFile);

        final StringBuilder listStr = new StringBuilder(256);
        final ArrayList<File> fileList = prodSet.getFileList();
        for(File file : fileList) {
            listStr.append(file.getAbsolutePath());
            listStr.append('\n');
        }

        return listStr.toString();
    }
}
