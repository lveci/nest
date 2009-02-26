package org.esa.nest.dat.plugins.graphbuilder;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.graph.Graph;
import org.esa.beam.framework.gpf.graph.GraphException;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.help.HelpSys;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.visat.dialogs.PromptDialog;
import org.esa.nest.util.DatUtils;
import org.esa.nest.util.DialogUtils;

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
 *  Provides the User Interface for creating, loading and saving Graphs
 */
public class GraphBuilderDialog extends ModelessDialog implements Observer {

    private static final ImageIcon processIcon = DatUtils.LoadIcon("org/esa/nest/icons/cog.png");
    private static final ImageIcon saveIcon = DatUtils.LoadIcon("org/esa/nest/icons/save.png");
    private static final ImageIcon loadIcon = DatUtils.LoadIcon("org/esa/nest/icons/open.png");
    private static final ImageIcon clearIcon = DatUtils.LoadIcon("org/esa/nest/icons/edit-clear.png");
    private static final ImageIcon helpIcon = DatUtils.LoadIcon("org/esa/nest/icons/help-browser.png");
    private static final ImageIcon infoIcon = DatUtils.LoadIcon("org/esa/nest/icons/info22.png");

    private final AppContext appContext;
    private GraphPanel graphPanel = null;
    private JLabel statusLabel = null;

    private JPanel progressPanel = null;
    private JProgressBar progressBar = null;
    private ProgressBarProgressMonitor progBarMonitor = null;
    private boolean initGraphEnabled = true;

    private final GraphExecuter graphEx;
    private int graphCount = 0;
    private boolean isProcessing = false;
    private boolean allowGraphBuilding = true;

    //TabbedPanel
    private JTabbedPane tabbedPanel = null;
    private static final ImageIcon OpIcon = UIUtils.loadImageIcon("icons/cog_add.png");

    public GraphBuilderDialog(final AppContext theAppContext, final String title, final String helpID) {
        super(theAppContext.getApplicationWindow(), title, 0, helpID);

        appContext = theAppContext;
        graphEx = new GraphExecuter();
        graphEx.addObserver(this);

        initUI();
        super.getJDialog().setMinimumSize(new Dimension(600, 700));

        if(this.allowGraphBuilding) {
            final File graphPath = GraphBuilderDialog.getInternalGraphFolder();
            final File graphFile =  new File(graphPath, "ReadWriteGraph.xml");
    
            LoadGraph(graphFile);
        }
    }

    public GraphBuilderDialog(final AppContext theAppContext, final String title, final String helpID, final boolean allowGraphBuilding) {
        super(theAppContext.getApplicationWindow(), title, 0, helpID);

        this.allowGraphBuilding = allowGraphBuilding;
        appContext = theAppContext;
        graphEx = new GraphExecuter();
        graphEx.addObserver(this);

        initUI();
        super.getJDialog().setMinimumSize(new Dimension(600, 400));
    }

    /**
     * Initializes the dialog components
     */
    private void initUI() {
        final JPanel mainPanel = new JPanel(new BorderLayout(4, 4));

        // north panel
        final JPanel northPanel = new JPanel(new BorderLayout(4, 4));

        if(allowGraphBuilding) {
            graphPanel = new GraphPanel(graphEx);
            graphPanel.setBackground(Color.WHITE);
            graphPanel.setPreferredSize(new Dimension(500,500));
            final JScrollPane scrollPane = new JScrollPane(graphPanel);
            scrollPane.setPreferredSize(new Dimension(300,300));
            northPanel.add(scrollPane, BorderLayout.CENTER);

            mainPanel.add(northPanel, BorderLayout.NORTH);
        }

        // mid panel
        final JPanel midPanel = new JPanel(new BorderLayout(4, 4));
        tabbedPanel = new JTabbedPane();
        tabbedPanel.addChangeListener(new ChangeListener() {

            public void stateChanged(final ChangeEvent e) {
                ValidateAllNodes();
            }
        });

        statusLabel = new JLabel("");
        statusLabel.setForeground(new Color(255,0,0));
        
        midPanel.add(tabbedPanel, BorderLayout.CENTER);
        midPanel.add(statusLabel, BorderLayout.SOUTH);

        mainPanel.add(midPanel, BorderLayout.CENTER);

        // south panel
        final JPanel southPanel = new JPanel(new BorderLayout(4, 4));
        final JPanel buttonPanel = new JPanel();
        initButtonPanel(buttonPanel);
        southPanel.add(buttonPanel, BorderLayout.CENTER);

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
        southPanel.add(progressPanel, BorderLayout.SOUTH);

        mainPanel.add(southPanel, BorderLayout.SOUTH);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        setContent(mainPanel);
    }

