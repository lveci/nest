package org.esa.nest.dat.actions.importbrowser.ui;

import com.bc.ceres.core.Assert;
import com.bc.ceres.core.ProgressMonitor;
import com.jidesoft.icons.IconsFactory;
import com.jidesoft.swing.JideScrollPane;
import com.jidesoft.swing.JideSplitPane;
import org.esa.beam.framework.help.HelpSys;
import org.esa.beam.framework.ui.BasicApp;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.framework.ui.WorldMapPaneDataModel;
import org.esa.beam.framework.ui.WorldMapPane;
import org.esa.beam.framework.ui.tool.ToolButtonFactory;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.dat.actions.importbrowser.model.*;
import org.esa.nest.dat.actions.importbrowser.model.dataprovider.ProductPropertiesProvider;
import org.esa.nest.dat.actions.importbrowser.model.dataprovider.QuicklookProvider;
import org.esa.nest.dat.actions.importbrowser.util.Callback;
import org.esa.nest.dat.toolviews.Projects.Project;
import org.esa.nest.dat.dialogs.BatchGraphDialog;
import org.esa.nest.dat.DatContext;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

public class ImportBrowser {

    private static final int quickLookWidth = 1000;
    private static final String stopCommand = "stop";
    private static final String updateCommand = "update";
    private static final ImageIcon updateIcon = UIUtils.loadImageIcon("icons/Update24.gif");
    private static final ImageIcon updateRolloverIcon = ToolButtonFactory.createRolloverIcon(updateIcon);
    private static final ImageIcon stopIcon = UIUtils.loadImageIcon("icons/Stop24.gif");
    private static final ImageIcon stopRolloverIcon = ToolButtonFactory.createRolloverIcon(stopIcon);

    private JPanel mainPanel;
    private JComboBox repositoryListCombo;
    private JTable repositoryTable;
    private SortingDecorator sortedModel;
    private JLabel statusLabel;
    private JPanel progressPanel;
    private JButton openButton;
    private JButton addToProjectButton;
    private JButton openAllSelectedButton;
    private JButton batchProcessButton;
    private JButton removeButton;
    private JButton updateButton;
    private JProgressBar progressBar;
    private JPanel headerPanel;
    private final UiCallBack uiCallBack;
    private final RepositoryManager repositoryManager;
    private File currentDirectory;
    private ProductOpenHandler openHandler;
    private final ProductGrabberConfig pgConfig;
    private final String helpId;
    private JFrame mainFrame;

    private WorldMapPaneDataModel worldMapDataModel = null;

    private RepositoryTree repositoryTree = null;
    private DefaultMutableTreeNode rootNode = null;

    public ImportBrowser(final BasicApp basicApp, final RepositoryManager repositoryManager, final String helpId) {
        pgConfig = new ProductGrabberConfig(basicApp.getPreferences());
        this.repositoryManager = repositoryManager;
        this.repositoryManager.addListener(new MyRepositoryManagerListener());
        addDefaultDataProvider(this.repositoryManager);
        uiCallBack = new UiCallBack();
        this.helpId = helpId;
    }

    public RepositoryManager getRepositoryManager() {
        return repositoryManager;
    }

    /**
     * Sets the ProductOpenHandler which handles the action when products should
     * be opened.
     *
     * @param handler The <code>ProductOpenHandler</code>, can be
     *                <code>null</code> to unset the handler.
     */
    public void setProductOpenHandler(final ProductOpenHandler handler) {
        openHandler = handler;
    }

    public synchronized JFrame getFrame() {
        if (mainFrame == null) {
            mainFrame = new JFrame("Import Browser");
            mainFrame.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            initUI();
            mainFrame.addComponentListener(new ComponentAdapter() {

                @Override
                public void componentHidden(final ComponentEvent e) {
                    repositoryManager.stopUpdateRepository();
                }
            });
            mainFrame.add(mainPanel);
            mainFrame.setBounds(pgConfig.getWindowBounds());
            applyConfig(pgConfig);
            mainFrame.addComponentListener(new ComponentAdapter() {

                @Override
                public void componentMoved(final ComponentEvent e) {
                    pgConfig.setWindowBounds(e.getComponent().getBounds());
                }

                @Override
                public void componentResized(final ComponentEvent e) {
                    pgConfig.setWindowBounds(e.getComponent().getBounds());
                }
            });
            setUIComponentsEnabled(repositoryListCombo.getItemCount() > 0);
        }
        return mainFrame;
    }

