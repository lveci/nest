package org.esa.beam.visat.actions.session.dom;

import com.bc.ceres.binding.ValueContainer;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerType;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.glayer.NoDataLayerType;
import org.junit.After;
import org.junit.Before;

import java.awt.Color;
import java.awt.geom.AffineTransform;

public class NoDataLayerConfigurationPersistencyTest extends AbstractLayerConfigurationPersistencyTest {

    private Product product;
    private RasterDataNode raster;

    public NoDataLayerConfigurationPersistencyTest() {
        super(LayerType.getLayerType(NoDataLayerType.class.getName()));
    }

    @Before
    public void setup() {
        product = createTestProduct("Test", "Test");
        raster = addVirtualBand(product, "virtualBand", ProductData.TYPE_INT32, "17");

        getProductManager().addProduct(product);
    }

    @After
    public void tearDown() {
        getProductManager().removeProduct(product);
    }

    @Override
    protected Layer createLayer(LayerType layerType) throws Exception {
        final ValueContainer configuration = layerType.getConfigurationTemplate();

        configuration.setValue("raster", raster);
        configuration.setValue("color", new Color(17, 11, 67));
        configuration.setValue("imageToModelTransform", new AffineTransform());

        return layerType.createLayer(null, configuration);
    }
}