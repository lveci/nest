package org.esa.beam.visat.toolviews.imageinfo;

import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.ValueSet;
import com.bc.ceres.swing.binding.Binding;
import com.bc.ceres.swing.binding.BindingContext;
import com.jidesoft.combobox.ColorComboBox;
import org.esa.beam.framework.datamodel.ImageInfo;
import org.esa.beam.framework.ui.ColorComboBoxAdapter;

import javax.swing.*;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

class MoreOptionsForm {
    static final String GAIN_PROPERTY = "gain";
    static final String BIAS_PROPERTY = "bias";
    static final String LOG_SCALING_PROPERTY = "logScaling";
    static final String EXPONENT_PROPERTY = "exponent";
    static final String NO_DATA_COLOR_PROPERTY = "noDataColor";
    static final String HISTOGRAM_MATCHING_PROPERTY = "histogramMatching";

    private JPanel contentPanel;
    private GridBagConstraints constraints;
    private BindingContext bindingContext;

    private ColorManipulationForm parentForm;
    private boolean hasHistogramMatching;

    MoreOptionsForm(ColorManipulationForm parentForm, boolean hasHistogramMatching) {
        this.parentForm = parentForm;
        PropertyContainer propertyContainer = new PropertyContainer();

        propertyContainer.addProperty(Property.create(GAIN_PROPERTY, 1.0));
        propertyContainer.addProperty(Property.create(BIAS_PROPERTY, 0.0));
        propertyContainer.addProperty(Property.create(LOG_SCALING_PROPERTY, false));
        propertyContainer.addProperty(Property.create(EXPONENT_PROPERTY, 1.0));

        propertyContainer.addProperty(Property.create(NO_DATA_COLOR_PROPERTY, ImageInfo.NO_COLOR));

        this.hasHistogramMatching = hasHistogramMatching;
        if (this.hasHistogramMatching) {
            propertyContainer.addProperty(Property.create(HISTOGRAM_MATCHING_PROPERTY, ImageInfo.HistogramMatching.None));
            propertyContainer.getDescriptor(HISTOGRAM_MATCHING_PROPERTY).setNotNull(true);
            propertyContainer.getDescriptor(HISTOGRAM_MATCHING_PROPERTY).setValueSet(new ValueSet(
                    new ImageInfo.HistogramMatching[]{
                            ImageInfo.HistogramMatching.None,
                            ImageInfo.HistogramMatching.Equalize,
                            ImageInfo.HistogramMatching.Normalize,
                    })
            );
        }

        contentPanel = new JPanel(new GridBagLayout());

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.BASELINE;
        constraints.weightx = 0.5;
        constraints.weighty = 0.0;
        constraints.insets = new Insets(1, 0, 1, 0);

        bindingContext = new BindingContext(propertyContainer);

        final PropertyChangeListener pcl = new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                updateModel();
            }
        };

        // gain
        JLabel gainLabel = new JLabel("Gain: ");
        JTextField gainTextField = new JTextField();
        gainTextField.addKeyListener(new TextAreaKeyListener());
        Binding gainBinding = bindingContext.bind(GAIN_PROPERTY, gainTextField);
        gainBinding.addComponent(gainLabel);
        addRow(gainLabel, gainTextField);
        bindingContext.addPropertyChangeListener(GAIN_PROPERTY, pcl);

        // bias
        JLabel biasLabel = new JLabel("Bias: ");
        JTextField biasTextField = new JTextField();
        biasTextField.addKeyListener(new TextAreaKeyListener());
        Binding biasBinding = bindingContext.bind(BIAS_PROPERTY, biasTextField);
        biasBinding.addComponent(biasLabel);
        addRow(biasLabel, biasTextField);
        bindingContext.addPropertyChangeListener(BIAS_PROPERTY, pcl);

        // exponent
        JLabel exponentLabel = new JLabel("Exponent Scaling: ");
        JTextField exponentTextField = new JTextField();
        exponentTextField.addKeyListener(new TextAreaKeyListener());
        Binding exponentBinding = bindingContext.bind(EXPONENT_PROPERTY, exponentTextField);
        exponentBinding.addComponent(exponentLabel);
        addRow(exponentLabel, exponentTextField);
        bindingContext.addPropertyChangeListener(EXPONENT_PROPERTY, pcl);

        // logarithmic scaling
        final JLabel logLabel = new JLabel("Logarithmic Scaling: ");
        final JCheckBox logCheckBox = new JCheckBox();
        final Binding logBinding = bindingContext.bind(LOG_SCALING_PROPERTY, logCheckBox);
        logBinding.addComponent(logLabel);
        addRow(logLabel, logCheckBox);
        bindingContext.addPropertyChangeListener(LOG_SCALING_PROPERTY, pcl);

        JLabel noDataColorLabel = new JLabel("No-data colour: ");
        ColorComboBox noDataColorComboBox = new ColorComboBox();
        noDataColorComboBox.setColorValueVisible(false);
        noDataColorComboBox.setAllowDefaultColor(true);
        Binding noDataColorBinding = bindingContext.bind(NO_DATA_COLOR_PROPERTY, new ColorComboBoxAdapter(noDataColorComboBox));
        noDataColorBinding.addComponent(noDataColorLabel);
        addRow(noDataColorLabel, noDataColorComboBox);
        bindingContext.addPropertyChangeListener(NO_DATA_COLOR_PROPERTY, pcl);

        if (hasHistogramMatching) {
            JLabel histogramMatchingLabel = new JLabel("Histogram matching: ");
            JComboBox histogramMatchingBox = new JComboBox();
            Binding histogramMatchingBinding = bindingContext.bind(HISTOGRAM_MATCHING_PROPERTY, histogramMatchingBox);
            histogramMatchingBinding.addComponent(histogramMatchingLabel);
            addRow(histogramMatchingLabel, histogramMatchingBox);
            bindingContext.addPropertyChangeListener(HISTOGRAM_MATCHING_PROPERTY, pcl);
        }
    }

    private ImageInfo getImageInfo() {
        return getParentForm().getImageInfo();
    }

    public ColorManipulationForm getParentForm() {
        return parentForm;
    }

    public BindingContext getBindingContext() {
        return bindingContext;
    }

    public void addRow(JLabel label, JComponent editor) {
        constraints.gridwidth = 1;
        constraints.gridy++;
        constraints.gridx = 0;
        contentPanel.add(label, constraints);
        constraints.gridx = 1;
        contentPanel.add(editor, constraints);
    }

    public void addRow(JComponent editor) {
        constraints.gridwidth = 2;
        constraints.gridy++;
        constraints.gridx = 0;
        contentPanel.add(editor, constraints);
    }
        
    public void updateForm() {
        setNoDataColor(getImageInfo().getNoDataColor());
        if (hasHistogramMatching) {
            setHistogramMatching(getImageInfo().getHistogramMatching());
        }

        setGain(getImageInfo().getGain());
        setBias(getImageInfo().getBias());
        setExponent(getImageInfo().getExponent());
        setLogScaling(getImageInfo().getLog10Scaling());
    }

    public void updateModel() {
        getImageInfo().setNoDataColor(getNoDataColor());
        if (hasHistogramMatching) {
            getImageInfo().setHistogramMatching(getHistogramMatching());
        }

        getImageInfo().setGain(getGain());
        getImageInfo().setBias(getBias());
        getImageInfo().setExponent(getExponent());
        getImageInfo().setLog10Scaling(getLogScaling());
        
        getParentForm().setApplyEnabled(true);
    }

    public JPanel getContentPanel() {
        return contentPanel;
    }

    public void addPropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        bindingContext.addPropertyChangeListener(propertyChangeListener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener propertyChangeListener) {
        bindingContext.addPropertyChangeListener(propertyName, propertyChangeListener);
    }

    private Color getNoDataColor() {
        return (Color) getBindingContext().getBinding(NO_DATA_COLOR_PROPERTY).getPropertyValue();
    }

    private void setNoDataColor(Color color) {
        getBindingContext().getBinding(NO_DATA_COLOR_PROPERTY).setPropertyValue(color);
    }

    private ImageInfo.HistogramMatching getHistogramMatching() {
        return (ImageInfo.HistogramMatching) getBindingContext().getBinding(HISTOGRAM_MATCHING_PROPERTY).getPropertyValue();
    }

    private void setHistogramMatching(ImageInfo.HistogramMatching histogramMatching) {
        getBindingContext().getBinding(HISTOGRAM_MATCHING_PROPERTY).setPropertyValue(histogramMatching);
    }

    private double getGain() {
        return (Double)getBindingContext().getBinding(GAIN_PROPERTY).getPropertyValue();
    }

    private void setGain(double gain) {
        getBindingContext().getBinding(GAIN_PROPERTY).setPropertyValue(gain);
    }

    private double getBias() {
        return (Double)getBindingContext().getBinding(BIAS_PROPERTY).getPropertyValue();
    }

    private void setBias(double bias) {
        getBindingContext().getBinding(BIAS_PROPERTY).setPropertyValue(bias);
    }

    private double getExponent() {
        return (Double)getBindingContext().getBinding(EXPONENT_PROPERTY).getPropertyValue();
    }

    private void setExponent(double exp) {
        getBindingContext().getBinding(EXPONENT_PROPERTY).setPropertyValue(exp);
    }

    public boolean getLogScaling() {
        return (Boolean)getBindingContext().getBinding(LOG_SCALING_PROPERTY).getPropertyValue();
    }

    private void setLogScaling(boolean log) {
        getBindingContext().getBinding(LOG_SCALING_PROPERTY).setPropertyValue(log);
    }

    private class TextAreaKeyListener implements KeyListener {
        public void keyPressed(KeyEvent e) {
        }
        public void keyReleased(KeyEvent e) {
        }
        public void keyTyped(KeyEvent e) {
            updateModel();
        }
    }
}
