package org.esa.nest.util;

import com.sun.org.apache.xerces.internal.parsers.DOMParser;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.DOMBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.xml.sax.SAXException;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jan 23, 2008
 * Time: 2:45:16 PM
 * To change this template use File | Settings | File Templates.
 */
public class XMLSupport {

    public static void SaveXML(final Document doc, final String filePath) {

        try {
            final Format xmlFormat = Format.getPrettyFormat();
            final XMLOutputter outputter = new XMLOutputter(xmlFormat);
                             
            final FileWriter writer = new FileWriter(filePath);
            outputter.output(doc, writer);
            //outputter.output(doc, System.out);
            writer.close();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    public static org.jdom.Document LoadXML(final String filePath) throws IOException {

        final DOMBuilder builder = new DOMBuilder();

        final DOMParser parser = new DOMParser();
        try {
            // handle spaces in the path
            final String path = filePath.replaceAll(" ", "%20");
            parser.parse(path);
            final org.w3c.dom.Document domDoc = parser.getDocument();
            return builder.build(domDoc);
        } catch (IOException e) {
            System.out.println("Path to xml is not valid: " + e.getMessage());
            throw e;
        } catch (SAXException e) {
            System.out.println("cannot parse xml : " + e.getMessage());
            throw new IOException(e.getMessage());
        }
    }

    public static org.jdom.Document LoadXMLFromResource(final String filePath, final Class theClass) throws IOException {
       
        final java.net.URL resURL = theClass.getClassLoader().getResource(filePath);
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
            setAttribute(childDomElem, "name", childMetaAttrib.getName());
            setAttribute(childDomElem, "value", childMetaAttrib.getData().getElemString());
            if(childMetaAttrib.getUnit() != null && childMetaAttrib.getUnit().equalsIgnoreCase("utc"))
                setAttribute(childDomElem, "type", String.valueOf(ProductData.TYPE_UTC));
            else
                setAttribute(childDomElem, "type", String.valueOf(childMetaAttrib.getDataType()));
            setAttribute(childDomElem, "unit", childMetaAttrib.getUnit());
            setAttribute(childDomElem, "desc", childMetaAttrib.getDescription());
            domElem.addContent(childDomElem);
        }
    }

    private static void setAttribute(final Element childDomElem, final String tag, final String val) {
        if(val != null)
            childDomElem.setAttribute(tag, val);
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
    private static void addAttribute(final MetadataElement root, final Element domElem) {

        final Attribute nameAttrib = domElem.getAttribute("name");
        final Attribute valueAttrib = domElem.getAttribute("value");
        final Attribute typeAttrib = domElem.getAttribute("type");
        final Attribute unitAttrib = domElem.getAttribute("unit");
        final Attribute descAttrib = domElem.getAttribute("desc");

        if(nameAttrib == null || valueAttrib == null)
            return;

        final MetadataAttribute attribute = new MetadataAttribute(nameAttrib.getName(), ProductData.TYPE_ASCII, 1);
        attribute.getData().setElems(valueAttrib.getValue());

        if(unitAttrib != null)
            attribute.setUnit(unitAttrib.getValue());
        if(descAttrib != null)
            attribute.setDescription(descAttrib.getValue());

        root.addAttributeFast(attribute);
    }
}
