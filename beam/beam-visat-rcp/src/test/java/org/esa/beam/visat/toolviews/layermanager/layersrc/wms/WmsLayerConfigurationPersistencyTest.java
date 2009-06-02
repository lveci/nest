package org.esa.beam.visat.toolviews.layermanager.layersrc.wms;

import com.bc.ceres.binding.ConversionException;
import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.binding.ValueContainer;
import com.bc.ceres.binding.ValueDescriptor;
import com.bc.ceres.binding.ValueModel;
import com.bc.ceres.binding.dom.DefaultDomElement;
import com.bc.ceres.binding.dom.DomElement;
import com.bc.ceres.glayer.LayerType;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertSame;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductManager;
import org.esa.beam.framework.datamodel.VirtualBand;
import org.esa.beam.visat.actions.session.dom.SessionDomConverter;
import org.geotools.data.ows.CRSEnvelope;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;

import java.awt.Dimension;
import java.io.File;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;

public class WmsLayerConfigurationPersistencyTest {

    private ProductManager productManager;
    private Band band;

    @Before
    public void setup() {
        Product product = new Product("P", "T", 10, 10);
        product.setFileLocation(new File(String.format("out/%s.dim", product.getName())));

        band = new VirtualBand("V", ProductData.TYPE_INT32, 10, 10, "42");
        product.addBand(band);

        productManager = new ProductManager();
        productManager.addProduct(product);

    }

    @Test
    public void testPersistency() throws ValidationException, ConversionException, MalformedURLException {
        final WmsLayerType wmsLayerType = (WmsLayerType) LayerType.getLayerType(WmsLayerType.class.getName());
        final ValueContainer configuration = wmsLayerType.getConfigurationTemplate();
        configuration.setValue(WmsLayerType.PROPERTY_NAME_STYLE_NAME, "FancyStyle");
        configuration.setValue(WmsLayerType.PROPERTY_NAME_URL, new URL("http://www.mapserver.org"));
        configuration.setValue(WmsLayerType.PROPERTY_NAME_CRS_ENVELOPE, new CRSEnvelope("EPSG:4324", -10, 20, 15, 50));
        configuration.setValue(WmsLayerType.PROPERTY_NAME_IMAGE_SIZE, new Dimension(200, 300));
        configuration.setValue(WmsLayerType.PROPERTY_NAME_LAYER_INDEX, 12);
        configuration.setValue(WmsLayerType.PROPERTY_NAME_RASTER, band);
        final DomElement originalDomElement = new DefaultDomElement("configuration");
        final SessionDomConverter domConverter = new SessionDomConverter(productManager);

        domConverter.convertValueToDom(configuration, originalDomElement);
        // For debug purposes
//        System.out.println(originalDomElement.toXml());

        final ValueContainer restoredConfiguration = (ValueContainer) domConverter.convertDomToValue(originalDomElement,
                                                                                                     wmsLayerType.getConfigurationTemplate());
        compareConfigurations(configuration, restoredConfiguration);

    }

    private static void compareConfigurations(ValueContainer originalConfiguration,
                                              ValueContainer restoredConfiguration) {
        for (final ValueModel originalModel : originalConfiguration.getModels()) {
            final ValueDescriptor originalDescriptor = originalModel.getDescriptor();
            final ValueModel restoredModel = restoredConfiguration.getModel(originalDescriptor.getName());
            final ValueDescriptor restoredDescriptor = restoredModel.getDescriptor();

            assertNotNull(restoredModel);
            assertSame(originalDescriptor.getName(), restoredDescriptor.getName());
            assertSame(originalDescriptor.getType(), restoredDescriptor.getType());

            if (originalDescriptor.isTransient()) {
                assertEquals(originalDescriptor.isTransient(), restoredDescriptor.isTransient());
            } else {
                final Object originalValue = originalModel.getValue();
                final Object restoredValue = restoredModel.getValue();
                assertSame(originalValue.getClass(), restoredValue.getClass());

                if (originalValue.getClass().isArray()) {
                    final int originalLength = Array.getLength(originalValue);
                    final int restoredLength = Array.getLength(restoredValue);

                    assertEquals(originalLength, restoredLength);
                    for (int i = 0; i < restoredLength; i++) {
                        assertEquals(Array.get(originalValue, i), Array.get(restoredValue, i));
                    }
                }
            }
        }
    }

}