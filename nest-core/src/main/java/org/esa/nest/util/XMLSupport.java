package org.esa.nest.util;

import org.jdom.input.DOMBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.Document;
import org.xml.sax.SAXException;

import java.io.FileWriter;
import java.io.IOException;

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

}
