package org.esa.nest.dat.layersrc;

import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.binding.ValueContainer;
import com.bc.ceres.binding.ValueModel;
import com.bc.ceres.binding.ValueDescriptor;
import com.bc.ceres.binding.accessors.DefaultValueAccessor;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerContext;
import com.bc.ceres.glayer.LayerType;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.Band;

/**
 * The type descriptor of the {@link org.esa.nest.dat.layersrc.GCPVectorLayer}.
 *
 */
public class GCPVectorLayerType extends LayerType {

    static LayerType instance = new GCPVectorLayerType();

    public static GCPVectorLayer createLayer(final Product product, final Band band) {
        // todo - weird!!!
        final ValueContainer template = instance.getConfigurationTemplate();
        try {
            template.setValue("product", product);
            template.setValue("band", band);
        } catch (ValidationException e) {
            throw new IllegalStateException(e);
        }
        return new GCPVectorLayer(template);
    }

    @Override
    public String getName() {
        return "GCP Movement";
    }

    @Override
    public boolean isValidFor(LayerContext ctx) {
        // todo - need to check for availability of windu, windv  (nf)
        return true;
    }

    @Override
    protected Layer createLayerImpl(LayerContext ctx, ValueContainer configuration) {
        return new GCPVectorLayer(configuration);
    }

    // todo - rename getDefaultConfiguration  ? (nf)
    @Override
    public ValueContainer getConfigurationTemplate() {
        final ValueContainer valueContainer = new ValueContainer();
        // todo - how do I know whether my value model type can be serialized or not? (nf)
        valueContainer.addModel(new ValueModel(new ValueDescriptor("product", Product.class), new DefaultValueAccessor()));
        valueContainer.addModel(new ValueModel(new ValueDescriptor("band", Band.class), new DefaultValueAccessor()));
        return valueContainer;
    }
}