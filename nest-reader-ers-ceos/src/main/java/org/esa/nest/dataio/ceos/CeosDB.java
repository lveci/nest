package org.esa.nest.dataio.ceos;

import org.jdom.Element;
import org.jdom.Attribute;
import org.esa.nest.util.XMLSupport;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: May 13, 2008
 * To change this template use File | Settings | File Templates.
 */
public class CeosDB {

    public enum CeosDBTypes {
         Skip(0),
         An(1),
         In(2),
         B1(3),
         B4(4),
         Fn(5),
         Debug(-1);

         private int theValue;
         int value() {return theValue;}

         CeosDBTypes(int value){
           theValue = value;
         }
     }

    private Map metadata;
    private org.jdom.Document xmlDoc;

    public CeosDB(File defFile) throws IOException {

        xmlDoc = XMLSupport.LoadXML(defFile.getAbsolutePath());
        metadata = new HashMap(100);
    }

    public Map getMetadataElement() {
        return metadata;
    }

    public void readRecord(CeosFileReader reader) throws IOException, IllegalCeosFormatException
    {
        try {
        Element root = xmlDoc.getRootElement();

        List children = root.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                Element child = (Element) aChild;
                Attribute nameAttrib = child.getAttribute("name");
                Attribute typeAttrib = child.getAttribute("type");
                Attribute numAttrib = child.getAttribute("num");
                if(nameAttrib != null && typeAttrib != null && numAttrib != null) {

                    String name = nameAttrib.getValue();
                    int type = Integer.parseInt(typeAttrib.getValue());
                    int num = Integer.parseInt(numAttrib.getValue());

                    System.out.print(" " + reader.getCurrentPos() + ' ' + name + ' ' + type + ' ' + num);

                    if(type == CeosDBTypes.Skip.value()) {
                        reader.skipBytes(num); // blank
                    } else if(type == CeosDBTypes.An.value()) {

                        String tmp = reader.readAn(num);
                        System.out.print(" = " + tmp);
                        metadata.put(name , tmp);
                        //metadata.put(name , reader.readAn(num));
                    } else if(type == CeosDBTypes.In.value()) {

                        int tmp = (int)reader.readIn(num);
                        System.out.print(" = " + tmp);
                        metadata.put(name , tmp);
                        //metadata.put(name , (int)reader.readIn(num));
                    } else if(type == CeosDBTypes.B1.value()) {

                        int tmp = reader.readB1();
                        System.out.print(" = " + tmp);
                        metadata.put(name , tmp);
                        //metadata.put(name , reader.readB1());
                    } else if(type == CeosDBTypes.B4.value()) {

                        int tmp = reader.readB4();
                        System.out.print(" = " + tmp);
                        metadata.put(name , tmp);
                        //metadata.put(name , reader.readB4());
                    } else if(type == CeosDBTypes.Fn.value()) {

                        double tmp = reader.readFn(num);
                        System.out.print(" = " + tmp);
                        metadata.put(name , tmp);
                        //metadata.put(name , reader.readFn(num));
                    } else if(type == CeosDBTypes.Debug.value()) {

                        for(int i=0; i < num; ++i) {
                            String tmp = reader.readAn(1);
                            if(!tmp.isEmpty() && !tmp.equals(" "))
                                System.out.print(tmp);
                        }
                    } else {
                        throw new IllegalCeosFormatException("Unknown type " + type, reader.getCurrentPos());
                    }

                    System.out.println();
                }
            }
        }
        System.out.println();

        } catch(IllegalCeosFormatException e) {
            throw e;
        }
    }

    public String getAttributeString(String name) {
        return (String)metadata.get(name);
    }
    
    public int getAttributeInt(String name) {
        return (Integer)metadata.get(name);
    }

    public Double getAttributeDouble(String name) {
        return (Double)metadata.get(name);
    }
}
