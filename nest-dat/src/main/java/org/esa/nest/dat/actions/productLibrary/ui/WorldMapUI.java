package org.esa.nest.dat.actions.productLibrary.ui;

import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.ui.WorldMapPane;
import org.esa.beam.framework.ui.WorldMapPaneDataModel;
import org.esa.nest.db.ProductEntry;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Apr 13, 2010
 * Time: 1:48:38 PM
 * To change this template use File | Settings | File Templates.
 */
public class WorldMapUI {

    private final WorldMapPaneDataModel worldMapDataModel;
    private final WorldMapPane worlMapPane;

    public WorldMapUI() {

        worldMapDataModel = new WorldMapPaneDataModel();
        worlMapPane = new WorldMapPane(worldMapDataModel);
    }

    public WorldMapPane getWorlMapPane() {
        return worlMapPane;
    }

    public void setProductEntryList(final ProductEntry[] productEntryList) {

        final GeoPos[][] geoBoundaries = new GeoPos[productEntryList.length][4];
        int i = 0;
        for(ProductEntry entry : productEntryList) {
            final GeoPos[] geoBound = new GeoPos[4];
            geoBound[0] = entry.getFirstNearGeoPos();
            geoBound[1] = entry.getFirstFarGeoPos();
            geoBound[2] = entry.getLastFarGeoPos();
            geoBound[3] = entry.getLastNearGeoPos();
            geoBoundaries[i++] = geoBound;
        }

        worldMapDataModel.setAdditionalGeoBoundaries(geoBoundaries);
    }
}
