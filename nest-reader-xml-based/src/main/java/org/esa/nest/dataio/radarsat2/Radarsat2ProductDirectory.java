package org.esa.nest.dataio.radarsat2;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.dataio.XMLProductDirectory;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.io.IOException;

/**
 * This class represents a product directory.
 *
 */
public class Radarsat2ProductDirectory extends XMLProductDirectory {

    private String productName = "Radarsat2";
    private String productType = "Radarsat2";
    private String productDescription = "";

    public Radarsat2ProductDirectory(final File headerFile, final File imageFolder) {
        super(headerFile, imageFolder);
    }

    @Override
    protected void addGeoCoding(final Product product) {

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
    protected void addAbstractedMetadataHeader(Product product, MetadataElement root) {

        AbstractMetadata.addAbstractedMetadataHeader(root);

        final MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);

        //mph
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, getProductName());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, getProductType());
       /* AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SPH_DESCRIPTOR,
                _leaderFile.getSceneRecord().getAttributeString("Product type descriptor"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, getMission());

        String procTime = _volumeDirectoryFile.getTextRecord().getAttributeString("Location and datetime of product creation").trim();
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME, procTime );
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.REL_ORBIT,
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