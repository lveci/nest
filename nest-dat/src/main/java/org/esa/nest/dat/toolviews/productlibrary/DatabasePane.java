package org.esa.nest.dat.toolviews.productlibrary;

import com.jidesoft.combobox.DateComboBox;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.util.StringUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.db.DBQuery;
import org.esa.nest.db.ProductDB;
import org.esa.nest.db.ProductEntry;
import org.esa.nest.util.DialogUtils;
import org.esa.nest.util.SQLUtils;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.metal.MetalBorders;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
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
    private final JTextArea metadataArea = new JTextArea();
    private final JButton addMetadataButton = new JButton("+");
    private final JButton updateButton = new JButton(UIUtils.loadImageIcon("icons/Update16.gif"));

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
                    updateProductTypeCombo();
                    queryDatabase();
                }
            });
            productTypeJList.setFixedCellWidth(100);
            productTypeJList.addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent event) {
                    if(modifyingCombos || event.getValueIsAdjusting()) return;
                    queryDatabase();
                }
            });
            passCombo.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent event) {
                    if(modifyingCombos || event.getStateChange() == ItemEvent.DESELECTED) return;
                    queryDatabase();
                }
            });
            startDateBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent event) {
                    if(event.getStateChange() == ItemEvent.SELECTED)
                        queryDatabase();
                }
            });
            endDateBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent event) {
                    if(event.getStateChange() == ItemEvent.SELECTED)
                        queryDatabase();
                }
            });
            addMetadataButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    addMetadataText();
                }
            });
            updateButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    queryDatabase();
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
        this.add(new JLabel("Mission:"), gbc);
        gbc.gridx = 1;
        this.add(new JLabel("Product Type:"), gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        this.add(new JScrollPane(missionJList), gbc);
        gbc.gridx = 1;
        this.add(new JScrollPane(productTypeJList), gbc);
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
        gbc.gridx = 2;
        this.add(addMetadataButton, gbc);
        addMetadataButton.setMaximumSize(new Dimension(3, 3));

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        this.add(metadataArea, gbc);
        metadataArea.setBorder(new LineBorder(Color.BLACK));
        metadataArea.setLineWrap(true);
        metadataArea.setRows(4);
        metadataArea.setToolTipText("Use AND,OR,NOT and =,<,>,<=,>-");
        gbc.gridx = 2;
        gbc.gridwidth = 1;
        this.add(updateButton, gbc);
        updateButton.setMaximumSize(new Dimension(3, 3));
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

    private void updateProductTypeCombo() {
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
        } catch(Throwable t) {
            handleException(t);
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
        dbQuery.setBaseDir(dir);
        queryDatabase();
    }

    public void removeProducts(final File baseDir) {
        try {
            db.removeProducts(baseDir);
        } catch(Throwable t) {
            handleException(t);
        }
    }

    private void addMetadataText() {
        final String name = (String)metadataNameCombo.getSelectedItem();
        final String value = metdataValueField.getText();
        if(!name.isEmpty() && !value.isEmpty()) {
            if(metadataArea.getText().length() > 0)
                metadataArea.append(" AND ");
            metadataArea.append(name+"='"+value+"' ");
        }
    }

    private void queryDatabase() {
        dbQuery.setSelectedMissions(toStringArray(missionJList.getSelectedValues()));
        dbQuery.setSelectedProductTypes(toStringArray(productTypeJList.getSelectedValues()));
        dbQuery.setSelectedPass((String)passCombo.getSelectedItem());
        dbQuery.setStartEndDate(startDateBox.getCalendar(), endDateBox.getCalendar());

        dbQuery.clearMetadataQuery();
        dbQuery.setFreeQuery(metadataArea.getText());

        if(productEntryList != null) {
            ProductEntry.dispose(productEntryList);
        }
        try {
            productEntryList = dbQuery.queryDatabase(db);
        } catch(Throwable t) {
            handleException(t);
        }

        notifyQuery();
    }

    public void setSelectionRect(final GeoPos[] selectionBox) {
        dbQuery.setSelectionRect(selectionBox);
        queryDatabase();
    }

    public ProductEntry[] getProductEntryList() {
        return productEntryList;
    }
}
