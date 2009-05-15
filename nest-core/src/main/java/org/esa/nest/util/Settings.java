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
public final class Settings {

    private static final Settings _instance = new Settings();
    private static final String settingsFile = "settings.xml";
    private final Map<String, String> settingMap = new HashMap<String, String>(100);

    private Element rootXML = null;

    /**
    * @return The unique instance of this class.
    */
    static public Settings instance() {
      return _instance;
    }

    private Settings() {
        Load();
    }

    public Element getSettingsRootXML() {
        return rootXML;
    }

    public void Save() {

        final File userHomePath = SystemUtils.getUserHomeDir();
        final String filePath = userHomePath.getAbsolutePath() + File.separator + "."
                + System.getProperty("ceres.context", "nest")
                + File.separator + settingsFile;

        final Element root = new Element("Settings");
        final Document doc = new Document(root);

        final Set keys = settingMap.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            final Object value = settingMap.get(key);                   // Get the value for that key.
            if (value == null) continue;

            final Element projElem = new Element(key.toString()).setAttribute("value", value.toString());
            root.addContent(projElem);
        }

        XMLSupport.SaveXML(doc, filePath);
    }

    public void Load() {

        final File filePath = ResourceUtils.findSystemFile(settingsFile);

        org.jdom.Document doc;
        try {
            doc = XMLSupport.LoadXML(filePath.getAbsolutePath());
        } catch(IOException e) {
            return;
        }

        settingMap.clear();

        rootXML = doc.getRootElement();
        recurseElements(rootXML.getContent(), "");
    }

    private void recurseElements(final List children, final String id) {
        for (Object aChild : children) {
            String newId = "";
            if (aChild instanceof Element) {
                final Element child = (Element) aChild;
                if(!id.isEmpty())
                    newId = id + '/';
                newId += child.getName();
                final Attribute attrib = child.getAttribute("value");
                if (attrib != null) {

                    String value = attrib.getValue();
                    if(value.contains("${"))
                        value = resolve(value);
                    settingMap.put(newId, value);
                }

                final List grandChildren = child.getChildren();
                if(!grandChildren.isEmpty()) {
                    recurseElements(grandChildren, newId);
                }
            }
        }
    }

    private String resolve(String value)
    {
        String out;
        final int idx1 = value.indexOf("${");
        final int idx2 = value.indexOf('}') + 1;
        final String keyWord = value.substring(idx1+2, idx2-1);
        final String fullKey = value.substring(idx1, idx2);

        final String property = System.getProperty(keyWord);
        if (property != null && property.length() > 0) {
            out = value.replace(fullKey, property);
        } else {

            final String settingStr = get(keyWord);
            if(settingStr != null) {
                out = value.replace(fullKey, settingStr);
            } else {
                out = value.substring(0, idx1) + value.substring(idx2, value.length());
            }
        }

        if(keyWord.equalsIgnoreCase("nest.home") || keyWord.equalsIgnoreCase("NEST_HOME")) {
            final File file = ResourceUtils.findInHomeFolder(out);
            if(file != null)
                return file.getAbsolutePath();
        }

        if(out.contains("${"))
           out = resolve(out);

        return out;
    }

    public String get(String key)
    {
        return settingMap.get(key);
    }

    public void set(String key, String value)
    {
        //rootXML

        settingMap.put(key, value);
    }
}