    private void applyConfig(final ProductGrabberConfig config) {
        final Repository[] repositories = config.getRepositories();
        for (Repository repository : repositories) {
            repositoryManager.addRepository(repository);
        }

     /*   final String lastSelectedRepositoryDir = config.getLastSelectedRepositoryDir();
        final Repository selectedRepository = repositoryManager.getRepository(lastSelectedRepositoryDir);
        if (selectedRepository != null) {
            repositoryListCombo.setSelectedItem(selectedRepository);
        } else if (repositoryManager.getNumRepositories() > 0) {
            repositoryListCombo.setSelectedItem(repositoryManager.getRepository(0));
        }   */
    }

    private void performSelectAction() {
        final Repository repository = repositoryManager.getRepositoryShown();
        if (repository == null) {
            return;
        }
        final int[] selectedRows = getSelectedRows();
        if(repository.getEntryCount() > selectedRows[0]) {
            final RepositoryEntry entry = repository.getEntry(selectedRows[0]);
            worldMapDataModel.setSelectedProduct(entry.getProduct());
        }
    }

    private void performOpenAction() {
        if (openHandler != null) {
            //final Repository repository = (Repository) repositoryListCombo.getSelectedItem();
            final Repository repository = repositoryManager.getRepositoryShown();
            if (repository == null) {
                return;
            }
            final int[] selectedRows = getSelectedRows();
            final File[] productFilesToOpen = new File[selectedRows.length];
            for (int i = 0; i < selectedRows.length; i++) {
                if(repository.getEntryCount() > selectedRows[i]) {
                    final RepositoryEntry entry = repository.getEntry(selectedRows[i]);
                    productFilesToOpen[i] = entry.getProductFile();
                }
            }
            openHandler.openProducts(productFilesToOpen);
        }
    }

    private Vector<File> getSelectedFiles() {
        final Vector<File> fileList = new Vector<File>(10);

        final Repository[] repoList = repositoryManager.getRepositories();
        for(Repository repo : repoList) {
            final int numEntries = repo.getEntryCount();
            for(int i=0; i < numEntries; ++i) {
                final RepositoryEntry entry = repo.getEntry(i);
                if(entry.isSelected()) {
                    // add to list to open
                    fileList.add(entry.getProductFile());
                }
            }
        }

        return fileList;
    }

    private void unselectAll() {
        final Repository[] repoList = repositoryManager.getRepositories();
        for(Repository repo : repoList) {
            final int numEntries = repo.getEntryCount();
            for(int i=0; i < numEntries; ++i) {
                repo.getEntry(i).setSelected(false);
            }
        }
    }

    private void performOpenAllSelectedAction() {
        final Vector<File> fileList = getSelectedFiles();

        if(!fileList.isEmpty()) {
            final File[] productFilesToOpen = new File[fileList.size()];
            fileList.toArray(productFilesToOpen);

            openHandler.openProducts(productFilesToOpen);
            unselectAll();
        }
    }

    private void performBatchProcessSelectedAction() {
        final Vector<File> fileList = getSelectedFiles();

        if(!fileList.isEmpty()) {
            final File[] productFiles = new File[fileList.size()];
            fileList.toArray(productFiles);

            final BatchGraphDialog batchDlg = new BatchGraphDialog(new DatContext(""),
                        "Batch Processing", "batchProcessing");
            batchDlg.setInputFiles(productFiles);
            batchDlg.show();
        }
    }

    private void performAddToProjectAction() {
        final Vector<File> fileList = getSelectedFiles();

        if(!fileList.isEmpty()) {
            final File[] productFilesToOpen = new File[fileList.size()];
            fileList.toArray(productFilesToOpen);

            Project.instance().ImportFileList(productFilesToOpen);
            unselectAll();
        }
    }

    private int[] getSelectedRows() {
        final int[] selectedRows = repositoryTable.getSelectedRows();
        final int[] sortedRows = new int[selectedRows.length];
        if (sortedModel != null) {
            for (int i = 0; i < selectedRows.length; i++) {
                sortedRows[i] = sortedModel.getSortedIndex(selectedRows[i]);
            }
            return sortedRows;
        } else {
            return selectedRows;
        }
    }

