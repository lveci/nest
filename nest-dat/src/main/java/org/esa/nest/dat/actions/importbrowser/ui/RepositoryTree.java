package org.esa.nest.dat.actions.importbrowser.ui;

import org.esa.beam.framework.ui.PopupMenuFactory;
import org.esa.beam.framework.ui.PopupMenuHandler;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.nest.dat.actions.importbrowser.model.Repository;
import org.esa.nest.dat.actions.importbrowser.model.RepositoryManager;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Enumeration;

/**
 * A tree-view component for Repositories
 */
public class RepositoryTree extends JTree implements PopupMenuFactory, ActionListener {

    private Object menuContext;
    private DefaultMutableTreeNode selectedNode;
    private ImportBrowser importBrowser;

    /**
     * Constructs a new single selection <code>ProductTree</code>.
     * @param browser the ImportBrowser
     */
    public RepositoryTree(ImportBrowser browser) {
        this(false);
        importBrowser = browser;
    }

    /**
     * Constructs a new <code>ProductTree</code> with the given selection mode.
     *
     * @param multipleSelect whether or not the tree is multiple selection capable
     */
    public RepositoryTree(final boolean multipleSelect) {

        getSelectionModel().setSelectionMode(multipleSelect
                ? TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
                : TreeSelectionModel.SINGLE_TREE_SELECTION);
        addMouseListener(new PTMouseListener());
        setCellRenderer(new PTCellRenderer());
        setRootVisible(true);
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

    public void RemoveRepository(final Repository repository) {
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();

        Repository[] allRepositories = importBrowser.getRepositoryManager().getRepositories();
        if(allRepositories.length == 0) {
            root.removeAllChildren();
            importBrowser.getRepositoryManager().stopUpdateRepository();
        } else {
            DefaultMutableTreeNode nodeToRemove = findTreeNode(root, repository);
            removeNode(nodeToRemove);
        }

        populateTree(root);
    }

    private void removeNode(DefaultMutableTreeNode nodeToRemove) {
        if(nodeToRemove == null)
            return;
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) nodeToRemove.getParent();
        if(parentNode == null)
            return;
        parentNode.remove(nodeToRemove);

        if(parentNode.isLeaf()) {
            if(parentNode.getUserObject() instanceof RepoTreeNode) {
                final RepoTreeNode repoTreeNode = (RepoTreeNode)parentNode.getUserObject();
                if(!IsNodeARepository(repoTreeNode)) {
                    removeNode(parentNode);
                }
            }
        }
    }

    private boolean IsNodeARepository(RepoTreeNode node) {
        Repository[] allRepositories = importBrowser.getRepositoryManager().getRepositories();

        for(Repository rep : allRepositories) {
            if(rep.getBaseDir().equals(node.getBaseDir())) {
                return true;
            }
        }
        return false;
    }

