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
package org.esa.nest.dat.toolviews.productlibrary;

import com.jidesoft.swing.JideSplitPane;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductManager;
import org.esa.beam.framework.help.HelpSys;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.framework.ui.application.support.AbstractToolView;
import org.esa.beam.framework.ui.tool.ToolButtonFactory;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dat.DatContext;
import org.esa.nest.dat.dialogs.BatchGraphDialog;
import org.esa.nest.dat.toolviews.Projects.Project;
import org.esa.nest.dat.toolviews.productlibrary.model.ProductEntryTableModel;
import org.esa.nest.dat.toolviews.productlibrary.model.ProductLibraryConfig;
import org.esa.nest.dat.toolviews.productlibrary.model.SortingDecorator;
import org.esa.nest.db.DBQuery;
import org.esa.nest.db.DBScanner;
import org.esa.nest.db.ProductEntry;
import org.esa.nest.util.ResourceUtils;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;

public class ProductLibraryToolView extends AbstractToolView {

    private static final ImageIcon updateIcon = UIUtils.loadImageIcon("icons/Update24.gif");
    private static final ImageIcon updateRolloverIcon = ToolButtonFactory.createRolloverIcon(updateIcon);
    private static final ImageIcon stopIcon = UIUtils.loadImageIcon("icons/Stop24.gif");
    private static final ImageIcon stopRolloverIcon = ToolButtonFactory.createRolloverIcon(stopIcon);

    private JPanel mainPanel;
    private JComboBox repositoryListCombo;
    private JTable productEntryTable;
    private SortingDecorator sortedModel;

    private JLabel statusLabel;
    private JPanel progressPanel;
    private JButton addToProjectButton;
    private JButton openAllSelectedButton;
    private JButton batchProcessButton;
    private JButton addButton;
    private JButton removeButton;
    private JButton updateButton;

    private LabelBarProgressMonitor progMon;
    private JProgressBar progressBar;
    private File currentDirectory;
    private ProductOpenHandler openHandler;
    private ProductLibraryConfig libConfig;
    private final String helpId = "ProductLibrary";

    private WorldMapUI worldMapUI = null;

    //private RepositoryTree repositoryTree = null;
    private DefaultMutableTreeNode rootNode = null;

    private DatabasePane dbPane;

