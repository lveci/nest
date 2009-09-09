package org.esa.nest.dat.layersrc;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.visat.toolviews.layermanager.LayerSource;
import org.esa.beam.visat.toolviews.layermanager.layersrc.AbstractLayerSourceAssistantPage;
import org.esa.beam.visat.toolviews.layermanager.layersrc.LayerSourcePageContext;

/**
 * A source for {@link org.esa.nest.dat.layersrc.ObjectDetectionLayer}s.
 *
 */
public class ObjectDetectionLayerSource implements LayerSource {
    private static final String WINDU_NAME = "zonal_wind";
    private static final String WINDV_NAME = "merid_wind";

    @Override
    public boolean isApplicable(LayerSourcePageContext pageContext) {
        final Product product = pageContext.getAppContext().getSelectedProduct();

        return true;
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

        final ObjectDetectionLayer fieldLayer = ObjectDetectionLayerType.createLayer(product);
        pageContext.getLayerContext().getRootLayer().getChildren().add(0, fieldLayer);
        return true;
    }

    @Override
    public void cancel(LayerSourcePageContext pageContext) {
    }
}