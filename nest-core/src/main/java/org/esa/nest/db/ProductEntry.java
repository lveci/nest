package org.esa.nest.db;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;

/**

 */
public class ProductEntry {

    private int id;
    private String path;
    private String mission;
    private String productType;

    // corner locations

    public ProductEntry(final int id, final String path) {
        this.id = id;
        this.path = path;
    }

    public ProductEntry(final int id, final String path, final String mission, final String productType) {
        this.id = id;
        this.path = path;
        this.mission = mission;
        this.productType = productType;
    }

    public ProductEntry(final Product product) {
        final File file = product.getFileLocation();
        if(file != null)
            this.path = product.getFileLocation().getAbsolutePath();

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
        if(absRoot != null) {
            this.mission = absRoot.getAttributeString(AbstractMetadata.MISSION);
            this.productType = absRoot.getAttributeString(AbstractMetadata.PRODUCT_TYPE);
        }
        this.id = -1;
    }

    public void setPath(String lastName) {
        this.path = lastName;
    }

    public String getPath() {
        return path;
    }

    public String getMission() {
        return mission;
    }

    public String getProductType() {
        return productType;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public boolean equals(Object other) {
        boolean bEqual = false;
        if (this == other) {
            bEqual = true;
        } else if (other instanceof ProductEntry) {
            ProductEntry entry = (ProductEntry) other;
            if ((path == null ? entry.path == null : path.equalsIgnoreCase(entry.path))) {
                // don't use id in determining equality
                bEqual = true;
            }
        }

        return bEqual;
    }
}