    private void addRepository() {
        final File baseDir = promptForRepositoryBaseDir();
        if (baseDir == null) {
            return;
        }

        if (repositoryManager.getRepository(baseDir.getPath()) != null) {
            JOptionPane.showMessageDialog(mainPanel,
                                          "The selected directory is already in the repository list.",
                                          "Add Repository",
                                          JOptionPane.WARNING_MESSAGE); /* I18N */
            repositoryListCombo.setSelectedItem(repositoryManager.getRepository(baseDir.getPath()));
            return;
        }

        if (!baseDir.exists()) {
            JOptionPane.showMessageDialog(mainPanel,
                                          "Directory does not exist.", "Add Repository",
                                          JOptionPane.ERROR_MESSAGE); /* I18N */
            return;
        }

        final int answer = JOptionPane.showOptionDialog(mainPanel,
                                                        "Search directory recursively?", "Add Repository",
                                                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                                                        null, null); /* I18N */

        boolean doRecursive = false;
        if (answer == JOptionPane.YES_OPTION) {
            doRecursive = true;
        }

        final SwingWorker repositoryCollector = new RepositoryCollector(baseDir, doRecursive,
                                                                        new ProgressBarProgressMonitor(progressBar,
                                                                                                       statusLabel));
        repositoryCollector.execute();
    }

    private void removeRepository(final Repository repository) {
        if (repository != null) {
            repositoryManager.removeRepository(repository);
            final boolean repositoryListIsNotEmpty = repositoryManager.getNumRepositories() > 0;
            if (repositoryListIsNotEmpty) {
                final Repository firstElement = repositoryManager.getRepository(0);
                if(firstElement != null)
                    repositoryListCombo.setSelectedItem(firstElement);
            }
            setUIComponentsEnabled(repositoryListIsNotEmpty);
        }
    }

    private void setUIComponentsEnabled(final boolean enable) {
        openButton.setEnabled(enable);
        addToProjectButton.setEnabled(enable);
        openAllSelectedButton.setEnabled(enable);
        batchProcessButton.setEnabled(enable);
        removeButton.setEnabled(enable);
        updateButton.setEnabled(enable);
        repositoryListCombo.setEnabled(enable);
    }

    private void toggleUpdateButton(final String command) {
        if (command.equals(stopCommand)) {
            updateButton.setIcon(stopIcon);
            updateButton.setRolloverIcon(stopRolloverIcon);
            updateButton.setActionCommand(stopCommand);
        } else {
            updateButton.setIcon(updateIcon);
            updateButton.setRolloverIcon(updateRolloverIcon);
            updateButton.setActionCommand(updateCommand);
        }
    }

    private File promptForRepositoryBaseDir() {
        final JFileChooser fileChooser = createDirectoryChooser();
        fileChooser.setCurrentDirectory(currentDirectory);
        final int response = fileChooser.showOpenDialog(mainPanel);
        currentDirectory = fileChooser.getCurrentDirectory();
        File selectedDir = fileChooser.getSelectedFile();
        if(selectedDir != null && selectedDir.isFile())
            selectedDir = selectedDir.getParentFile();
        if (response == JFileChooser.APPROVE_OPTION) {
            return selectedDir;
        }
        return null;
    }

