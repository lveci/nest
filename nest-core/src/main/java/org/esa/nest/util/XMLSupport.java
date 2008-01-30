package org.esa.nest.util;

import org.jdom.input.DOMBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.Document;
import java.io.FileWriter;
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

    public static org.jdom.Document LoadXML(String filePath) {

        DOMBuilder builder = new DOMBuilder();

        DOMParser parser = new DOMParser();
        try {
            // handle spaces in the path
            String path = filePath.replaceAll(" ", "%20");
            parser.parse(path);
            org.w3c.dom.Document domDoc = parser.getDocument();
            return builder.build(domDoc);
        } catch (Exception e) {
            System.out.println(" is not valid:" + e.getMessage());
        }
        return null;
    }

}
