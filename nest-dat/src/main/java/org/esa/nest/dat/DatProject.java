package org.esa.nest.dat;

import org.esa.nest.util.XMLSupport;
import org.esa.nest.util.DatUtils;
import org.esa.beam.visat.VisatApp;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Attribute;

import java.util.List;
import java.util.Iterator;
import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jan 23, 2008
 * Time: 1:39:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class DatProject {

    static private DatProject _instance = null;

    /**
    * @return The unique instance of this class.
    */
   static public DatProject instance() {
      if(null == _instance) {
         _instance = new DatProject();
      }
      return _instance;
   }

    public void SaveWorkSpace() {

    }

    public void SaveProject() {

        //if(projectLoaded)

        String filePath = DatUtils.GetFilePath("Save Project", "XML", "xml", "Project File", true);
     
        Element root = new Element("Project");
        Document doc = new Document(root);

        int numProducts = VisatApp.getApp().getProductManager().getNumProducts();
        for(int i=0; i < numProducts; ++i) {
            String path = VisatApp.getApp().getProductManager().getProductAt(i).getFileLocation().getAbsolutePath();

            Element projElem = new Element("product").setAttribute("path", path);
            root.addContent(projElem);
            
        }

        XMLSupport.SaveXML(doc, filePath);
    }

    public void LoadProject() {

        String filePath = DatUtils.GetFilePath("Load Project", "XML", "xml", "Project File", false);

        org.jdom.Document doc;
        try {
            doc = XMLSupport.LoadXML(filePath);
        } catch(IOException e) {
            VisatApp.getApp().showErrorDialog(e.getMessage());
            return;
        }

        Element root = doc.getRootElement();

        List children = root.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                Element child = (Element) aChild;
                Attribute attrib = child.getAttribute("path");
                if (attrib != null) {
                    String path = attrib.getValue();

                    File file = new File(path);
                    if (file.exists()) {
                        VisatApp.getApp().openProduct(file);
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {

                        }
                    }
                }
            }
        }

    }
}
