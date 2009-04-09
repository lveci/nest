package org.esa.nest.dat.toolviews.nestwwview;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.layers.Layer;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;

public class ProductPanel extends JPanel {
    private final SurfaceImageLayer imageLayer;
    private JPanel layersPanel;
    private JPanel westPanel;
    private JScrollPane scrollPane;
    private Font defaultFont = null;

    public ProductPanel(WorldWindow wwd, SurfaceImageLayer layer) {
        super(new BorderLayout());
        imageLayer = layer;
        this.makePanel(wwd, new Dimension(100, 400));
    }

    public ProductPanel(WorldWindow wwd, Dimension size, SurfaceImageLayer layer) {
        super(new BorderLayout());
        imageLayer = layer;
        this.makePanel(wwd, size);
    }

    private void makePanel(WorldWindow wwd, Dimension size) {
        // Make and fill the panel holding the layer titles.
        this.layersPanel = new JPanel(new GridLayout(0, 1, 0, 4));
        this.layersPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        this.fill(wwd);

        // Must put the layer grid in a container to prevent scroll panel from stretching their vertical spacing.
        final JPanel dummyPanel = new JPanel(new BorderLayout());
        dummyPanel.add(this.layersPanel, BorderLayout.NORTH);

        // Put the name panel in a scroll bar.
        this.scrollPane = new JScrollPane(dummyPanel);
        this.scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        if (size != null)
            this.scrollPane.setPreferredSize(size);

        // Add the scroll bar and name panel to a titled panel that will resize with the main window.
        westPanel = new JPanel(new GridLayout(0, 1, 0, 10));
        westPanel.setBorder(
                new CompoundBorder(BorderFactory.createEmptyBorder(9, 9, 9, 9), new TitledBorder("Products")));
        westPanel.setToolTipText("Products to Show");
        westPanel.add(scrollPane);
        this.add(westPanel, BorderLayout.CENTER);
    }

    private void fill(WorldWindow wwd) {
        final String[] productNames = imageLayer.getProductNames();
        for(String name : productNames) {
            final LayerAction action = new LayerAction(imageLayer, wwd, name, imageLayer.getOpacity(name) != 0);
            final JCheckBox jcb = new JCheckBox(action);
            jcb.setSelected(action.selected);
            this.layersPanel.add(jcb);

            if (defaultFont == null) {
                this.defaultFont = jcb.getFont();
            }
        }
    }

    public void update(WorldWindow wwd) {
        // Replace all the layer names in the layers panel with the names of the current layers.
        this.layersPanel.removeAll();
        this.fill(wwd);
        this.westPanel.revalidate();
        this.westPanel.repaint();
    }

    @Override
    public void setToolTipText(String string) {
        this.scrollPane.setToolTipText(string);
    }

    private static class LayerAction extends AbstractAction {
        WorldWindow wwd;
        private SurfaceImageLayer layer;
        private boolean selected;
        private String name;

        public LayerAction(SurfaceImageLayer layer, WorldWindow wwd, String name, boolean selected) {
            super(name);
            this.wwd = wwd;
            this.layer = layer;
            this.name = name;
            this.selected = selected;
            this.layer.setEnabled(this.selected);
        }

        public void actionPerformed(ActionEvent actionEvent) {
            // Simply enable or disable the layer based on its toggle button.
            if (((JCheckBox) actionEvent.getSource()).isSelected())
                this.layer.setOpacity(name, this.layer.getOpacity());
            else
                this.layer.setOpacity(name, 0);

            wwd.redraw();
        }
    }
}