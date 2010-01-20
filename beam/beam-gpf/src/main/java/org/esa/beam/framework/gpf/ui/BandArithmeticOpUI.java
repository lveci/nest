package org.esa.beam.framework.gpf.ui;

import org.esa.beam.framework.gpf.operators.common.BandMathOp;
import org.esa.beam.framework.ui.*;
import org.esa.beam.framework.ui.product.ProductExpressionPane;
import org.esa.beam.framework.param.*;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.barithm.BandArithmetic;
import org.esa.beam.util.Debug;
import org.esa.beam.util.PropertyMap;

import javax.swing.*;

import java.util.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import com.bc.jexp.Namespace;
import com.bc.jexp.Parser;
import com.bc.jexp.ParseException;
import com.bc.jexp.impl.ParserImpl;

/**
User interface for BandArithetic Operator
 */
public class BandArithmeticOpUI extends BaseOperatorUI {

    private static final String _PARAM_NAME_BAND = "targetBand";

    private Parameter _paramBand = null;
    private Parameter _paramExpression = null;
    private Product _targetProduct = null;
    private Band _targetBand = null;
    private ProductNodeList<Product> _productsList = null;
    private JButton _editExpressionButton = null;
    private JComponent panel = null;
    private String errorText = "";

    private BandMathOp.BandDescriptor bandDesc = new BandMathOp.BandDescriptor();

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        initVariables();
        panel = createUI();
        initParameters();

        bandDesc.name =  "band";
        bandDesc.type =  ProductData.TYPESTRING_FLOAT32;
        bandDesc.expression = " ";

