package org.esa.nest.util;

import org.jdom.Element;
import org.jdom.Document;
import org.jdom.Attribute;
import org.esa.beam.util.SystemUtils;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.io.File;
import java.io.IOException;

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

        File userHomePath = SystemUtils.getUserHomeDir();
        String filePath = userHomePath.getAbsolutePath() + File.separator + "."
                + System.getProperty("ceres.context", "nest")
                + File.separator + settingsFile;

        Element root = new Element("DAT");
        Document doc = new Document(root);

        Set keys = settingMap.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            Object value = settingMap.get(key);                   // Get the value for that key.
            if (value == null) continue;

            Element projElem = new Element(key.toString()).setAttribute("value", value.toString());
            root.addContent(projElem);
        }

        XMLSupport.SaveXML(doc, filePath);
    }

    public void Load() {

        File filePath = DatUtils.findSystemFile(settingsFile);

        org.jdom.Document doc;
        try {
            doc = XMLSupport.LoadXML(filePath.getAbsolutePath());
        } catch(IOException e) {
            return;
        }

        settingMap.clear();

        Element root = doc.getRootElement();

        List children = root.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                Element child = (Element) aChild;
                Attribute attrib = child.getAttribute("value");
                if (attrib != null) {

                    String value = attrib.getValue();
                    if(value.contains("$("))
                        value = resolve(value);
                    settingMap.put(child.getName(), value);
                }
            }
        }
    }

    private String resolve(String value)
    {
        String out;
        int idx1 = value.indexOf("$(");
        int idx2 = value.indexOf(')') + 1;
        String keyWord = value.substring(idx1+2, idx2-1);
        String fullKey = value.substring(idx1, idx2);

        String property = System.getProperty(keyWord);
        if (property != null && property.length() > 0) {
            out = value.replace(fullKey, property);
        } else {

            String settingStr = get(keyWord);
            if(settingStr != null) {
                out = value.replace(fullKey, settingStr);
            } else {
                out = value.substring(0, idx1) + value.substring(idx2, value.length());
            }
        }

        if(keyWord.equalsIgnoreCase("nest.home")) {
            File file = DatUtils.findInHomeFolder(out);
            if(file != null)
                return file.getAbsolutePath();
        }

        if(out.contains("$("))
           out = resolve(out);

        return out;
    }

    public String get(String key)
    {
        return (String)settingMap.get(key);
    }
}
