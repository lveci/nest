package org.esa.nest.dataio.envi;

import org.esa.beam.dataio.envi.EnviProductReader;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.io.IOException;

public class NestEnviProductReader extends EnviProductReader {

    NestEnviProductReader(ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
    }

    @Override
    protected void initMetadata(final Product product, final File inputFile) throws IOException {

        final MetadataElement root = product.getMetadataRoot();
        AbstractMetadata.addAbstractedMetadataHeader(root);

        final MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);
        AbstractMetadata.loadExternalMetadata(product, absRoot, inputFile);
    }
}