        return panel;
    }

    @Override
    public void initParameters() {

        Object[] bandDescriptors = (Object[])paramMap.get("targetBands");
        if(bandDescriptors == null)
            bandDescriptors = (Object[])paramMap.get("targetBandDescriptors");
        if(bandDescriptors !=null && bandDescriptors.length > 0) {
            bandDesc = (BandMathOp.BandDescriptor)bandDescriptors[0];
            bandDesc.type = ProductData.TYPESTRING_FLOAT32;

            try {
                _paramBand.setValueAsText(bandDesc.name);
                _paramExpression.setValueAsText(bandDesc.expression);
            } catch(Exception e) {
                //
            }
        }
        if(sourceProducts != null && sourceProducts.length > 0) {
            _targetProduct = sourceProducts[0];

            _targetBand = new Band(bandDesc.name, ProductData.TYPE_FLOAT32,
                    _targetProduct.getSceneRasterWidth(), _targetProduct.getSceneRasterHeight());
            _targetBand.setDescription("");
            //_targetBand.setUnit(dialog.getNewBandsUnit());

            _productsList = new ProductNodeList<Product>();
            for (Product prod : sourceProducts) {
                _productsList.add(prod);
            }
        } else {
            _targetProduct = null;
            _targetBand = null;
        }
        updateUIState(_paramBand.getName());
    }

    @Override
    public UIValidation validateParameters() {
        if(!(_targetProduct == null || isValidExpression()))
            return new UIValidation(UIValidation.State.ERROR, "Expression is invalid. "+ errorText);
        return new UIValidation(UIValidation.State.ERROR, "");
    }

    @Override
    public void updateParameters() {

        bandDesc.name = _paramBand.getValueAsText();
        bandDesc.expression = _paramExpression.getValueAsText();

        final BandMathOp.BandDescriptor[] bandDescriptors = new BandMathOp.BandDescriptor[1];
        bandDescriptors[0] = bandDesc;
        paramMap.put("targetBandDescriptors", bandDescriptors);
    }

    private void initVariables() {
        final ParamChangeListener paramChangeListener = createParamChangeListener();

        _paramBand = new Parameter(_PARAM_NAME_BAND, "new_band");
        _paramBand.getProperties().setValueSetBound(false);
        _paramBand.getProperties().setLabel("Target Band"); /*I18N*/
        _paramBand.addParamChangeListener(paramChangeListener);

        _paramExpression = new Parameter("arithmetikExpr", "");
        _paramExpression.getProperties().setLabel("Expression"); /*I18N*/
        _paramExpression.getProperties().setDescription("Arithmetic expression"); /*I18N*/
        _paramExpression.getProperties().setNumRows(3);
//        _paramExpression.getProperties().setEditorClass(ArithmetikExpressionEditor.class);
//        _paramExpression.getProperties().setValidatorClass(BandArithmeticExprValidator.class);

        setArithmetikValues();
    }

    private JComponent createUI() {

        _editExpressionButton = new JButton("Edit Expression...");
        _editExpressionButton.setName("editExpressionButton");
        _editExpressionButton.addActionListener(createEditExpressionButtonListener());

        final JPanel gridPanel = GridBagUtils.createPanel();
        int line = 0;
        final GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridy = ++line;
        GridBagUtils.addToPanel(gridPanel, _paramBand.getEditor().getLabelComponent(), gbc,
                                "insets.top=4, gridwidth=3, fill=BOTH, anchor=WEST");
        gbc.gridy = ++line;
        GridBagUtils.addToPanel(gridPanel, _paramBand.getEditor().getComponent(), gbc,
                                "insets.top=3, gridwidth=3, fill=BOTH, anchor=WEST");

        gbc.gridy = ++line;
        GridBagUtils.addToPanel(gridPanel, _paramExpression.getEditor().getLabelComponent(), gbc,
                                "insets.top=4, gridwidth=3, anchor=WEST");
        gbc.gridy = ++line;
        GridBagUtils.addToPanel(gridPanel, _paramExpression.getEditor().getComponent(), gbc,
                                "weighty=1, insets.top=3, gridwidth=3, fill=BOTH, anchor=WEST");
        gbc.gridy = ++line;
        GridBagUtils.addToPanel(gridPanel, _editExpressionButton, gbc,
                                "weighty=0, insets.top=3, gridwidth=3, fill=NONE, anchor=EAST");

        return gridPanel;
    }

    private void setArithmetikValues() {
        final ParamProperties props = _paramExpression.getProperties();
        props.setPropertyValue(ParamProperties.COMP_PRODUCTS_FOR_BAND_ARITHMETHIK_KEY
                , getCompatibleProducts());
        props.setPropertyValue(ParamProperties.SEL_PRODUCT_FOR_BAND_ARITHMETHIK_KEY
                , _targetProduct);
    }

     private ParamChangeListener createParamChangeListener() {
        return new ParamChangeListener() {

            public void parameterValueChanged(ParamChangeEvent event) {
                updateUIState(event.getParameter().getName());
            }
        };
    }

    private Product[] getCompatibleProducts() {
        if (_targetProduct == null) {
            return null;
        }
        final Vector<Product> compatibleProducts = new Vector<Product>();
        compatibleProducts.add(_targetProduct);
            final float geolocationEps = 10;
            Debug.trace("BandArithmetikDialog.geolocationEps = " + geolocationEps);
            Debug.trace("BandArithmetikDialog.getCompatibleProducts:");
            Debug.trace("  comparing: " + _targetProduct.getName());
            for (int i = 0; i < _productsList.size(); i++) {
                final Product product = _productsList.getAt(i);
                if (_targetProduct != product) {
                    Debug.trace("  with:      " + product.getDisplayName());
                    final boolean compatibleProduct = _targetProduct.isCompatibleProduct(product, geolocationEps);
                    Debug.trace("  result:    " + compatibleProduct);
                    if (compatibleProduct) {
                        compatibleProducts.add(product);
                    }
                }
            }
        return compatibleProducts.toArray(new Product[compatibleProducts.size()]);
    }

    private ActionListener createEditExpressionButtonListener() {
        return new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                ProductExpressionPane pep = ProductExpressionPane.createGeneralExpressionPane(getCompatibleProducts(),
                                                                                              _targetProduct,
                                                                                              new PropertyMap());
                pep.setCode(_paramExpression.getValueAsText());
                int status = pep.showModalDialog(SwingUtilities.getWindowAncestor(panel), "Arithmetic Expression Editor");
                if (status == ModalDialog.ID_OK) {
                    _paramExpression.setValue(pep.getCode(), null);
                    Debug.trace("BandArithmetikDialog: expression is: " + pep.getCode());

                    bandDesc.expression = _paramExpression.getValueAsText();
                }
                pep.dispose();
                pep = null;
            }
        };
    }

    private boolean isValidExpression() {
        errorText = "";
        final Product[] products = getCompatibleProducts();
        if (products == null || products.length == 0) {
            return false;
        }

        final String expression = _paramExpression.getValueAsText();
        if (expression == null || expression.length() == 0) {
            return false;
        }

        final int defaultIndex = 0;//Arrays.asList(products).indexOf(_visatApp.getSelectedProduct());
        final Namespace namespace = BandArithmetic.createDefaultNamespace(products,
                                                                          defaultIndex == -1 ? 0 : defaultIndex);
        final Parser parser = new ParserImpl(namespace, false);
        try {
            parser.parse(expression);
        } catch (ParseException e) {
            errorText = e.getMessage();
            return false;
        }
        return true;
    }

    private void updateUIState(String parameterName) {

        if (parameterName == null) {
            return;
        }

        if (parameterName.equals(_PARAM_NAME_BAND)) {
            final boolean b = _targetProduct != null;
            _paramExpression.setUIEnabled(b);
            _editExpressionButton.setEnabled(b);
            _paramBand.setUIEnabled(b);
            if (b) {
                setArithmetikValues();
            }

            final String selectedBandName = _paramBand.getValueAsText();
            Band band = null;
            if (b) {
                if (selectedBandName != null && selectedBandName.length() > 0) {
                    _targetBand = _targetProduct.getBand(selectedBandName);
                }
            }
        }
    }

}