    private static DefaultMutableTreeNode findTreeNode(DefaultMutableTreeNode parentNode, final Repository repository) {

        String baseStr = repository.getBaseDir().toString();
        final Enumeration enumeration = parentNode.children();
        while (enumeration.hasMoreElements()) {
            final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) enumeration.nextElement();
            RepoTreeNode repoTreeNode = (RepoTreeNode)treeNode.getUserObject();
            String treeNodeStr = repoTreeNode.getBaseDir().toString();
            if (baseStr.startsWith(treeNodeStr)) {
                if(baseStr.equals(treeNodeStr))
                    return treeNode;
                else
                    return findTreeNode(treeNode, repository);
            }
        }
        return null;
    }

    public void AddRepository(final Repository repository) {
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();

        File recursionBaseDir = repository.getRecursionBaseDir();
        RepoTreeNode repoTreeNode = new RepoTreeNode(recursionBaseDir, recursionBaseDir.toString());
        DefaultMutableTreeNode rootNode = findNode(root, repoTreeNode);
        if(rootNode == null) {
            rootNode = new DefaultMutableTreeNode(repoTreeNode);

            root.add(rootNode);
        }

        //if(repository.getBaseDir().equals(recursionBaseDir))
        //    return;

        String name = repository.getBaseDir().toString().substring(
                recursionBaseDir.toString().length());

        if(!name.isEmpty())
            AddRepository(rootNode, recursionBaseDir, name);

        populateTree(root);
    }

    private static void AddRepository(final DefaultMutableTreeNode rootNode, final File parentFolder, String name) {

        if(name.startsWith(File.separator))
            name = name.substring(name.indexOf(File.separator)+1, name.length());
        int i = name.indexOf(File.separator);

        if(i < 0) {
            File nodeFolder = new File(parentFolder, name);
            RepoTreeNode repoTreeNode = new RepoTreeNode(nodeFolder, name);
            DefaultMutableTreeNode node = findNode(rootNode, repoTreeNode);
            if(node == null) {
                node = new DefaultMutableTreeNode(repoTreeNode);

                rootNode.add(node);
            }
        } else {
            String nodeName = name.substring(0, i);

            File nodeFolder = new File(parentFolder, nodeName);
            RepoTreeNode repoTreeNode = new RepoTreeNode(nodeFolder, nodeName);
            DefaultMutableTreeNode node = findNode(rootNode, repoTreeNode);
            if(node == null) {
                node = new DefaultMutableTreeNode(repoTreeNode);

                rootNode.add(node);
            }

            String subName = name.substring(i, name.length());
            AddRepository(node, nodeFolder, subName);
        }
    }

    private static DefaultMutableTreeNode findNode(final DefaultMutableTreeNode parentNode, final RepoTreeNode r) {
        final Enumeration enumeration = parentNode.children();
        while (enumeration.hasMoreElements()) {
            final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) enumeration.nextElement();
            RepoTreeNode repoTreeNode = (RepoTreeNode)treeNode.getUserObject();
            if (repoTreeNode.getBaseDir().equals(r.getBaseDir())) {
                return treeNode;
            }
        }
        return null;
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

        //if (selectedNode.isRoot()) {
        //}
        
        createMenuItem(popup, "Select All");
        createMenuItem(popup, "Unselect All");
        addSeparator(popup);
        createMenuItem(popup, "Remove All");

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
        RepoTreeNode repoTreeNode = (RepoTreeNode) menuContext;

        if(e.getActionCommand().equals("Select All")) {
            selectRepositories(repoTreeNode, true);
        } else if(e.getActionCommand().equals("Unselect All")) {
            selectRepositories(repoTreeNode, false);
        } else if(e.getActionCommand().equals("Remove All")) {
            removeRepositories(repoTreeNode);
        }
    }

    private void selectRepositories(RepoTreeNode repoNode, boolean flag) {
        Repository[] allRepositories = importBrowser.getRepositoryManager().getRepositories();

        String prefix = repoNode.getBaseDir().toString();
        for(Repository rep : allRepositories) {
            if(rep.getBaseDir().toString().startsWith(prefix)) {
                for(int i=0; i < rep.getEntryCount(); ++i) {
                    rep.getEntry(i).setSelected(flag);
                }
            }
        }
        importBrowser.UpdateUI();
    }

    private void removeRepositories(RepoTreeNode repoNode) {
        RepositoryManager repMan = importBrowser.getRepositoryManager();
        Repository[] allRepositories = repMan.getRepositories();

        String prefix = repoNode.getBaseDir().toString();
        for(Repository rep : allRepositories) {
            if(rep.getBaseDir().toString().startsWith(prefix)) {
                repMan.removeRepository(rep);
            }
        }
        importBrowser.UpdateUI();
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
                TreePath selPath = getPathForLocation(event.getX(), event.getY());
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) selPath.getLastPathComponent();
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();

                Object o = node.getUserObject();
                if (o instanceof RepoTreeNode) {
                    RepoTreeNode repoNode = (RepoTreeNode) o;

                    openSubFolders(repoNode);
                }
            }
        }
    }

    private void openSubFolders(RepoTreeNode repoNode) {

        final Repository[] allRepositories = importBrowser.getRepositoryManager().getRepositories();

        final Repository totalRepo = new Repository(repoNode.getBaseDir(), repoNode.getBaseDir());
        totalRepo.setDataProviders(importBrowser.getRepositoryManager().getDataProviders());
        final String prefix = repoNode.getBaseDir().toString();
        for(Repository rep : allRepositories) {
            if(rep.getBaseDir().toString().startsWith(prefix)) {
                for(int i=0; i < rep.getEntryCount(); ++i) {
                    totalRepo.addEntry(rep.getEntry(i));
                }
            }
        }

        importBrowser.ShowRepository(totalRepo);
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

            if (value instanceof RepoTreeNode) {
                RepoTreeNode repoNode = (RepoTreeNode) value;
                this.setText(repoNode.getName());
                //this.setIcon(productIcon);
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
            JTree tree = (JTree)c;
            TreePath path = tree.getSelectionPath();

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();

            Object context = node.getUserObject();
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
                
            }
            return true;
        }
    }

    private static class RepoTreeNode {
        private File _baseDir;
        private String _name;

        RepoTreeNode(File baseDir, String name) {
            _baseDir = baseDir;
            _name = name;
        }

        public String getName() {
            return _name;
        }

        public File getBaseDir() {
            return _baseDir;
        }
    }
}