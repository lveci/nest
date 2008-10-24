package org.esa.nest.dataio.ceos;

import org.esa.nest.util.XMLSupport;
import org.jdom.Attribute;
import org.jdom.Element;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
         B2(6),
         Debug(-1);

         private int theValue;
         int value() {return theValue;}

         CeosDBTypes(int value){
           theValue = value;
         }
     }

    private Map metaMap;
    private org.jdom.Document xmlDoc;
    private final String definitionFile;

    public CeosDB(String defFile) throws IOException {

        definitionFile = defFile;
        try {

            xmlDoc = XMLSupport.LoadXMLFromResource(defFile, this.getClass());

        } catch(Exception e) {
            System.out.println(e.toString());
        }
        metaMap = new HashMap(100);
    }

    public Map getMetadataElement() {
        return metaMap;
    }

    public void readRecord(CeosFileReader reader) throws IOException, IllegalCeosFormatException
    {
        final Element root = xmlDoc.getRootElement();
        //System.out.println(definitionFile);
        
        final List children = root.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                final Element child = (Element) aChild;

                if(child.getName().equals("struct")) {
                    final Attribute loopAttrib = child.getAttribute("loop");
                    final String loopName = loopAttrib.getValue();
                    final int loop = getAttributeInt(loopName);

                    final List structChildren = child.getChildren();
                    for(int l=1; l <= loop; ++l) {

                        final String suffix = " " + l;
                        for (Object aStructChild : structChildren) {
                            if (aStructChild instanceof Element) {

                                DecodeElement(reader, metaMap, (Element) aStructChild, suffix);
                            }
                        }
                    }
                }
                
                DecodeElement(reader, metaMap, child, null);
            }
        }

    }

    private static void DecodeElement(CeosFileReader reader, Map metaMap, Element child, String suffix)
            throws IOException, IllegalCeosFormatException {

        String name="";
        try {
            final Attribute nameAttrib = child.getAttribute("name");
            final Attribute typeAttrib = child.getAttribute("type");
            final Attribute numAttrib = child.getAttribute("num");
            if(nameAttrib != null && typeAttrib != null && numAttrib != null) {

                name = nameAttrib.getValue();
                if(suffix != null)
                    name += suffix;
                final int type = Integer.parseInt(typeAttrib.getValue());
                final int num = Integer.parseInt(numAttrib.getValue());

                //System.out.print(" " + reader.getCurrentPos() + ' ' + name + ' ' + type + ' ' + num);

                if(type == CeosDBTypes.Skip.value()) {
                    reader.skipBytes(num); // blank
                } else if(type == CeosDBTypes.An.value()) {

                    //String tmp = reader.readAn(num);
                    //System.out.print(" = " + tmp);
                    //metaMap.put(name , tmp);
                    metaMap.put(name , reader.readAn(num));
                } else if(type == CeosDBTypes.In.value()) {

                    //int tmp = (int)reader.readIn(num);
                    //System.out.print(" = " + tmp);
                    //metaMap.put(name , tmp);
                    metaMap.put(name , (int)reader.readIn(num));
                } else if(type == CeosDBTypes.B1.value()) {

                    //int tmp = reader.readB1();
                    //System.out.print(" = " + tmp);
                    //metaMap.put(name , tmp);
                    metaMap.put(name , reader.readB1());
                } else if(type == CeosDBTypes.B2.value()) {

                    //int tmp = reader.readB2();
                    //System.out.print(" = " + tmp);
                    //metaMap.put(name , tmp);
                    metaMap.put(name , reader.readB2());
                } else if(type == CeosDBTypes.B4.value()) {

                    //int tmp = reader.readB4();
                    //System.out.print(" = " + tmp);
                    //metaMap.put(name , tmp);
                    metaMap.put(name , reader.readB4());
                } else if(type == CeosDBTypes.Fn.value()) {

                    //double tmp = reader.readFn(num);
                    //System.out.print(" = " + tmp);
                    //metaMap.put(name , tmp);
                    metaMap.put(name , reader.readFn(num));
                } else if(type == CeosDBTypes.Debug.value()) {

                    for(int i=0; i < num; ++i) {
                        final String tmp = reader.readAn(1);
                        if(!tmp.isEmpty() && !tmp.equals(" "))
                            System.out.print(tmp);
                    }
                    System.out.println();
                } else {
                    throw new IllegalCeosFormatException("Unknown type " + type, reader.getCurrentPos());
                }
                //System.out.println();
            }

        } catch(IllegalCeosFormatException e) {
            System.out.println(e.toString() + " for "+ name);

            throw e;
        }

    }

    private Object get(String name) {
        final Object obj = metaMap.get(name);
        if(obj == null) {
            System.out.println("metadata "+name+" is null");
        }
        return obj;
    }

    public String getAttributeString(String name) {
        return (String) get(name);
    }
    
    public int getAttributeInt(String name) {
        return (Integer) get(name);
    }

    public Double getAttributeDouble(String name) {
        return (Double) get(name);
    }
}
