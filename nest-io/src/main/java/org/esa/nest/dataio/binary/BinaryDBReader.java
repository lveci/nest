/*
 * Copyright (C) 2011 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.dataio.binary;

import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.util.ResourceUtils;
import org.esa.nest.util.XMLSupport;
import org.jdom.Attribute;
import org.jdom.Element;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Binary database reader
 */
public final class BinaryDBReader {

    public enum CeosDBTypes {
         Skip(0),
         An(1),
         In(2),
         B1(3),
         B4(4),
         Fn(5),
         B2(6),
         Debug(-1);

         private final int theValue;
         int value() {return theValue;}

         CeosDBTypes(int value){
           theValue = value;
         }
     }

    private final Map<String, Object> metaMap = new HashMap<String, Object>(100);
    private final org.jdom.Document xmlDoc;

    public BinaryDBReader(final org.jdom.Document xmlDoc) {
        this.xmlDoc = xmlDoc;
    }

    public void assignMetadataTo(final MetadataElement elem) {

        final Set<String> keys = metaMap.keySet();                           // The set of keys in the map.
        for (String key : keys) {
            final Object value = metaMap.get(key);                   // Get the value for that key.
            if (value == null) continue;

            if(value instanceof String) {
                elem.setAttributeString(key, value.toString());
            } else if(value instanceof Integer) {
                elem.setAttributeInt(key, (Integer)value);
            } else if(value instanceof Double) {
                MetadataAttribute attrib = new MetadataAttribute(key, ProductData.TYPE_FLOAT64, 1);
                attrib.getData().setElemDouble((Double)value);
                elem.addAttribute(attrib);
            } else {
                elem.setAttributeString(key, String.valueOf(value));
            }
        }
    }

    public void readRecord(BinaryFileReader reader) {
        final Element root = xmlDoc.getRootElement();
        
        final List children = root.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                final Element child = (Element) aChild;

                if(child.getName().equals("struct")) {
                    final Attribute loopAttrib = child.getAttribute("loop");
                    int loop = 0;
                    if(loopAttrib != null) {
                        final String loopName = loopAttrib.getValue();
                        loop = getAttributeInt(loopName);
                    } else {
                        final Attribute nloopAttrib = child.getAttribute("nloop");
                        loop = Integer.parseInt(nloopAttrib.getValue());
                    }                                                                      

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

    private static void DecodeElement(BinaryFileReader reader, Map metaMap, Element child, String suffix) {

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

                    //final String tmp = reader.readAn(num);
                    //System.out.print(" = " + tmp);
                    //metaMap.put(name , tmp);
                    metaMap.put(name , reader.readAn(num));
                } else if(type == CeosDBTypes.In.value()) {

                    //final int tmp = (int)reader.readIn(num);
                    //System.out.print(" = " + tmp);
                    //metaMap.put(name , tmp);
                    metaMap.put(name , (int)reader.readIn(num));
                } else if(type == CeosDBTypes.B1.value()) {

                    //final int tmp = reader.readB1();
                    //System.out.print(" = " + tmp);
                    //metaMap.put(name , tmp);
                    metaMap.put(name , reader.readB1());
                } else if(type == CeosDBTypes.B2.value()) {

                    //final int tmp = reader.readB2();
                    //System.out.print(" = " + tmp);
                    //metaMap.put(name , tmp);
                    metaMap.put(name , reader.readB2());
                } else if(type == CeosDBTypes.B4.value()) {

                    //final int tmp = reader.readB4();
                    //System.out.print(" = " + tmp);
                    //metaMap.put(name , tmp);
                    metaMap.put(name , reader.readB4());
                } else if(type == CeosDBTypes.Fn.value()) {

                    //double tmp = reader.readFn(num);
                    //System.out.print(" = " + tmp);
                    //metaMap.put(name , tmp);
                    metaMap.put(name , reader.readFn(num));
                } else if(type == CeosDBTypes.Debug.value()) {

                    System.out.print(" = ");
                    for(int i=0; i < num; ++i) {
                        final String tmp = reader.readAn(1);
                        if(!tmp.isEmpty() && !tmp.equals(" "))
                            System.out.print(tmp);
                    }
                    System.out.println();
                } else {
                    throw new IllegalBinaryFormatException("Unknown type " + type, reader.getCurrentPos());
                }
                //System.out.println();
            }

        } catch(Exception e) {
            System.out.println(" " +e.toString() +":"+e.getCause().toString() + " for "+ name);

            //throw new IllegalBinaryFormatException(e.toString(), reader.getCurrentPos());
        }

    }

    private Object get(String name) {
        final Object obj = metaMap.get(name);
        if(obj == null) {
            System.out.println("metadata "+name+" is null");
        }
        return obj;
    }

    public final String getAttributeString(String name) {
        return (String) get(name);
    }
    
    public final Integer getAttributeInt(String name) {
        return (Integer) get(name);
    }

    public final Double getAttributeDouble(String name) {
        return (Double) get(name);
    }

    public final void set(String name, Object o) {
        metaMap.put(name, o);
    }

    /**
     * Read in the definition file
     * @param mission sub folder
     * @param fileName definition file
     * @return xml document
     */
    public static org.jdom.Document loadDefinitionFile(final String mission, final String fileName) {
        try {
            final File defFile = getResFile(mission, fileName);
            return XMLSupport.LoadXML(defFile.getAbsolutePath());
        } catch(Exception e) {
            System.out.println(e.toString());
        }
        return null;
    }

    private static File getResFile(final String mission, final String fileName) {
        final String homeUrl = ResourceUtils.findHomeFolder().getAbsolutePath();
        final String path = homeUrl + File.separator + "res" + File.separator + "ceos_db" +
                File.separator + mission + File.separator + fileName;
        return new File(path);
    }
}
