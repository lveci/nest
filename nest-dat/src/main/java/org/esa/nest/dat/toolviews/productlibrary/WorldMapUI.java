package org.esa.nest.dat.toolviews.productlibrary;

import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.ui.WorldMapPane;
import org.esa.beam.framework.ui.WorldMapPaneDataModel;
import org.esa.nest.dat.toolviews.worldmap.NestWorldMapPane;
import org.esa.nest.db.AOI;
import org.esa.nest.db.ProductEntry;

import javax.swing.event.MouseInputAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**

 */
public class WorldMapUI {

    private final WorldMapPaneDataModel worldMapDataModel;
    private final NestWorldMapPane worlMapPane;

    private final ArrayList<DatabaseQueryListener> listenerList = new ArrayList<DatabaseQueryListener>(1);

    public WorldMapUI() {

        worldMapDataModel = new WorldMapPaneDataModel();
        worlMapPane = new NestWorldMapPane(worldMapDataModel);
        worlMapPane.getLayerCanvas().addMouseListener(new MouseHandler());
    }

    /**
     * Adds a <code>DatabasePaneListener</code>.
     *
     * @param listener the <code>DatabasePaneListener</code> to be added.
     */
    public void addListener(final DatabaseQueryListener listener) {
        if (!listenerList.contains(listener)) {
            listenerList.add(listener);
        }
    }

    public GeoPos[] getSelectionBox() {
        return worldMapDataModel.getSelectionBox();
    }

    public void setSelectionStart(final float lat, final float lon) {
        worldMapDataModel.setSelectionBoxStart(lat, lon);
    }

    public void setSelectionEnd(final float lat, final float lon) {
        worldMapDataModel.setSelectionBoxEnd(lat, lon);
    }

    /**
     * Removes a <code>DatabasePaneListener</code>.
     *
     * @param listener the <code>DatabasePaneListener</code> to be removed.
     */
    public void removeListener(final DatabaseQueryListener listener) {
        listenerList.remove(listener);
    }

    private void notifyQuery() {
        for (final DatabaseQueryListener listener : listenerList) {
            listener.notifyNewMapSelectionAvailable();
        }
    }

    public WorldMapPane getWorlMapPane() {
        return worlMapPane;
    }

    public void setAOIList(final AOI[] aoiList) {
        final GeoPos[][] geoBoundaries = new GeoPos[aoiList.length][4];
        int i = 0;
        for(AOI aoi : aoiList) {
            geoBoundaries[i++] = aoi.getAOIPoints();
        }

        worldMapDataModel.setAdditionalGeoBoundaries(geoBoundaries);
    }

    public void setSelectedAOIList(final AOI[] selectedAOIList) {
        final GeoPos[][] geoBoundaries = new GeoPos[selectedAOIList.length][4];
        int i = 0;
        for(AOI aoi : selectedAOIList) {
            geoBoundaries[i++] = aoi.getAOIPoints();
        }

        worldMapDataModel.setSelectedGeoBoundaries(geoBoundaries);
    }

    public void setProductEntryList(final ProductEntry[] productEntryList) {

        final GeoPos[][] geoBoundaries = new GeoPos[productEntryList.length][4];
        int i = 0;
        for(ProductEntry entry : productEntryList) {
            geoBoundaries[i++] = entry.getBox();
        }

        worldMapDataModel.setAdditionalGeoBoundaries(geoBoundaries);
    }

    public void setSelectedProductEntryList(final ProductEntry[] selectedProductEntryList) {

        final GeoPos[][] geoBoundaries = new GeoPos[selectedProductEntryList.length][4];
        int i = 0;
        for(ProductEntry entry : selectedProductEntryList) {
            geoBoundaries[i++] = entry.getBox();
        }

        worldMapDataModel.setSelectedGeoBoundaries(geoBoundaries);
    }

    private class MouseHandler extends MouseInputAdapter {

        @Override
        public void mouseReleased(MouseEvent e) {
            if(e.getButton() == MouseEvent.BUTTON1) {
                notifyQuery();
            }
        }
    }
}
