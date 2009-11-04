package org.esa.beam.visat.toolviews.layermanager.editors;

import com.bc.ceres.binding.PropertyDescriptor;
import com.bc.ceres.binding.swing.BindingContext;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.glayer.PlacemarkLayer;

import java.awt.Color;

/**
 * Editor for placemark layers.
 *
 * @author Ralf Quast
 * @version $Revision: 1.3 $ $Date: 2009-11-04 17:04:32 $
 * @since BEAM 4.6
 */
public class PlacemarkLayerEditor extends AbstractBindingLayerEditor {

    @Override
    protected void initializeBinding(AppContext appContext, BindingContext bindingContext) {
        PropertyDescriptor vd0 = new PropertyDescriptor(PlacemarkLayer.PROPERTY_NAME_TEXT_ENABLED, Boolean.class);
        vd0.setDefaultValue(PlacemarkLayer.DEFAULT_TEXT_ENABLED);
        vd0.setDisplayName("Text enabled");
        addValueDescriptor(vd0);

        PropertyDescriptor vd1 = new PropertyDescriptor(PlacemarkLayer.PROPERTY_NAME_TEXT_FG_COLOR, Color.class);
        vd1.setDefaultValue(PlacemarkLayer.DEFAULT_TEXT_FG_COLOR);
        vd1.setDisplayName("Text foreground colour");
        addValueDescriptor(vd1);

        PropertyDescriptor vd2 = new PropertyDescriptor(PlacemarkLayer.PROPERTY_NAME_TEXT_BG_COLOR, Color.class);
        vd2.setDefaultValue(PlacemarkLayer.DEFAULT_TEXT_BG_COLOR);
        vd2.setDisplayName("Text background colour");
        addValueDescriptor(vd2);

    }

}
