package org.esa.nest.dat.dialogs;

import org.esa.beam.framework.ui.PopupMenuFactory;
import org.esa.beam.framework.ui.PopupMenuHandler;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dat.actions.importbrowser.ImportBrowserAction;
import org.esa.nest.dat.toolviews.Projects.Project;
import org.esa.nest.dat.toolviews.Projects.ProjectSubFolder;
import org.esa.nest.dat.toolviews.Projects.ProjectFile;
import org.esa.nest.dat.toolviews.Projects.ProductSet;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.io.File;

/**
 * A tree-view component for Projects
 */
public class SettingsTree extends JTree implements PopupMenuFactory, ActionListener {

    private Object menuContext;
    private DefaultMutableTreeNode selectedNode;
    private TreePath selectedPath;

    /**
     * Constructs a new single selection <code>ProductTree</code>.
     */
    public SettingsTree() {
        this(false);
    }

    /**
     * Constructs a new <code>ProductTree</code> with the given selection mode.
     *
     * @param multipleSelect whether or not the tree is multiple selection capable
     */
    public SettingsTree(final boolean multipleSelect) {

        getSelectionModel().setSelectionMode(multipleSelect
                ? TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
                : TreeSelectionModel.SINGLE_TREE_SELECTION);
        addMouseListener(new PTMouseListener());
        setCellRenderer(new PTCellRenderer());
        setRootVisible(false);
        setShowsRootHandles(true);
        setToggleClickCount(2);
        setExpandsSelectedPaths(true);
        setScrollsOnExpand(true);
        setAutoscrolls(true);
        setDragEnabled(true);
        setDropMode(DropMode.ON);
        setTransferHandler(new TreeTransferHandler());
        putClientProperty("JTree.lineStyle", "Angled");
        ToolTipManager.sharedInstance().registerComponent(this);

        final PopupMenuHandler popupMenuHandler = new PopupMenuHandler(this);
        addMouseListener(popupMenuHandler);
        addKeyListener(popupMenuHandler);
    }

    public JPopupMenu createPopupMenu(final Component component) {
        return null;
    }

    public JPopupMenu createPopupMenu(MouseEvent event) {
        selectedPath = getPathForLocation(event.getX(), event.getY());
        if (selectedPath != null) {
            setSelectionPath(selectedPath);
            selectedNode = (DefaultMutableTreeNode) getLastSelectedPathComponent();
            if (selectedNode != null) {
                Object context = selectedNode.getUserObject();
                if (context != null) {
                    return createPopup(context);
                }
            }
        }
        return null;
    }

    public JPopupMenu createPopup(final Object context) {

        final JPopupMenu popup = new JPopupMenu();
        menuContext = context;

        
        return popup;
    }

    private JMenuItem createMenuItem(final JPopupMenu popup, final String text) {
        final JMenuItem menuItem;
        menuItem = new JMenuItem(text);
        menuItem.addActionListener(this);
        popup.add(menuItem);
        return menuItem;
    }

    private static void addSeparator(JPopupMenu popup) {
        if (popup.getComponentCount() > 0) {
            popup.addSeparator();
        }
    }

    /**
     * Invoked when an action occurs.
     */
    public void actionPerformed(ActionEvent e) {

        final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();


    }

    public void expandAll() {
        final TreeNode root = (TreeNode) getModel().getRoot();
        expandAll(this, new TreePath(root), true);
    }

    /**
     * If expand is true, expands all nodes in the tree.
     * Otherwise, collapses all nodes in the tree.
     *
     * @param tree the tree
     * @param parent the parent path
     * @param expand or collapse
     */
    private static void expandAll(JTree tree, TreePath parent, boolean expand) {
        // Traverse children
        final TreeNode node = (TreeNode) parent.getLastPathComponent();
        if (node.getChildCount() >= 0) {
            for (Enumeration e = node.children(); e.hasMoreElements();) {
                final TreeNode n = (TreeNode) e.nextElement();
                final TreePath path = parent.pathByAddingChild(n);
                expandAll(tree, path, expand);
            }
        }

        // Expansion or collapse must be done bottom-up
        if (expand) {
            tree.expandPath(parent);
        } else {
            tree.collapsePath(parent);
        }
    }


