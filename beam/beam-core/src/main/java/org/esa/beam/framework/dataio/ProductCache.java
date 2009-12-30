package org.esa.beam.framework.dataio;

import org.esa.beam.framework.datamodel.Product;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * a cache of recently used Products
 */
public final class ProductCache {
    private final Map<File, Product> productMap = new HashMap<File, Product>(10);
    private final Map<File, Long> timeStampMap = new HashMap<File, Long>(10);
    private final ArrayList<File> fileList = new ArrayList<File>(10);
    private static final ProductCache theInstance = new ProductCache();

    private ProductCache() {
    }

    public static ProductCache instance() {
        return theInstance;
    }

    public void addProduct(final File file, final Product p) {
        productMap.put(file, p);
        //timeStampMap.put(file, file.lastModified());

        fileList.remove(file);
        fileList.add(0, file);
        if (fileList.size() > 10) {
            final int index = fileList.size() - 1;
            final File lastFile = fileList.get(index);
            productMap.remove(lastFile);
            fileList.remove(index);
        }
    }

    public Product getProduct(final File file) {
        final Product prod = productMap.get(file);
        if(prod != null && file.lastModified() == prod.getFileLocation().lastModified())
            return prod;
        return null;
    }

    public void removeProduct(final File file) {
        productMap.remove(file);
        fileList.remove(file);
    }

}
