package org.esa.nest.dat.dialogs;

import org.esa.nest.db.ProductEntry;
import org.esa.nest.db.ProductDB;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.gpf.OperatorUtils;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.visat.VisatApp;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.*;
import java.io.File;
import java.util.ArrayList;

public class FileModel extends AbstractTableModel {

        private final String titles[] = new String[]{
                "File Name", "Type", "Acquisition", "Track", "Orbit"
        };

        private final Class types[] = new Class[]{
                String.class, String.class, String.class, String.class, String.class
        };

        private final int widths[] = new int[]{
                75, 10, 20, 5, 5
        };

        private class FileStats {
            String data[] = new String[titles.length];
            FileStats(final File file) {
                data[0] = file.getName();
            }
            FileStats(final ProductEntry entry) {
                data[0] = entry.getName();
                data[1] = entry.getProductType();
                data[2] = entry.getFirstLineTime().format();

                final MetadataElement meta = entry.getMetadata();
                if(meta != null) {
                    data[3] = String.valueOf(meta.getAttributeInt(AbstractMetadata.REL_ORBIT, 0));
                    data[4] = String.valueOf(meta.getAttributeInt(AbstractMetadata.ABS_ORBIT, 0));
                }
            }
        }

        private final ArrayList<File> fileList = new ArrayList<File>(10);
        private final ArrayList<FileStats> dataList = new ArrayList<FileStats>(10);

        public FileModel() {
            addBlankFile();
        }

        public ArrayList<File> getFileList() {
            return fileList;
        }

        private static ProductEntry getProductEntry(final File file) {
            try {
                return ProductDB.instance().getProductEntry(file);
            } catch(Exception e) {
                if(VisatApp.getApp() != null) {
                    VisatApp.getApp().showErrorDialog(e.getMessage());
                }
            }
            return null;
        }

        public void addFile(final File file) {
            fileList.add(file);
            clearBlankFile();

            // check if already exists in db
            final ProductEntry existingEntry = getProductEntry(file);
            if(existingEntry != null) {
                dataList.add(new FileStats(existingEntry));
            } else {
                final FileStats fstat = new FileStats(file);
                dataList.add(fstat);

                updateProductData(fstat, file);
            }
            fireTableDataChanged();
        }

        public void addFile(final ProductEntry entry) {
            fileList.add(entry.getFile());
            clearBlankFile();

            dataList.add(new FileStats(entry));
            fireTableDataChanged();
        }

        public void removeFile(final int index) {
            fileList.remove(index);
            dataList.get(index).data = null;
            dataList.remove(index);

            fireTableDataChanged();
        }

        public void move(final int oldIndex, final int newIndex) {
            if((oldIndex < 1 && oldIndex > newIndex) || oldIndex > fileList.size() ||
               newIndex < 0 || newIndex >= fileList.size())
                return;
            final File file = fileList.get(oldIndex);
            final FileStats fstat = dataList.get(oldIndex);

            fileList.remove(oldIndex);
            dataList.remove(oldIndex);
            fileList.add(newIndex, file);
            dataList.add(newIndex, fstat);

            fireTableDataChanged();
        }

        public int getIndexOf(final File file) {
            return fileList.indexOf(file);
        }

        /**
         * Needed for drag and drop
         */
        private void addBlankFile() {
            addFile(new File(""));
        }

        private void clearBlankFile() {
            if(fileList.size() > 1 && fileList.get(0).getName().isEmpty()) {
                removeFile(0);
            }
        }

        public void clear() {
            fileList.clear();
            dataList.clear();
            addBlankFile();

            fireTableDataChanged();
        }

        // Implement the methods of the TableModel interface we're interested
        // in.  Only getRowCount(), getColumnCount() and getValueAt() are
        // required.  The other methods tailor the look of the table.
        public int getRowCount() {
            return dataList.size();
        }

        public int getColumnCount() {
            return titles.length;
        }

        @Override
        public String getColumnName(final int c) {
            return titles[c];
        }

        @Override
        public Class getColumnClass(final int c) {
            return types[c];
        }

        public Object getValueAt(final int r, final int c) {
            return dataList.get(r).data[c];
        }

        public File getFileAt(final int index) {
            return fileList.get(index);
        }

        public File[] getFilesAt(final int[] indices) {
            final ArrayList<File> files = new ArrayList<File>(indices.length);
            for(int i : indices) {
                files.add(fileList.get(i));
            }
            return files.toArray(new File[files.size()]);
        }

        public void setColumnWidths(final TableColumnModel columnModel) {
            for(int i=0; i < widths.length; ++i) {
                columnModel.getColumn(i).setMinWidth(widths[i]);
                columnModel.getColumn(i).setPreferredWidth(widths[i]);
                columnModel.getColumn(i).setWidth(widths[i]);
            }
        }

        private void updateProductData(final FileStats fstat, final File file) {

            final SwingWorker worker = new SwingWorker() {
                @Override
                protected Object doInBackground() throws Exception {
                    try {
                        if(!file.getName().isEmpty()) {
                            try {
                                final Product product = ProductIO.readProduct(file);
                                final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);

                                fstat.data[0] = product.getName();
                                fstat.data[1] = product.getProductType();
                                fstat.data[2] = OperatorUtils.getAcquisitionDate(absRoot);
                                fstat.data[3] = String.valueOf(absRoot.getAttributeInt(AbstractMetadata.REL_ORBIT, 0));
                                fstat.data[4] = String.valueOf(absRoot.getAttributeInt(AbstractMetadata.ABS_ORBIT, 0));
                            } catch(Exception ex) {
                                fstat.data[0] = file.getName();
                                fstat.data[1] = "";
                                fstat.data[2] = "";
                                fstat.data[3] = "";
                                fstat.data[4] = "";
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