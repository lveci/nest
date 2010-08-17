/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.dat;

import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.graph.GraphException;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.ui.AppContext;
import org.esa.nest.dat.dialogs.MultiGraphDialog;
import org.esa.nest.dat.plugins.graphbuilder.GraphExecuter;
import org.esa.nest.dat.plugins.graphbuilder.GraphNode;
import org.esa.nest.util.ResourceUtils;

import java.awt.*;
import java.io.File;

/**
 *  Provides the User Interface for Automated Terrain Correction
 */
public class SARSimTerrainCorrectionDialog extends MultiGraphDialog {

    private final static File graphPath = ResourceUtils.getGraphFolder("internal");
    private final static String internalFormat = DimapProductConstants.DIMAP_FORMAT_NAME;

    private final static File tmpFolder = ResourceUtils.getApplicationUserTempDataDir();
    private final static File tmpFile1 = new File(tmpFolder, TMP_FILENAME + ".dim");
    private final static File tmpDataFile1 = new File(tmpFolder, TMP_FILENAME + ".data");

    public SARSimTerrainCorrectionDialog(final AppContext theAppContext, final String title, final String helpID) {
        super(theAppContext, title, helpID, true);

        getIOPanel().setTargetProductName("gcp_selected_TC");
    }

    @Override
    protected void createGraphs() throws GraphException {
        try {
            addGraph(new File(graphPath, "SARSimulationGraph.xml"), "", true);
            addGraph(new File(graphPath, "SARSimTerrainCorrectionGraph.xml"), "", true);

            //placeTargetProductTabAtEnd();

        } catch(Exception e) {
            throw new GraphException(e.getMessage());
        }
    }

    private void addGraph(final File graphFile, final String title, final boolean addUI) {

        final GraphExecuter graphEx = new GraphExecuter();
        LoadGraph(graphEx, graphFile);

        for(GraphNode n : graphEx.GetGraphNodes()) {
            if(n.GetOperatorUI() == null)
                continue;
            if(n.getNode().getOperatorName().equals("Read") || n.getNode().getOperatorName().equals("Write")) {
                n.setOperatorUI(null);
                continue;
            }

            if(addUI) {
                String tabTitle = title;
                if(tabTitle.isEmpty())
                    tabTitle = n.getOperatorName();
                tabbedPane.addTab(tabTitle, null,
                        n.GetOperatorUI().CreateOpTab(n.getOperatorName(), n.getParameterMap(), appContext),
                        n.getID() + " Operator");
            }
        }

        graphExecuterList.add(graphEx);
    }

    private void placeTargetProductTabAtEnd() {
        final int index = tabbedPane.indexOfTab("Target Product");
        final Component tab = tabbedPane.getComponentAt(index);
        tabbedPane.remove(index);
        tabbedPane.add("Target Product", tab);
    }

    @Override
    protected void assignParameters() throws GraphException {
        
        // first Graph - SAR Simulation
        GraphExecuter.setGraphIO(graphExecuterList.get(0),
                "1-Read", ioPanel.getSelectedSourceProduct().getFileLocation(),
                "4-Write", tmpFile1, internalFormat);

        // second Graph - Automated Terrain Correction
        GraphExecuter.setGraphIO(graphExecuterList.get(1),
                "1-Read", tmpFile1,
                "3-Write", ioPanel.getTargetFile(), ioPanel.getTargetFormat());

        final GraphNode tcNode = graphExecuterList.get(1).findGraphNode("2-SARSim-Terrain-Correction");
        if (tcNode != null) {
            final BaseOperatorUI ui = (BaseOperatorUI)tcNode.GetOperatorUI();
            ui.setSourceProducts(new Product[] { ioPanel.getSelectedSourceProduct() });
        }
    }

    @Override
    protected void cleanUpTempFiles() {
        ResourceUtils.deleteFile(tmpFile1);
        ResourceUtils.deleteFile(tmpDataFile1);
    }
}