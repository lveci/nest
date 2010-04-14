package org.esa.nest.dat.actions.productLibrary.ui;

import org.esa.beam.visat.VisatApp;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.db.ProductDB;
import org.esa.nest.db.ProductEntry;
import org.esa.nest.util.DialogUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**

 */
public class DatabasePane extends JPanel {

    private final JComboBox missionCombo = new JComboBox();
    private final JComboBox productTypeCombo = new JComboBox();
    private final JComboBox passCombo = new JComboBox(new String[] {ALL_PASSES, ASCENDING_PASS, DESCENDING_PASS });

    private final JLabel missionLabel = new JLabel("Mission:");
    private final JLabel productTypeLabel = new JLabel("Product Type:");
    private final JLabel passLabel = new JLabel("Pass:");


    private static final String ALL_MISSIONS = "All Missions";
    private static final String ALL_PRODUCT_TYPES = "All Types";
    private static final String ALL_PASSES = "All Passes";
    private static final String ASCENDING_PASS = "ASCENDING";
    private static final String DESCENDING_PASS = "DESCENDING";

    private ProductDB db;
    private ProductEntry[] productEntryList = null;
    boolean modifyingCombos = false;

    private final List<DatabasePaneListener> listenerList = new ArrayList<DatabasePaneListener>(1);

    public DatabasePane() {
        try {
            createPanel();
            connectToDatabase();

            missionCombo.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent event) {
                    if(modifyingCombos || event.getStateChange() == ItemEvent.DESELECTED) return;
                    try {
                        updateProductTypeCombo();
                        queryDatabase();
                    } catch(Throwable t) {
                        handleException(t);
                    }
                }
            });
            productTypeCombo.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent event) {
                    if(modifyingCombos || event.getStateChange() == ItemEvent.DESELECTED) return;
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
    public void addListener(final DatabasePaneListener listener) {
        if (!listenerList.contains(listener)) {
            listenerList.add(listener);
        }
    }

    /**
     * Removes a <code>DatabasePaneListener</code>.
     *
     * @param listener the <code>DatabasePaneListener</code> to be removed.
     */
    public void removeListener(final DatabasePaneListener listener) {
        listenerList.remove(listener);
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

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        DialogUtils.addComponent(this, gbc, "Mission:", missionCombo);
        gbc.gridy++;
        DialogUtils.addComponent(this, gbc, "Product Type:", productTypeCombo);
        gbc.gridy++;
        DialogUtils.addComponent(this, gbc, "Pass:", passCombo);

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
        missionCombo.removeAllItems();

        missionCombo.addItem(ALL_MISSIONS);
        final String[] missionList = db.getAllMissions();
        for(String mission : missionList) {
            missionCombo.addItem(mission);
        }
        lockCombos(origState);
    }

    private void updateProductTypeCombo() throws SQLException {
        boolean origState = lockCombos(true);
        productTypeCombo.removeAllItems();

        productTypeCombo.addItem(ALL_PRODUCT_TYPES);
        String selectedMission = (String)missionCombo.getSelectedItem();
        String[] missionList;
        if(selectedMission.equals(ALL_MISSIONS))
            missionList = db.getAllProductTypes();
        else
            missionList = db.getProductTypes(selectedMission);
        for(String mission : missionList) {
            productTypeCombo.addItem(mission);
        }
        lockCombos(origState);
    }

    private void queryDatabase() throws SQLException {
        String selectedMission = (String)missionCombo.getSelectedItem();
        String selectedProductType = (String)productTypeCombo.getSelectedItem();
        String selectedPass = (String)passCombo.getSelectedItem();
        if(selectedMission.equals(ALL_MISSIONS))
            selectedMission = "";
        if(selectedProductType.equals(ALL_PRODUCT_TYPES))
            selectedProductType = "";
        if(selectedPass.equals(ALL_PASSES))
            selectedPass = "";

        String queryStr = "";
        if(!selectedMission.isEmpty()) {
            queryStr += AbstractMetadata.MISSION+"='"+selectedMission+"'";
        }
        if(!selectedProductType.isEmpty()) {
            if(!queryStr.isEmpty())
                queryStr += " AND ";
            queryStr += AbstractMetadata.PRODUCT_TYPE+"='"+selectedProductType+"'";
        }
        if(!selectedPass.isEmpty()) {
            if(!queryStr.isEmpty())
                queryStr += " AND ";
            queryStr += AbstractMetadata.PASS+"='"+selectedPass+"'";
        }

        if(productEntryList != null) {
            ProductEntry.dispose(productEntryList);
        }

        if(queryStr.isEmpty()) {
            productEntryList = db.getProductEntryList();     
        } else {
            productEntryList = db.queryProduct(queryStr);
        }

        for (final DatabasePaneListener listener : listenerList) {
            listener.notifyNewProductEntryListAvailable();
        }
    }

    public ProductEntry[] getProductEntryList() {
        return productEntryList;
    }
}
