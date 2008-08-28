package org.esa.nest.dataio.terrasarx;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.Guardian;
import org.esa.nest.dataio.AbstractMetadata;
import org.esa.nest.util.XMLSupport;
import org.jdom.Element;
import org.jdom.Attribute;

import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * This class represents a product directory.
 * <p/>
 * <p>This class is public for the benefit of the implementation of another (internal) class and its API may
 * change in future releases of the software.</p>
 *
 */
class XMLProductDirectory {

    private final File _xmlHeader;
    private final File _baseDir;
    private org.jdom.Document xmlDoc;

    private int _sceneWidth;
    private int _sceneHeight;
    private String productType = "Type";

    //private transient Map<String, ERSImageFile> bandImageFileMap = new HashMap<String, ERSImageFile>(1);

    public XMLProductDirectory(final File file) {
        Guardian.assertNotNull("file", file);

        _xmlHeader = file;
        _baseDir = file.getParentFile();
    }

    protected void readProductDirectory() throws IOException {

        xmlDoc = XMLSupport.LoadXML(_xmlHeader.getAbsolutePath());

   /*     final String[] imageFileNames = CEOSImageFile.getImageFileNames(_baseDir, "DAT_");
        int numImageFiles = imageFileNames.length;
        _imageFiles = new ERSImageFile[numImageFiles];
        for (int i = 0; i < numImageFiles; i++) {
            _imageFiles[i] = new ERSImageFile(createInputStream(imageFileNames[i]));
        }

        _sceneWidth = _imageFiles[0].getRasterWidth();
        _sceneHeight = _imageFiles[0].getRasterHeight();
        assertSameWidthAndHeightForAllImages();     */
    }

    public Product createProduct() throws IOException {
        final Product product = new Product(getProductName(),
                                            productType,
                                            _sceneWidth, _sceneHeight);

     /*   if(_imageFiles.length > 1) {
            int index = 1;
            for (final ERSImageFile imageFile : _imageFiles) {

                if(isProductSLC) {
                    String bandName = "i_" + index;
                    Band bandI = createBand(bandName);
                    product.addBand(bandI);
                    bandImageFileMap.put(bandName, imageFile);
                    bandName = "q_" + index;
                    Band bandQ = createBand(bandName);
                    product.addBand(bandQ);
                    bandImageFileMap.put(bandName, imageFile);

                    CEOSProductDirectory.createVirtualIntensityBand(product, bandI, bandQ, "_"+index);
                    ++index;
                } else {
                    String bandName = "amplitude_" + index;
                    Band band = createBand(bandName);
                    product.addBand(band);
                    bandImageFileMap.put(bandName, imageFile);
                    CEOSProductDirectory.createVirtualIntensityBand(product, band, "_"+index);
                    ++index;
                }
            }
        } else {
            ERSImageFile imageFile = _imageFiles[0];
            if(isProductSLC) {
                Band bandI = createBand("i");
                product.addBand(bandI);
                bandImageFileMap.put("i", imageFile);
                Band bandQ = createBand("q");
                product.addBand(bandQ);
                bandImageFileMap.put("q", imageFile);
                CEOSProductDirectory.createVirtualIntensityBand(product, bandI, bandQ, "");
            } else {
                Band band = createBand("amplitude");
                product.addBand(band);
                bandImageFileMap.put("amplitude", imageFile);
                CEOSProductDirectory.createVirtualIntensityBand(product, band, "");
            }
        }                     */

        //product.setStartTime(getUTCScanStartTime());
        //product.setEndTime(getUTCScanStopTime());
        product.setDescription(getProductDescription());

        addGeoCoding(product);
        addMetaData(product);

        return product;
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

    }

    private Band createBand(String name) {
        final Band band = new Band(name, ProductData.TYPE_INT16,
                                   _sceneWidth, _sceneHeight);

        //band.setUnit(ERSImageFile.getGeophysicalUnit());

      /*
        final int bandIndex = index;
        final double scalingFactor = _leaderFile.getAbsoluteCalibrationGain(bandIndex);
        final double scalingOffset = _leaderFile.getAbsoluteCalibrationOffset(bandIndex);
        band.setScalingFactor(scalingFactor);
        band.setScalingOffset(scalingOffset);
        band.setNoDataValueUsed(false);
        final int[] histogramBins = _trailerFile.getHistogramBinsForBand(bandIndex);
        final float scaledMinSample = (float) (getMinSampleValue(histogramBins) * scalingFactor + scalingOffset);
        final float scaledMaxSample = (float) (getMaxSampleValue(histogramBins) * scalingFactor + scalingOffset);
        final ImageInfo imageInfo = new ImageInfo(scaledMinSample, scaledMaxSample, histogramBins);
        band.setImageInfo(imageInfo);
        band.setDescription("Radiance band " + ImageFile.getBandIndex());
        */
        return band;
    }

    private void addMetaData(final Product product) throws IOException {
        final MetadataElement root = product.getMetadataRoot();
        root.addElement(new MetadataElement(Product.ABSTRACTED_METADATA_ROOT_NAME));

        Element rootElement = xmlDoc.getRootElement();
        AddXMLMetadata(rootElement, root);

        addAbstractedMetadataHeader(root);
    }

    private static void AddXMLMetadata(Element xmlRoot, MetadataElement metadataRoot) {

        MetadataElement metaElem = new MetadataElement(xmlRoot.getName());
        xmlRoot.getAttributes();

        List children = xmlRoot.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                Element child = (Element) aChild;
                AddXMLMetadata(child, metaElem);
            } else if(aChild instanceof Attribute) {
                Attribute childAtrrib = (Attribute) aChild;
                metaElem.setAttributeString(childAtrrib.getName(), childAtrrib.getValue());
            }
        }

        List<Attribute> xmlAttribs = xmlRoot.getAttributes();
        for (Attribute aChild : xmlAttribs) {
            metaElem.setAttributeString(aChild.getName(), aChild.getValue());
        }

        metadataRoot.addElement(metaElem);
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

    private ImageInputStream createInputStream(final String fileName) throws IOException {
        return new FileImageInputStream(new File(_baseDir, fileName));
    }

}