    public void populateTree(DefaultMutableTreeNode treeNode) {
        setModel(new DefaultTreeModel(treeNode));
        expandAll();
    }

    /**
     * Selects the specified object in this tree's model. If the given object has no representation in the tree, the
     * current selection will not be changed.
     *
     * @param toSelect the object whose representation in the tree will be selected.
     */
    public void select(Object toSelect) {
        final TreePath path = findTreePathFor(toSelect);
        if (path != null) {
            setSelectionPath(path);
        }
    }

    private class PTMouseListener extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent event) {
            int selRow = getRowForLocation(event.getX(), event.getY());
            if (selRow >= 0) {
                int clickCount = event.getClickCount();
                if (clickCount > 1) {
                    final TreePath selPath = getPathForLocation(event.getX(), event.getY());
                    final DefaultMutableTreeNode node = (DefaultMutableTreeNode) selPath.getLastPathComponent();
                    final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();

                    final Object o = node.getUserObject();
                    if (o instanceof ProjectFile) {
                        final ProjectFile file = (ProjectFile) o;

                        final ProjectSubFolder parentFolder = (ProjectSubFolder)parentNode.getUserObject();
                        Project.openFile(parentFolder, file.getFile());
                    }
                }
            }
        }
    }

    private TreePath findTreePathFor(final Object o) {
        final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) getModel().getRoot();
        final Enumeration enumeration = rootNode.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) enumeration.nextElement();
            if (treeNode.getUserObject() == o) {
                return new TreePath(treeNode.getPath());
            }
        }
        return null;
    }

    private static class PTCellRenderer extends DefaultTreeCellRenderer {

        private final ImageIcon productIcon;
        private final ImageIcon groupOpenIcon;
        private final ImageIcon projectIcon;

        public PTCellRenderer() {
            productIcon = UIUtils.loadImageIcon("icons/RsProduct16.gif");
            groupOpenIcon = UIUtils.loadImageIcon("icons/RsGroupOpen16.gif");
            projectIcon = UIUtils.loadImageIcon("icons/RsGroupClosed16.gif");

        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree,
                                                      Object value,
                                                      boolean sel,
                                                      boolean expanded,
                                                      boolean leaf,
                                                      int row,
                                                      boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) value;
            value = treeNode.getUserObject();

            if (value instanceof ProjectFile) {
                final ProjectFile file = (ProjectFile) value;
                this.setText(file.getDisplayName());
                this.setIcon(productIcon);
                this.setToolTipText(leaf ? file.getToolTipText() : null);
            } else if (value instanceof ProjectSubFolder) {
                final ProjectSubFolder subFolder = (ProjectSubFolder) value;
                this.setText(subFolder.getName());
                this.setIcon(projectIcon);
                this.setToolTipText(null);
            }

            return this;
        }
    }

    public static class TreeTransferHandler extends TransferHandler {

        public boolean canImport(TransferSupport info) {
            return info.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        public int getSourceActions(JComponent c) {
            return TransferHandler.COPY;
        }

        /**
         * Bundle up the selected items in a single list for export.
         * Each line is separated by a newline.
         */
        protected Transferable createTransferable(JComponent c) {
            final JTree tree = (JTree)c;
            final TreePath path = tree.getSelectionPath();

            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();

            final Object context = node.getUserObject();
            if (context != null) {

            }
            return null;
        }

        /**
         * Perform the actual import.  This only supports drag and drop.
         */
        public boolean importData(TransferSupport info) {
            if (!info.isDrop()) {
                return false;
            }

            final JTree tree = (JTree) info.getComponent();
            final DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();

            // Get the string that is being dropped.
            final Transferable t = info.getTransferable();
            String data;
            try {
                data = (String) t.getTransferData(DataFlavor.stringFlavor);
            }
            catch (Exception e) {
                return false;
            }

            // Wherever there is a newline in the incoming data,
            // break it into a separate item in the list.
            final String[] values = data.split("\n");

            // Perform the actual import.
            for (String value : values) {
                final TreePath dropPath = tree.getDropLocation().getPath();
                final DefaultMutableTreeNode node = (DefaultMutableTreeNode) dropPath.getLastPathComponent();
                final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();

                final Object o = node.getUserObject();

            }
            return true;
        }
    }

}