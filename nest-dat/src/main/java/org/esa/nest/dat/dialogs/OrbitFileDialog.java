
package org.esa.nest.dat.dialogs;

import java.awt.GridBagConstraints;
import java.awt.Window;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.util.Guardian;
import org.esa.nest.dataio.OrbitFileUpdater;

public class OrbitFileDialog extends ModalDialog {

    private final Product _sourceProduct;
    private Product _resultProduct;
    private Exception _exception;

    private JRadioButton _buttonVOR;
    private JRadioButton _buttonPOR;

    public OrbitFileDialog(Window parent, Product sourceProduct, String helpID) {
        super(parent, "Apply Orbit File", ModalDialog.ID_OK_CANCEL_HELP, helpID); /* I18N */
        Guardian.assertNotNull("sourceProduct", sourceProduct);
        _sourceProduct = sourceProduct;
    }

    public int show() {
        createUI();
        updateUI();
        return super.show();
    }

    public Product getSourceProduct() {
        return _sourceProduct;
    }

    public Product getResultProduct() {
        return _resultProduct;
    }

    public Exception getException() {
        return _exception;
    }

    protected void onCancel() {
        super.onCancel();
        _resultProduct = null;
    }

    protected void onOK() {
        super.onOK();

        try {
            OrbitFileUpdater.OrbitType orbitType = OrbitFileUpdater.OrbitType.DORIS_VOR ;
            if(_buttonVOR.isSelected())
                orbitType = OrbitFileUpdater.OrbitType.DORIS_VOR;
            else if(_buttonPOR.isSelected())
                orbitType = OrbitFileUpdater.OrbitType.DORIS_POR;

            OrbitFileUpdater orbitUpdater = new OrbitFileUpdater(_sourceProduct, orbitType);
            orbitUpdater.updateStateVector();

            _resultProduct = null;

        } catch (Exception e) {
            _exception = e;
        }
    }

    protected boolean verifyUserInput() {
        boolean b = super.verifyUserInput();

        boolean valid = true;

        return b && valid;
    }

    private void createUI() {
        _buttonVOR = new JRadioButton("Doris VOR");
        _buttonPOR = new JRadioButton("Doris POR");
        final ButtonGroup group = new ButtonGroup();
        group.add(_buttonVOR);
        group.add(_buttonPOR);

        _buttonVOR.setSelected(true);

        int line = 0;
        JPanel dialogPane = GridBagUtils.createPanel();
        final GridBagConstraints gbc = GridBagUtils.createDefaultConstraints();

        gbc.gridy = ++line;
        GridBagUtils.addToPanel(dialogPane, new JLabel("Input Product:"), gbc, "fill=BOTH, gridwidth=4");

        gbc.gridy = ++line;
        GridBagUtils.addToPanel(dialogPane, new JLabel(_sourceProduct.getDisplayName()), gbc);

        gbc.gridy = ++line;
        GridBagUtils.addToPanel(dialogPane, new JLabel("Doris Orbit "), gbc, "gridwidth=1");
        GridBagUtils.addToPanel(dialogPane, _buttonVOR, gbc);
        GridBagUtils.addToPanel(dialogPane, _buttonPOR, gbc);

        setContent(dialogPane);
    }

    private void updateUI() {

    }
}