package org.esa.nest.dat.dialogs;

import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dat.toolviews.Projects.ProductSet;
import org.esa.nest.gpf.ProductSetReaderOpUI;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jun 5, 2008
 * To change this template use File | Settings | File Templates.
 */
public class ProductSetDialog extends ModelessDialog {

    private final ProductSetReaderOpUI.FileModel fileModel = new ProductSetReaderOpUI.FileModel();
    private final JTable productSetTable = new JTable(fileModel);
    private final JTextField nameField;
    private final ProductSet productSet;

    private boolean ok = false;

    public ProductSetDialog(String title, ProductSet prodSet) {
        super(VisatApp.getApp().getMainFrame(), title, ModalDialog.ID_OK_CANCEL, null);
        productSet = prodSet;

        final ArrayList<File> fileList = productSet.getFileList();
        for(File file : fileList) {
            fileModel.addFile(file);
        }

        final JComponent content =  ProductSetReaderOpUI.createComponent(productSetTable, fileModel);

        final JPanel topPanel = new JPanel(new BorderLayout(4, 4));
        final JLabel nameLabel = new JLabel("Name:");
        topPanel.add(nameLabel, BorderLayout.WEST);
        nameField = new JTextField(productSet.getName());
        topPanel.add(nameField, BorderLayout.CENTER);

        content.add(topPanel, BorderLayout.NORTH);

        setContent(content);
    }

    @Override
    protected void onOK() {
        productSet.setName(nameField.getText());
        productSet.setFileList(fileModel.getFileList());
        productSet.Save();
        
        ok = true;
        hide();
    }

    public boolean IsOK() {
        return ok;
    }

}