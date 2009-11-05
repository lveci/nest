package org.esa.nest.dataio;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.Guardian;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.XMLSupport;
import org.jdom.Attribute;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private org.jdom.Document xmlDoc = null;

    private int _sceneWidth = 0;
    private int _sceneHeight = 0;

    protected transient final Map<String, ImageIOFile> bandImageFileMap = new HashMap<String, ImageIOFile>(1);
    protected transient final Map<Band, ImageIOFile.BandInfo> bandMap = new HashMap<Band, ImageIOFile.BandInfo>(3);

    public XMLProductDirectory(final File headerFile, final File imageFolder) {
        Guardian.assertNotNull("headerFile", headerFile);

        _xmlHeader = headerFile;
        _baseDir = headerFile.getParentFile();
        imgFolder = imageFolder;
    }

    public void readProductDirectory() throws IOException {

        xmlDoc = XMLSupport.LoadXML(_xmlHeader.getAbsolutePath());

        final File[] fileList = imgFolder.listFiles();
        for (File file : fileList) {
            addImageFile(file);
        }   
    }

    protected void addImageFile(final File file) throws IOException {
        if (file.getName().toUpperCase().endsWith("TIF") && !file.getName().toLowerCase().contains("browse")) {
            final ImageIOFile img = new ImageIOFile(file, ImageIOFile.getTiffIIOReader(file));
            bandImageFileMap.put(img.getName(), img);

           setSceneWidthHeight(img.getSceneWidth(), img.getSceneHeight());
        }
    }

    protected void setSceneWidthHeight(final int width, final int height) {
        _sceneWidth = width;
        _sceneHeight = height;
    }

    public Product createProduct() throws IOException {
        final Product product = new Product(getProductName(),
                                            getProductType(),
                                            _sceneWidth, _sceneHeight);

        addMetaData(product);
        addGeoCoding(product);
        addTiePointGrids(product);

        addBands(product);

        product.setName(getProductName());
        product.setProductType(getProductType());
        product.setDescription(getProductDescription());

        return product;
    }

    public ImageIOFile.BandInfo getBandInfo(Band destBand) {
        return bandMap.get(destBand);
    }

    public void close() throws IOException {
        final Set keys = bandImageFileMap.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            final ImageIOFile img = bandImageFileMap.get(key);
            img.close();
        }
    }

    protected void addBands(final Product product) {
        int bandCnt = 1;
        final Set keys = bandImageFileMap.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            final ImageIOFile img = bandImageFileMap.get(key);

            for(int i=0; i < img.getNumImages(); ++i) {

                for(int b=0; b < img.getNumBands(); ++b) {
                    final Band band = new Band(img.getName()+bandCnt++, img.getDataType(),
                                       img.getSceneWidth(), img.getSceneHeight());
                    band.setUnit(Unit.AMPLITUDE);
                    product.addBand(band);
                    bandMap.put(band, new ImageIOFile.BandInfo(img, i, b));
                }
            }
        }
    }

    protected void addGeoCoding(final Product product) {

    }

    protected static void addGeoCoding(final Product product, final float[] latCorners, final float[] lonCorners) {

        if(latCorners == null || lonCorners == null) return;

        int gridWidth = 10;
        int gridHeight = 10;

        final float[] fineLatTiePoints = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, latCorners, fineLatTiePoints);

        float subSamplingX = (float)product.getSceneRasterWidth() / (gridWidth - 1);
        float subSamplingY = (float)product.getSceneRasterHeight() / (gridHeight - 1);

        final TiePointGrid latGrid = new TiePointGrid("latitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLatTiePoints);
        latGrid.setUnit(Unit.DEGREES);

        final float[] fineLonTiePoints = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, lonCorners, fineLonTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid("longitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLonTiePoints, TiePointGrid.DISCONT_AT_180);
        lonGrid.setUnit(Unit.DEGREES);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        product.addTiePointGrid(latGrid);
        product.addTiePointGrid(lonGrid);
        product.setGeoCoding(tpGeoCoding);
    }

    protected void addTiePointGrids(final Product product) {

    }

    private void addMetaData(final Product product) throws IOException {
        final MetadataElement root = product.getMetadataRoot();
        final Element rootElement = xmlDoc.getRootElement();
        AddXMLMetadata(rootElement, root);

        addAbstractedMetadataHeader(product, root);
    }

    protected Element getXMLRootElement() {
        return xmlDoc.getRootElement();
    }

    private static void AddXMLMetadata(Element xmlRoot, MetadataElement metadataRoot) {

        if(xmlRoot.getChildren().isEmpty() && xmlRoot.getAttributes().isEmpty()) {
            if(!xmlRoot.getValue().isEmpty()) {
                addAttribute(metadataRoot, xmlRoot.getName(), xmlRoot.getValue());
            }
        } else if(xmlRoot.getChildren().isEmpty()) {
            final MetadataElement metaElem = new MetadataElement(xmlRoot.getName());

            addAttribute(metaElem, xmlRoot.getName(), xmlRoot.getValue());

            final List<Attribute> xmlAttribs = xmlRoot.getAttributes();
            for (Attribute aChild : xmlAttribs) {
                addAttribute(metaElem, aChild.getName(), aChild.getValue());
            }

            metadataRoot.addElement(metaElem);
        } else {
            final MetadataElement metaElem = new MetadataElement(xmlRoot.getName());
            xmlRoot.getAttributes();

            final List children = xmlRoot.getContent();
            for (Object aChild : children) {
                if (aChild instanceof Element) {
                    AddXMLMetadata((Element) aChild, metaElem);
                } else if(aChild instanceof Attribute) {
                    final Attribute childAtrrib = (Attribute) aChild;
                    addAttribute(metaElem, childAtrrib.getName(), childAtrrib.getValue());
                }
            }

            final List<Attribute> xmlAttribs = xmlRoot.getAttributes();
            for (Attribute aChild : xmlAttribs) {
                addAttribute(metaElem, aChild.getName(), aChild.getValue());
            }

            metadataRoot.addElement(metaElem);
        }
    }

    private static void addAttribute(MetadataElement meta, String name, String value) {
        final MetadataAttribute attribute = new MetadataAttribute(name, ProductData.TYPE_ASCII, 1);
        attribute.getData().setElems(value);
        meta.addAttribute(attribute);
    }

    protected static int getTotalSize(Product product) {
        return (int)(product.getRawStorageSize() / (1024.0f * 1024.0f));
    }

    protected void addAbstractedMetadataHeader(Product product, MetadataElement root) {

        AbstractMetadata.addAbstractedMetadataHeader(root);
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