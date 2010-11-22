/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
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
package org.esa.nest.dataio.ceos;

import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.util.XMLSupport;
import org.jdom.Attribute;
import org.jdom.Element;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: May 13, 2008
 * To change this template use File | Settings | File Templates.
 */
public final class CeosDB {

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

    private final Map<String, Object> metaMap;
    private org.jdom.Document xmlDoc = null;

    public CeosDB(String defFile) throws IOException {

        try {
            xmlDoc = XMLSupport.LoadXML(defFile);
            //System.out.println(defFile);
        } catch(Exception e) {
            System.out.println(e.toString());
        }
        metaMap = new HashMap<String, Object>(100);
    }

    public void assignMetadataTo(final MetadataElement elem) {

        final Set keys = metaMap.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            final Object value = metaMap.get(key);                   // Get the value for that key.
            if (value == null) continue;

            if(value instanceof String) {
                elem.setAttributeString((String)key, value.toString());
            } else if(value instanceof Integer) {
                elem.setAttributeInt((String)key, (Integer)value);
            } else if(value instanceof Double) {
                MetadataAttribute attrib = new MetadataAttribute((String)key, ProductData.TYPE_FLOAT64, 1);
                attrib.getData().setElemDouble((Double)value);
                elem.addAttribute(attrib);
            } else {
                elem.setAttributeString((String)key, String.valueOf(value));
            }
        }
    }

    public void readRecord(BinaryFileReader reader) throws IOException, IllegalBinaryFormatException
    {
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

    private static void DecodeElement(BinaryFileReader reader, Map metaMap, Element child, String suffix)
            throws IOException, IllegalBinaryFormatException {

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
    
    public final int getAttributeInt(String name) {
        return (Integer) get(name);
    }

    public final Double getAttributeDouble(String name) {
        return (Double) get(name);
    }

    public final void set(String name, Object o) {
        metaMap.put(name, o);
    }
}
