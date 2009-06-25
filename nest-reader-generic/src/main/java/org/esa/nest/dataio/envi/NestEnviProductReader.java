package org.esa.nest.dataio.envi;

import org.esa.beam.dataio.envi.EnviProductReader;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.io.IOException;

public class NestEnviProductReader extends EnviProductReader {

    public NestEnviProductReader(ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
    }

    @Override
    protected void initMetadata(final Product product, final File inputFile) throws IOException {

        final MetadataElement root = product.getMetadataRoot();
        final MetadataElement absRoot = AbstractMetadata.addAbstractedMetadataHeader(root);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, product.getName());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, product.getProductType());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line, product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines, product.getSceneRasterHeight());
        
        if(!AbstractMetadata.loadExternalMetadata(product, absRoot, inputFile))
            AbstractMetadata.loadExternalMetadata(product, absRoot, new File(inputFile.getParentFile(), "PolSARPro_NEST_metadata.xml"));

        // set name from metadata if found
        product.setName(absRoot.getAttributeString(AbstractMetadata.PRODUCT, product.getName()));
    }
}