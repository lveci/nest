package org.esa.nest.gpf;

import com.bc.ceres.binding.swing.BindingContext;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.util.io.FileChooserFactory;

import javax.swing.*;
import javax.swing.tree.TreePath;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.Vector;

/**
 * Stack Reader Operator User Interface
 * User: lveci
 * Date: Feb 12, 2008
 */
public class ProductSetReaderOpUI extends BaseOperatorUI {

    FileModel fileModel = new FileModel();
    JTable productSetTable;

    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);

        JComponent comp =  createComponent(productSetTable, fileModel);
        initParameters();
        return comp;
    }

    public void initParameters() {
        convertFromDOM();

        String[] fList = (String[])paramMap.get("fileList");
        if(fList != null) {
            fileModel.clear();
            for (String str : fList) {
                fileModel.addFile(new File(str));
            }
        }
    }

    public UIValidation validateParameters() {

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

    public static JComponent createComponent(JTable table, FileModel fileModel) {

        final JPanel fileListPanel = new JPanel(new BorderLayout(4, 4));

        table = new JTable(fileModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setColumnSelectionAllowed(true);
        table.setDropMode(DropMode.ON);
        table.setTransferHandler(new ProductSetTransferHandler(fileModel));

        JScrollPane scrollPane = new JScrollPane(table);
        fileListPanel.add(scrollPane, BorderLayout.CENTER);

        final JPanel buttonPanel = new JPanel();
        initButtonPanel(buttonPanel, table, fileModel);
        fileListPanel.add(buttonPanel, BorderLayout.EAST);

        return fileListPanel;
    }

    private static void initButtonPanel(final JPanel panel, final JTable table, final FileModel fileModel) {
        panel.setLayout(new GridLayout(10, 1));

        final JButton addButton = CreateButton("addButton", "Add", panel);
        addButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                File file = GetFilePath(addButton, "Add Product");
                if(file != null) {
                    ProductReader reader = ProductIO.getProductReaderForFile(file);
                    if (reader != null) {
                        fileModel.addFile(file);
                    }
                }
            }
        });

        final JButton removeButton = CreateButton("removeButton", "Remove", panel);
        removeButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                int selRow = table.getSelectedRow();
                if(selRow >= 0) {
                    fileModel.removeFile(selRow);
                }
            }

        });

        JButton clearButton = CreateButton("clearButton", "Clear", panel);
        clearButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                fileModel.clear();
            }
        });

        panel.add(addButton);
        panel.add(removeButton);
        panel.add(clearButton);
    }

    private static JButton CreateButton(String name, String text, JPanel panel) {
        JButton button = new JButton();
        button.setName(name);
        button = new JButton();
        button.setBackground(panel.getBackground());
        button.setText(text);
        button.setActionCommand(name);
        return button;
    }

    public static File GetFilePath(Component component, String title) {

        File file = null;
        final JFileChooser chooser = FileChooserFactory.getInstance().createFileChooser(new File("."));
        chooser.setDialogTitle(title);
        if (chooser.showDialog(component, "ok") == JFileChooser.APPROVE_OPTION) {

            file = chooser.getSelectedFile();
        }

        return file;
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
            addBlankFile();
        }

        public Vector<File> getFileList() {
            return fileList;
        }

        public void addFile(File file) {
            fileList.add(file);
            clearBlankFile();
            setFileStats();
        }

        public void removeFile(int index) {
            fileList.remove(index);
            setFileStats();
        }

        /**
         * Needed for drag and drop
         */
        private void addBlankFile() {
            addFile(new File(""));
        }

        private void clearBlankFile() {
            if(fileList.size() > 1 && fileList.get(0).getName().isEmpty())
                removeFile(0);
        }

        public void clear() {
            fileList.clear();
            setFileStats();
            addBlankFile();
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

                if(!file.getName().isEmpty()) {
                    data[i][0] = file.getName();
                    data[i][1] = file.length();
                    data[i][2] = new Date(file.lastModified());
                }
            }

            // Just in case anyone's listening...
            fireTableDataChanged();
        }
    }


    public static class ProductSetTransferHandler extends TransferHandler {

        FileModel fileModel;

        public ProductSetTransferHandler(FileModel model) {
            fileModel = model;
        }

        public boolean canImport(TransferHandler.TransferSupport info) {
            return info.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        public int getSourceActions(JComponent c) {
            return TransferHandler.COPY;
        }

        /**
         * Perform the actual import 
         */
        public boolean importData(TransferHandler.TransferSupport info) {
            if (!info.isDrop()) {
                return false;
            }

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

                File file = new File(value);
                if(file.exists()) {
                    ProductReader reader = ProductIO.getProductReaderForFile(file);
                    if (reader != null) {
                        fileModel.addFile(file);
                    }
                }
            }
            return true;
        }
    }
}