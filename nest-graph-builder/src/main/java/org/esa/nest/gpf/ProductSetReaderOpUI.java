package org.esa.nest.gpf;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.BasicApp;
import org.esa.beam.util.io.FileChooserFactory;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.util.DialogUtils;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;

/**
 * Stack Reader Operator User Interface
 * User: lveci
 * Date: Feb 12, 2008
 */
public class ProductSetReaderOpUI extends BaseOperatorUI {

    private final FileModel fileModel = new FileModel();
    private final JTable productSetTable = new JTable(fileModel);

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);

        final JComponent comp = createComponent(productSetTable, fileModel);
        initParameters();
        return comp;
    }

    @Override
    public void initParameters() {
        convertFromDOM();

        final String[] fList = (String[])paramMap.get("fileList");
        if(fList != null) {
            fileModel.clear();
            for (String str : fList) {
                fileModel.addFile(new File(str));
            }
        }
    }

    @Override
    public UIValidation validateParameters() {

        return new UIValidation(true, "");
    }

    @Override
    public void updateParameters() {

        final ArrayList<File> fileList = fileModel.getFileList();
        if(fileList.isEmpty()) return;

        final String[] fList = new String[fileList.size()];
        for(int i=0; i < fileList.size(); ++i) {
            if(fileList.get(i).getName().isEmpty())
                fList[i] = "";
            else
                fList[i] = fileList.get(i).getAbsolutePath();
        }
        paramMap.put("fileList", fList);
    }

    public static JComponent createComponent(final JTable table, final FileModel fileModel) {

        final JPanel fileListPanel = new JPanel(new BorderLayout(4, 4));

        fileModel.setColumnWidths(table.getColumnModel());
        table.setColumnSelectionAllowed(true);
        table.setDropMode(DropMode.ON);
        table.setTransferHandler(new ProductSetTransferHandler(fileModel));

        final JScrollPane scrollPane = new JScrollPane(table);
        fileListPanel.add(scrollPane, BorderLayout.CENTER);

        final JPanel buttonPanel = new JPanel();
        initButtonPanel(buttonPanel, table, fileModel);
        fileListPanel.add(buttonPanel, BorderLayout.EAST);

        return fileListPanel;
    }



    private static void initButtonPanel(final JPanel panel, final JTable table, final FileModel fileModel) {
        panel.setLayout(new GridLayout(10, 1));

        final JButton addButton = DialogUtils.CreateButton("addButton", "Add", null, panel);
        addButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final File[] files = GetFilePath(addButton, "Add Product");
                if(files != null) {
                    for(File file : files) {
                        final ProductReader reader = ProductIO.getProductReaderForFile(file);
                        if (reader != null) {
                            fileModel.addFile(file);
                        }
                    }
                }
            }
        });

        final JButton removeButton = DialogUtils.CreateButton("removeButton", "Remove", null, panel);
        removeButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final int selRow = table.getSelectedRow();
                if(selRow >= 0) {
                    fileModel.removeFile(selRow);
                }
            }

        });

        final JButton clearButton = DialogUtils.CreateButton("clearButton", "Clear", null, panel);
        clearButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                fileModel.clear();
            }
        });

        panel.add(addButton);
        panel.add(removeButton);
        panel.add(clearButton);
    }

    public static File[] GetFilePath(Component component, String title) {

        File[] files = null;
        final File openDir = new File(VisatApp.getApp().getPreferences().
                getPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_OPEN_DIR, "."));
        final JFileChooser chooser = FileChooserFactory.getInstance().createFileChooser(openDir);
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle(title);
        if (chooser.showDialog(component, "ok") == JFileChooser.APPROVE_OPTION) {
            files = chooser.getSelectedFiles();
        }
        return files;
    }


    public static class FileModel extends AbstractTableModel {

        private final String titles[] = new String[]{
                "File Name", "Type", "Acquisition", "Track", "Orbit"
        };

        private final Class types[] = new Class[]{
                String.class, String.class, String.class, Number.class, Number.class
        };

        private final int widths[] = new int[]{
                75, 10, 20, 5, 5
        };

        private Object data[][] = new Object[0][titles.length];
        private final ArrayList<File> fileList = new ArrayList<File>(10);

        public FileModel() {
            addBlankFile();
        }

        public ArrayList<File> getFileList() {
            return fileList;
        }

        public void addFile(final File file) {
            fileList.add(file);
            clearBlankFile();
            setFileStats();
        }

        public void removeFile(final int index) {
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

        @Override
        public String getColumnName(int c) {
            return titles[c];
        }

        @Override
        public Class getColumnClass(int c) {
            return types[c];
        }

        public Object getValueAt(int r, int c) {
            return data[r][c];
        }

        void setColumnWidths(TableColumnModel columnModel) {
            for(int i=0; i < widths.length; ++i) {
                columnModel.getColumn(i).setMinWidth(widths[i]);
                columnModel.getColumn(i).setPreferredWidth(widths[i]);
                columnModel.getColumn(i).setWidth(widths[i]);
            }
        }

        // Our own method for setting/changing the current directory
        // being displayed.  This method fills the data set with file info
        // from the given directory.  It also fires an update event so this
        // method could also be called after the table is on display.
        public void setFileStats() {

            data = new Object[fileList.size()][titles.length];

            updateProductData(data, fileList);
        }

        private void updateProductData(final Object[][] data, final ArrayList<File> fileList) {

            final SwingWorker worker = new SwingWorker() {
                @Override
                protected Object doInBackground() throws Exception {
                    try {
                        for(int i=0; i < fileList.size(); ++i) {
                            final File file = fileList.get(i);

                            if(!file.getName().isEmpty()) {
                                try {
                                    final Product product = ProductIO.readProduct(file, null);
                                    final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);

                                    data[i][0] = product.getName();
                                    data[i][1] = product.getProductType();
                                    data[i][2] = OperatorUtils.getAcquisitionDate(absRoot);
                                    data[i][3] = absRoot.getAttributeInt(AbstractMetadata.REL_ORBIT, 0);
                                    data[i][4] = absRoot.getAttributeInt(AbstractMetadata.ABS_ORBIT, 0);
                                } catch(Exception ex) {
                                    data[i][0] = file.getName();
                                    data[i][1] = "";
                                    data[i][2] = "";
                                    data[i][3] = 0;
                                    data[i][4] = 0;
                                }
                            }
                        }
                    } finally {
                        fireTableDataChanged();
                    }
                    return null;
                }
            };
            worker.execute();
        }
    }


    public static class ProductSetTransferHandler extends TransferHandler {

        private final FileModel fileModel;

        public ProductSetTransferHandler(FileModel model) {
            fileModel = model;
        }

        @Override
        public boolean canImport(TransferHandler.TransferSupport info) {
            return info.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public int getSourceActions(JComponent c) {
            return TransferHandler.COPY;
        }

        /**
         * Perform the actual import 
         */
        @Override
        public boolean importData(TransferHandler.TransferSupport info) {
            if (!info.isDrop()) {
                return false;
            }

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

                final File file = new File(value);
                if(file.exists()) {
                    final ProductReader reader = ProductIO.getProductReaderForFile(file);
                    if (reader != null) {
                        fileModel.addFile(file);
                    }
                }
            }
            return true;
        }
    }
}