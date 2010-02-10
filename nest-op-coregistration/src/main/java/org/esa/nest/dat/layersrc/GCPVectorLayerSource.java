package org.esa.nest.dat.layersrc;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.datamodel.Placemark;
import org.esa.beam.framework.ui.layer.AbstractLayerSourceAssistantPage;
import org.esa.beam.framework.ui.layer.LayerSource;
import org.esa.beam.framework.ui.layer.LayerSourcePageContext;

/**
 * A source for {@link org.esa.nest.dat.layersrc.GCPVectorLayer}s.
 *
 */
public class GCPVectorLayerSource implements LayerSource {

    @Override
    public boolean isApplicable(LayerSourcePageContext pageContext) {
        final Product product = pageContext.getAppContext().getSelectedProduct();
        final ProductNodeGroup<Placemark> masterGCPGroup = product.getGcpGroup(product.getBandAt(0));
        final Band band = product.getBand(pageContext.getAppContext().getSelectedProductSceneView().getRaster().getName());

        return (masterGCPGroup != null && masterGCPGroup.getNodeCount() > 0 &&
                band != null && product.getBandAt(0) != band);
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

        final GCPVectorLayer fieldLayer = GCPVectorLayerType.createLayer(product, band);
        pageContext.getLayerContext().getRootLayer().getChildren().add(0, fieldLayer);
        return true;
    }

    @Override
    public void cancel(LayerSourcePageContext pageContext) {
    }
}