package org.esa.beam.framework.datamodel;

import java.awt.Image;
import java.awt.Point;

/**
 * Created by Marco Peters.
 *
 * @author Marco Peters
 * @version $Revision: 1.1 $ $Date: 2009-04-28 14:39:33 $
 */
public interface PlacemarkDescriptor {

    String getShowLayerCommandId();

    String getRoleName();

    String getRoleLabel();

    Image getCursorImage();

    ProductNodeGroup<Pin> getPlacemarkGroup(Product product);

    PlacemarkSymbol createDefaultSymbol();

    PixelPos updatePixelPos(GeoCoding geoCoding, GeoPos geoPos, PixelPos pixelPos);

    GeoPos updateGeoPos(GeoCoding geoCoding, PixelPos pixelPos, GeoPos geoPos);

    Point getCursorHotSpot();
}
