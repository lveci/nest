package org.esa.nest.dat.toolviews.Projects;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.ui.PopupMenuFactory;
import org.esa.beam.framework.ui.PopupMenuHandler;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.visat.VisatApp;

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
import java.io.File;
import java.util.Enumeration;

/**
 * A tree-view component for Projects
 */
public class ProjectTree extends JTree implements PopupMenuFactory, ActionListener {

    private Object menuContext;
    private DefaultMutableTreeNode selectedNode;

    /**
     * Constructs a new single selection <code>ProductTree</code>.
     */
    public ProjectTree() {
        this(false);
    }

    /**
     * Constructs a new <code>ProductTree</code> with the given selection mode.
     *
     * @param multipleSelect whether or not the tree is multiple selection capable
     */
    public ProjectTree(final boolean multipleSelect) {

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
        TreePath selPath = getPathForLocation(event.getX(), event.getY());
        if (selPath != null) {
            setSelectionPath(selPath);
            selectedNode =
                    (DefaultMutableTreeNode) getLastSelectedPathComponent();
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

        JPopupMenu popup = new JPopupMenu();
        menuContext = context;

        if (context instanceof Project.ProjectSubFolder) {
            createMenuItem(popup, "Create Folder");
            JMenuItem menuItem = createMenuItem(popup, "Remove Folder");
            Project.ProjectSubFolder folder = (Project.ProjectSubFolder) context;
            if (!folder.canBeRemoved())
                menuItem.setEnabled(false);
            if (selectedNode.isRoot()) {
                addSeparator(popup);
                createMenuItem(popup, "Save Project As...");
                addSeparator(popup);
                createMenuItem(popup, "Expand All");
            }
        } else if (context instanceof File) {
            createMenuItem(popup, "Remove Product");
        } else if (context instanceof MetadataElement) {

        } else if (context instanceof RasterDataNode) {

        }

        return popup;
    }

    private JMenuItem createMenuItem(final JPopupMenu popup, final String text) {
        JMenuItem menuItem;
        menuItem = new JMenuItem(text);
        menuItem.addActionListener(this);
        popup.add(menuItem);
        return menuItem;
    }

    private static void addSeparator(JPopupMenu popup) {
        if (popup.getComponentCount() > 1) {
            popup.addSeparator();
        }
    }

    /**
     * Invoked when an action occurs.
     */
    public void actionPerformed(ActionEvent e) {

        if (e.getActionCommand().equals("Create Folder")) {
            Project.ProjectSubFolder subFolder = (Project.ProjectSubFolder) menuContext;
            Project.instance().CreateNewFolder(subFolder);
        } else if (e.getActionCommand().equals("Remove Folder")) {
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
            if (parentNode != null) {
                Object context = parentNode.getUserObject();
                if (context != null) {
                    Project.ProjectSubFolder parentFolder = (Project.ProjectSubFolder) context;
                    Project.ProjectSubFolder subFolder = (Project.ProjectSubFolder) menuContext;
                    Project.instance().DeleteFolder(parentFolder, subFolder);
                }
            }
        } else if (e.getActionCommand().equals("Remove Product")) {
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
            if (parentNode != null) {
                Object context = parentNode.getUserObject();
                if (context != null) {
                    Project.ProjectSubFolder parentFolder = (Project.ProjectSubFolder) context;
                    File file = (File) menuContext;
                    Project.instance().RemoveFile(parentFolder, file);
                }
            }
        } else if (e.getActionCommand().equals("Expand All")) {
            expandAll();
        }
    }

    public void expandAll() {
        TreeNode root = (TreeNode) getModel().getRoot();
        expandAll(this, new TreePath(root), true);
    }

    /**
     * If expand is true, expands all nodes in the tree.
     * Otherwise, collapses all nodes in the tree.
     *
     * @param tree
     * @param parent
     * @param expand
     */
    private static void expandAll(JTree tree, TreePath parent, boolean expand) {
        // Traverse children
        TreeNode node = (TreeNode) parent.getLastPathComponent();
        if (node.getChildCount() >= 0) {
            for (Enumeration e = node.children(); e.hasMoreElements();) {
                TreeNode n = (TreeNode) e.nextElement();
                TreePath path = parent.pathByAddingChild(n);
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
                    TreePath selPath = getPathForLocation(event.getX(), event.getY());
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) selPath.getLastPathComponent();

                    Object o = node.getUserObject();
                    if (o instanceof File) {
                        File file = (File) o;
                        VisatApp.getApp().openProduct(file);
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

        ImageIcon productIcon;
        ImageIcon groupOpenIcon;
        ImageIcon groupClosedIcon;
        ImageIcon bandAsSwathIcon;
        ImageIcon bandAsSwathIconDisabled;
        ImageIcon bandAsGridIcon;
        ImageIcon bandAsGridIconDisabled;
        ImageIcon metadataIcon;
        ImageIcon bandFlagsIcon;
        ImageIcon bandFlagsIconDisabled;
        ImageIcon bandVirtualIcon;
        ImageIcon bandVirtualIconDisabled;

        public PTCellRenderer() {
            productIcon = UIUtils.loadImageIcon("icons/RsProduct16.gif");
            groupOpenIcon = UIUtils.loadImageIcon("icons/RsGroupOpen16.gif");
            groupClosedIcon = UIUtils.loadImageIcon("icons/RsGroupClosed16.gif");
            bandAsSwathIcon = UIUtils.loadImageIcon("icons/RsBandAsSwath16.gif");
            bandAsSwathIconDisabled = UIUtils.loadImageIcon("icons/RsBandAsSwath16Disabled.gif");
            bandAsGridIcon = UIUtils.loadImageIcon("icons/RsBandAsGrid16.gif");
            bandAsGridIconDisabled = UIUtils.loadImageIcon("icons/RsBandAsGrid16Disabled.gif");
            metadataIcon = UIUtils.loadImageIcon("icons/RsMetaData16.gif");
            bandFlagsIcon = UIUtils.loadImageIcon("icons/RsBandFlags16.gif");
            bandFlagsIconDisabled = UIUtils.loadImageIcon("icons/RsBandFlags16Disabled.gif");
            bandVirtualIcon = UIUtils.loadImageIcon("icons/RsBandVirtual16.gif");
            bandVirtualIconDisabled = UIUtils.loadImageIcon("icons/RsBandVirtual16Disabled.gif");
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree,
                                                      Object value,
                                                      boolean sel,
                                                      boolean expanded,
                                                      boolean leaf,
                                                      int row,
                                                      boolean hasFocus) {

            super.getTreeCellRendererComponent(tree,
                    value,
                    sel,
                    expanded,
                    leaf,
                    row,
                    hasFocus);

            DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) value;
            value = treeNode.getUserObject();

            if (value instanceof File) {
                File file = (File) value;
                this.setText(file.getName());
                this.setIcon(productIcon);
            } else if (value instanceof Project.ProjectSubFolder) {
                Project.ProjectSubFolder subFolder = (Project.ProjectSubFolder) value;
                this.setText(subFolder.getName());
                this.setIcon(groupClosedIcon);
            }

            return this;
        }
    }

    public static class TreeTransferHandler extends TransferHandler {

        public boolean canImport(TransferHandler.TransferSupport info) {
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
            JTree tree = (JTree)c;
            TreePath path = tree.getSelectionPath();

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object context = node.getUserObject();
            if (context != null) {
                if(context instanceof File) {
                    File file = (File)context;
                    return new StringSelection(file.getAbsolutePath());
                } else if(context instanceof Project.ProjectSubFolder) {
                    Project.ProjectSubFolder parentFolder = (Project.ProjectSubFolder) context;

                    return new StringSelection(parentFolder.getPath().getAbsolutePath());
                }
            }
            return null;
        }

        /**
         * Perform the actual import.  This demo only supports drag and drop.
         */
        public boolean importData(TransferHandler.TransferSupport info) {
            if (!info.isDrop()) {
                return false;
            }

            JTree tree = (JTree) info.getComponent();
            DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();

            // Get the string that is being dropped.
            Transferable t = info.getTransferable();
            String data;
            try {
                data = (String) t.getTransferData(DataFlavor.stringFlavor);
            }
            catch (Exception e) {
                return false;
            }

            // Wherever there is a newline in the incoming data,
            // break it into a separate item in the list.
            String[] values = data.split("\n");

            // Perform the actual import.
            for (String value : values) {

            }
            return true;
        }
    }

}