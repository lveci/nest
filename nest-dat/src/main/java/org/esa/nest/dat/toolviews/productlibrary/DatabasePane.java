package org.esa.nest.dat.toolviews.productlibrary;

import com.jidesoft.combobox.DateComboBox;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.util.StringUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.db.DBQuery;
import org.esa.nest.db.ProductDB;
import org.esa.nest.db.ProductEntry;
import org.esa.nest.util.DialogUtils;
import org.esa.nest.util.SQLUtils;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**

 */
public class DatabasePane extends JPanel {

    private final JList missionJList = new JList();
    private final JList productTypeJList = new JList();
    private final JComboBox passCombo = new JComboBox(new String[] {
            DBQuery.ALL_PASSES, DBQuery.ASCENDING_PASS, DBQuery.DESCENDING_PASS });
    private final DateComboBox startDateBox = new DateComboBox();
    private final DateComboBox endDateBox = new DateComboBox();
    private final JComboBox metadataNameCombo = new JComboBox();
    private final JTextField metdataValueField = new JTextField();

    private ProductDB db;
    private final DBQuery dbQuery = new DBQuery();
    private ProductEntry[] productEntryList = null;
    boolean modifyingCombos = false;

    private final List<DatabaseQueryListener> listenerList = new ArrayList<DatabaseQueryListener>(1);