    private static JFileChooser createDirectoryChooser() {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(final File f) {
                return true;//f.isDirectory();
            }

            @Override
            public String getDescription() {
                return "Directories"; /* I18N */
            }
        });
        fileChooser.setDialogTitle("Select Directory"); /* I18N */
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setApproveButtonText("Select"); /* I18N */
        fileChooser.setApproveButtonMnemonic('S');
        return fileChooser;
    }

    private void initUI() {
        mainPanel = new JPanel(new BorderLayout(4, 4));
        final JPanel southPanel = new JPanel(new BorderLayout(4, 4));

        final JPanel northPanel = new JPanel(new BorderLayout(4, 4));
        repositoryListCombo = new JComboBox();
        setComponentName(repositoryListCombo, "repositoryListCombo");
        repositoryTable = new JTable();
        statusLabel = new JLabel("");
        progressPanel = new JPanel();
        openButton = new JButton();
        setComponentName(openButton, "openButton");
        removeButton = new JButton();
        setComponentName(removeButton, "removeButton");
        updateButton = new JButton();
        setComponentName(updateButton, "updateButton");
        progressBar = new JProgressBar();
        setComponentName(progressBar, "progressBar");
        headerPanel = new JPanel();

        final JPanel openPanel = new JPanel(new BorderLayout(4, 4));

        addToProjectButton = new JButton();
        setComponentName(addToProjectButton, "addToProject");
        addToProjectButton.setText("Import to Project");
        addToProjectButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                performAddToProjectAction();
            }
        });

        openAllSelectedButton = new JButton();
        setComponentName(openAllSelectedButton, "openAllSelectedButton");
        openAllSelectedButton.setText("Open Selected");
        openAllSelectedButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                performOpenAllSelectedAction();
            }
        });

        batchProcessButton = new JButton();
        setComponentName(batchProcessButton, "batchProcessButton");
        batchProcessButton.setText("Batch Process");
        batchProcessButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                performBatchProcessSelectedAction();
            }
        });

        openPanel.add(addToProjectButton, BorderLayout.WEST);
        openPanel.add(openAllSelectedButton, BorderLayout.CENTER);
        openPanel.add(batchProcessButton, BorderLayout.EAST);

        northPanel.add(headerPanel, BorderLayout.CENTER);
        southPanel.add(statusLabel, BorderLayout.CENTER);
        southPanel.add(openPanel, BorderLayout.WEST);
        southPanel.add(progressPanel, BorderLayout.EAST);

        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(createCentrePanel(), BorderLayout.CENTER);
        mainPanel.add(southPanel, BorderLayout.SOUTH);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressPanel.setLayout(new BorderLayout());
        progressPanel.add(progressBar);
        progressPanel.setVisible(false);

        repositoryListCombo.addItemListener(new RepositoryChangeHandler());
        repositoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        repositoryTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        repositoryTable.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(final MouseEvent e) {
                final int clickCount = e.getClickCount();
                if (clickCount == 2) {
                    performOpenAction();
                } else if(clickCount == 1) {
                    performSelectAction();
                }
            }
        });
        initHeaderPanel(headerPanel);
    }

    private JPanel createCentrePanel() {
        final JideSplitPane splitPane1H = new JideSplitPane(JideSplitPane.HORIZONTAL_SPLIT);

        final JideSplitPane splitPane11 = new JideSplitPane(JideSplitPane.VERTICAL_SPLIT);
        splitPane11.add(createRepositoryTreeControl());
        worldMapDataModel = new WorldMapPaneDataModel();
        splitPane11.add(new WorldMapPane(worldMapDataModel));

        splitPane1H.add(splitPane11);
        splitPane1H.add(new JScrollPane(repositoryTable));

        return splitPane1H;
    }

    private void setComponentName(JComponent button, String name) {
        button.setName(getClass().getName() + name);
    }

    public JComponent createRepositoryTreeControl() {
        final JScrollPane prjScrollPane = new JideScrollPane(createTree());
        prjScrollPane.setPreferredSize(new Dimension(320, 480));
        prjScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        prjScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        return prjScrollPane;
    }

    public void UpdateUI() {
        repositoryTable.updateUI();
    }

    private RepositoryTree createTree() {
        rootNode = new DefaultMutableTreeNode("");
        repositoryTree = new RepositoryTree(this);
        repositoryTree.populateTree(rootNode);
        repositoryTree.setRootVisible(false);
        repositoryTree.setShowsRootHandles(true);
        final DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) repositoryTree.getCellRenderer();
        renderer.setLeafIcon(IconsFactory.getImageIcon(ImportBrowser.class, "/org/esa/beam/resources/images/icons/RsBandAsSwath16.gif"));
        renderer.setClosedIcon(IconsFactory.getImageIcon(ImportBrowser.class, "/org/esa/beam/resources/images/icons/RsGroupClosed16.gif"));
        renderer.setOpenIcon(IconsFactory.getImageIcon(ImportBrowser.class, "/org/esa/beam/resources/images/icons/RsGroupOpen16.gif"));
        return repositoryTree;
    }

    private void initHeaderPanel(final JPanel headerBar) {
        headerBar.setLayout(new GridBagLayout());
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        openButton = createToolButton(UIUtils.loadImageIcon("icons/Open24.gif"));
        openButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                performOpenAction();
            }
        });
        headerBar.add(openButton, gbc);

        updateButton = createToolButton(updateIcon);
        updateButton.setActionCommand(updateCommand);
        updateButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                if (e.getActionCommand().equals("stop")) {
                    updateButton.setEnabled(false);
                    mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    repositoryManager.stopUpdateRepository();
                } else {
                    repositoryManager.startUpdateRepository((Repository) repositoryListCombo.getSelectedItem(),
                                                            new ProgressBarProgressMonitor(progressBar, statusLabel),
                                                            uiCallBack);
                }
            }
        });
        headerBar.add(updateButton, gbc);

        headerBar.add(new JLabel("Repository:")); /* I18N */
        gbc.weightx = 99;
        headerBar.add(repositoryListCombo, gbc);
        gbc.weightx = 0;

        JButton _addButton = createToolButton(UIUtils.loadImageIcon("icons/Plus24.gif"));
        _addButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                addRepository();
            }
        });
        headerBar.add(_addButton, gbc);

        removeButton = createToolButton(UIUtils.loadImageIcon("icons/Minus24.gif"));
        removeButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                removeRepository((Repository) repositoryListCombo.getSelectedItem());
            }
        });
        headerBar.add(removeButton, gbc);

        JButton helpButton = createToolButton(UIUtils.loadImageIcon("icons/Help24.gif"));
        setComponentName(helpButton, "helpButton");
        HelpSys.enableHelpOnButton(helpButton, helpId);
        headerBar.add(helpButton, gbc);
    }

    private JButton createToolButton(final ImageIcon icon) {
        final JButton button = (JButton) ToolButtonFactory.createButton(icon,
                                                                        false);
        button.setBackground(headerPanel.getBackground());
        return button;
    }

    private static void addDefaultDataProvider(final RepositoryManager repositoryManager) {
        repositoryManager.addDataProvider(new ProductPropertiesProvider());
        repositoryManager.addDataProvider(new QuicklookProvider(quickLookWidth));
        //repositoryManager.addDataProvider(new WorldMapProvider(false));
    }

    public void ShowRepository(Repository repository) {
        repositoryManager.setRepositoryShown(repository);
        pgConfig.setLastSelectedRepository(repository);
        final RepositoryTableModel tableModel = new RepositoryTableModel(repository);
        sortedModel = new SortingDecorator(tableModel, repositoryTable.getTableHeader());
        repositoryTable.setModel(sortedModel);
        repositoryTable.setColumnModel(tableModel.getColumnModel());
        repositoryManager.startUpdateRepository(repository,
                new ProgressBarProgressMonitor(progressBar, statusLabel),
                uiCallBack);

        final ArrayList<Product> productList = new ArrayList<Product>(repository.getEntryCount());
        for(int i=0; i < repository.getEntryCount(); ++i) {
            final Product prod = repository.getEntry(i).getProduct();
            if(prod != null) {
                productList.add(prod);
            }
        }
        if(worldMapDataModel != null && !productList.isEmpty()) {
            worldMapDataModel.setProducts(productList.toArray(new Product[productList.size()]));
        }
    }
    
    private class RepositoryChangeHandler implements ItemListener {

        public void itemStateChanged(final ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                final Repository repository = (Repository) e.getItem();
                ShowRepository(repository);
            }
        }
    }

    /**
     * A {@link com.bc.ceres.core.ProgressMonitor} which uses a
     * Swing's {@link javax.swing.ProgressMonitor} to display progress.
     */
    private class ProgressBarProgressMonitor implements ProgressMonitor {

        private final JProgressBar progressBar;
        private final JLabel messageLabel;

        private double currentWork;
        private double totalWork;

        private int totalWorkUI;
        private int currentWorkUI;
        private int lastWorkUI;
        private boolean cancelRequested;

        public ProgressBarProgressMonitor(JProgressBar progressBar, JLabel messageLabel) {
            this.progressBar = progressBar;
            this.messageLabel = messageLabel;
        }

        /**
         * Notifies that the main task is beginning.  This must only be called once
         * on a given progress monitor instance.
         *
         * @param name      the name (or description) of the main task
         * @param totalWork the total number of work units into which
         *                  the main task is been subdivided. If the value is <code>UNKNOWN</code>
         *                  the implementation is free to indicate progress in a way which
         *                  doesn't require the total number of work units in advance.
         */
        public void beginTask(String name, int totalWork) {
            Assert.notNull(name, "name");
            currentWork = 0.0;
            this.totalWork = totalWork;
            currentWorkUI = 0;
            lastWorkUI = 0;
            totalWorkUI = totalWork;
            if (messageLabel != null) {
                messageLabel.setText(name);
            }
            cancelRequested = false;
            setDescription(name);
            setVisibility(true);
            progressBar.setMaximum(totalWork);
            toggleUpdateButton(stopCommand);
        }

        /**
         * Notifies that the work is done; that is, either the main task is completed
         * or the user canceled it. This method may be called more than once
         * (implementations should be prepared to handle this case).
         */
        public void done() {
            runInUI(new Runnable() {
                public void run() {
                    if (progressBar != null) {
                        progressBar.setValue(progressBar.getMaximum());
                        setVisibility(false);
                        toggleUpdateButton(updateCommand);
                        updateButton.setEnabled(true);
                        mainPanel.setCursor(Cursor.getDefaultCursor());
                    }
                }
            });
        }

        /**
         * Internal method to handle scaling correctly. This method
         * must not be called by a client. Clients should
         * always use the method </code>worked(int)</code>.
         *
         * @param work the amount of work done
         */
        public void internalWorked(double work) {
            currentWork += work;
            currentWorkUI = (int) (totalWorkUI * currentWork / totalWork);
            if (currentWorkUI > lastWorkUI) {
                runInUI(new Runnable() {
                    public void run() {
                        if (progressBar != null) {
                            int progress = progressBar.getMinimum() + currentWorkUI;
                            progressBar.setValue(progress);
                            setVisibility(true);
                            toggleUpdateButton(stopCommand);
                        }
                        lastWorkUI = currentWorkUI;
                    }
                });
            }
        }

        /**
         * Returns whether cancelation of current operation has been requested.
         * Long-running operations should poll to see if cancelation
         * has been requested.
         *
         * @return <code>true</code> if cancellation has been requested,
         *         and <code>false</code> otherwise
         *
         * @see #setCanceled(boolean)
         */
        public boolean isCanceled() {
            return cancelRequested;
        }

        /**
         * Sets the cancel state to the given value.
         *
         * @param canceled <code>true</code> indicates that cancelation has
         *                 been requested (but not necessarily acknowledged);
         *                 <code>false</code> clears this flag
         *
         * @see #isCanceled()
         */
        public void setCanceled(boolean canceled) {
            cancelRequested = canceled;
            if (canceled) {
                done();
            }
        }

        /**
         * Sets the task name to the given value. This method is used to
         * restore the task label after a nested operation was executed.
         * Normally there is no need for clients to call this method.
         *
         * @param name the name (or description) of the main task
         *
         * @see #beginTask(String, int)
         */
        public void setTaskName(final String name) {
            runInUI(new Runnable() {
                public void run() {
                    if (messageLabel != null) {
                        messageLabel.setText(name);
                    }
                }
            });
        }

        /**
         * Notifies that a subtask of the main task is beginning.
         * Subtasks are optional; the main task might not have subtasks.
         *
         * @param name the name (or description) of the subtask
         */
        public void setSubTaskName(final String name) {
            setVisibility(true);
            messageLabel.setText(name);
            toggleUpdateButton(stopCommand);
        }

        /**
         * Notifies that a given number of work unit of the main task
         * has been completed. Note that this amount represents an
         * installment, as opposed to a cumulative amount of work done
         * to date.
         *
         * @param work the number of work units just completed
         */
        public void worked(int work) {
            internalWorked(work);
        }

        ////////////////////////////////////////////////////////////////////////
        // Stuff to be performed in Swing's event-dispatching thread

        private void runInUI(Runnable task) {
            if (SwingUtilities.isEventDispatchThread()) {
                task.run();
            } else {
                SwingUtilities.invokeLater(task);
            }
        }

        private void setDescription(final String description) {
            statusLabel.setText(description);
        }

        private void setVisibility(final boolean visible) {
            progressPanel.setVisible(visible);
            statusLabel.setVisible(visible);
        }
    }

    private class MyRepositoryManagerListener implements RepositoryManagerListener {

        /**
         * Implementation should handle that a new
         * <code>Repository<code> was added.
         *
         * @param repository the <code>Repository<code> that was added.
         */
        public void repositoryAdded(final Repository repository) {
            repositoryListCombo.insertItemAt(repository, repositoryListCombo.getItemCount());
            repositoryTree.AddRepository(repository);
            pgConfig.setRepositories(repositoryManager.getRepositories());
        }

        /**
         * Implementation should handle that a new
         * <code>Repository<code> was removed.
         *
         * @param repository the <code>Repository<code> that was removed.
         */
        public void repositoryRemoved(final Repository repository) {
            repositoryListCombo.removeItem(repository);
            if (repositoryListCombo.getItemCount() == 0) {
                repositoryTable.setModel(new DefaultTableModel());
                repositoryTable.setColumnModel(new DefaultTableColumnModel());
            }
            repositoryTree.RemoveRepository(repository);
            pgConfig.setRepositories(repositoryManager.getRepositories());
        }
    }


    /**
     * Should be implemented for handling opening {@link org.esa.beam.framework.datamodel.Product} files.
     */
    public static interface ProductOpenHandler {

        /**
         * Implemetation should open the given files as {@link org.esa.beam.framework.datamodel.Product}s.
         *
         * @param productFiles the files to open.
         */
        public void openProducts(File[] productFiles);
    }

    private class UiCallBack implements Callback {

        public void callback() {
            repositoryTable.updateUI();
        }
    }

    private class RepositoryCollector extends SwingWorker<Repository[], Object> {

        private final File baseDir;
        private final boolean doRecursive;
        private final ProgressMonitor pm;


        public RepositoryCollector(final File baseDir, final boolean doRecursive, final ProgressMonitor pm) {
            this.pm = pm;
            this.baseDir = baseDir;
            this.doRecursive = doRecursive;
        }

        @Override
        protected Repository[] doInBackground() throws Exception {
            final ArrayList<File> dirList = new ArrayList<File>();
            dirList.add(baseDir);
            if (doRecursive) {
                final File[] subDirs = collectAllSubDirs(baseDir);
                dirList.addAll(Arrays.asList(subDirs));
            }

            pm.beginTask("Collecting repositories...", dirList.size());
            final ArrayList<Repository> repositoryList = new ArrayList<Repository>();
            try {
                final RepositoryScanner.ProductFileFilter filter = new RepositoryScanner.ProductFileFilter();
                for (File subDir : dirList) {
                    final File[] subDirFiles = subDir.listFiles(filter);
                    if (subDirFiles.length > 0) {
                        final Repository repository = new Repository(subDir, baseDir);
                        repositoryList.add(repository);
                    }

                    if (pm.isCanceled()) {
                        return repositoryList.toArray(new Repository[repositoryList.size()]);
                    }
                    pm.worked(1);
                }
                return repositoryList.toArray(new Repository[repositoryList.size()]);
            } finally {
                pm.done();
            }
        }

        @Override
        public void done() {
            Repository[] repositories;
            try {
                repositories = get();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainPanel, "An internal Error occured:\n" + e.getMessage());
                return;
            }
            if (repositories.length == 0) {
                JOptionPane.showMessageDialog(mainPanel,
                                              "No readable products found in the specified directory: \n"
                                              + "'" + baseDir.getPath() + "'.\n"
                                              + "It is not added to the repository list."); /* I18N */
                return;
            }

            for (Repository repository : repositories) {
                repositoryManager.addRepository(repository);
            }
            if (repositories[0] != null) {
                // triggers also an update of the repository
                repositoryListCombo.setSelectedItem(repositories[0]);
            }
            setUIComponentsEnabled(repositoryManager.getNumRepositories() > 0);
        }

        private File[] collectAllSubDirs(final File dir) {
            final ArrayList<File> dirList = new ArrayList<File>();
            final RepositoryScanner.DirectoryFileFilter dirFilter = new RepositoryScanner.DirectoryFileFilter();

            final File[] subDirs = dir.listFiles(dirFilter);
            for (final File subDir : subDirs) {
                dirList.add(subDir);
                final File[] dirs = collectAllSubDirs(subDir);
                dirList.addAll(Arrays.asList(dirs));
            }
            return dirList.toArray(new File[dirList.size()]);
        }

    }
}
