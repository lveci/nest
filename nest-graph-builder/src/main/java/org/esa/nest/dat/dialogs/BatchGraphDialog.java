package org.esa.nest.dat.dialogs;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.graph.GraphException;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.nest.dat.plugins.graphbuilder.GraphExecuter;
import org.esa.nest.dat.plugins.graphbuilder.GraphNode;
import org.esa.nest.dat.plugins.graphbuilder.ProgressBarProgressMonitor;

import javax.media.jai.JAI;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 *  Provides the dialog for excuting a graph on a list of products
 */
public class BatchGraphDialog extends ModelessDialog {

    protected final AppContext appContext;
    protected final ProductSetPanel productSetPanel;
    protected final ArrayList<GraphExecuter> graphExecuterList = new ArrayList<GraphExecuter>(3);

    private final static String homeUrl = System.getProperty("nest.home", ".");
    private final static File graphPath = new File(homeUrl, File.separator + "graphs" + File.separator + "internal");
    private final static String internalFormat = DimapProductConstants.DIMAP_FORMAT_NAME;

    private final JPanel mainPanel;
    protected final JTabbedPane tabbedPane;
    private final JLabel statusLabel;
    private final JPanel progressPanel;
    private final JProgressBar progressBar;
    private ProgressBarProgressMonitor progBarMonitor = null;

    private boolean isProcessing = false;

    protected static final String TMP_FILENAME = "tmp_intermediate";

