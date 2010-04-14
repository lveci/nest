package org.esa.nest.db;

import org.esa.beam.framework.datamodel.*;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;

/**

 */
public class ProductEntry {

    private int id;
    private File file;
    private String name;
    private String mission;
    private String productType;
    private ProductData.UTC acquisition;
    private String pass;
    private double range_spacing;
    private double azimuth_spacing;
    
    private MetadataElement absRoot;
    private boolean isSelected = false;

    // corner locations
    private final GeoPos firstNear = new GeoPos();
    private final GeoPos firstFar = new GeoPos();
    private final GeoPos lastNear = new GeoPos();
    private final GeoPos lastFar = new GeoPos();

    public ProductEntry(final int id, final File file) {
        this.id = id;
        this.file = file;
    }

    public ProductEntry(final Product product) {
        final File file = product.getFileLocation();
        if(file != null)
            this.file = product.getFileLocation();

        this.name = product.getName();
        this.absRoot = AbstractMetadata.getAbstractedMetadata(product);
        if(absRoot != null) {
            this.mission = absRoot.getAttributeString(AbstractMetadata.MISSION);
            this.productType = absRoot.getAttributeString(AbstractMetadata.PRODUCT_TYPE);
            this.pass = absRoot.getAttributeString(AbstractMetadata.PASS);
            this.range_spacing = absRoot.getAttributeDouble(AbstractMetadata.range_spacing);
            this.azimuth_spacing = absRoot.getAttributeDouble(AbstractMetadata.azimuth_spacing);
        }
        getCornerPoints(product);

        this.id = -1;
    }

    public ProductEntry(final ResultSet results) throws SQLException {
        this.id = results.getInt(1);
        this.file = new File(results.getString(AbstractMetadata.PATH));
        this.name = results.getString(AbstractMetadata.PRODUCT);
        this.mission = results.getString(AbstractMetadata.MISSION);
        this.productType = results.getString(AbstractMetadata.PRODUCT_TYPE);
        this.pass = results.getString(AbstractMetadata.PASS);
        this.range_spacing = results.getDouble(AbstractMetadata.range_spacing);
        this.azimuth_spacing =results.getDouble(AbstractMetadata.azimuth_spacing);

        this.firstNear.setLocation((float)results.getDouble(AbstractMetadata.first_near_lat),
                                   (float)results.getDouble(AbstractMetadata.first_near_long));
        this.firstFar.setLocation((float)results.getDouble(AbstractMetadata.first_far_lat),
                                   (float)results.getDouble(AbstractMetadata.first_far_long));
        this.lastNear.setLocation((float)results.getDouble(AbstractMetadata.last_near_lat),
                                   (float)results.getDouble(AbstractMetadata.last_near_long));
        this.lastFar.setLocation((float)results.getDouble(AbstractMetadata.last_far_lat),
                                   (float)results.getDouble(AbstractMetadata.last_far_long));
    }

    public void dispose() {
        if(absRoot != null)
            absRoot.dispose();
    }

    public static void dispose(final ProductEntry[] productEntryList) {
        for(ProductEntry e : productEntryList) {
            e.dispose();
        }
    }

    private void getCornerPoints(final Product product) {
        final GeoCoding geoCoding = product.getGeoCoding();
        if(geoCoding == null) return;
        if(!geoCoding.canGetGeoPos()) return;

        final int w = product.getSceneRasterWidth();
        final int h = product.getSceneRasterHeight();

        geoCoding.getGeoPos(new PixelPos(0,0), firstNear);
        geoCoding.getGeoPos(new PixelPos(w,0), firstFar);
        geoCoding.getGeoPos(new PixelPos(0,h), lastNear);
        geoCoding.getGeoPos(new PixelPos(w,h), lastFar);
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public String getMission() {
        return mission;
    }

    public String getProductType() {
        return productType;
    }

    public String getPass() {
        return pass;
    }

    public double getRangeSpacing() {
        return range_spacing;
    }

    public double getAzimuthSpacing() {
        return azimuth_spacing;
    }

    public GeoPos getFirstNearGeoPos() {
        return firstNear;
    }
    public GeoPos getFirstFarGeoPos() {
        return firstFar;
    }
    public GeoPos getLastNearGeoPos() {
        return lastNear;
    }
    public GeoPos getLastFarGeoPos() {
        return lastFar;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public MetadataElement getMetadata() {
        return absRoot;
    }

    public void setSelected(boolean flag) {
        isSelected = flag;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public boolean equals(Object other) {
        boolean bEqual = false;
        if (this == other) {
            bEqual = true;
        } else if (other instanceof ProductEntry) {
            ProductEntry entry = (ProductEntry) other;
            if ((file == null ? entry.file == null : file.equals(entry.file))) {
                // don't use id in determining equality
                bEqual = true;
            }
        }
        return bEqual;
    }
}
