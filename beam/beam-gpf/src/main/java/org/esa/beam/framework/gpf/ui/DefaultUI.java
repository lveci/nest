package org.esa.beam.framework.gpf.ui;

import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.PropertyPane;

import javax.swing.*;
import com.bc.ceres.binding.*;
import com.bc.ceres.binding.swing.BindingContext;

import java.util.Map;

/**
 * Default OperatorUI for operators using @parameter
 */
public class DefaultUI extends BaseOperatorUI {

    @Override
    public JComponent CreateOpTab(final String operatorName,
                                  final Map<String, Object> parameterMap, final AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        final BindingContext context = new BindingContext(valueContainer);

        final PropertyPane parametersPane = new PropertyPane(context);
        return new JScrollPane(parametersPane.createPanel());
    }

    @Override
    public void initParameters() {
        if(valueContainer == null) return;

        final Property[] properties = valueContainer.getProperties();
        for(Property p : properties) {
            final PropertyDescriptor descriptor = p.getDescriptor();
            final String itemAlias = descriptor.getItemAlias();

            if(sourceProducts != null && itemAlias != null && itemAlias.equals("band")) {
                final String[] bandNames = getBandNames();
                if(bandNames.length > 0) {
                    final ValueSet valueSet = new ValueSet(bandNames);
                    descriptor.setValueSet(valueSet);

                    try {
                        if(descriptor.getType().isArray()) {
                            if(p.getValue() == null)
                                p.setValue(new String[] {bandNames[0]});
                        } else {
                            p.setValue(bandNames[0]);
                        }
                    } catch(ValidationException e) {
                        System.out.println(e.toString());
                    }  
                }
            }
        }
    }

    @Override
    public UIValidation validateParameters() {

        //todo how to validate generated UIs? should be in operator initialize
        return new UIValidation(true, "");
    }

    @Override
    public void updateParameters() {

    }
}