    public ProductLibraryToolView() {
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

    public JComponent createControl() {

        libConfig = new ProductLibraryConfig(VisatApp.getApp().getPreferences());
        setProductOpenHandler(new MyProductOpenHandler(VisatApp.getApp()));

        initUI();
        mainPanel.addComponentListener(new ComponentAdapter() {

            @Override
            public void componentHidden(final ComponentEvent e) {
                if(progMon != null)
                    progMon.setCanceled(true);
            }
        });
        applyConfig(libConfig);
        mainPanel.addComponentListener(new ComponentAdapter() {

            @Override
            public void componentMoved(final ComponentEvent e) {
                libConfig.setWindowBounds(e.getComponent().getBounds());
            }

            @Override
            public void componentResized(final ComponentEvent e) {
                libConfig.setWindowBounds(e.getComponent().getBounds());
            }
        });
        setUIComponentsEnabled(repositoryListCombo.getItemCount() > 1);

        return mainPanel;
    }

    private void applyConfig(final ProductLibraryConfig config) {
        final File[] baseDirList = config.getBaseDirs();
        repositoryListCombo.insertItemAt(DBQuery.ALL_FOLDERS, 0);
        for(File f : baseDirList) {
            repositoryListCombo.insertItemAt(f, repositoryListCombo.getItemCount());
        }
        if(baseDirList.length > 0)
            repositoryListCombo.setSelectedIndex(0);
    }

    private void performSelectAction() {
        updateStatusLabel();
        final ProductEntry[] selections = getSelectedProductEntries();
        setOpenProductButtonsEnabled(selections.length > 0);

        worldMapUI.setSelectedProductEntryList(selections);
    }

    private void performOpenAction() {
        if (openHandler != null) {
            openHandler.openProducts(getSelectedFiles());
        }
    }

    private ProductEntry[] getSelectedProductEntries() {
        final int[] selectedRows = productEntryTable.getSelectedRows();
        final ProductEntry[] selectedEntries = new ProductEntry[selectedRows.length];
        for (int i = 0; i < selectedRows.length; i++) {
            final Object entry = productEntryTable.getValueAt(selectedRows[i], 0);
            if(entry instanceof ProductEntry) {
                selectedEntries[i] = (ProductEntry)entry;
            }
        }
        return selectedEntries;
    }

    private File[] getSelectedFiles() {
        final int[] selectedRows = productEntryTable.getSelectedRows();
        final File[] selectedFiles = new File[selectedRows.length];
        for (int i = 0; i < selectedRows.length; i++) {
            final Object entry = productEntryTable.getValueAt(selectedRows[i], 0);
            if(entry instanceof ProductEntry) {
                selectedFiles[i] = ((ProductEntry)entry).getFile();
            }
        }
        return selectedFiles;
    }

    private JPopupMenu createPopup() {
        final File graphPath = ResourceUtils.getGraphFolder("");

        final JPopupMenu popup = new JPopupMenu();
        if(graphPath.exists()) {
            createGraphMenu(popup, graphPath);
        }
        return popup;
    }

    private void createGraphMenu(final JPopupMenu menu, final File path) {
        final File[] filesList = path.listFiles();
        if(filesList == null || filesList.length == 0) return;

        for (final File file : filesList) {
            final String name = file.getName();
            if(file.isDirectory() && !file.isHidden() && !name.equalsIgnoreCase("internal")) {
                final JMenu subMenu = new JMenu(name);
                menu.add(subMenu);
                createGraphMenu(subMenu, file);
            } else if(name.toLowerCase().endsWith(".xml")) {
                final JMenuItem item = new JMenuItem(name.substring(0, name.indexOf(".xml")));
                item.addActionListener(new ActionListener() {

                    public void actionPerformed(final ActionEvent e) {
                        if(batchProcessButton.isEnabled())
                            batchProcess(getSelectedProductEntries(), file);
                    }
                });
                menu.add(item);
            }
        }
    }

    private void createGraphMenu(final JMenu menu, final File path) {
        final File[] filesList = path.listFiles();
        if(filesList == null || filesList.length == 0) return;

        for (final File file : filesList) {
            final String name = file.getName();
            if(file.isDirectory() && !file.isHidden() && !name.equalsIgnoreCase("internal")) {
                final JMenu subMenu = new JMenu(name);
                menu.add(subMenu);
                createGraphMenu(subMenu, file);
            } else if(name.toLowerCase().endsWith(".xml")) {
                final JMenuItem item = new JMenuItem(name.substring(0, name.indexOf(".xml")));
                item.addActionListener(new ActionListener() {

                    public void actionPerformed(final ActionEvent e) {
                        if(batchProcessButton.isEnabled())
                            batchProcess(getSelectedProductEntries(), file);
                    }
                });
                menu.add(item);
            }
        }
    }

    private static void batchProcess(final ProductEntry[] productEntryList, final File graphFile) {
        final BatchGraphDialog batchDlg = new BatchGraphDialog(new DatContext(""),
                "Batch Processing", "batchProcessing", false);
        batchDlg.setInputFiles(productEntryList);
        if(graphFile != null) {
            batchDlg.LoadGraphFile(graphFile);
        }
        batchDlg.show();
    }

    private void addRepository() {
        final File baseDir = promptForRepositoryBaseDir();
        if (baseDir == null) {
            return;
        }

        final int answer = JOptionPane.showOptionDialog(mainPanel,
                                                        "Search directory recursively?", "Add Repository",
                                                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                                                        null, null);
        boolean doRecursive = false;
        if (answer == JOptionPane.YES_OPTION) {
            doRecursive = true;
        }

        libConfig.addBaseDir(baseDir);
        final int index = repositoryListCombo.getItemCount();
        repositoryListCombo.insertItemAt(baseDir, index);
        setUIComponentsEnabled(repositoryListCombo.getItemCount() > 1);

        updateRepostitory(baseDir, doRecursive);
    }

    private void updateRepostitory(final File baseDir, final boolean doRecursive) {
        if(baseDir == null) return;
        progMon = new LabelBarProgressMonitor(progressBar, statusLabel);
        progMon.addListener(new MyProgressBarListener());
        final DBScanner repositoryCollector = new DBScanner(dbPane.getDB(), baseDir, doRecursive, true, progMon);
        repositoryCollector.addListener(new MyDatabaseScannerListener());
        repositoryCollector.execute();
    }

    private void removeRepository() {

        final Object selectedItem = repositoryListCombo.getSelectedItem();
        final int index = repositoryListCombo.getSelectedIndex();
        if(index == 0) {
            final int status=VisatApp.getApp().showQuestionDialog("This will remove all folders and products from the database.\n" +
                    "Are you sure you wish to continue?", null);
            if (status == JOptionPane.NO_OPTION)
                return;
            while(repositoryListCombo.getItemCount() > 1) {
                final File baseDir = (File)repositoryListCombo.getItemAt(1);
                libConfig.removeBaseDir(baseDir);
                repositoryListCombo.removeItemAt(1);
                dbPane.removeProducts(baseDir);
            }
            try {
                dbPane.getDB().removeAllProducts();
            } catch(Exception e) {
                //
            }
        } else if(selectedItem instanceof File) {
            final File baseDir = (File)selectedItem;
            final int status=VisatApp.getApp().showQuestionDialog("This will remove all products within " +
                    baseDir.getAbsolutePath()+" from the database\n" +
                    "Are you sure you wish to continue?", null);
            if (status == JOptionPane.NO_OPTION)
                return;
            libConfig.removeBaseDir(baseDir);
            repositoryListCombo.removeItemAt(index);
            dbPane.removeProducts(baseDir);
        }
        setUIComponentsEnabled(repositoryListCombo.getItemCount() > 1);
        UpdateUI();
    }

    private void setUIComponentsEnabled(final boolean enable) {
        removeButton.setEnabled(enable);
        updateButton.setEnabled(enable);
        repositoryListCombo.setEnabled(enable);
    }

    private void setOpenProductButtonsEnabled(final boolean enable) {
        addToProjectButton.setEnabled(enable);
        openAllSelectedButton.setEnabled(enable);
        batchProcessButton.setEnabled(enable);
    }

    private void toggleUpdateButton(final String command) {
        if (command.equals(LabelBarProgressMonitor.stopCommand)) {
            updateButton.setIcon(stopIcon);
            updateButton.setRolloverIcon(stopRolloverIcon);
            updateButton.setActionCommand(LabelBarProgressMonitor.stopCommand);
            addButton.setEnabled(false);
            removeButton.setEnabled(false);
        } else {
            updateButton.setIcon(updateIcon);
            updateButton.setRolloverIcon(updateRolloverIcon);
            updateButton.setActionCommand(LabelBarProgressMonitor.updateCommand);
            addButton.setEnabled(true);
            removeButton.setEnabled(true);
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
        final JPanel northPanel = new JPanel(new BorderLayout(4, 4));
        northPanel.add(createHeaderPanel(), BorderLayout.CENTER);

        addToProjectButton = new JButton();
        setComponentName(addToProjectButton, "addToProject");
        addToProjectButton.setText("Import to Project");
        addToProjectButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                Project.instance().ImportFileList(getSelectedFiles());
            }
        });

