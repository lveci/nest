package org.esa.nest.dataio;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.Guardian;
import org.esa.nest.util.XMLSupport;
import org.esa.nest.datamodel.AbstractMetadata;
import org.jdom.Element;
import org.jdom.Attribute;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * This class represents a product directory.
 * <p/>
 * <p>This class is public for the benefit of the implementation of another (internal) class and its API may
 * change in future releases of the software.</p>
 *
 */
public class XMLProductDirectory {

    private final File _xmlHeader;
    private final File _baseDir;
    private final File imgFolder;
    private org.jdom.Document xmlDoc;

    private int _sceneWidth;
    private int _sceneHeight;

    private transient Map<String, ImageIOFile> bandImageFileMap = new HashMap<String, ImageIOFile>(1);
    private transient Map<Band, ImageIOFile.BandInfo> bandMap = new HashMap<Band, ImageIOFile.BandInfo>(3);

    public XMLProductDirectory(final File headerFile, final File imageFolder) {
        Guardian.assertNotNull("headerFile", headerFile);

        _xmlHeader = headerFile;
        _baseDir = headerFile.getParentFile();
        imgFolder = imageFolder;
    }

    public void readProductDirectory() throws IOException {

        xmlDoc = XMLSupport.LoadXML(_xmlHeader.getAbsolutePath());

        File[] fileList = imgFolder.listFiles();
        for (File file : fileList) {
            if (file.getName().toUpperCase().endsWith("TIF") && !file.getName().toLowerCase().contains("browse")) {
                ImageIOFile img = new ImageIOFile(file);
                bandImageFileMap.put(img.getName(), img);

                _sceneWidth = img.getSceneWidth();
                _sceneHeight = img.getSceneHeight();
            }
        }   
    }

    public Product createProduct() throws IOException {
        final Product product = new Product(getProductName(),
                                            getProductType(),
                                            _sceneWidth, _sceneHeight);

        int bandCnt = 1;
        Set keys = bandImageFileMap.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            ImageIOFile img = bandImageFileMap.get(key);

            for(int i=0; i < img.getNumImages(); ++i) {

                for(int b=0; b < img.getNumBands(); ++b) {
                    final Band band = new Band(img.getName()+bandCnt++, img.getDataType(),
                                       img.getSceneWidth(), img.getSceneHeight());
                    product.addBand(band);
                    bandMap.put(band, new ImageIOFile.BandInfo(img, i, b));
                }
            }
        }

        addGeoCoding(product);
        addMetaData(product);

        //product.setStartTime(getUTCScanStartTime());
        //product.setEndTime(getUTCScanStopTime());
        product.setName(getProductName());
        product.setProductType(getProductType());
        product.setDescription(getProductDescription());

        return product;
    }

    public ImageIOFile.BandInfo getBandInfo(Band destBand) {
        return bandMap.get(destBand);
    }

    public void close() throws IOException {
        Set keys = bandImageFileMap.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            ImageIOFile img = bandImageFileMap.get(key);
            img.close();
        }
    }

    protected void addGeoCoding(final Product product) throws IOException {

    }

    private void addMetaData(final Product product) throws IOException {
        final MetadataElement root = product.getMetadataRoot();
        root.addElement(new MetadataElement(Product.ABSTRACTED_METADATA_ROOT_NAME));

        Element rootElement = xmlDoc.getRootElement();
        AddXMLMetadata(rootElement, root);

        addAbstractedMetadataHeader(root);
    }

    private static void AddXMLMetadata(Element xmlRoot, MetadataElement metadataRoot) {

        if(xmlRoot.getChildren().isEmpty() && xmlRoot.getAttributes().isEmpty()) {
            if(!xmlRoot.getValue().isEmpty()) {
                addAttribute(metadataRoot, xmlRoot.getName(), xmlRoot.getValue());
            }
        } else if(xmlRoot.getChildren().isEmpty()) {
            MetadataElement metaElem = new MetadataElement(xmlRoot.getName());

            addAttribute(metaElem, xmlRoot.getName(), xmlRoot.getValue());

            List<Attribute> xmlAttribs = xmlRoot.getAttributes();
            for (Attribute aChild : xmlAttribs) {
                addAttribute(metaElem, aChild.getName(), aChild.getValue());
            }

            metadataRoot.addElement(metaElem);
        } else {
            MetadataElement metaElem = new MetadataElement(xmlRoot.getName());
            xmlRoot.getAttributes();

            List children = xmlRoot.getContent();
            for (Object aChild : children) {
                if (aChild instanceof Element) {
                    Element childElem = (Element) aChild;
                    AddXMLMetadata(childElem, metaElem);
                } else if(aChild instanceof Attribute) {
                    Attribute childAtrrib = (Attribute) aChild;
                    addAttribute(metaElem, childAtrrib.getName(), childAtrrib.getValue());
                }
            }

            List<Attribute> xmlAttribs = xmlRoot.getAttributes();
            for (Attribute aChild : xmlAttribs) {
                addAttribute(metaElem, aChild.getName(), aChild.getValue());
            }

            metadataRoot.addElement(metaElem);
        }
    }

    private static void addAttribute(MetadataElement meta, String name, String value) {
        MetadataAttribute attribute = new MetadataAttribute(name, ProductData.TYPE_ASCII, 1);
        attribute.getData().setElems(value);
        meta.addAttributeFast(attribute);
    }

    protected void addAbstractedMetadataHeader(MetadataElement root) {

        AbstractMetadata.addAbstractedMetadataHeader(root);

        MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);

    }

    protected String getProductName() {
        return _xmlHeader.getName();
    }

    protected String getProductDescription() {
        return "";
    }

    protected String getProductType() {
        return "XML-based Product";
    }

}