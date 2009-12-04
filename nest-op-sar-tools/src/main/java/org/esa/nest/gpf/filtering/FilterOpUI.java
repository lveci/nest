package org.esa.nest.gpf.filtering;

import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.util.DialogUtils;
import org.esa.nest.gpf.OperatorUIUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.Enumeration;
import java.util.Map;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Feb 12, 2008
 * Time: 1:52:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class FilterOpUI extends BaseOperatorUI {

    private final JList bandList = new JList();

    private JTree tree = null;
    private DefaultMutableTreeNode root = null;
    private final JLabel filterLabel = new JLabel("Filters:");
    private final JLabel kernelFileLabel = new JLabel("User Defined Kernel File:");
    private final JTextField kernelFile = new JTextField("");
    protected final JButton kernelFileBrowseButton = new JButton("...");

    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        JComponent panel = createPanel();
        initParameters();

        kernelFile.setColumns(30);
        kernelFileBrowseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final File file = VisatApp.getApp().showFileOpenDialog("User Defined Kernel File", false, null);
                kernelFile.setText(file.getAbsolutePath());
            }
        });

        return panel;
    }

    public void initParameters() {

        OperatorUIUtils.initBandList(bandList, getBandNames());

        String filterName = (String)paramMap.get("selectedFilterName");
        if(filterName != null) {
            setSelectedFilter(filterName);    
        }

        File kFile = (File)paramMap.get("userDefinedKernelFile");
        if(kFile != null) {
            kernelFile.setText(kFile.getAbsolutePath());
        }
    }

    public UIValidation validateParameters() {

        return new UIValidation(getSelectedFilter(tree) != null, "Filter not selected");
    }

    public void updateParameters() {

        OperatorUIUtils.updateBandList(bandList, paramMap);

        FilterOperator.Filter filter = getSelectedFilter(tree);
        if(filter != null) {
            paramMap.put("selectedFilterName", filter.toString());
        }
        
        final String kernelFileStr = kernelFile.getText();
        if(!kernelFileStr.isEmpty()) {
            paramMap.put("userDefinedKernelFile", new File(kernelFileStr));
        }
    }

    private static DefaultMutableTreeNode findItem(DefaultMutableTreeNode parentItem, String filterName) {
        if(!parentItem.isLeaf()) {
            final Enumeration enumeration = parentItem.children();
            while (enumeration.hasMoreElements()) {
                final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) enumeration.nextElement();
                DefaultMutableTreeNode found = findItem(treeNode, filterName);
                if (found != null)
                    return found;
            }
        }

        if(parentItem.toString().equals(filterName))
                return parentItem;
        return null;
    }

    private JComponent createPanel() {
        tree = createTree();
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        final JScrollPane treeView = new JScrollPane(tree);

        final JPanel contentPane = new JPanel(new BorderLayout(4, 4));
        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints _gbc = GridBagUtils.createDefaultConstraints();
        _gbc.fill = GridBagConstraints.HORIZONTAL;
        _gbc.anchor = GridBagConstraints.NORTHWEST;
        _gbc.insets.top = 2;
        _gbc.insets.bottom = 2;

        _gbc.gridx = 0;
        _gbc.gridy = 0;
        contentPane.add(new JLabel("Source Bands:"), _gbc);
        _gbc.fill = GridBagConstraints.BOTH;
        _gbc.gridx = 1;
        contentPane.add(new JScrollPane(bandList), _gbc);
        _gbc.fill = GridBagConstraints.HORIZONTAL;

        _gbc.gridy++;
        DialogUtils.addComponent(contentPane, _gbc, filterLabel, treeView);

        _gbc.gridy++;
        DialogUtils.addComponent(contentPane, _gbc, kernelFileLabel, kernelFile);
        DialogUtils.enableComponents(kernelFileLabel, kernelFile, true);
        _gbc.gridx = 2;
        contentPane.add(kernelFileBrowseButton, _gbc);

        return contentPane;
    }

    private JTree createTree() {
        root = new DefaultMutableTreeNode("@root");

        root.add(createNodes("Detect Lines", FilterOperator.LINE_DETECTION_FILTERS));
        root.add(createNodes("Detect Gradients (Emboss)", FilterOperator.GRADIENT_DETECTION_FILTERS));
        root.add(createNodes("Smooth and Blurr", FilterOperator.SMOOTHING_FILTERS));
        root.add(createNodes("Sharpen", FilterOperator.SHARPENING_FILTERS));
        root.add(createNodes("Enhance Discontinuities", FilterOperator.LAPLACIAN_FILTERS));
        root.add(createNodes("Non-Linear Filters", FilterOperator.NON_LINEAR_FILTERS));
        final JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new MyDefaultTreeCellRenderer());
        tree.putClientProperty("JTree.lineStyle", "Angled");
        expandAll(tree);
        return tree;
    }

    protected JTree getTree() {
        return tree;
    }

    protected void setSelectedFilter(String filterName) {
        DefaultMutableTreeNode item = findItem(root, filterName);
        if(item != null) {
            tree.setSelectionPath(new TreePath(item.getPath()));
        }
    }

    protected static FilterOperator.Filter getSelectedFilter(final JTree tree) {
        final TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath == null) {
            return null;
        }

        final Object[] path = selectionPath.getPath();
        if (path != null && path.length > 0) {
            final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path[path.length - 1];
            if (treeNode.getUserObject() instanceof FilterOperator.Filter) {
                return (FilterOperator.Filter) treeNode.getUserObject();

            }
        }
        return null;
    }


    private static DefaultMutableTreeNode createNodes(String categoryName, FilterOperator.Filter[] filters) {

        DefaultMutableTreeNode category = new DefaultMutableTreeNode(categoryName);

        for (FilterOperator.Filter filter : filters) {
            DefaultMutableTreeNode item = new DefaultMutableTreeNode(filter);
            category.add(item);
        }

        return category;
    }


    private static void expandAll(JTree tree) {
        DefaultMutableTreeNode actNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
        while (actNode != null) {
            if (!actNode.isLeaf()) {
                final TreePath actPath = new TreePath(actNode.getPath());
                tree.expandRow(tree.getRowForPath(actPath));
            }
            actNode = actNode.getNextNode();
        }
    }

    private static class MyDefaultTreeCellRenderer extends DefaultTreeCellRenderer {

        private Font _plainFont;
        private Font _boldFont;

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            final JLabel c = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row,
                                                                         hasFocus);
            if (_plainFont == null) {
                _plainFont = c.getFont().deriveFont(Font.PLAIN);
                _boldFont = c.getFont().deriveFont(Font.BOLD);
            }
            c.setFont(leaf ? _plainFont : _boldFont);
            c.setIcon(null);
            return c;
        }
    }


}