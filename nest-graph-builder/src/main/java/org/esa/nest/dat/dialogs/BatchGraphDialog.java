package org.esa.nest.dat.dialogs;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.graph.GraphException;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.util.io.FileChooserFactory;
import org.esa.beam.util.io.FileUtils;
import org.esa.nest.dat.plugins.graphbuilder.GraphExecuter;
import org.esa.nest.dat.plugins.graphbuilder.GraphNode;
import org.esa.nest.dat.plugins.graphbuilder.ProgressBarProgressMonitor;
import org.esa.nest.db.ProductEntry;
import org.esa.nest.util.ResourceUtils;

import javax.media.jai.JAI;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 *  Provides the dialog for excuting a graph on a list of products
 */
public class BatchGraphDialog extends ModelessDialog {

    private final AppContext appContext;
    private final ProductSetPanel productSetPanel;
    private final ArrayList<GraphExecuter> graphExecuterList = new ArrayList<GraphExecuter>(10);

    private final static File graphPath = ResourceUtils.getGraphFolder("");
    private final static String internalFormat = DimapProductConstants.DIMAP_FORMAT_NAME;

    private final JPanel mainPanel;
    private final JTabbedPane tabbedPane;
    private final JLabel statusLabel;
    private final JPanel progressPanel;
    private final JProgressBar progressBar;
    private ProgressBarProgressMonitor progBarMonitor = null;

    private Map<File, File[]> slaveFileMap = null;
    private final Map<File, File> targetFileMap = new HashMap<File, File>();
    private final ArrayList<BatchProcessListener> listenerList = new ArrayList<BatchProcessListener>(1);
    private final boolean closeOnDone;

    private boolean isProcessing = false;
    private File graphFile;

    public BatchGraphDialog(final AppContext theAppContext, final String title, final String helpID, 
                            final boolean closeOnDone) {
        super(theAppContext.getApplicationWindow(), title, ID_YES| ID_APPLY_CLOSE_HELP, helpID);
        this.appContext = theAppContext;
        this.closeOnDone = closeOnDone;

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
        getButton(ID_YES).setText("Load");

        graphFile = new File(graphPath+File.separator+"internal", "importGraph.xml");
        super.getJDialog().setMinimumSize(new Dimension(400, 300));
    }

    @Override
    public int show() {
        productSetPanel.initProducts();
        setContent(mainPanel);
        //initGraphs();
        //addGraphTabs("", true);
        return super.show();
    }

    @Override
    public void hide() {
        productSetPanel.releaseProducts();
        if(progBarMonitor != null)
            progBarMonitor.setCanceled(true);
        notifyMSG(BatchProcessListener.BatchMSG.CLOSE);
        super.hide();
    }

    @Override
    public void onApply() {
        if(isProcessing) return;

        productSetPanel.onApply();

        try {
            DoProcessing();
        } catch(Exception e) {
            statusLabel.setText(e.getMessage());
        }
    }

    public void addListener(final BatchProcessListener listener) {
        if (!listenerList.contains(listener)) {
            listenerList.add(listener);
        }
    }

    public void removeListener(final BatchProcessListener listener) {
        listenerList.remove(listener);
    }

    private void notifyMSG(final BatchProcessListener.BatchMSG msg) {
        for (final BatchProcessListener listener : listenerList) {
            listener.notifyMSG(msg, targetFileMap);
        }
    }

    /**
     * OnLoad
     */
    @Override
    protected void onYes() {
        if(isProcessing) return;

        final File file = getFilePath(this.getContent(), "Graph File");
        if(file != null) {
            LoadGraphFile(file);
        }
    }

    public void setInputFiles(final ProductEntry[] productEntryList) {
        productSetPanel.setProductEntryList(productEntryList);
    }

    public void setTargetFolder(final File path) {
        productSetPanel.setTargetFolder(path);
    }

    public void LoadGraphFile(File file) {
        graphFile = file;

        initGraphs();
        addGraphTabs("", true);
    }

