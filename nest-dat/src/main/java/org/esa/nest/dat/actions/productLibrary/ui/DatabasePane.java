package org.esa.nest.dat.actions.productLibrary.ui;

import org.esa.beam.util.StringUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.nest.datamodel.AbstractMetadata;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

/**

 */
public class DatabasePane extends JPanel {

    private final JList missionJList = new JList();
    private final JList productTypeJList = new JList();
    private final JComboBox passCombo = new JComboBox(new String[] {ALL_PASSES, ASCENDING_PASS, DESCENDING_PASS });

    private static final String ALL_MISSIONS = "All Missions";
    private static final String ALL_PRODUCT_TYPES = "All Types";
    private static final String ALL_PASSES = "All Passes";
    private static final String ASCENDING_PASS = "ASCENDING";
    private static final String DESCENDING_PASS = "DESCENDING";

    private ProductDB db;
    private ProductEntry[] productEntryList = null;
    private Rectangle.Float selectionRectangle = null;
    private File baseDir = null;
    boolean modifyingCombos = false;

    private final List<DatabaseQueryListener> listenerList = new ArrayList<DatabaseQueryListener>(1);

    public DatabasePane() {
        try {
            missionJList.setFixedCellWidth(200);
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
            productTypeJList.setFixedCellWidth(200);
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

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        DialogUtils.addComponent(this, gbc, "Mission:", new JScrollPane(missionJList));
        gbc.gridy++;
        DialogUtils.addComponent(this, gbc, "Product Type:", new JScrollPane(productTypeJList));
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
        try {
            missionJList.removeAll();
            missionJList.setListData(SQLUtils.prependString(ALL_MISSIONS, db.getAllMissions()));
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
            if(StringUtils.contains(selectedMissions, ALL_MISSIONS))
                productTypeList = db.getAllProductTypes();
            else
                productTypeList = db.getProductTypes(selectedMissions);

            productTypeJList.setListData(SQLUtils.prependString(ALL_PRODUCT_TYPES, productTypeList));
        } finally {
            lockCombos(origState);
        }
    }

    private String[] toStringArray(Object[] objects) {
        final String strArray[] = new String[objects.length];
        for(int i=0; i<objects.length; ++i) {
            strArray[i] = (String)objects[i];
        }
        return strArray;
    }

    public void setBaseDir(final File dir) {
        baseDir = dir;
    }

    public void removeProducts(final File baseDir) {
        try {
            db.removeProducts(baseDir);
        } catch(Throwable t) {
            handleException(t);
        }
    }

    private void queryDatabase() throws SQLException {
        String selectedMissions[] = toStringArray(missionJList.getSelectedValues());
        String selectedProductTypes[] = toStringArray(productTypeJList.getSelectedValues());
        String selectedPass = (String)passCombo.getSelectedItem();
        if(StringUtils.contains(selectedMissions, ALL_MISSIONS))
            selectedMissions = new String[] {};
        if(StringUtils.contains(selectedProductTypes, ALL_PRODUCT_TYPES))
            selectedProductTypes = new String[] {};
        if(selectedPass.equals(ALL_PASSES))
            selectedPass = "";

        String queryStr = "";
        if(selectedMissions.length > 0) {
            queryStr += SQLUtils.getOrList(AbstractMetadata.MISSION, selectedMissions);
        }
        if(selectedProductTypes.length > 0) {
            if(!queryStr.isEmpty())
                queryStr += " AND ";
            queryStr += SQLUtils.getOrList(AbstractMetadata.PRODUCT_TYPE, selectedProductTypes);
        }
        if(!selectedPass.isEmpty()) {
            if(!queryStr.isEmpty())
                queryStr += " AND ";
            queryStr += AbstractMetadata.PASS+"='"+selectedPass+"'";
        }

        if(baseDir != null) {
            if(!queryStr.isEmpty())
                queryStr += " AND ";
            queryStr += AbstractMetadata.PATH+" LIKE '"+baseDir.getAbsolutePath()+"%'";
        }

        if(productEntryList != null) {
            ProductEntry.dispose(productEntryList);
        }

        if(queryStr.isEmpty()) {
            productEntryList = instersectMapSelection(db.getProductEntryList());
        } else {
            productEntryList = instersectMapSelection(db.queryProduct(queryStr));
        }

        notifyQuery();
    }

    public void setSelectionRect(final GeoPos[] selectionBox) {
        try {
            selectionRectangle = getBoundingRect(selectionBox);
            queryDatabase();
        } catch(Throwable t) {
            handleException(t);
        }
    }

    private ProductEntry[] instersectMapSelection(final ProductEntry[] resultsList) {
        if(selectionRectangle != null && selectionRectangle.getWidth() != 0 && selectionRectangle.getHeight() != 0) {
            final ArrayList<ProductEntry> intersectList = new ArrayList<ProductEntry>();
            for(ProductEntry entry : resultsList) {
                final GeoPos start = entry.getFirstNearGeoPos();
                final GeoPos end = entry.getLastFarGeoPos();
                final float w = Math.abs(end.getLon()-start.getLon());
                final float h = Math.abs(end.getLat()-start.getLat());
                final Rectangle.Float entryRect = new Rectangle.Float(start.getLon(), start.getLat(), w, h);
                if(selectionRectangle.intersects(entryRect)) {
                    intersectList.add(entry);    
                }
            }
            return intersectList.toArray(new ProductEntry[intersectList.size()]);
        }
        return resultsList;
    }

    private static Rectangle.Float getBoundingRect(final GeoPos[] geoPositions) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (final GeoPos pos : geoPositions) {
            final float x = pos.getLon();
            final float y = pos.getLat();

            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }
        }
        if (minX >= maxX || minY >= maxY) {
            return null;
        }

        return new Rectangle.Float(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    public ProductEntry[] getProductEntryList() {
        return productEntryList;
    }
}