    private void initButtonPanel(final JPanel panel) {
        panel.setLayout(new GridBagLayout());
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        final JButton processButton = DialogUtils.CreateButton("processButton", "Process", processIcon, panel);
        processButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                DoProcessing();
            }
        });

        final JButton saveButton = DialogUtils.CreateButton("saveButton", "Save", saveIcon, panel);
        saveButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                SaveGraph();
            }
        });

        final JButton loadButton = DialogUtils.CreateButton("loadButton", "Load", loadIcon, panel);
        loadButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                LoadGraph();
            }
        });

        final JButton clearButton = DialogUtils.CreateButton("clearButton", "Clear", clearIcon, panel);
        clearButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                ClearGraph();
            }
        });

        final JButton infoButton = DialogUtils.CreateButton("infoButton", "Info", infoIcon, panel);
        infoButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                OnInfo();
            }
        });
                       //getClass().getName() + name
        final JButton helpButton = DialogUtils.CreateButton("helpButton", "Help", helpIcon, panel);
        helpButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                OnHelp();
            }
        });

        gbc.weightx = 0;
        if(allowGraphBuilding) {
            panel.add(loadButton, gbc);
            panel.add(saveButton, gbc);
            panel.add(clearButton, gbc);
            panel.add(infoButton, gbc);
        }
        panel.add(helpButton, gbc);
        panel.add(processButton, gbc);
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

    private boolean InitGraph() {
        boolean result = true;
        try {
            statusLabel.setText("");
            if(initGraphEnabled) {
                result = graphEx.InitGraph();
            }
            if(!result)
                statusLabel.setText("Graph is incomplete");
        } catch(GraphException e) {
            statusLabel.setText(e.getMessage());
            result = false ;
        }
        return result;
    }

    /**
     * Validates the input and then saves the current graph to a file
     */
    private void SaveGraph() {

        if(ValidateAllNodes()) {
            try {
                graphEx.saveGraph();
            } catch(GraphException e) {
                showErrorDialog(e.getMessage());
            }
        } else {
            showErrorDialog(statusLabel.getText());
        }
    }

    /**
     * Loads a new graph from a file
     */
    private void LoadGraph() {
        final File file = DatUtils.GetFilePath("Load Graph", "XML", "xml", "Graph", false);
        if(file == null) return;

        LoadGraph(file);
    }

    /**
     * Loads a new graph from a file
     * @param file the graph file to load
     */
    public void LoadGraph(final File file) {
        try {
            initGraphEnabled = false;
            tabbedPanel.removeAll();
            graphEx.loadGraph(file, true);
            if(allowGraphBuilding)
                graphPanel.repaint();
            initGraphEnabled = true;
        } catch(GraphException e) {
            showErrorDialog(e.getMessage());
        }
    }

    /**
     * Removes all tabs and clears the graph
     */
    private void ClearGraph() {

        initGraphEnabled = false;
        tabbedPanel.removeAll();
        graphEx.ClearGraph();
        graphPanel.repaint();
        initGraphEnabled = true;
        statusLabel.setText("");
    }

    /**
     * Call Help
     */             
    private void OnHelp() {
        HelpSys.showTheme(super.getHelpID());
    }

    /**
     * Call decription dialog
     */
    private void OnInfo() {
        final PromptDialog dlg = new PromptDialog("Graph Description", "Description", graphEx.getGraphDescription(), true);
        dlg.show();
        if(dlg.IsOK()) {
            graphEx.setGraphDescription(dlg.getValue());
        }
    }

    /**
     * lets all operatorUIs validate their parameters
     * If parameter validation fails then a list of the failures is presented to the user
     * @return true if validation passes
     */
    boolean ValidateAllNodes() {

        if(isProcessing) return false;

        boolean isValid = true;
        final StringBuilder msg = new StringBuilder(100);
        for(GraphNode n : graphEx.GetGraphNodes()) {
            final UIValidation validation = n.validateParameterMap();
            if(!validation.getState()) {
                isValid = false;
                msg.append(validation.getMsg()).append('\n');
            }
        }

        if(!isValid) {

            statusLabel.setText(msg.toString());
            return false;
        }

        return InitGraph();
    }

     /**
     Implements the functionality of Observer participant of Observer Design Pattern to define a one-to-many
     dependency between a Subject object and any number of Observer objects so that when the
     Subject object changes state, all its Observer objects are notified and updated automatically.

     Defines an updating interface for objects that should be notified of changes in a subject.
     * @param subject The Observerable subject
     * @param data optional data
     */
    public void update(java.util.Observable subject, java.lang.Object data) {

        try {
            final GraphExecuter.GraphEvent event = (GraphExecuter.GraphEvent)data;
            final GraphNode node = (GraphNode)event.getData();
            final String opID = node.getID();
            if(event.getEventType() == GraphExecuter.events.ADD_EVENT) {

                tabbedPanel.addTab(opID, null, CreateOperatorTab(node), opID + " Operator");
            } else if(event.getEventType() == GraphExecuter.events.REMOVE_EVENT) {

                int index = tabbedPanel.indexOfTab(opID);
                tabbedPanel.remove(index);
            } else if(event.getEventType() == GraphExecuter.events.SELECT_EVENT) {

                int index = tabbedPanel.indexOfTab(opID);
                tabbedPanel.setSelectedIndex(index);
            }
        } catch(Exception e) {
            statusLabel.setText(e.getMessage());   
        }
    }

    private JComponent CreateOperatorTab(final GraphNode node) {

        return node.GetOperatorUI().CreateOpTab(node.getOperatorName(), node.getParameterMap(), appContext);
    }

    private class ProcessThread extends SwingWorker<GraphExecuter, Object> {

        private final ProgressMonitor pm;
        private Date executeStartTime = null;
        private boolean errorOccured = false;

        public ProcessThread(final ProgressMonitor pm) {
            this.pm = pm;
        }

        @Override
        protected GraphExecuter doInBackground() throws Exception {

            pm.beginTask("Processing Graph...", 10);
            try {
                executeStartTime = Calendar.getInstance().getTime();
                isProcessing = true;
                graphEx.executeGraph(pm);

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
            return graphEx;
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
            }
            graphEx.disposeGraphContext();

            if(!errorOccured) {
                openTargetProducts(graphEx.getProductsToOpenInDAT());
            }
        }

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

    public static File getInternalGraphFolder() {
        final String homeUrl = System.getProperty("nest.home", ".");
        return new File(homeUrl, File.separator + "graphs" + File.separator + "internal");
    }
}
