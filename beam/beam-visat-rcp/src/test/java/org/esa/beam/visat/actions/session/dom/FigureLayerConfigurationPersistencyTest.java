package org.esa.beam.visat.actions.session.dom;

import com.bc.ceres.binding.ValueContainer;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerType;
import org.esa.beam.framework.draw.Figure;
import org.esa.beam.framework.draw.LineFigure;
import org.esa.beam.glayer.FigureLayer;
import org.esa.beam.glayer.FigureLayerType;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FigureLayerConfigurationPersistencyTest extends AbstractLayerConfigurationPersistencyTest {

    public FigureLayerConfigurationPersistencyTest() {
        super(LayerType.getLayerType(FigureLayerType.class.getName()));
    }

    @Override
    protected Layer createLayer(LayerType layerType) throws Exception {
        final ValueContainer configuration = layerType.getConfigurationTemplate();
        final ArrayList<Figure> figureList = new ArrayList<Figure>();
        figureList.add(createFigure());
        configuration.setValue(FigureLayer.PROPERTY_NAME_FIGURE_LIST, figureList);

        return layerType.createLayer(null, configuration);
    }

    private LineFigure createFigure() {
        final Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("Beam Developer1", "Ralf");
        attributes.put("Beam Developer2", "Marco");

        return new LineFigure(new Rectangle(0, 0, 10, 10), attributes);
    }

}
