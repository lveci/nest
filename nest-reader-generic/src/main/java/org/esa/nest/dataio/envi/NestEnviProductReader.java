package org.esa.nest.dataio.envi;

import org.esa.beam.dataio.envi.EnviProductReader;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;

public class NestEnviProductReader extends EnviProductReader {

    NestEnviProductReader(ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
    }

    @Override
    protected void initMetadata(final Product product, final File inputFile) {

        final MetadataElement root = product.getMetadataRoot();
        root.addElement(new MetadataElement(Product.ABSTRACTED_METADATA_ROOT_NAME));

        AbstractMetadata.addAbstractedMetadataHeader(root);

        final MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);

        loadExternalMetadata(absRoot, inputFile);
    }

    private static void loadExternalMetadata(final MetadataElement absRoot, final File inputFile) {
         // load metadata xml file if found
        final String inputStr = inputFile.getAbsolutePath();
        final String metadataStr = inputStr.substring(0, inputStr.lastIndexOf('.')) + ".xml";
        final File metadataFile = new File(metadataStr);
        if(metadataFile.exists())
            AbstractMetadata.Load(absRoot, metadataFile);
    }
}