    private static File getFilePath(Component component, String title) {

        final JFileChooser chooser = FileChooserFactory.getInstance().createFileChooser(graphPath);
        chooser.setMultiSelectionEnabled(false);
        chooser.setDialogTitle(title);
        if (chooser.showDialog(component, "ok") == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
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
    void LoadGraph(final GraphExecuter executer, final File file) {
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

                    final Product product = ProductIO.readProduct(file);
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

    void createGraphs() throws GraphException {
        try {
            final GraphExecuter graphEx = new GraphExecuter();
            LoadGraph(graphEx, graphFile);
            graphExecuterList.add(graphEx);
        } catch(Exception e) {
            throw new GraphException(e.getMessage());
        }
    }

    private void addGraphTabs(final String title, final boolean addUI) {

        if(graphExecuterList.isEmpty()) {
            return;
        }

        tabbedPane.setSelectedIndex(0);
        while(tabbedPane.getTabCount() > 1) {
            tabbedPane.remove(tabbedPane.getTabCount()-1);
        }
        
        final GraphExecuter graphEx = graphExecuterList.get(0);
        for(GraphNode n : graphEx.GetGraphNodes()) {
            if(n.GetOperatorUI() == null)
                continue;
            if(n.getNode().getOperatorName().equals("Read") || n.getNode().getOperatorName().equals("Write")
               || n.getNode().getOperatorName().equals("ProductSet-Reader")) {
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

    public void setSlaveFileMap(Map<File, File[]> fileMap) {
        slaveFileMap = fileMap;
    }

    void assignParameters() {
        final File[] fileList = productSetPanel.getFileList();
        int graphIndex = 0;
        targetFileMap.clear();
        for(File f : fileList) {
            String name;
            final Object o = productSetPanel.getValueAt(graphIndex, 0);
            if(o instanceof String)
                name = (String) o;
            else
                name = FileUtils.getFilenameWithoutExtension(f);

            final File targetFile = new File(productSetPanel.getTargetFolder(), name);
            targetFileMap.put(f, targetFile);

            setIO(graphExecuterList.get(graphIndex),
                "Read", f,
                "Write", targetFile, internalFormat);
            if(slaveFileMap != null) {
                final File[] slaveFiles = slaveFileMap.get(f);
                if(slaveFiles != null) {
                    setSlaveIO(graphExecuterList.get(graphIndex),
                                "ProductSet-Reader", f, slaveFiles);
                }
            }
            ++graphIndex;
        }
    }

    private static void setIO(final GraphExecuter graphEx,
                              final String readID, final File readPath,
                              final String writeID, final File writePath,
                              final String format) {
        final GraphNode readNode = graphEx.findGraphNodeByOperator(readID);
        if (readNode != null) {
            graphEx.setOperatorParam(readNode.getID(), "file", readPath.getAbsolutePath());
        }

        if (writeID != null) {
            final GraphNode writeNode = graphEx.findGraphNodeByOperator(writeID);
            if (writeNode != null) {
                graphEx.setOperatorParam(writeNode.getID(), "formatName", format);
                graphEx.setOperatorParam(writeNode.getID(), "file", writePath.getAbsolutePath());
            }
        }
    }

    /**
     * For coregistration
     * @param graphEx the graph executer
     * @param productSetID the product set reader
     * @param masterFile master file
     * @param slaveFiles slave file list
     */
    private static void setSlaveIO(final GraphExecuter graphEx, final String productSetID,
                                   final File masterFile, final File[] slaveFiles) {
        final GraphNode productSetNode = graphEx.findGraphNodeByOperator(productSetID);
        if(productSetNode != null) {
            StringBuilder str = new StringBuilder(masterFile.getAbsolutePath());
            for(File slaveFile : slaveFiles) {
                str.append(",");
                str.append(slaveFile.getAbsolutePath());
            }
            graphEx.setOperatorParam(productSetNode.getID(), "fileList", str.toString());
        }
    }

    void cloneGraphs() {
        final GraphExecuter graphEx = graphExecuterList.get(0);
        for(int graphIndex = 1; graphIndex < graphExecuterList.size(); ++graphIndex) {
            final GraphExecuter cloneGraphEx = graphExecuterList.get(graphIndex);
            cloneGraphEx.ClearGraph();
        }
        graphExecuterList.clear();
        graphExecuterList.add(graphEx);

        final File[] fileList = productSetPanel.getFileList();
        for(int graphIndex = 1; graphIndex < fileList.length; ++graphIndex) {

            final GraphExecuter cloneGraphEx = new GraphExecuter();
            LoadGraph(cloneGraphEx, graphFile);
            graphExecuterList.add(cloneGraphEx);

            final ArrayList<GraphNode> cloneGraphNodes = cloneGraphEx.GetGraphNodes();
            for(GraphNode cloneNode : cloneGraphNodes) {
                final GraphNode node = graphEx.findGraphNode(cloneNode.getID());
                if(node != null)
                    cloneNode.setOperatorUI(node.GetOperatorUI());
            }
        }
    }

    void cleanUpTempFiles() {

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
                    if(pm.isCanceled()) break;

                    try {
                        final String nOfm = String.valueOf(graphIndex+1)+" of "+fileList.length + ' ';
                        statusLabel.setText("Processing "+ nOfm +fileList[graphIndex].getName());

                        JAI.getDefaultInstance().getTileCache().flush();
                        System.gc();

                        graphEx.InitGraph();

                        graphEx.executeGraph(new SubProgressMonitor(pm, 100));

                    } catch(Exception e) {
                        System.out.print(e.getMessage());
                    }
                    graphEx.disposeGraphContext();
                    graphEx = null;
                    ++graphIndex;
                }
                graphExecuterList.clear();

                JAI.getDefaultInstance().getTileCache().flush();
                System.gc();

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
            notifyMSG(BatchProcessListener.BatchMSG.DONE);
            if(closeOnDone)
                close();
        }



    }

    public interface BatchProcessListener {

        public enum BatchMSG { DONE, CLOSE }

        public void notifyMSG(final BatchMSG msg, final Map<File, File> targetFileMap);
    }

}