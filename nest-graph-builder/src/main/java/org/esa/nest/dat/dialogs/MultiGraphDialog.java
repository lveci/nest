package org.esa.nest.dat.dialogs;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.graph.GraphException;
import org.esa.beam.framework.gpf.ui.SourceProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.BasicApp;
import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.TableLayout;
import org.esa.beam.framework.ui.application.SelectionChangeEvent;
import org.esa.beam.framework.ui.application.SelectionChangeListener;
import org.esa.beam.util.SystemUtils;
import org.esa.nest.dat.plugins.graphbuilder.GraphExecuter;
import org.esa.nest.dat.plugins.graphbuilder.ProgressBarProgressMonitor;

import javax.media.jai.JAI;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 *  Provides the dialog for excuting multiple graph from one user interface
 */
public abstract class MultiGraphDialog extends ModelessDialog {

    protected final AppContext appContext;
    protected final ArrayList<GraphExecuter> graphExecuterList = new ArrayList<GraphExecuter>(3);

    private final JPanel mainPanel;
    protected final JTabbedPane tabbedPane;
    private final JLabel statusLabel;
    private final JPanel progressPanel;
    private final JProgressBar progressBar;
    private ProgressBarProgressMonitor progBarMonitor = null;

    private boolean isProcessing = false;

    private final TargetProductSelector targetProductSelector;
    private java.util.List<SourceProductSelector> sourceProductSelectorList;
    private String targetProductNameSuffix = "";

    public MultiGraphDialog(final AppContext theAppContext, final String title, final String helpID) {
        super(theAppContext.getApplicationWindow(), title, ID_APPLY_CLOSE_HELP, helpID);

        appContext = theAppContext;

        targetProductSelector = new TargetProductSelector();
        final String homeDirPath = SystemUtils.getUserHomeDir().getPath();
        final String saveDir = appContext.getPreferences().getPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, homeDirPath);
        targetProductSelector.getModel().setProductDir(new File(saveDir));
        targetProductSelector.getOpenInAppCheckBox().setText("Open in " + appContext.getApplicationName());

        // Fetch source products
        initSourceProductSelectors();

        final TableLayout tableLayout = new TableLayout(1);
        tableLayout.setTableAnchor(TableLayout.Anchor.WEST);
        tableLayout.setTableWeightX(1.0);
        tableLayout.setTableFill(TableLayout.Fill.HORIZONTAL);
        tableLayout.setTablePadding(3, 3);

        final JPanel ioParametersPanel = new JPanel(tableLayout);
        for (SourceProductSelector selector : sourceProductSelectorList) {
            ioParametersPanel.add(selector.createDefaultPanel());
        }
        ioParametersPanel.add(targetProductSelector.createDefaultPanel());
        ioParametersPanel.add(tableLayout.createVerticalSpacer());
        sourceProductSelectorList.get(0).addSelectionChangeListener(new SelectionChangeListener() {
            public void selectionChanged(SelectionChangeEvent event) {
                final Product selectedProduct = (Product) event.getSelection().getFirstElement();
                final TargetProductSelectorModel targetProductSelectorModel = targetProductSelector.getModel();
                targetProductSelectorModel.setProductName(selectedProduct.getName() + getTargetProductNameSuffix());
            }
        });

        mainPanel = new JPanel(new BorderLayout(4, 4));

        tabbedPane = new JTabbedPane();
        tabbedPane.add("I/O Parameters", ioParametersPanel);
        tabbedPane.addChangeListener(new ChangeListener() {

            public void stateChanged(final ChangeEvent e) {
                ValidateAllNodes();
            }
        });

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

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
        progressPanel.setVisible(true);
        mainPanel.add(progressPanel, BorderLayout.SOUTH);

        AbstractButton button = getButton(ID_APPLY);
        button.setText("Run");

        super.getJDialog().setMinimumSize(new Dimension(400, 300));
    }

    private void initSourceProductSelectors() {
        sourceProductSelectorList = new ArrayList<SourceProductSelector>(3);
        sourceProductSelectorList.add(new SourceProductSelector(appContext));
    }

    @Override
    public int show() {
        for (SourceProductSelector sourceProductSelector : sourceProductSelectorList) {
            sourceProductSelector.initProducts();
        }
        setContent(mainPanel);
        initGraphs();
        return super.show();
    }

    @Override
    public void hide() {
        for (SourceProductSelector sourceProductSelector : sourceProductSelectorList) {
            sourceProductSelector.releaseProducts();
        }
        super.hide();
    }

    @Override
    protected void onApply() {

        if(isProcessing) return;

        final String productDir = targetProductSelector.getModel().getProductDir().getAbsolutePath();
        appContext.getPreferences().setPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, productDir);

        try {
            //initGraphs()
            assignParameters();

            DoProcessing();
        } catch(Exception e) {
            statusLabel.setText(e.getMessage());
        }
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
     */
    private void DoProcessing() {

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

    protected abstract void createGraphs() throws GraphException;

    protected abstract void assignParameters() throws GraphException;

    protected abstract void cleanUpTempFiles();

    private boolean ValidateAllNodes() {
        boolean result=true;
        try {
            assignParameters();
            for(GraphExecuter graphEx : graphExecuterList) {
                graphEx.InitGraph();
            }
        } catch(GraphException e) {
            statusLabel.setText(e.getMessage());
            result = false;
        }
        return result;
    }

    private class ProcessThread extends SwingWorker<Boolean, Object> {

        private final ProgressMonitor pm;
        private Date executeStartTime;

        public ProcessThread(final ProgressMonitor pm) {
            this.pm = pm;
        }

        @Override
        protected Boolean doInBackground() throws Exception {

            pm.beginTask("Processing Graph...", 10);
            try {
                executeStartTime = Calendar.getInstance().getTime();
                isProcessing = true;

                for(GraphExecuter graphEx : graphExecuterList) {
                    graphEx.recreateGraphContext(true);
                    graphEx.executeGraph(pm);

                    graphEx.disposeGraphContext();
                }

            } catch(Exception e) {
                System.out.print(e.getMessage());
            } finally {
                isProcessing = false;
                pm.done();
            }
            return true;
        }

        @Override
        public void done() {
            final Date now = Calendar.getInstance().getTime();
            final long diff = (now.getTime() - executeStartTime.getTime()) / 1000;
            if(diff > 120) {
                final float minutes = diff / 60f;
                statusLabel.setText("Processing completed in " + minutes + " minutes");
            } else {
                statusLabel.setText("Processing completed in " + diff + " seconds");
            }

            if(targetProductSelector.getModel().isOpenInAppSelected()) {
                final GraphExecuter graphEx = graphExecuterList.get(graphExecuterList.size()-1);
                openTargetProducts(graphEx.getProductsToOpenInDAT());
            }

            cleanUpTempFiles();
        }

    }

    private void openTargetProducts(final Vector<File> fileList) {
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

    public Product getSelectedSourceProduct() {
        return sourceProductSelectorList.get(0).getSelectedProduct();
    }

    public File getTargetFile() {
        return targetProductSelector.getModel().getProductFile();
    }

    public String getTargetFormat() {
        return targetProductSelector.getModel().getFormatName();
    }

    String getTargetProductNameSuffix() {
        return targetProductNameSuffix;
    }

    public void setTargetProductNameSuffix(final String suffix) {
        targetProductNameSuffix = suffix;
    }
}