        openAllSelectedButton = new JButton();
        setComponentName(openAllSelectedButton, "openAllSelectedButton");
        openAllSelectedButton.setText("Open Selected");
        openAllSelectedButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                performOpenAction();
            }
        });

        batchProcessButton = new JButton();
        setComponentName(batchProcessButton, "batchProcessButton");
        batchProcessButton.setText("Batch Process");
        batchProcessButton.setToolTipText("Right click to select a graph");
        batchProcessButton.setComponentPopupMenu(createPopup());
        batchProcessButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                batchProcess(getSelectedProductEntries(), null);
            }
        });

        final JPanel openPanel = new JPanel(new BorderLayout(4, 4));
        openPanel.add(addToProjectButton, BorderLayout.WEST);
        openPanel.add(openAllSelectedButton, BorderLayout.CENTER);
        openPanel.add(batchProcessButton, BorderLayout.EAST);

        final JPanel southPanel = new JPanel(new BorderLayout(4, 4));
        statusLabel = new JLabel("");
        southPanel.add(statusLabel, BorderLayout.CENTER);
        southPanel.add(openPanel, BorderLayout.WEST);

        progressBar = new JProgressBar();
        setComponentName(progressBar, "progressBar");
        progressBar.setStringPainted(true);
        progressPanel = new JPanel();
        progressPanel.setLayout(new BorderLayout());
        progressPanel.add(progressBar);
        progressPanel.setVisible(false);
        southPanel.add(progressPanel, BorderLayout.EAST);

        mainPanel = new JPanel(new BorderLayout(4, 4));
        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(createCentrePanel(), BorderLayout.CENTER);
        mainPanel.add(southPanel, BorderLayout.SOUTH);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    }

    private JPanel createCentrePanel() {
        final JideSplitPane splitPane1H = new JideSplitPane(JideSplitPane.HORIZONTAL_SPLIT);
        final MyDatabaseQueryListener dbQueryListener = new MyDatabaseQueryListener();
        dbPane = new DatabasePane();
        dbPane.addListener(dbQueryListener);
        splitPane1H.add(new JScrollPane(dbPane));

        productEntryTable = new JTable();
        productEntryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        productEntryTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        productEntryTable.addMouseListener(new MouseAdapter() {

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
        splitPane1H.add(new JScrollPane(productEntryTable));

        final JideSplitPane splitPane1V = new JideSplitPane(JideSplitPane.VERTICAL_SPLIT);
        splitPane1V.add(splitPane1H);

        worldMapUI = new WorldMapUI();
        worldMapUI.addListener(dbQueryListener);
        splitPane1V.add(worldMapUI.getWorlMapPane());

        return splitPane1V;
    }

    private void setComponentName(JComponent button, String name) {
        button.setName(getClass().getName() + name);
    }

   /* JComponent createRepositoryTreeControl() {
        final JScrollPane prjScrollPane = new JideScrollPane(createTree());
        prjScrollPane.setPreferredSize(new Dimension(320, 480));
        prjScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        prjScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        return prjScrollPane;
    }*/

    public void UpdateUI() {
        dbPane.refresh();
        productEntryTable.updateUI();
        //updateWorldMap();
    }

  /*  private RepositoryTree createTree() {
        rootNode = new DefaultMutableTreeNode("");
        repositoryTree = new RepositoryTree(this);
        repositoryTree.populateTree(rootNode);
        repositoryTree.setRootVisible(false);
        repositoryTree.setShowsRootHandles(true);
        final DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) repositoryTree.getCellRenderer();
        renderer.setLeafIcon(IconsFactory.getImageIcon(ProductLibraryUI.class, "/org/esa/beam/resources/images/icons/RsBandAsSwath16.gif"));
        renderer.setClosedIcon(IconsFactory.getImageIcon(ProductLibraryUI.class, "/org/esa/beam/resources/images/icons/RsGroupClosed16.gif"));
        renderer.setOpenIcon(IconsFactory.getImageIcon(ProductLibraryUI.class, "/org/esa/beam/resources/images/icons/RsGroupOpen16.gif"));
        return repositoryTree;
    }    */

    private JPanel createHeaderPanel() {
        final JPanel headerBar = new JPanel();
        headerBar.setLayout(new GridBagLayout());
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        updateButton = createToolButton("updateButton", updateIcon);
        updateButton.setActionCommand(LabelBarProgressMonitor.updateCommand);
        updateButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                if (e.getActionCommand().equals("stop")) {
                    updateButton.setEnabled(false);
                    mainPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    progMon.setCanceled(true);
                } else {
                    if(repositoryListCombo.getSelectedIndex() != 0) {
                        updateRepostitory((File)repositoryListCombo.getSelectedItem(), true);
                    } else {
                        final File[] baseDirList = libConfig.getBaseDirs();
                        for(File f : baseDirList) {
                             updateRepostitory(f, true);
                        }
                    }
                }
            }
        });
        headerBar.add(updateButton, gbc);

        headerBar.add(new JLabel("Repository:")); /* I18N */
        gbc.weightx = 99;
        repositoryListCombo = new JComboBox();
        setComponentName(repositoryListCombo, "repositoryListCombo");
        repositoryListCombo.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent event) {
                if(event.getStateChange() == ItemEvent.SELECTED) {
                    final Object selectedItem = repositoryListCombo.getSelectedItem();
                    if(selectedItem instanceof File) {
                        dbPane.setBaseDir((File)selectedItem);
                    } else {
                        dbPane.setBaseDir(null);
                    }
                }
            }
        });
        headerBar.add(repositoryListCombo, gbc);
        gbc.weightx = 0;

        addButton = createToolButton("addButton", UIUtils.loadImageIcon("icons/Plus24.gif"));
        addButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                addRepository();
            }
        });
        headerBar.add(addButton, gbc);

        removeButton = createToolButton("removeButton", UIUtils.loadImageIcon("icons/Minus24.gif"));
        removeButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                removeRepository();
            }
        });
        headerBar.add(removeButton, gbc);

        final JButton helpButton = createToolButton("helpButton", UIUtils.loadImageIcon("icons/Help24.gif"));
        helpButton.setVisible(false);
        HelpSys.enableHelpOnButton(helpButton, helpId);
        headerBar.add(helpButton, gbc);

        return headerBar;
    }

    private JButton createToolButton(final String name, final ImageIcon icon) {
        final JButton button = (JButton) ToolButtonFactory.createButton(icon, false);
        setComponentName(button, name);
        //button.setBackground(mainPanel.getBackground());
        return button;
    }

    private void updateStatusLabel() {
        String selectedText = "";
        final int selecteRows = productEntryTable.getSelectedRowCount();
        
        setOpenProductButtonsEnabled(selecteRows > 0);
        if(selecteRows > 0)
            selectedText = ", "+selecteRows+" Selected";
        statusLabel.setText(productEntryTable.getRowCount() + " Products"+ selectedText);
    }

    public void ShowRepository(final ProductEntry[] productEntryList) {
        final ProductEntryTableModel tableModel = new ProductEntryTableModel(productEntryList);
        sortedModel = new SortingDecorator(tableModel, productEntryTable.getTableHeader());
        productEntryTable.setModel(sortedModel);
        productEntryTable.setColumnModel(tableModel.getColumnModel());
        updateStatusLabel();
        worldMapUI.setProductEntryList(productEntryList);
        worldMapUI.setSelectedProductEntryList(null);
    }

    private class MyDatabaseQueryListener implements DatabaseQueryListener {

        public void notifyNewProductEntryListAvailable() {
            ShowRepository(dbPane.getProductEntryList());
        }

        public void notifyNewMapSelectionAvailable() {
            dbPane.setSelectionRect(worldMapUI.getSelectionBox());
        }
    }

    private class MyDatabaseScannerListener implements DBScanner.DBScannerListener {

        public void notifyMSG(MSG msg) {
            UpdateUI();
        }
    }

    private class MyProgressBarListener implements LabelBarProgressMonitor.ProgressBarListener {
        public void notifyStart() {
            progressPanel.setVisible(true);
            toggleUpdateButton(LabelBarProgressMonitor.stopCommand);
        }

        public void notifyDone() {
            progressPanel.setVisible(false);
            toggleUpdateButton(LabelBarProgressMonitor.updateCommand);
            updateButton.setEnabled(true);
            mainPanel.setCursor(Cursor.getDefaultCursor());
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

    private static class MyProductOpenHandler implements ProductOpenHandler {

        private final VisatApp visatApp;

        public MyProductOpenHandler(final VisatApp visatApp) {
            this.visatApp = visatApp;
        }

        public void openProducts(final File[] productFiles) {
            for (File productFile : productFiles) {
                if (isProductOpen(productFile)) {
                    continue;
                }
                try {
                    final Product product = ProductIO.readProduct(productFile);

                    final ProductManager productManager = visatApp.getProductManager();
                    productManager.addProduct(product);
                } catch (IOException e) {
                    visatApp.showErrorDialog("Not able to open product:\n" +
                            productFile.getPath());
                }
            }
        }

        private boolean isProductOpen(final File productFile) {
            final Product openedProduct = visatApp.getOpenProduct(productFile);
            if (openedProduct != null) {
                visatApp.showInfoDialog("Product '" + openedProduct.getName() + "' is already opened.", null);
                return true;
            }
            return false;
        }
    }

}