package org.esa.beam.framework.gpf.ui;

import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.ValueEditorsPane;

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

        final ValueEditorsPane parametersPane = new ValueEditorsPane(context);
        return new JScrollPane(parametersPane.createPanel());
    }

    @Override
    public void initParameters() {
        if(valueContainer == null) return;
        
        for(ValueModel model : valueContainer.getModels()) {
            final ValueDescriptor descriptor = model.getDescriptor();
            final String itemAlias = descriptor.getItemAlias();

            if(sourceProducts != null && itemAlias != null && itemAlias.equals("band")) {
                final String[] bandNames = getBandNames();
                if(bandNames.length > 0) {
                    final ValueSet valueSet = new ValueSet(bandNames);
                    descriptor.setValueSet(valueSet);

                    try {
                        if(descriptor.getType().isArray()) {
                            if(model.getValue() == null)
                                model.setValue(new String[] {bandNames[0]});    
                        } else {
                            model.setValue(bandNames[0]);
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
