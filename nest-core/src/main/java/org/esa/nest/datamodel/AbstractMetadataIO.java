package org.esa.nest.datamodel;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.nest.util.XMLSupport;
import org.esa.nest.dataio.ReaderUtils;
import org.jdom.Element;
import org.jdom.Document;
import org.jdom.Attribute;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.StringTokenizer;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Aug 12, 2008
 * To change this template use File | Settings | File Templates.
 */
public class AbstractMetadataIO {

     static void Save(final MetadataElement metadataElem, final File metadataFile) {

         Element root = new Element("AbstractedMetadata");
         Document doc = new Document(root);

         XMLSupport.metadataElementToDOMElement(metadataElem, root);

         XMLSupport.SaveXML(doc, metadataFile.getAbsoluteFile().toString());
    }

    static void Load(final Product product, final MetadataElement metadataElem, final File metadataFile)
        throws IOException {

        Document doc;
        try {
            doc = XMLSupport.LoadXML(metadataFile.getAbsolutePath());
        } catch(IOException e) {
            System.out.println(e.getMessage());
            return;
        }

        Element root = doc.getRootElement();
        final List elements = root.getContent();
        for(Object o : elements) {
            if(o instanceof Element) {
                final Element elem = (Element) o;
                if(elem.getName().equals("AbstractedMetadata"))
                    findAbstractedMetadata(metadataElem, elem);
                if(elem.getName().equals("tie-point-grids"))
                    parseTiePointGrids(product, elem);
            }
        }
    }

    private static void findAbstractedMetadata(final MetadataElement metadataElem, final Element root) {
        final MetadataElement[] metaElements = metadataElem.getElements();
        for(MetadataElement childMetaElem : metaElements) {
            findAbstractedMetadata(childMetaElem, root);
        }

        final MetadataAttribute[] metaAttributes = metadataElem.getAttributes();
        final List domChildren = root.getContent();
        for(MetadataAttribute childMetaAttrib : metaAttributes) {
            findElement(domChildren, childMetaAttrib);
        }
    }

    private static void findElement(final List domChildren, final MetadataAttribute childMetaAttrib) {

        for (Object aChild : domChildren) {
            if (aChild instanceof Element) {
                final Element child = (Element) aChild;
                final List grandChild = child.getContent();
                if(!grandChild.isEmpty())
                   findElement(grandChild, childMetaAttrib);

                if(child.getName().equals("attrib")) {
                   loadAttribute(childMetaAttrib, child);
                }
            }
        }
    }

    private static void loadAttribute(final MetadataAttribute metaAttrib, final Element domElem) {

        final Attribute nameAttrib = domElem.getAttribute("name");
        if(nameAttrib == null) return;

        if(!metaAttrib.getName().equalsIgnoreCase(nameAttrib.getValue()))
            return;

        final Attribute valueAttrib = domElem.getAttribute("value");
        if(valueAttrib == null) return;

        final int type = metaAttrib.getDataType();
        if(type == ProductData.TYPE_ASCII)
            metaAttrib.getData().setElems(valueAttrib.getValue());
        else if(type == ProductData.TYPE_UTC)
                metaAttrib.getData().setElems(AbstractMetadata.parseUTC(valueAttrib.getValue()).getArray());
        else if(type == ProductData.TYPE_FLOAT64 || type == ProductData.TYPE_FLOAT32)
            metaAttrib.getData().setElemDouble(Double.parseDouble(valueAttrib.getValue()));
        else
            metaAttrib.getData().setElemInt(Integer.parseInt(valueAttrib.getValue()));

        final Attribute unitAttrib = domElem.getAttribute("unit");
        final Attribute descAttrib = domElem.getAttribute("desc");

        if(descAttrib != null)
            metaAttrib.setDescription(descAttrib.getValue());
        if(unitAttrib != null)
            metaAttrib.setUnit(unitAttrib.getValue());
    }

    private static void parseTiePointGrids(final Product product, final Element tpgElem) throws IOException {
        final List tpgElements = tpgElem.getContent();
        for(Object o : tpgElements) {
            if(!(o instanceof Element)) continue;

            final Element elem = (Element) o;
            final String name = elem.getName();
            final List content = elem.getContent();
            final ArrayList<Float> valueList = new ArrayList<Float>();
            int columnCount = 0;
            int rowCount = 0;
            for(Object row : content) {
                if(!(row instanceof Element)) continue;
                final Element rowElem = (Element) row;
                final Attribute value = rowElem.getAttribute("value");

                int columns = parseTiePointGirdRow(value.getValue(), valueList);
                if(columnCount == 0)
                    columnCount = columns;
                else if(columnCount != columns)
                    throw new IOException("Metadata for tie-point-grid "+name+" has incorrect number of columns");
                ++rowCount;
            }

            addTiePointGrid(product, name, valueList, columnCount, rowCount);
        }

        // set GeoCoding
        TiePointGrid[] grids = product.getTiePointGrids();
        TiePointGrid latGrid = null;
        TiePointGrid lonGrid = null;
        for(TiePointGrid g : grids) {
            if(g.getName().toLowerCase().contains("lat"))
                latGrid = g;
            else if(g.getName().toLowerCase().contains("lon"))
                lonGrid = g;
        }
        if(latGrid != null && lonGrid != null) {
            final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

            product.setGeoCoding(tpGeoCoding);
        }
    }

    private static int parseTiePointGirdRow(final String line, final ArrayList<Float> valueList) {
        final StringTokenizer tokenizer = new StringTokenizer(line, ",");
        int tokenCount = 0;
        while(tokenizer.hasMoreTokens()) {
            valueList.add(Float.parseFloat(tokenizer.nextToken()));
            ++tokenCount;
        }
        return tokenCount;
    }

    private static void addTiePointGrid(final Product product, final String name, final ArrayList<Float> valueList,
                                        final int inputWidth, final int inputHeight) {
        final int gridWidth = inputWidth * 5;
        final int gridHeight = inputHeight * 5;

        final float subSamplingX = (float)product.getSceneRasterWidth() / (float)(gridWidth - 1);
        final float subSamplingY = (float)product.getSceneRasterHeight() / (float)(gridHeight - 1);

        final float[] inPoints = new float[valueList.size()];
        final float[] outPoints = new float[gridWidth*gridHeight];
        int i = 0;
        for(Float val : valueList) {
            inPoints[i++] = val;
        }

        ReaderUtils.createFineTiePointGrid(inputWidth, inputHeight, gridWidth, gridHeight, inPoints, outPoints);

        final TiePointGrid incidentAngleGrid = new TiePointGrid(name, gridWidth, gridHeight, 0, 0,
                subSamplingX, subSamplingY, outPoints);

        product.addTiePointGrid(incidentAngleGrid);
    }

}