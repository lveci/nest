package org.esa.nest.dat.dialogs;

import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.BasicApp;
import org.esa.beam.visat.VisatApp;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

import com.jidesoft.swing.JideScrollPane;
import com.jidesoft.swing.JideSplitPane;
import com.jidesoft.icons.IconsFactory;

/**
 * Display the Settings
 */
public class SettingsDialog extends ModelessDialog {

    private SettingsTree settingsTree;
    private DefaultMutableTreeNode rootNode;

    private boolean ok = false;

    public SettingsDialog(String title) {
        super(VisatApp.getApp().getMainFrame(), title, ModalDialog.ID_OK_CANCEL, null);

        final JScrollPane scrollPane = new JideScrollPane(createTree());
        scrollPane.setPreferredSize(new Dimension(320, 480));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.setViewportBorder(null);

        final JideSplitPane splitPane = new JideSplitPane(JideSplitPane.VERTICAL_SPLIT);
        splitPane.addPane(scrollPane);

        setContent(splitPane);
    }

    private SettingsTree createTree() {
        rootNode = new DefaultMutableTreeNode("");
        settingsTree = new SettingsTree(false);//rootNode);
        settingsTree.populateTree(rootNode);
        settingsTree.setRootVisible(false);
        settingsTree.setShowsRootHandles(true);
        DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) settingsTree.getCellRenderer();
        renderer.setLeafIcon(IconsFactory.getImageIcon(BasicApp.class, "/org/esa/beam/resources/images/icons/RsBandAsSwath16.gif"));
        renderer.setClosedIcon(IconsFactory.getImageIcon(BasicApp.class, "/org/esa/beam/resources/images/icons/RsGroupClosed16.gif"));
        renderer.setOpenIcon(IconsFactory.getImageIcon(BasicApp.class, "/org/esa/beam/resources/images/icons/RsGroupOpen16.gif"));
        return settingsTree;
    }

    @Override
    protected void onOK() {


        ok = true;
        hide();
    }

    public boolean IsOK() {
        return ok;
    }

}