    public BatchGraphDialog(final AppContext theAppContext, final String title, final String helpID) {
        super(theAppContext.getApplicationWindow(), title, ID_APPLY_CLOSE_HELP, helpID);
        appContext = theAppContext;

        mainPanel = new JPanel(new BorderLayout(4, 4));

        tabbedPane = new JTabbedPane();
        tabbedPane.addChangeListener(new ChangeListener() {

            public void stateChanged(final ChangeEvent e) {
                ValidateAllNodes();
            }
        });
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        productSetPanel = new ProductSetPanel(appContext, tabbedPane);

        // status
        statusLabel = new JLabel("");
        statusLabel.setForeground(new Color(255,0,0));
        mainPanel.add(statusLabel, BorderLayout.NORTH);

        // progress Bar
        progressBar = new JProgressBar();
        progressBar.setName(getClass().getName() + "progressBar");
        progressBar.setStringPainted(true);
        progressPanel = new JPanel();
        progressPanel.setLayout(new BorderLayout(2,2));
        progressPanel.add(progressBar, BorderLayout.CENTER);
        final JButton progressCancelBtn = new JButton("Cancel");
        progressCancelBtn.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                CancelProcessing();
            }
        });
        progressPanel.add(progressCancelBtn, BorderLayout.EAST);
        progressPanel.setVisible(false);
        mainPanel.add(progressPanel, BorderLayout.SOUTH);

        getButton(ID_APPLY).setText("Run");

        super.getJDialog().setMinimumSize(new Dimension(400, 300));
    }

    @Override
    public int show() {
        productSetPanel.initProducts();
        setContent(mainPanel);
        initGraphs();
        addGraphTabs("", true);
        return super.show();
    }

    @Override
    public void hide() {
        productSetPanel.releaseProducts();
        super.hide();
    }

    @Override
    protected void onApply() {

        if(isProcessing) return;

        productSetPanel.onApply();

        try {
            DoProcessing();
        } catch(Exception e) {
            statusLabel.setText(e.getMessage());
        }
    }

    @Override
    protected void onClose() {
        CancelProcessing();

        super.onClose();
    }

    void initGraphs() {
        try {
            deleteGraphs();
            createGraphs();
        } catch(Exception e) {
            statusLabel.setText(e.getMessage());
        }
    }

    /**
     * Validates the input and then call the GPF to execute the graph
     * @throws org.esa.beam.framework.gpf.graph.GraphException on assignParameters
     */
    private void DoProcessing() throws GraphException {

        if(ValidateAllNodes()) {

            JAI.getDefaultInstance().getTileCache().flush();
            System.gc();

            progressBar.setValue(0);
            progBarMonitor = new ProgressBarProgressMonitor(progressBar, null, progressPanel);

            final SwingWorker processThread = new ProcessThread(progBarMonitor);
            processThread.execute();

        } else {
            showErrorDialog(statusLabel.getText());
        }
    }

    private void CancelProcessing() {
        if(progBarMonitor != null)
            progBarMonitor.setCanceled(true);
    }

    private void deleteGraphs() {
        for(GraphExecuter gex : graphExecuterList) {
            gex.ClearGraph();
        }
        graphExecuterList.clear();
    }

    /**
     * Loads a new graph from a file
     * @param executer the GraphExcecuter
     * @param file the graph file to load
     */
    public void LoadGraph(final GraphExecuter executer, final File file) {
        try {
            executer.loadGraph(file, true);

        } catch(GraphException e) {
            showErrorDialog(e.getMessage());
        }
    }

    private boolean ValidateAllNodes() {
        if(isProcessing) return false;
        if(productSetPanel == null)
            return false;

        boolean result;
        statusLabel.setText("");
        try {
            cloneGraphs();

            assignParameters();

            // first graph must pass
            result = graphExecuterList.get(0).InitGraph();

        } catch(GraphException e) {
            statusLabel.setText(e.getMessage());
            result = false;
        }
        return result;
    }

    private void openTargetProducts(final ArrayList<File> fileList) {
        if(!fileList.isEmpty()) {
            for(File file : fileList) {
                try {

                    final Product product = ProductIO.readProduct(file, null);
                    if (product != null) {
                        appContext.getProductManager().addProduct(product);
                    }
                } catch(IOException e) {
                    showErrorDialog(e.getMessage());
                }
            }
        }
    }

    protected ProductSetPanel getProductSetPanel() {
        return productSetPanel;
    }

    public void setTargetProductNameSuffix(final String suffix) {
        productSetPanel.setTargetProductNameSuffix(suffix);
    }

    protected void createGraphs() throws GraphException {
        try {
            final GraphExecuter graphEx = new GraphExecuter();
            LoadGraph(graphEx, new File(graphPath, "importGraph.xml"));
            graphExecuterList.add(graphEx);
        } catch(Exception e) {
            throw new GraphException(e.getMessage());
        }
    }

    private void addGraphTabs(final String title, final boolean addUI) {

        if(graphExecuterList.isEmpty()) {
            return;
        }
        final GraphExecuter graphEx = graphExecuterList.get(0);
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
    }

    protected void assignParameters() throws GraphException {
        final File[] fileList = productSetPanel.getFileList();
        int graphIndex = 0;
        for(File f : fileList) {
            final File targetFile = new File(productSetPanel.getTargetFolder(), f.getName()+graphIndex);
            setIO(graphExecuterList.get(graphIndex),
                "1-Read", f,
                "3-Write", targetFile, internalFormat);
            ++graphIndex;
        }
    }

    private static void setIO(final GraphExecuter graphEx,
                              final String readID, final File readPath,
                              final String writeID, final File writePath,
                              final String format) {
        final GraphNode readNode = graphEx.findGraphNode(readID);
        if (readNode != null) {
            graphEx.setOperatorParam(readNode.getID(), "file", readPath.getAbsolutePath());
        }

        if (writeID != null) {
            final GraphNode writeNode = graphEx.findGraphNode(writeID);
            if (writeNode != null) {
                graphEx.setOperatorParam(writeNode.getID(), "formatName", format);
                graphEx.setOperatorParam(writeNode.getID(), "file", writePath.getAbsolutePath());
            }
        }
    }

    protected void cloneGraphs() {
        final GraphExecuter graphEx = graphExecuterList.get(0);
        for(int graphIndex = 1; graphIndex < graphExecuterList.size(); ++graphIndex) {
            final GraphExecuter cloneGraphEx = graphExecuterList.get(graphIndex);
            cloneGraphEx.ClearGraph();
        }
        graphExecuterList.clear();
        graphExecuterList.add(graphEx);

        final ArrayList<GraphNode> graphNodes = graphEx.GetGraphNodes();
        final File[] fileList = productSetPanel.getFileList();
        for(int graphIndex = 1; graphIndex < fileList.length; ++graphIndex) {

            final GraphExecuter cloneGraphEx = new GraphExecuter();
            LoadGraph(cloneGraphEx, new File(graphPath, "importGraph.xml"));
            graphExecuterList.add(cloneGraphEx);

            final ArrayList<GraphNode> cloneGraphNodes = cloneGraphEx.GetGraphNodes();
            for(int i=0; i < graphNodes.size(); ++i) {
                cloneGraphNodes.get(i).setOperatorUI(graphNodes.get(i).GetOperatorUI());
            }
        }
    }

    protected void cleanUpTempFiles() {

    }

    /////

    private class ProcessThread extends SwingWorker<Boolean, Object> {

        private final ProgressMonitor pm;
        private Date executeStartTime = null;
        private boolean errorOccured = false;

        public ProcessThread(final ProgressMonitor pm) {
            this.pm = pm;
        }

        @Override
        protected Boolean doInBackground() throws Exception {

            pm.beginTask("Processing Graph...", 100*graphExecuterList.size());
            try {
                executeStartTime = Calendar.getInstance().getTime();
                isProcessing = true;

                final File[] fileList = productSetPanel.getFileList();
                int graphIndex = 0;
                for(GraphExecuter graphEx : graphExecuterList) {
                    statusLabel.setText("Processing "+ fileList[graphIndex].getName());

                    graphEx.InitGraph();

                    graphEx.executeGraph(new SubProgressMonitor(pm, 100));
                    graphEx.disposeGraphContext();
                    ++graphIndex;
                }

            } catch(Exception e) {
                System.out.print(e.getMessage());
                if(e.getMessage() != null && !e.getMessage().isEmpty())
                    statusLabel.setText(e.getMessage());
                else
                    statusLabel.setText(e.toString());
                errorOccured = true;
            } finally {
                isProcessing = false;
                pm.done();
            }
            return true;
        }

        @Override
        public void done() {
            if(!errorOccured) {
                final Date now = Calendar.getInstance().getTime();
                final long diff = (now.getTime() - executeStartTime.getTime()) / 1000;
                if(diff > 120) {
                    final float minutes = diff / 60f;
                    statusLabel.setText("Processing completed in " + minutes + " minutes");
                } else {
                    statusLabel.setText("Processing completed in " + diff + " seconds");
                }

                //if(productSetPanel.isOpenInAppSelected()) {
                //    final GraphExecuter graphEx = graphExecuterList.get(graphExecuterList.size()-1);
                //    openTargetProducts(graphEx.getProductsToOpenInDAT());
                //}
            }
            cleanUpTempFiles();
        }

    }

}