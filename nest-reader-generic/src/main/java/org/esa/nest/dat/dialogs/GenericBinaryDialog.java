package org.esa.nest.dat.dialogs;

import java.awt.*;
import java.text.NumberFormat;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.*;

import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.util.DialogUtils;

public class GenericBinaryDialog extends ModalDialog {

    private final NumberFormat numFormat = NumberFormat.getNumberInstance();
    private final ProteryListener propListener = new ProteryListener();

    private int rasterWidth = 0;
    private int rasterHeight = 0;
    private int numBands = 1;
    private int dataType = ProductData.TYPE_INT16;
    private int headerBytes = 0;

    private final JFormattedTextField rasterWidthField = DialogUtils.createFormattedTextField(numFormat, rasterWidth, propListener);
    private final JFormattedTextField rasterHeightField = DialogUtils.createFormattedTextField(numFormat, rasterHeight, propListener);
    private final JFormattedTextField numBandsField = DialogUtils.createFormattedTextField(numFormat, numBands, propListener);
    private final JFormattedTextField headerBytesField = DialogUtils.createFormattedTextField(numFormat, headerBytes, propListener);

    private final JComboBox dataTypeBox = new JComboBox(new String[] {  ProductData.TYPESTRING_INT8,
                                                                        ProductData.TYPESTRING_INT16,
                                                                        ProductData.TYPESTRING_INT32,
                                                                        ProductData.TYPESTRING_UINT8,
                                                                        ProductData.TYPESTRING_UINT16,
                                                                        ProductData.TYPESTRING_UINT32,
                                                                        ProductData.TYPESTRING_FLOAT32,
                                                                        ProductData.TYPESTRING_FLOAT64 } );

    private final JLabel rasterHeightLabel = new JLabel("Height:");
    private final JLabel rasterWidthLabel = new JLabel("Width:");
    private final JLabel numBandsLabel = new JLabel("Number Of Bands:");
    private final JLabel dataTypeLabel = new JLabel("Data Type:");
    private final JLabel headerBytesLabel = new JLabel("Header Bytes:");

    public GenericBinaryDialog(Window parent, String helpID) {
        super(parent, "Generic Binary", ModalDialog.ID_OK_CANCEL_HELP, helpID); /* I18N */
    }

    public int show() {
        dataTypeBox.addPropertyChangeListener("value", propListener);
        dataTypeBox.setSelectedItem(ProductData.TYPESTRING_INT16);

        createUI();
        return super.show();
    }

    public int getRasterWidth() {
        return rasterWidth;
    }

    public int getRasterHeight() {
        return rasterHeight;
    }

    public int getNumBands() {
        return numBands;
    }

    public int getDataType() {
        return dataType;
    }

    public int getHeaderBytes() {
        return headerBytes;
    }

    private void createUI() {

        final JPanel contentPane = new JPanel();
        final GridLayout grid = new GridLayout(0,1);
        grid.setVgap(5);

        //Lay out the labels in a panel.
        final JPanel labelPane = new JPanel(grid);
        labelPane.add(rasterWidthLabel);
        labelPane.add(rasterHeightLabel);
        labelPane.add(numBandsLabel);
        labelPane.add(headerBytesLabel);
        labelPane.add(dataTypeLabel);

        //Layout the text fields in a panel.
        final JPanel fieldPane = new JPanel(new GridLayout(0,1));
        fieldPane.add(rasterWidthField);
        fieldPane.add(rasterHeightField);
        fieldPane.add(numBandsField);
        fieldPane.add(headerBytesField);
        fieldPane.add(dataTypeBox);

        //Put the panels in this panel, labels on left, text fields on right.
        //contentPane.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        contentPane.add(labelPane, BorderLayout.CENTER);
        contentPane.add(fieldPane, BorderLayout.LINE_END);

        setContent(contentPane);
    }

    protected void onCancel() {
        super.onCancel();
    }

    protected void onOK() {
        super.onOK();

        dataType = ProductData.getType((String)dataTypeBox.getSelectedItem());
    }

    protected boolean verifyUserInput() {
        boolean b = super.verifyUserInput();

        boolean valid = true;
        if(rasterWidth <=0 || rasterHeight <= 0 || numBands <= 0)
            valid = false;
        
        return b && valid;
    }

    class ProteryListener implements PropertyChangeListener {
        /** Called when a field's "value" property changes. */
        public void propertyChange(PropertyChangeEvent e) {
            final Object source = e.getSource();
            if (source == rasterWidthField) {
                rasterWidth = ((Number)rasterWidthField.getValue()).intValue();
            } else if (source == rasterHeightField) {
                rasterHeight = ((Number)rasterHeightField.getValue()).intValue();
            } else if (source == numBandsField) {
                numBands = ((Number)numBandsField.getValue()).intValue();
            } else if (source == headerBytesField) {
                dataType = ((Number)headerBytesField.getValue()).intValue();
            }
        }
    }

}