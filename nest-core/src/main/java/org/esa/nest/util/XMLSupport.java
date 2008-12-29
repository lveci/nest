package org.esa.nest.util;

import org.jdom.input.DOMBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Attribute;
import org.xml.sax.SAXException;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.ProductData;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import com.sun.org.apache.xerces.internal.parsers.DOMParser;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jan 23, 2008
 * Time: 2:45:16 PM
 * To change this template use File | Settings | File Templates.
 */
public class XMLSupport {

    public static void SaveXML(Document doc, String filePath) {

        try {
            Format xmlFormat = Format.getRawFormat().setIndent(" ").setLineSeparator("\n");
            XMLOutputter outputter = new XMLOutputter(xmlFormat);

            FileWriter writer = new FileWriter(filePath);
            outputter.output(doc, writer);
            //outputter.output(doc, System.out);
            writer.close();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    public static org.jdom.Document LoadXML(String filePath) throws IOException {

        DOMBuilder builder = new DOMBuilder();

        DOMParser parser = new DOMParser();
        try {
            // handle spaces in the path
            String path = filePath.replaceAll(" ", "%20");
            parser.parse(path);
            org.w3c.dom.Document domDoc = parser.getDocument();
            return builder.build(domDoc);
        } catch (IOException e) {
            System.out.println("Path to xml is not valid: " + e.getMessage());
            throw e;
        } catch (SAXException e) {
            System.out.println("cannot parse xml : " + e.getMessage());
            throw new IOException(e.getMessage());
        }
    }

    public static org.jdom.Document LoadXMLFromResource(String filePath, Class theClass) throws IOException {
       
        java.net.URL resURL = theClass.getClassLoader().getResource(filePath);
        if (resURL != null)
            return LoadXML(resURL.toString());
        return null;
    }


    public static void metadataElementToDOMElement(final MetadataElement metadataElem, final Element domElem) {

        final MetadataElement[] metaElements = metadataElem.getElements();
        for(MetadataElement childMetaElem : metaElements) {
            final Element childDomElem = new Element(childMetaElem.getName());
            metadataElementToDOMElement(childMetaElem, childDomElem);
            domElem.addContent(childDomElem);
        }

        final MetadataAttribute[] metaAttributes = metadataElem.getAttributes();
        for(MetadataAttribute childMetaAttrib : metaAttributes) {
            final Element childDomElem = new Element("attrib");
            childDomElem.setAttribute("name", childMetaAttrib.getName());
            childDomElem.setAttribute("value", childMetaAttrib.getData().getElemString());
            childDomElem.setAttribute("type", String.valueOf(childMetaAttrib.getDataType()));
            childDomElem.setAttribute("unit", childMetaAttrib.getUnit());
            childDomElem.setAttribute("desc", childMetaAttrib.getDescription());
            domElem.addContent(childDomElem);
        }
    }

    public static void domElementToMetadataElement(final Element domElem, final MetadataElement metadataElem) {

        final List children = domElem.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                final Element child = (Element) aChild;
                final List grandChildren = child.getContent();
                if(!grandChildren.isEmpty()) {
                    final MetadataElement newElem = new MetadataElement(child.getName());
                    domElementToMetadataElement(child, newElem);
                    metadataElem.addElement(newElem);
                }

                if(child.getName().equals("attrib")) {
                    addAttribute(metadataElem, child);
                }
            }
        }
    }

    // todo incomplete
    private static void addAttribute(MetadataElement root, Element domElem) {

        final Attribute nameAttrib = domElem.getAttribute("name");
        final Attribute valueAttrib = domElem.getAttribute("value");
        final Attribute typeAttrib = domElem.getAttribute("type");
        final Attribute unitAttrib = domElem.getAttribute("unit");
        final Attribute descAttrib = domElem.getAttribute("desc");

        if(nameAttrib == null || valueAttrib == null)
            return;

        final MetadataAttribute attribute = new MetadataAttribute(nameAttrib.getName(), ProductData.TYPE_ASCII, 1);
        attribute.getData().setElems(valueAttrib.getValue());
        
        root.addAttributeFast(attribute);
    }
}
