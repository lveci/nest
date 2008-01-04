package org.esa.nest.dataio.ceos.ers;

import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.beam.framework.datamodel.MetadataElement;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;

/**
 * * This class represents a supplemental file of an Avnir-2 product.
 *
 * @author Marco Peters
 */
class ERSSupplementalFile {

    private static final String META_ELEMENT_NAME = "Supplemental";

    private CeosFileReader _ceosReader;

    public ERSSupplementalFile(final ImageInputStream supplementalStream) {
        _ceosReader = new CeosFileReader(supplementalStream);
    }

    public MetadataElement getAsMetadata() {
        final MetadataElement root = new MetadataElement(META_ELEMENT_NAME);
        addPCDData(root);
        return root;
    }

    private void addPCDData(final MetadataElement root) {
    }

    public void close() throws IOException {
        _ceosReader.close();
        _ceosReader = null;
    }
}
