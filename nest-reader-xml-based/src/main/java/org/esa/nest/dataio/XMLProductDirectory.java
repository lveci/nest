package org.esa.nest.dataio;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.util.XMLSupport;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
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
    private org.jdom.Document xmlDoc = null;

    private int _sceneWidth = 0;
    private int _sceneHeight = 0;
    private boolean cosarFormat = false;

    protected transient Map<String, ImageIOFile> bandImageFileMap = new HashMap<String, ImageIOFile>(1);
    protected transient Map<Band, ImageIOFile.BandInfo> bandMap = new HashMap<Band, ImageIOFile.BandInfo>(3);

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
                final ImageIOFile img = new ImageIOFile(file);
                bandImageFileMap.put(img.getName(), img);

                _sceneWidth = img.getSceneWidth();
                _sceneHeight = img.getSceneHeight();
            } else if (file.getName().toUpperCase().endsWith("COS")) {
                cosarFormat = true;

                throw new IOException("Cosar format is not yet supported");
            }
        }   
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
        Set keys = bandImageFileMap.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            ImageIOFile img = bandImageFileMap.get(key);
            img.close();
        }
    }

    protected void addBands(Product product) {
        int bandCnt = 1;
        Set keys = bandImageFileMap.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            ImageIOFile img = bandImageFileMap.get(key);

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

    protected void addGeoCoding(final Product product, final float[] latCorners, final float[] lonCorners) {

        if(latCorners == null || lonCorners == null) return;

        int gridWidth = 10;
        int gridHeight = 10;

        final float[] fineLatTiePoints = new float[gridWidth*gridHeight];
        createFineTiePointGrid(2, 2, gridWidth, gridHeight, latCorners, fineLatTiePoints);

        float subSamplingX = (float)product.getSceneRasterWidth() / (gridWidth - 1);
        float subSamplingY = (float)product.getSceneRasterHeight() / (gridHeight - 1);

        final TiePointGrid latGrid = new TiePointGrid("lat", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLatTiePoints);

        final float[] fineLonTiePoints = new float[gridWidth*gridHeight];
        createFineTiePointGrid(2, 2, gridWidth, gridHeight, lonCorners, fineLonTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid("lon", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLonTiePoints, TiePointGrid.DISCONT_AT_180);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        product.addTiePointGrid(latGrid);
        product.addTiePointGrid(lonGrid);
        product.setGeoCoding(tpGeoCoding);
    }

    protected void addTiePointGrids(final Product product) {

    }

    protected static void createFineTiePointGrid(int coarseGridWidth,
                                          int coarseGridHeight,
                                          int fineGridWidth,
                                          int fineGridHeight,
                                          float[] coarseTiePoints,
                                          float[] fineTiePoints) {

        if (coarseTiePoints == null || coarseTiePoints.length != coarseGridWidth*coarseGridHeight) {
            throw new IllegalArgumentException(
                    "coarse tie point array size does not match 'coarseGridWidth' x 'coarseGridHeight'");
        }

        if (fineTiePoints == null || fineTiePoints.length != fineGridWidth*fineGridHeight) {
            throw new IllegalArgumentException(
                    "fine tie point array size does not match 'fineGridWidth' x 'fineGridHeight'");
        }

        int k = 0;
        for (int r = 0; r < fineGridHeight; r++) {

            final float lambdaR = (float)(r) / (float)(fineGridHeight - 1);
            final float betaR = lambdaR*(coarseGridHeight - 1);
            final int j0 = (int)(betaR);
            final int j1 = Math.min(j0 + 1, coarseGridHeight - 1);
            final float wj = betaR - j0;

            for (int c = 0; c < fineGridWidth; c++) {

                final float lambdaC = (float)(c) / (float)(fineGridWidth - 1);
                final float betaC = lambdaC*(coarseGridWidth - 1);
                final int i0 = (int)(betaC);
                final int i1 = Math.min(i0 + 1, coarseGridWidth - 1);
                final float wi = betaC - i0;

                fineTiePoints[k++] = MathUtils.interpolate2D(wi, wj,
                                                           coarseTiePoints[i0 + j0 * coarseGridWidth],
                                                           coarseTiePoints[i1 + j0 * coarseGridWidth],
                                                           coarseTiePoints[i0 + j1 * coarseGridWidth],
                                                           coarseTiePoints[i1 + j1 * coarseGridWidth]);
            }
        }
    }

    private void addMetaData(final Product product) throws IOException {
        final MetadataElement root = product.getMetadataRoot();
        root.addElement(new MetadataElement(Product.ABSTRACTED_METADATA_ROOT_NAME));

        final Element rootElement = xmlDoc.getRootElement();
        AddXMLMetadata(rootElement, root);

        addAbstractedMetadataHeader(product, root);
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
        meta.addAttributeFast(attribute);
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