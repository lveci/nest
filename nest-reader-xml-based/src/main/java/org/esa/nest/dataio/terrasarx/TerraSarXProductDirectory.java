package org.esa.nest.dataio.terrasarx;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.ProductData;
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

        final MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);
        final MetadataElement level1Elem = root.getElementAt(1);
        final MetadataElement generalHeader = level1Elem.getElement("generalHeader");
        final MetadataElement productInfo = level1Elem.getElement("productInfo");
        final MetadataElement missionInfo = productInfo.getElement("missionInfo");
        final MetadataElement productVariantInfo = productInfo.getElement("productVariantInfo");
        final MetadataElement imageDataInfo = productInfo.getElement("imageDataInfo");

        MetadataAttribute attrib = generalHeader.getAttribute("fileName");
        if(attrib != null)
            productName = attrib.getData().getElemString();

        //mph
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, productName);
        productType = productVariantInfo.getAttributeString("productType", " ");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, productType);
        //AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SPH_DESCRIPTOR,
        //        _leaderFile.getSceneRecord().getAttributeString("Product type descriptor"));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, generalHeader.getAttributeString("mission", " "));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME, getTime(generalHeader, "generationTime"));

        MetadataElement elem = generalHeader.getElement("generationSystem");
        if(elem != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ProcessingSystemIdentifier,
                elem.getAttributeString("generationSystem", " "));
        }

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.CYCLE, missionInfo.getAttributeInt("orbitCycle", 0));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.REL_ORBIT, missionInfo.getAttributeInt("relOrbit", 0));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT, missionInfo.getAttributeInt("absOrbit", 0));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, missionInfo.getAttributeString("orbitDirection", " "));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SAMPLE_TYPE, imageDataInfo.getAttributeString("imageDataType", " "));

        int srgr = 0;
        if(productVariantInfo.getAttributeString("projection", " ").equalsIgnoreCase("GROUNDRANGE"))
            srgr = 1;
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, srgr);

    }

    private ProductData.UTC getTime(MetadataElement elem, String tag) {
        final String timeStr = elem.getAttributeString(tag, " ").replace("T", " ");
        return AbstractMetadata.parseUTC(timeStr, "yyyy-mm-dd HH:mm:ss");
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