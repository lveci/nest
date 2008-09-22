package org.esa.nest.dataio.terrasarx;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.nest.dataio.XMLProductDirectory;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.io.IOException;

/**
 * This class represents a product directory.
 *
 */
public class TerraSarXProductDirectory extends XMLProductDirectory {

    private String productName = "TerraSar-X";
    private String productType = "TerraSar-X";
    private String productDescription = "";

    public TerraSarXProductDirectory(final File headerFile, final File imageFolder) {
        super(headerFile, imageFolder);
    }

    @Override
    protected void addGeoCoding(final Product product) throws IOException {

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

    @Override
    protected void addAbstractedMetadataHeader(MetadataElement root) {

        AbstractMetadata.addAbstractedMetadataHeader(root);

        MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);
        MetadataElement level1Elem = root.getElementAt(1);
        MetadataElement generalHeaderElem = level1Elem.getElement("generalHeader");
        MetadataElement productInfoElem = level1Elem.getElement("productInfo");

        MetadataAttribute attrib = generalHeaderElem.getAttribute("fileName");
        if(attrib != null)
            productName = attrib.getData().getElemString();

        //mph
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, productName);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, getProductType());
        //AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SPH_DESCRIPTOR,
        //        _leaderFile.getSceneRecord().getAttributeString("Product type descriptor"));
        attrib = generalHeaderElem.getAttribute("mission");
        if(attrib != null)
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, attrib.getData().getElemString());

        attrib = generalHeaderElem.getAttribute("generationTime");
        if(attrib != null)
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME, attrib.getData().getElemString());

       /* AbstractMetadata.setAttribute(absRoot, AbstractMetadata.REL_ORBIT,
                Integer.parseInt(_leaderFile.getSceneRecord().getAttributeString("Orbit number").trim()));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT,
                Integer.parseInt(_leaderFile.getSceneRecord().getAttributeString("Orbit number").trim()));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.STATE_VECTOR_TIME,
                _leaderFile.getFacilityRecord().getAttributeString("Time of input state vector used to processed the image"));


        //sph

        AbstractMetadata.setAttribute(absRoot, "SAMPLE_TYPE", getSampleType());    */
    }

    @Override
    protected String getProductName() {
        return productName;
    }

    @Override
    protected String getProductDescription() {
        return productDescription;
    }

    @Override
    protected String getProductType() {
        return productType;
    }
}