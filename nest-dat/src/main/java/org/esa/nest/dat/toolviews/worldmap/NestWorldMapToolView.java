package org.esa.nest.dat.toolviews.worldmap;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.ui.WorldMapPane;
import org.esa.beam.framework.ui.WorldMapPaneDataModel;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.toolviews.worldmap.WorldMapToolView;
import org.esa.nest.dat.views.polarview.PolarView;

import javax.swing.*;
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
    public JComponent createControl() {
        final JPanel mainPane = new JPanel(new BorderLayout(4, 4));
        mainPane.setPreferredSize(new Dimension(320, 160));

        worldMapDataModel = new WorldMapPaneDataModel();
        final NestWorldMapPane worldMapPane = new NestWorldMapPane(worldMapDataModel);
        worldMapPane.setNavControlVisible(true);
        mainPane.add(worldMapPane, BorderLayout.CENTER);

        VisatApp.getApp().addProductTreeListener(new WorldMapPTL());

        // Add an internal frame listener to VISAT so that we can update our
        // world map window with the information of the currently activated
        // product scene view.
        //
        VisatApp.getApp().addInternalFrameListener(new WorldMapIFL());
        setProducts(VisatApp.getApp().getProductManager().getProducts());
        setSelectedProduct(VisatApp.getApp().getSelectedProduct());

        return mainPane;
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
    }
}