    public DatabasePane() {
        try {
            missionJList.setFixedCellWidth(100);
            createPanel();
            connectToDatabase();

            missionJList.addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent event) {
                    if(modifyingCombos || event.getValueIsAdjusting()) return;
                    try {
                        updateProductTypeCombo();
                        queryDatabase();
                    } catch(Throwable t) {
                        handleException(t);
                    }
                }
            });
            productTypeJList.setFixedCellWidth(100);
            productTypeJList.addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent event) {
                    if(modifyingCombos || event.getValueIsAdjusting()) return;
                    try {
                        queryDatabase();
                    } catch(Throwable t) {
                        handleException(t);
                    }
                }
            });
            passCombo.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent event) {
                    if(modifyingCombos || event.getStateChange() == ItemEvent.DESELECTED) return;
                    try {
                        queryDatabase();
                    } catch(Throwable t) {
                        handleException(t);
                    }
                }
            });

            final String[] metadataNames = db.getMetadataNames();
            for(String name : metadataNames) {
                metadataNameCombo.insertItemAt(name, metadataNameCombo.getItemCount());
            }

            refresh();
        } catch(Throwable t) {
            handleException(t);
        }
    }

    /**
     * Adds a <code>DatabasePaneListener</code>.
     *
     * @param listener the <code>DatabasePaneListener</code> to be added.
     */
    public void addListener(final DatabaseQueryListener listener) {
        if (!listenerList.contains(listener)) {
            listenerList.add(listener);
        }
    }

    /**
     * Removes a <code>DatabasePaneListener</code>.
     *
     * @param listener the <code>DatabasePaneListener</code> to be removed.
     */
    public void removeListener(final DatabaseQueryListener listener) {
        listenerList.remove(listener);
    }

    private void notifyQuery() {
        for (final DatabaseQueryListener listener : listenerList) {
            listener.notifyNewProductEntryListAvailable();
        }
    }

    private static void handleException(Throwable t) {
        t.printStackTrace();
        final VisatApp app = VisatApp.getApp();
        if(app != null) {
            app.showErrorDialog(t.getMessage());
        }
    }

    private void createPanel() {
        setLayout(new GridBagLayout());
        final GridBagConstraints gbc = DialogUtils.createGridBagConstraints();

        JLabel label;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        label = DialogUtils.addComponent(this, gbc, "Mission:", new JScrollPane(missionJList));
        label.setHorizontalAlignment(JLabel.RIGHT);
        gbc.gridy++;
        label = DialogUtils.addComponent(this, gbc, "Product Type:", new JScrollPane(productTypeJList));
        label.setHorizontalAlignment(JLabel.RIGHT);
        gbc.gridy++;
        label = DialogUtils.addComponent(this, gbc, "Pass:", passCombo);
        label.setHorizontalAlignment(JLabel.RIGHT);

        gbc.gridy++;
        label = DialogUtils.addComponent(this, gbc, "Start Date:", startDateBox);
        label.setHorizontalAlignment(JLabel.RIGHT);
        gbc.gridy++;
        label = DialogUtils.addComponent(this, gbc, "End Date:", endDateBox);
        label.setHorizontalAlignment(JLabel.RIGHT);
        gbc.gridy++;
        gbc.gridx = 0;
        this.add(metadataNameCombo, gbc);
        metadataNameCombo.setPrototypeDisplayValue("1234567890123456789");
        gbc.gridx = 1;
        this.add(metdataValueField, gbc);

    }

    private void connectToDatabase() throws Exception {
        db = ProductDB.instance();
        final boolean connected = db.connect();
        if(!connected) {
            throw new Exception("Unable to connect to database\n"+db.getLastSQLException().getMessage());
        }
    }

    public ProductDB getDB() {
        return db;
    }

    public void refresh() {
        try {
            boolean origState = lockCombos(true);
            updateMissionCombo();
            lockCombos(origState);
        } catch(Throwable t) {
            handleException(t);
        }
    }

    private boolean lockCombos(boolean flag) {
        final boolean origState = modifyingCombos;
        modifyingCombos = flag;
        return origState;
    }

    private void updateMissionCombo() throws SQLException {
        boolean origState = lockCombos(true);
        try {
            missionJList.removeAll();
            missionJList.setListData(SQLUtils.prependString(DBQuery.ALL_MISSIONS, db.getAllMissions()));
        } finally {
            lockCombos(origState);
        }
    }

    private void updateProductTypeCombo() throws SQLException {
        boolean origState = lockCombos(true);
        try {
            productTypeJList.removeAll();

            final String selectedMissions[] = toStringArray(missionJList.getSelectedValues());
            String[] productTypeList;
            if(StringUtils.contains(selectedMissions, DBQuery.ALL_MISSIONS))
                productTypeList = db.getAllProductTypes();
            else
                productTypeList = db.getProductTypes(selectedMissions);

            productTypeJList.setListData(SQLUtils.prependString(DBQuery.ALL_PRODUCT_TYPES, productTypeList));
        } finally {
            lockCombos(origState);
        }
    }

    private static String[] toStringArray(Object[] objects) {
        final String strArray[] = new String[objects.length];
        for(int i=0; i<objects.length; ++i) {
            strArray[i] = (String)objects[i];
        }
        return strArray;
    }

    public void setBaseDir(final File dir) {
        try {
            dbQuery.setBaseDir(dir);
            queryDatabase();
        } catch(Throwable t) {
            handleException(t);
        }
    }

    public void removeProducts(final File baseDir) {
        try {
            db.removeProducts(baseDir);
        } catch(Throwable t) {
            handleException(t);
        }
    }

    private void queryDatabase() throws SQLException {
        dbQuery.setSelectedMissions(toStringArray(missionJList.getSelectedValues()));
        dbQuery.setSelectedProductTypes(toStringArray(productTypeJList.getSelectedValues()));
        dbQuery.setSelectedPass((String)passCombo.getSelectedItem());

        dbQuery.clearMetadataQuery();
        dbQuery.addMetadataQuery((String)metadataNameCombo.getSelectedItem(), metdataValueField.getText());

        if(productEntryList != null) {
            ProductEntry.dispose(productEntryList);
        }
        productEntryList = dbQuery.queryDatabase(db);

        notifyQuery();
    }

    public void setSelectionRect(final GeoPos[] selectionBox) {
        try {
            dbQuery.setSelectionRect(selectionBox);
            queryDatabase();
        } catch(Throwable t) {
            handleException(t);
        }
    }

    public ProductEntry[] getProductEntryList() {
        return productEntryList;
    }
}
