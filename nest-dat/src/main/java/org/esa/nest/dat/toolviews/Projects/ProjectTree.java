package org.esa.nest.dat.toolviews.Projects;

import org.esa.beam.framework.ui.PopupMenuFactory;
import org.esa.beam.framework.ui.PopupMenuHandler;
import org.esa.beam.framework.ui.UIUtils;

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
public class ProjectTree extends JTree implements PopupMenuFactory, ActionListener {

    private Object menuContext;
    private DefaultMutableTreeNode selectedNode;
    private Project project = Project.instance();

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

        if (context instanceof ProjectSubFolder) {
            createMenuItem(popup, "Create Folder");
            JMenuItem menuItemRename = createMenuItem(popup, "Rename Folder");
            JMenuItem menuItemRemove = createMenuItem(popup, "Remove Folder");
            ProjectSubFolder folder = (ProjectSubFolder) context;
            if (!folder.canBeRemoved()) {
                menuItemRename.setEnabled(false);
                menuItemRemove.setEnabled(false);
            }
            if (selectedNode.isRoot()) {
                addSeparator(popup);
                createMenuItem(popup, "New Project...");
                createMenuItem(popup, "Load Project...");
                createMenuItem(popup, "Save Project As...");
                createMenuItem(popup, "Close Project");
                createMenuItem(popup, "Refresh Project");
                addSeparator(popup);
                createMenuItem(popup, "Expand All");
            }
            if(!folder.isPhysical() && !selectedNode.isRoot()) {
                createMenuItem(popup, "Clear");
            }
            if(folder.getFolderType() == ProjectSubFolder.FolderType.PRODUCTSET) {
                addSeparator(popup);
                createMenuItem(popup, "New ProductSet...");
            } else if(folder.getFolderType() == ProjectSubFolder.FolderType.GRAPH) {
                addSeparator(popup);
                createMenuItem(popup, "New Graph...");
            }
        } else if (context instanceof ProjectFile) {
            createMenuItem(popup, "Open");
            createMenuItem(popup, "Remove");

            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
            ProjectSubFolder folder = (ProjectSubFolder)parentNode.getUserObject();
            if(!folder.isPhysical()) {
                createMenuItem(popup, "Import as DIMAP");
            }
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

        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();

        if (e.getActionCommand().equals("Create Folder")) {
            ProjectSubFolder subFolder = (ProjectSubFolder) menuContext;
            project.createNewFolder(subFolder);
        } else if(e.getActionCommand().equals("Remove Folder")) {
            if (parentNode != null) {
                Object context = parentNode.getUserObject();
                if (context != null) {
                    ProjectSubFolder parentFolder = (ProjectSubFolder) context;
                    ProjectSubFolder subFolder = (ProjectSubFolder) menuContext;
                    project.deleteFolder(parentFolder, subFolder);
                }
            }
        } else if(e.getActionCommand().equals("Rename Folder")) {
            if (parentNode != null) {
                Object context = parentNode.getUserObject();
                if (context != null) {
                    ProjectSubFolder subFolder = (ProjectSubFolder) menuContext;
                    project.renameFolder(subFolder);
                }
            }
        } else if(e.getActionCommand().equals("Remove")) {
            ProjectSubFolder parentFolder = (ProjectSubFolder)parentNode.getUserObject();
            if (parentNode != null && parentFolder != null) {
                ProjectFile file = (ProjectFile) menuContext;
                project.removeFile(parentFolder, file.getFile());
            }
        } else if(e.getActionCommand().equals("Open")) {
            ProjectSubFolder parentFolder = (ProjectSubFolder)parentNode.getUserObject();
            ProjectFile file = (ProjectFile) menuContext;
            Project.openFile(parentFolder, file.getFile());
        } else if(e.getActionCommand().equals("Import as DIMAP")) {
            ProjectSubFolder parentFolder = (ProjectSubFolder)parentNode.getUserObject();
            ProjectFile file = (ProjectFile) menuContext;
            project.importFile(parentFolder, file.getFile());
        } else if(e.getActionCommand().equals("Clear")) {
            ProjectSubFolder subFolder = (ProjectSubFolder) menuContext;
            project.clearFolder(subFolder);
        } else if(e.getActionCommand().equals("Expand All")) {
            expandAll();
        } else if(e.getActionCommand().equals("New Project...")) {
            project.CreateNewProject();
        } else if(e.getActionCommand().equals("Load Project...")) {
            project.LoadProject();
        } else if(e.getActionCommand().equals("Save Project As...")) {
            project.SaveProjectAs();
        } else if(e.getActionCommand().equals("Close Project")) {
            project.CloseProject();
        } else if(e.getActionCommand().equals("Refresh Project")) {
            project.refreshProjectTree();
            project.notifyEvent(true);
        } else if(e.getActionCommand().equals("New ProductSet...")) {
            ProjectSubFolder subFolder = (ProjectSubFolder) menuContext;
            project.createNewProductSet(subFolder);
        } else if(e.getActionCommand().equals("New Graph...")) {
            ProjectSubFolder subFolder = (ProjectSubFolder) menuContext;
            Project.createNewGraph(subFolder);
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
     * @param tree the tree
     * @param parent the parent path
     * @param expand or collapse
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
                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();

                    Object o = node.getUserObject();
                    if (o instanceof ProjectFile) {
                        ProjectFile file = (ProjectFile) o;

                        ProjectSubFolder parentFolder = (ProjectSubFolder)parentNode.getUserObject();
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

        ImageIcon productIcon;
        ImageIcon groupOpenIcon;
        ImageIcon projectIcon;

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

            DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) value;
            value = treeNode.getUserObject();

            if (value instanceof ProjectFile) {
                ProjectFile file = (ProjectFile) value;
                this.setText(file.getDisplayName());
                this.setIcon(productIcon);
            } else if (value instanceof ProjectSubFolder) {
                ProjectSubFolder subFolder = (ProjectSubFolder) value;
                this.setText(subFolder.getName());
                this.setIcon(projectIcon);
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
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();

            Object context = node.getUserObject();
            if (context != null) {
                if(context instanceof ProjectFile) {
                    ProjectSubFolder parentFolder = (ProjectSubFolder)parentNode.getUserObject();
                    ProjectFile file = (ProjectFile)context;

                    if(parentFolder.getFolderType() == ProjectSubFolder.FolderType.PRODUCTSET) {
                        return new StringSelection(ProductSet.GetListAsString(file.getFile()));    
                    } else {
                        return new StringSelection(file.getFile().getAbsolutePath());
                    }
                } else if(context instanceof ProjectSubFolder) {
                    ProjectSubFolder parentFolder = (ProjectSubFolder) context;

                    return new StringSelection(parentFolder.getPath().getAbsolutePath());
                }
            }
            return null;
        }

        /**
         * Perform the actual import.  This only supports drag and drop.
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
                TreePath dropPath = tree.getDropLocation().getPath();
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) dropPath.getLastPathComponent();
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();

                Object o = node.getUserObject();
                if (o instanceof ProjectFile) {
                    ProjectFile projFile = (ProjectFile)o;
                    ProjectSubFolder projSubFolder = (ProjectSubFolder)parentNode.getUserObject();
                    if(projSubFolder.getFolderType() == ProjectSubFolder.FolderType.PRODUCTSET) {
                        ProductSet.AddProduct(projFile.getFile(), new File(value));
                    }
                }
            }
            return true;
        }
    }

}