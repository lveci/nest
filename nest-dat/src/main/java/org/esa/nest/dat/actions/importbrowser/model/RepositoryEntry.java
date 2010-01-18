package org.esa.nest.dat.actions.importbrowser.model;

import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.io.FileUtils;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA. User: marco Date: 01.07.2005 Time: 08:26:16
 */
public class RepositoryEntry {

    private final File _productFile;
    private Repository _repository;
    private final ArrayList _dataList;
    private Product _product;
    private boolean _selected;
    private boolean dataProviderMustCreateData = false;

    public RepositoryEntry(final String productFilePath) {
        _productFile = new File(productFilePath);
        _dataList = new ArrayList();
    }

    public void setDataProviderMustCreateData(final boolean flag) {
        dataProviderMustCreateData = flag;
    }

    public boolean getDataProviderMustCreateData() {
        return dataProviderMustCreateData;
    }

    /**
     * Set the <code>Repository</code> to which this <code>RepositoryEntry</code> contains.
     *
     * @param repository the repository.
     */
    public void setOwner(final Repository repository) {
        this._repository = repository;
    }

    /**
     * Gets the owning <code>Repository</code> of this <code>RepositoryEntry</code>
     *
     * @return the repository.
     */
    public Repository getOwner() {
        return _repository;
    }

    public File getProductFile() {
        return _productFile;
    }

    public void openProduct() {
        try {
            // temporary - don't open wss products
            final String name = _productFile.getName().toUpperCase();
            if(name.startsWith("ASA_WSS_") || name.startsWith("ASA_WV"))
                _product = null;
            else
                _product = ProductIO.readProduct(_productFile, null);
        } catch(Exception e) {
            System.out.println("Open product failed: "+e.getMessage());
            _product = null;
        }
    }

    public void closeProduct() {
        if (_product != null) {
            _product.dispose();
            _product = null;
        }
    }

    public Product getProduct() {
        return _product;
    }

    public void setData(final int index, final Object data) {
        if (index >= _dataList.size()) {
            _dataList.add(data);
        } else {
            _dataList.set(index, data);
        }
    }

    public Object getData(final int index) {
        if (index >= 0 && index < _dataList.size()) {
            return _dataList.get(index);
        } else {
            return null;
        }
    }

    public boolean equals(final Object obj) {
        if (!(obj instanceof RepositoryEntry)) {
            return false;
        }
        final RepositoryEntry entry = (RepositoryEntry) obj;
        return this._productFile.equals(entry._productFile);
    }

    /**
     * Returns the size of the Product in megabytes.
     *
     * @return the size in megabytes.
     */
    public float getProductSize() {
        final File productFile = getProductFile();
        final String extension = FileUtils.getExtension(productFile);
        long dirSize = 0;
        if (productFile.exists() &&
            productFile.getParentFile() != null &&
            DimapProductConstants.DIMAP_HEADER_FILE_EXTENSION.equals(extension)) {
            final File realtedDataDir = new File(productFile.getParentFile(),
                                                 FileUtils.getFilenameWithoutExtension(productFile) + DimapProductConstants.DIMAP_DATA_DIRECTORY_EXTENSION);
            if (realtedDataDir.exists()) {
                final File[] files = realtedDataDir.listFiles();
                for (File file : files) {
                    dirSize += file.length();
                }
            }
        }
        return (productFile.length() + dirSize) / (1024.0f * 1024.0f);
    }

    public void setSelected(boolean flag) {
        _selected = flag;
    }

    public boolean isSelected() {
        return _selected;
    }

}
