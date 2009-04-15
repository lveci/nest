package org.esa.nest.dat.toolviews.worldmap;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.ui.WorldMapImageLoader;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.toolviews.worldmap.WorldMapToolView;
import org.esa.nest.dat.views.polarview.PolarView;

import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;

/**
 * The window displaying the world map.
 *
 */
public class NestWorldMapToolView extends WorldMapToolView {

    public NestWorldMapToolView() {
    }

    @Override
    protected void createWorldMapPane() {
        _worldMapPane = new NestWorldMapPane(WorldMapImageLoader.getWorldMapImage(false));
    }

    @Override
    protected void addProductListeners() {
        final VisatApp visatApp = VisatApp.getApp();
        visatApp.addProductTreeListener(new WorldMapToolView.WorldMapPTL());

        // Add an internal frame listener to VISAT so that we can update our
        // world map window with the information of the currently activated
        // product scene view.
        //
        visatApp.addInternalFrameListener(new WorldMapIFL());
    }

    private class WorldMapIFL extends InternalFrameAdapter {

        @Override
        public void internalFrameActivated(final InternalFrameEvent e) {
            final Container contentPane = e.getInternalFrame().getContentPane();
            Product product = null;
            if (contentPane instanceof ProductSceneView) {
                product = ((ProductSceneView) contentPane).getProduct();
            } else if(contentPane instanceof PolarView) { 
                product = ((PolarView) contentPane).getProduct();
            }
            setSelectedProduct(product);
        }

        @Override
        public void internalFrameDeactivated(final InternalFrameEvent e) {
        }
    }
}