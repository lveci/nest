package org.esa.nest.util;

import org.jdom.Element;
import org.jdom.Document;
import org.jdom.Attribute;
import org.esa.beam.util.SystemUtils;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Apr 24, 2008
 * To change this template use File | Settings | File Templates.
 */
public class Settings {

    static private Settings _instance = null;
    static private String settingsFile = "settings.xml";
    private Map settingMap;

    /**
    * @return The unique instance of this class.
    */
    static public Settings instance() {
      if(null == _instance) {
         _instance = new Settings();
      }
      return _instance;
    }

    private Settings() {
        settingMap = new HashMap(100);

        Load();
    }

    public void Save() {

        File appHomePath = SystemUtils.getCurrentWorkingDir();
        String filePath = appHomePath.getAbsolutePath() + "\\config\\" + settingsFile;

        Element root = new Element("DAT");
        Document doc = new Document(root);

        int numProducts = settingMap.size();
        for(int i=0; i < numProducts; ++i) {
            //String path = VisatApp.getApp().getProductManager().getProductAt(i).getFileLocation().getAbsolutePath();

            //Element projElem = new Element("product").setAttribute("path", path);
            //root.addContent(projElem);

        }

        XMLSupport.SaveXML(doc, filePath);
    }

    public void Load() {

        File appHomePath = SystemUtils.getCurrentWorkingDir();
        String filePath = appHomePath.getAbsolutePath() + "\\config\\" + settingsFile;
        
        org.jdom.Document doc = XMLSupport.LoadXML(filePath);

        settingMap.clear();

        Element root = doc.getRootElement();

        List children = root.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                Element child = (Element) aChild;
                Attribute attrib = child.getAttribute("value");
                if (attrib != null) {

                    settingMap.put(child.getName(), attrib.getValue());
                }
            }
        }
    }


    public String get(String key)
    {
        return (String)settingMap.get(key);
    }
}
