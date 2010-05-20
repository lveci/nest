package org.esa.nest.db;

import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.util.StringUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.util.SQLUtils;

import java.awt.*;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**

 */
public class DBQuery {

    public static final String ALL_MISSIONS = "All Missions";
    public static final String ALL_PRODUCT_TYPES = "All Types";
    public static final String ALL_PASSES = "All Passes";
    public static final String ASCENDING_PASS = "ASCENDING";
    public static final String DESCENDING_PASS = "DESCENDING";
    public static final String ALL_FOLDERS = "All Folders";

    private String selectedMissions[] = {};
    private String selectedProductTypes[] = {};
    private String selectedPass = "";
    private Rectangle.Float selectionRectangle = null;
    private File baseDir = null;
    private final Map<String, String> metadataQueryMap = new HashMap<String, String>();

    public DBQuery() {
    }

    public void setSelectedMissions(final String[] missions) {
        selectedMissions = missions;
    }

    public void setSelectedProductTypes(final String[] productTypes) {
        selectedProductTypes = productTypes;
    }

    public void setSelectedPass(final String pass) {
        selectedPass = pass;
    }

    public void setBaseDir(final File dir) {
        baseDir = dir;
    }

    public void clearMetadataQuery() {
        metadataQueryMap.clear();
    }

    public void addMetadataQuery(final String name, final String value) {
        metadataQueryMap.put(name, value);
    }

    public ProductEntry[] queryDatabase(final ProductDB db) throws SQLException {

        if(StringUtils.contains(selectedMissions, ALL_MISSIONS))
            selectedMissions = new String[] {};
        if(StringUtils.contains(selectedProductTypes, ALL_PRODUCT_TYPES))
            selectedProductTypes = new String[] {};
        if(selectedPass.equals(ALL_PASSES))
            selectedPass = "";

        String queryStr = "";
        if(selectedMissions.length > 0) {
            queryStr += SQLUtils.getOrList(AbstractMetadata.MISSION, selectedMissions);
        }
        if(selectedProductTypes.length > 0) {
            if(!queryStr.isEmpty())
                queryStr += " AND ";
            queryStr += SQLUtils.getOrList(AbstractMetadata.PRODUCT_TYPE, selectedProductTypes);
        }
        if(!selectedPass.isEmpty()) {
            if(!queryStr.isEmpty())
                queryStr += " AND ";
            queryStr += AbstractMetadata.PASS+"='"+selectedPass+"'";
        }

        final Set<String> metadataNames = metadataQueryMap.keySet();
        for(String name : metadataNames) {
            final String value = metadataQueryMap.get(name);
            if(value != null && !value.isEmpty()) {
                if(!queryStr.isEmpty())
                    queryStr += " AND ";
                queryStr += name+"='"+value+"'";
            }
        }

        if(baseDir != null) {
            if(!queryStr.isEmpty())
                queryStr += " AND ";
            queryStr += AbstractMetadata.PATH+" LIKE '"+baseDir.getAbsolutePath()+"%'";
        }

        if(queryStr.isEmpty()) {
            return instersectMapSelection(db.getProductEntryList());
        } else {
            return instersectMapSelection(db.queryProduct(queryStr));
        }
    }

    public void setSelectionRect(final GeoPos[] selectionBox) {
        selectionRectangle = getBoundingRect(selectionBox);
    }

    private ProductEntry[] instersectMapSelection(final ProductEntry[] resultsList) {
        if(selectionRectangle != null && selectionRectangle.getWidth() != 0 && selectionRectangle.getHeight() != 0) {
            final ArrayList<ProductEntry> intersectList = new ArrayList<ProductEntry>();
            for(ProductEntry entry : resultsList) {
                final GeoPos start = entry.getFirstNearGeoPos();
                final GeoPos end = entry.getLastFarGeoPos();
                final float w = Math.abs(end.getLon()-start.getLon());
                final float h = Math.abs(end.getLat()-start.getLat());
                final Rectangle.Float entryRect = new Rectangle.Float(start.getLon(), start.getLat(), w, h);
                if(selectionRectangle.intersects(entryRect)) {
                    intersectList.add(entry);
                }
            }
            return intersectList.toArray(new ProductEntry[intersectList.size()]);
        }
        return resultsList;
    }

    private static Rectangle.Float getBoundingRect(final GeoPos[] geoPositions) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (final GeoPos pos : geoPositions) {
            final float x = pos.getLon();
            final float y = pos.getLat();

            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }
        }
        if (minX >= maxX || minY >= maxY) {
            return null;
        }

        return new Rectangle.Float(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}
