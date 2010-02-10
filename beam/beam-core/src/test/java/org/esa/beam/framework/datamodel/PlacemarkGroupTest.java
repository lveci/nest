package org.esa.beam.framework.datamodel;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.junit.Before;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import static org.junit.Assert.*;

public class PlacemarkGroupTest {

    private SimpleFeatureBuilder pinBuilder;
    private PlacemarkGroup placemarkGroup;
    private FeatureCollection<SimpleFeatureType, SimpleFeature> pinFeatureCollection;

    @Before()
    public void setup() {
        Product product = new Product("PinGroup Test", "TestType", 10, 10);
        VectorDataNode pinVectorDataNode = new VectorDataNode("pins", Placemark.getFeatureType());
        product.getVectorDataGroup().add(pinVectorDataNode);
        pinBuilder = new SimpleFeatureBuilder(Placemark.getFeatureType());
        placemarkGroup = new PlacemarkGroup(product, "pinGroup", pinVectorDataNode);
        pinFeatureCollection = pinVectorDataNode.getFeatureCollection();
    }

    @Test
    public void testManipulatingPinGroup() {
        assertAreEqual(placemarkGroup, pinFeatureCollection);

        placemarkGroup.add(createPlacemark("p1", new PixelPos(3, 1), new GeoPos(12, 34)));
        assertAreEqual(placemarkGroup, pinFeatureCollection);
        final Placemark placemark2 = createPlacemark("p2", new PixelPos(5, 4), new GeoPos(16, 48));
        placemarkGroup.add(placemark2);
        assertAreEqual(placemarkGroup, pinFeatureCollection);
        placemarkGroup.add(createPlacemark("p3", new PixelPos(6, 2), new GeoPos(-45, 80)));
        assertAreEqual(placemarkGroup, pinFeatureCollection);

        placemarkGroup.remove(placemark2);
        assertAreEqual(placemarkGroup, pinFeatureCollection);

        placemarkGroup.add(1, createPlacemark("p4", new PixelPos(6, 3), new GeoPos(-60, 47)));
        assertAreEqual(placemarkGroup, pinFeatureCollection);
    }

    @Test
    public void testManipulatingFeatureCollection() {
        assertAreEqual(placemarkGroup, pinFeatureCollection);

        pinFeatureCollection.add(createPinFeature());
        assertAreEqual(placemarkGroup, pinFeatureCollection);
        final SimpleFeature simpleFeature = createPinFeature();
        pinFeatureCollection.add(simpleFeature);
        assertAreEqual(placemarkGroup, pinFeatureCollection);
        pinFeatureCollection.add(createPinFeature());
        assertAreEqual(placemarkGroup, pinFeatureCollection);

        pinFeatureCollection.remove(simpleFeature);
        assertAreEqual(placemarkGroup, pinFeatureCollection);

        pinFeatureCollection.add(createPinFeature());
        assertAreEqual(placemarkGroup, pinFeatureCollection);
    }

    @Test
    public void testChangingFeature() {
        placemarkGroup.add(createPlacemark("p1", new PixelPos(3, 1), new GeoPos(12, 34)));
        placemarkGroup.add(createPlacemark("p2", new PixelPos(5, 4), new GeoPos(16, 48)));
        placemarkGroup.add(createPlacemark("p3", new PixelPos(6, 2), new GeoPos(-45, 80)));
        assertAreEqual(placemarkGroup, pinFeatureCollection);
        final CoordinateArraySequence coordinates = new CoordinateArraySequence(
                new Coordinate[]{new Coordinate(-30, 70)});
        final SimpleFeature changedFeature = placemarkGroup.get(2).getFeature();
        changedFeature.setDefaultGeometry(new Point(coordinates, new GeometryFactory()));
        assertAreEqual(placemarkGroup, pinFeatureCollection);

        final SimpleFeature[] features = pinFeatureCollection.toArray(new SimpleFeature[pinFeatureCollection.size()]);
        assertEquals(changedFeature.getDefaultGeometry(), features[2].getDefaultGeometry());
    }

    private SimpleFeature createPinFeature() {
        return pinBuilder.buildFeature(String.valueOf(System.currentTimeMillis()));
    }

    private static void assertAreEqual(PlacemarkGroup group, FeatureCollection<SimpleFeatureType, SimpleFeature> collection) {
        final SimpleFeature[] features = collection.toArray(new SimpleFeature[collection.size()]);
        assertEquals(group.getNodeCount(), features.length);
        for (int i = 0; i < group.getNodeCount(); i++) {
            SimpleFeature pinGroupFeature = group.get(i).getFeature();
            assertTrue("Feature of pin group is not contained in feature collection\n",
                       collection.contains(pinGroupFeature));

        }
    }

    private Placemark createPlacemark(String name, PixelPos pixelPos, GeoPos geoPos) {
        return new Placemark(name, "", "", pixelPos, geoPos, PinDescriptor.INSTANCE.createDefaultSymbol(), null);
    }
}
