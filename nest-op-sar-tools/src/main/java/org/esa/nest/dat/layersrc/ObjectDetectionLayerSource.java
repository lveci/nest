package org.esa.nest.dat.layersrc;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.visat.toolviews.layermanager.LayerSource;
import org.esa.beam.visat.toolviews.layermanager.layersrc.AbstractLayerSourceAssistantPage;
import org.esa.beam.visat.toolviews.layermanager.layersrc.LayerSourcePageContext;
import org.esa.nest.datamodel.AbstractMetadata;

/**
 * A source for {@link org.esa.nest.dat.layersrc.ObjectDetectionLayer}s.
 *
 */
public class ObjectDetectionLayerSource implements LayerSource {

    @Override
    public boolean isApplicable(LayerSourcePageContext pageContext) {
        final Product product = pageContext.getAppContext().getSelectedProduct();

        final MetadataElement root = product.getMetadataRoot();
        if (root != null) {
            final MetadataElement absMetadata = root.getElement(AbstractMetadata.ABSTRACT_METADATA_ROOT);
            if (absMetadata != null) {
                final String shipFilePath = absMetadata.getAttributeString("ship_detection_file", null);
                if(shipFilePath != null)
                    return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasFirstPage() {
        return false;
    }

    @Override
    public AbstractLayerSourceAssistantPage getFirstPage(LayerSourcePageContext pageContext) {
        return null;
    }

    @Override
    public boolean canFinish(LayerSourcePageContext pageContext) {
        return true;
    }

    @Override
    public boolean performFinish(LayerSourcePageContext pageContext) {
        final Product product = pageContext.getAppContext().getSelectedProduct();
        final Band band = product.getBand(pageContext.getAppContext().getSelectedProductSceneView().getRaster().getName());

        final ObjectDetectionLayer fieldLayer = ObjectDetectionLayerType.createLayer(product, band);
        pageContext.getLayerContext().getRootLayer().getChildren().add(0, fieldLayer);
        return true;
    }

    @Override
    public void cancel(LayerSourcePageContext pageContext) {
    }
}