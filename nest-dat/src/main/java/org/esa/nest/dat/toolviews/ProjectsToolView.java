package org.esa.nest.dat.toolviews;

import com.jidesoft.icons.IconsFactory;
import com.jidesoft.swing.CheckBoxTree;
import com.jidesoft.swing.JideScrollPane;
import com.jidesoft.swing.JideSplitPane;
import org.esa.beam.framework.ui.application.support.AbstractToolView;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.toolviews.lm.LayersToolView;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * The tool window which displays the current project
 */
public class ProjectsToolView extends AbstractToolView {

    public static final String ID = ProjectsToolView.class.getName();
    private VisatApp visatApp;

    public ProjectsToolView() {
        this.visatApp = VisatApp.getApp();
    }

    @Override
    public JComponent createControl() {
        final JScrollPane layerScrollPane = new JideScrollPane(createMockUpTree());
        layerScrollPane.setPreferredSize(new Dimension(320, 480));
        layerScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        layerScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        layerScrollPane.setBorder(null);
        layerScrollPane.setViewportBorder(null);

        final JideSplitPane splitPane = new JideSplitPane(JideSplitPane.VERTICAL_SPLIT);
        splitPane.addPane(layerScrollPane);

        return splitPane;
    }

    private CheckBoxTree createMockUpTree() {
        final CheckBoxTree tree = new CheckBoxTree(createMockUpNodes());
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) tree.getActualCellRenderer();
        renderer.setLeafIcon(IconsFactory.getImageIcon(LayersToolView.class, "/org/esa/beam/resources/images/icons/RsBandAsSwath16.gif"));
        renderer.setClosedIcon(IconsFactory.getImageIcon(LayersToolView.class, "/org/esa/beam/resources/images/icons/RsGroupClosed16.gif"));
        renderer.setOpenIcon(IconsFactory.getImageIcon(LayersToolView.class, "/org/esa/beam/resources/images/icons/RsGroupOpen16.gif"));
        return tree;
    }

    private DefaultMutableTreeNode createMockUpNodes() {
        final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
        rootNode.add(new DefaultMutableTreeNode("Background"));
        rootNode.add(createRasterDatasets());
        rootNode.add(createProductOverlays());
        return rootNode;
    }

    private DefaultMutableTreeNode createRasterDatasets() {
        final DefaultMutableTreeNode rasterDatasets = new DefaultMutableTreeNode("Raster datasets");

        final DefaultMutableTreeNode boavi = new DefaultMutableTreeNode("boavi");
        boavi.add(new DefaultMutableTreeNode("No-data mask"));
        boavi.add(new DefaultMutableTreeNode("ROI"));
        rasterDatasets.add(boavi);

        final DefaultMutableTreeNode yellowSubst = new DefaultMutableTreeNode("yellow_subst");
        yellowSubst.add(new DefaultMutableTreeNode("No-data mask"));
        yellowSubst.add(new DefaultMutableTreeNode("ROI"));
        rasterDatasets.add(yellowSubst);

        final DefaultMutableTreeNode cloudTopPress = new DefaultMutableTreeNode("cloud_top_press");
        cloudTopPress.add(new DefaultMutableTreeNode("No-data mask"));
        cloudTopPress.add(new DefaultMutableTreeNode("ROI"));
        rasterDatasets.add(cloudTopPress);
        return rasterDatasets;
    }

    private DefaultMutableTreeNode createProductOverlays() {
        final DefaultMutableTreeNode productOverlays = new DefaultMutableTreeNode("Product overlays");

        final DefaultMutableTreeNode bitmaskOverlays = new DefaultMutableTreeNode("Bitmasks");
        bitmaskOverlays.add(new DefaultMutableTreeNode("INVALID"));
        bitmaskOverlays.add(new DefaultMutableTreeNode("LAND"));
        bitmaskOverlays.add(new DefaultMutableTreeNode("WATER"));
        bitmaskOverlays.add(new DefaultMutableTreeNode("COASTLINE"));
        bitmaskOverlays.add(new DefaultMutableTreeNode("COSMETIC"));
        bitmaskOverlays.add(new DefaultMutableTreeNode("DUPLICATED"));
        bitmaskOverlays.add(new DefaultMutableTreeNode("SUSPICIOUS"));
        productOverlays.add(bitmaskOverlays);

        productOverlays.add(new DefaultMutableTreeNode("Pins"));
        productOverlays.add(new DefaultMutableTreeNode("Ground control points"));
        productOverlays.add(new DefaultMutableTreeNode("Graticule"));
        productOverlays.add(new DefaultMutableTreeNode("User-defined shapes"));
        return productOverlays;
    }

}