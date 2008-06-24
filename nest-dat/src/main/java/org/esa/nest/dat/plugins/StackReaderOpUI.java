package org.esa.nest.dat.plugins;

import com.bc.ceres.binding.*;
import com.bc.ceres.binding.swing.BindingContext;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.nest.util.DatUtils;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Date;
import java.util.Map;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Feb 12, 2008
 * Time: 1:52:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class StackReaderOpUI extends BaseOperatorUI {

    FileModel fileModel;

    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);

        JComponent comp =  createComponent();
        initParameters();
        return comp;
    }

    public void initParameters() {
        convertFromDOM();

        String[] fList = (String[])paramMap.get("fileList");
        if(fList != null) {
            for (String str : fList) {
                fileModel.addFile(new File(str));
            }
        }
    }

    public UIValidation validateParameters() {

        //todo how to validate generated UIs?
        return new UIValidation(true, "");
    }

    public void updateParameters() {

        Vector<File> fileList = fileModel.getFileList();
        if(fileList.isEmpty()) return;

        paramMap.put("defaultFile", fileList.get(0));
        String[] fList = new String[fileList.size()];
        for(int i=0; i < fileList.size(); ++i) {
            fList[i] = fileList.get(i).getAbsolutePath();
        }
        paramMap.put("fileList", fList);
    }

    JComponent createComponent() {

        final JPanel fileListPanel = new JPanel(new BorderLayout(4, 4));

        fileModel = new FileModel();
        JTable jt = new JTable(fileModel);
        jt.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        jt.setColumnSelectionAllowed(true);

        JScrollPane scrollPane = new JScrollPane(jt);
        fileListPanel.add(scrollPane, BorderLayout.CENTER);

        final JPanel buttonPanel = new JPanel();
        initButtonPanel(buttonPanel);
        fileListPanel.add(buttonPanel, BorderLayout.EAST);

        return fileListPanel;
    }

    private void initButtonPanel(final JPanel panel) {
        panel.setLayout(new GridLayout(10, 1));

        JButton addButton = CreateButton("addButton", "Add", panel);
        addButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                File file = DatUtils.GetFilePath("Add Product", "All Files", "", "Product", false);
                if(file != null) {
                    ProductReader reader = ProductIO.getProductReaderForFile(file);
                    if (reader != null) {
                            fileModel.addFile(file);
                    } else {
                        //throw
                    }
                }
            }
        });

        JButton clearButton = CreateButton("clearButton", "Clear", panel);
        clearButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {

            }
        });

        panel.add(addButton);
        panel.add(clearButton);
    }

    JButton CreateButton(String name, String text, JPanel panel) {
        JButton button = new JButton();
        button.setName(getClass().getName() + name);
        button = new JButton();
        button.setBackground(panel.getBackground());
        button.setText(text);
        button.setActionCommand(name);
        return button;
    }

    public static class FileModel extends AbstractTableModel {

        String titles[] = new String[]{
                "File Name", "Size", "Last Modified"
        };

        Class types[] = new Class[]{
                String.class, Number.class, Date.class
        };

        Object data[][] = new Object[0][titles.length];
        Vector<File> fileList = new Vector<File>(10);

        public FileModel() {
        }

        public Vector getFileList() {
            return fileList;
        }

        public void addFile(File file) {
            fileList.add(file);
            setFileStats();
        }

        // Implement the methods of the TableModel interface we're interested
        // in.  Only getRowCount(), getColumnCount() and getValueAt() are
        // required.  The other methods tailor the look of the table.
        public int getRowCount() {
            return data.length;
        }

        public int getColumnCount() {
            return titles.length;
        }

        public String getColumnName(int c) {
            return titles[c];
        }

        public Class getColumnClass(int c) {
            return types[c];
        }

        public Object getValueAt(int r, int c) {
            return data[r][c];
        }

        //  Our own method for setting/changing the current directory
        // being displayed.  This method fills the data set with file info
        // from the given directory.  It also fires an update event so this
        // method could also be called after the table is on display.
        public void setFileStats() {

            data = new Object[fileList.size()][titles.length];

            for (int i = 0; i < fileList.size(); i++) {
                File file = fileList.get(i);

                data[i][0] = file.getName();
                data[i][1] = file.length();
                data[i][2] = new Date(file.lastModified());
            }

            // Just in case anyone's listening...
            fireTableDataChanged();
        }
    }
}