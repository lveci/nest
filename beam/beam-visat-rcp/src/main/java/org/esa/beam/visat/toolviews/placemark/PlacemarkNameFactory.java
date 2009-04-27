package org.esa.beam.visat.toolviews.placemark;

import com.bc.ceres.core.Assert;
import org.esa.beam.framework.datamodel.Pin;
import org.esa.beam.framework.datamodel.PlacemarkDescriptor;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNodeGroup;

/**
 * Created by Marco Peters.
 *
 * @author Marco Peters
 * @version $Revision: 1.1 $ $Date: 2009-04-27 13:08:26 $
 */
public class PlacemarkNameFactory {

    private PlacemarkNameFactory() {
    }

    public static String[] createUniqueNameAndLabel(PlacemarkDescriptor placemarkDescriptor, Product product) {
        ProductNodeGroup<Pin> placemarkGroup = placemarkDescriptor.getPlacemarkGroup(product);
        return createUniqueNameAndLabel(placemarkDescriptor, placemarkGroup);
    }

    public static String[] createUniqueNameAndLabel(PlacemarkDescriptor placemarkDescriptor, ProductNodeGroup<Pin> group) {
        int pinNumber = group.getNodeCount() + 1;
        String name = PlacemarkNameFactory.createName(placemarkDescriptor, pinNumber);
        while (group.get(name) != null) {
            name = PlacemarkNameFactory.createName(placemarkDescriptor, ++pinNumber);
        }
        final String label = PlacemarkNameFactory.createLabel(placemarkDescriptor, pinNumber, true);
        return new String[]{name, label};
    }

    public static String createLabel(PlacemarkDescriptor placemarkDescriptor, int pinNumber, boolean firstLetterIsUpperCase) {
        Assert.argument(placemarkDescriptor.getRoleLabel().length() > 0, "placemarkDescriptor.getRoleLabel()");
        String name = placemarkDescriptor.getRoleLabel();
        if (firstLetterIsUpperCase) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return name + " " + pinNumber;
    }

    public static String createName(PlacemarkDescriptor placemarkDescriptor, int pinNumber) {
        return placemarkDescriptor.getRoleName() + "_" + pinNumber;
    }

}
