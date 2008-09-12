package org.esa.nest.dataio;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.Guardian;
import org.esa.nest.dataio.ImageIOFile;
import org.esa.nest.util.XMLSupport;
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
    private String productType = "Type";

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
                                            productType,
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

        //product.setStartTime(getUTCScanStartTime());
        //product.setEndTime(getUTCScanStopTime());
        product.setDescription(getProductDescription());

        addGeoCoding(product);
        addMetaData(product);

        return product;
    }

    public ImageIOFile.BandInfo getBandInfo(Band destBand) {
        return bandMap.get(destBand);
    }

    private void addGeoCoding(final Product product) throws IOException {

   /*     TiePointGrid latGrid = new TiePointGrid("lat", 2, 2, 0.5f, 0.5f,
                product.getSceneRasterWidth(), product.getSceneRasterHeight(),
                                                _leaderFile.getLatCorners());
        TiePointGrid lonGrid = new TiePointGrid("lon", 2, 2, 0.5f, 0.5f,
                product.getSceneRasterWidth(), product.getSceneRasterHeight(),
                                                _leaderFile.getLonCorners(),
                                                TiePointGrid.DISCONT_AT_360);
        TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        product.addTiePointGrid(latGrid);
        product.addTiePointGrid(lonGrid);
        product.setGeoCoding(tpGeoCoding);  */
    }

    public void close() throws IOException {
        Set keys = bandImageFileMap.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            ImageIOFile img = bandImageFileMap.get(key);
            img.close();
        }
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

    private void addAbstractedMetadataHeader(MetadataElement root) {

    /*    AbstractMetadata.addAbstractedMetadataHeader(root);

        MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);

        //mph
        AbstractMetadata.setAttributeString(absRoot, AbstractMetadata.PRODUCT, getProductName());
        AbstractMetadata.setAttributeString(absRoot, AbstractMetadata.PRODUCT_TYPE, getProductType());
        AbstractMetadata.setAttributeString(absRoot, AbstractMetadata.SPH_DESCRIPTOR,
                _leaderFile.getSceneRecord().getAttributeString("Product type descriptor"));
        AbstractMetadata.setAttributeString(absRoot, AbstractMetadata.MISSION, getMission());

        String procTime = _volumeDirectoryFile.getTextRecord().getAttributeString("Location and datetime of product creation").trim();
        AbstractMetadata.setAttributeString(absRoot, AbstractMetadata.PROC_TIME, procTime );
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.REL_ORBIT,
                Integer.parseInt(_leaderFile.getSceneRecord().getAttributeString("Orbit number").trim()));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT,
                Integer.parseInt(_leaderFile.getSceneRecord().getAttributeString("Orbit number").trim()));
        AbstractMetadata.setAttributeString(absRoot, AbstractMetadata.STATE_VECTOR_TIME,
                _leaderFile.getFacilityRecord().getAttributeString("Time of input state vector used to processed the image"));


        //sph

        AbstractMetadata.setAttributeString(absRoot, "SAMPLE_TYPE", getSampleType());    */
    }

    private String getProductName() {
        return "TerraSarX";//_volumeDirectoryFile.getProductName();
    }

    private String getProductDescription() {
        return "TerraSarX";//ERSConstants.PRODUCT_DESCRIPTION_PREFIX + _leaderFile.getProductLevel();
    }

}