package org.esa.nest.db;

import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.util.StringUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.util.SQLUtils;
import org.esa.nest.util.XMLSupport;
import org.jdom.Attribute;
import org.jdom.Element;

import java.awt.*;
import java.io.File;
import java.sql.SQLException;
import java.util.*;

/**

 */
public class DBQuery {

    public static final String ALL_MISSIONS = "All Missions";
    public static final String ALL_PRODUCT_TYPES = "All Types";
    public static final String ALL_PASSES = "All Passes";
    public static final String ALL_MODES = "All Modes";
    public static final String ASCENDING_PASS = "ASCENDING";
    public static final String DESCENDING_PASS = "DESCENDING";
    public static final String ALL_FOLDERS = "All Folders";
    public static final String DB_QUERY = "dbQuery";

    private String selectedMissions[] = {};
    private String selectedProductTypes[] = {};
    private String selectedAcquisitionMode = "";
    private String selectedPass = "";
    private Rectangle.Float selectionRectangle = null;
    private File baseDir = null;
    private File excludeDir = null;
    private Calendar startDate = null;
    private Calendar endDate = null;
    private String freeQuery = "";

    private final Map<String, String> metadataQueryMap = new HashMap<String, String>();

    public DBQuery() {
    }

    public void setSelectedMissions(final String[] missions) {
        selectedMissions = missions;
    }

    public String[] getSelectedMissions() {
        return selectedMissions;
    }

    public void setSelectedProductTypes(final String[] productTypes) {
        selectedProductTypes = productTypes;
    }

    public String[] getSelectedProductTypes() {
        return selectedProductTypes;
    }

    public void setSelectedAcquisitionMode(final String mode) {
        if(mode != null)
            selectedAcquisitionMode = mode;
    }

    public String getSelectedAcquisitionMode() {
        return selectedAcquisitionMode;
    }

    public void setSelectedPass(final String pass) {
        if(pass != null)
            selectedPass = pass;
    }

    public String getSelectedPass() {
        return selectedPass;
    }

    public void setBaseDir(final File dir) {
        baseDir = dir;
    }

    public void setExcludeDir(final File dir) {
        excludeDir = dir;
    }

    public void setStartEndDate(final Calendar start, final Calendar end) {
        startDate = start;
        endDate = end;
    }

    public Calendar getStartDate() {
        return startDate;
    }

    public Calendar getEndDate() {
        return endDate;
    }

    public void clearMetadataQuery() {
        metadataQueryMap.clear();
    }

    public void addMetadataQuery(final String name, final String value) {
        metadataQueryMap.put(name, value);
    }

    public void setFreeQuery(final String queryStr) {
        freeQuery = queryStr;
    }

    public String getFreeQuery() {
        return freeQuery;
    }

    public ProductEntry[] queryDatabase(final ProductDB db) throws SQLException {

        if(StringUtils.contains(selectedMissions, ALL_MISSIONS))
            selectedMissions = new String[] {};
        if(StringUtils.contains(selectedProductTypes, ALL_PRODUCT_TYPES))
            selectedProductTypes = new String[] {};
        if(selectedAcquisitionMode.equals(ALL_MODES))
            selectedAcquisitionMode = "";
        if(selectedPass.equals(ALL_PASSES))
            selectedPass = "";

        String queryStr = "";
        if(selectedMissions.length > 0) {
            queryStr += SQLUtils.getOrList(ProductDB.PROD_TABLE+'.'+AbstractMetadata.MISSION, selectedMissions);
        }
        if(selectedProductTypes.length > 0) {
            queryStr += SQLUtils.addAND(queryStr);
            queryStr += SQLUtils.getOrList(ProductDB.PROD_TABLE+'.'+AbstractMetadata.PRODUCT_TYPE, selectedProductTypes);
        }
        if(!selectedAcquisitionMode.isEmpty()) {
            queryStr += SQLUtils.addAND(queryStr);
            queryStr += ProductDB.PROD_TABLE+'.'+AbstractMetadata.ACQUISITION_MODE+"='"+selectedAcquisitionMode+"'";
        }
        if(!selectedPass.isEmpty()) {
            queryStr += SQLUtils.addAND(queryStr);
            queryStr += ProductDB.PROD_TABLE+'.'+AbstractMetadata.PASS+"='"+selectedPass+"'";
        }

        if(startDate != null) {
            queryStr += SQLUtils.addAND(queryStr);
            final Date start = SQLUtils.toSQLDate(startDate);
            if(endDate != null) {
                final Date end = SQLUtils.toSQLDate(endDate);
                queryStr += "( "+ProductDB.PROD_TABLE+'.'+AbstractMetadata.first_line_time
                        +" BETWEEN '"+ start.toString() +"' AND '"+ end.toString() + "' )";
            } else {
                queryStr += ProductDB.PROD_TABLE+'.'+AbstractMetadata.first_line_time +">='"+ start.toString()+"'";
            }
        } else if(endDate != null) {
            queryStr += SQLUtils.addAND(queryStr);
            final Date end = SQLUtils.toSQLDate(endDate);
            queryStr += ProductDB.PROD_TABLE+'.'+AbstractMetadata.first_line_time +"<='"+ end.toString()+"'";
        }

        final Set<String> metadataNames = metadataQueryMap.keySet();
        for(String name : metadataNames) {
            final String value = metadataQueryMap.get(name);
            if(value != null && !value.isEmpty()) {
                queryStr += SQLUtils.addAND(queryStr);
                queryStr += ProductDB.META_TABLE+'.'+name+"='"+value+"'";
            }
        }

        if(!freeQuery.isEmpty()) {
            queryStr += SQLUtils.addAND(queryStr);
            final String metadataFreeQuery = SQLUtils.insertTableName(db.getMetadataNames(), ProductDB.META_TABLE, freeQuery);
            queryStr += "( "+metadataFreeQuery+" )";
        }

        if(baseDir != null) {
            queryStr += SQLUtils.addAND(queryStr);
            queryStr += ProductDB.PROD_TABLE+'.'+AbstractMetadata.PATH+" LIKE '"+baseDir.getAbsolutePath()+"%'";
        }
        if(excludeDir != null) {
            queryStr += SQLUtils.addAND(queryStr);
            queryStr += ProductDB.PROD_TABLE+'.'+AbstractMetadata.PATH+" NOT LIKE '"+excludeDir.getAbsolutePath()+"%'";
        }

        if(queryStr.isEmpty()) {
            return instersectMapSelection(db.getProductEntryList());
        } else {
            System.out.println("Query="+queryStr);
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

    public Element toXML() {
        final Element elem = new Element(DB_QUERY);
        final Element missionsElem = new Element("selectedMissions");
        elem.addContent(missionsElem);
        for(String m : selectedMissions) {
            missionsElem.addContent(new Element(m));
        }
        final Element productTypesElem = new Element("selectedProductTypes");
        elem.addContent(productTypesElem);
        for(String p : selectedProductTypes) {
            productTypesElem.addContent(new Element(p));
        }
        if(selectionRectangle != null) {
            final Element rectElem = new Element("selectionRectangle");
            elem.addContent(rectElem);
            rectElem.setAttribute("x", String.valueOf(selectionRectangle.getX()));
            rectElem.setAttribute("y", String.valueOf(selectionRectangle.getY()));
            rectElem.setAttribute("w", String.valueOf(selectionRectangle.getWidth()));
            rectElem.setAttribute("h", String.valueOf(selectionRectangle.getHeight()));
        }

        elem.setAttribute("selectedAcquisitionMode", selectedAcquisitionMode);
        elem.setAttribute("selectedPass", selectedPass);
        if(baseDir != null)
            elem.setAttribute("baseDir", baseDir.getAbsolutePath());
        if(startDate != null) {
            final Element startDateElem = new Element("startDate");
            elem.addContent(startDateElem);
            startDateElem.setAttribute("year", String.valueOf(startDate.get(Calendar.YEAR)));
            startDateElem.setAttribute("month", String.valueOf(startDate.get(Calendar.MONTH)));
            startDateElem.setAttribute("day", String.valueOf(startDate.get(Calendar.DAY_OF_MONTH)));
        }
        if(endDate != null) {
            final Element endDateElem = new Element("endDate");
            elem.addContent(endDateElem);
            endDateElem.setAttribute("year", String.valueOf(endDate.get(Calendar.YEAR)));
            endDateElem.setAttribute("month", String.valueOf(endDate.get(Calendar.MONTH)));
            endDateElem.setAttribute("day", String.valueOf(endDate.get(Calendar.DAY_OF_MONTH)));
        }
        elem.setAttribute("freeQuery", freeQuery);
        return elem;
    }

    public void fromXML(final Element dbQueryElem) {

        final Element missionsElem = dbQueryElem.getChild("selectedMissions");
        if(missionsElem != null) {
            selectedMissions = XMLSupport.getStringList(missionsElem);
        }
        final Element productTypesElem = dbQueryElem.getChild("selectedProductTypes");
        if(productTypesElem != null) {
            selectedProductTypes = XMLSupport.getStringList(productTypesElem);
        }
        final Element rectElem = dbQueryElem.getChild("selectionRectangle");
        if(rectElem != null) {
            final Attribute x = rectElem.getAttribute("x");
            final Attribute y = rectElem.getAttribute("y");
            final Attribute w = rectElem.getAttribute("w");
            final Attribute h = rectElem.getAttribute("h");
            if(x != null && y != null && w != null && h != null) {
                selectionRectangle = new Rectangle.Float(
                        Float.parseFloat(x.getValue()),
                        Float.parseFloat(y.getValue()),
                        Float.parseFloat(w.getValue()),
                        Float.parseFloat(h.getValue()));
            }
        }

        selectedAcquisitionMode = XMLSupport.getAttrib(dbQueryElem, "selectedAcquisitionMode");
        selectedPass = XMLSupport.getAttrib(dbQueryElem, "selectedPass");
        final String baseDirStr = XMLSupport.getAttrib(dbQueryElem, "baseDir");
        if(!baseDirStr.isEmpty())
            baseDir = new File(baseDirStr);
        final Element startDateElem = dbQueryElem.getChild("startDate");
        if(startDateElem != null) {
            final Attribute y = startDateElem.getAttribute("year");
            final Attribute m = startDateElem.getAttribute("month");
            final Attribute d = startDateElem.getAttribute("day");
            if(y != null && m != null && d != null) {
                startDate = new GregorianCalendar(Integer.parseInt(y.getValue()),
                                                  Integer.parseInt(m.getValue()),
                                                  Integer.parseInt(d.getValue()));
            }
        }
        final Element endDateElem = dbQueryElem.getChild("endDate");
        if(endDateElem != null) {
            final Attribute y = endDateElem.getAttribute("year");
            final Attribute m = endDateElem.getAttribute("month");
            final Attribute d = endDateElem.getAttribute("day");
            if(y != null && m != null && d != null) {
                endDate = new GregorianCalendar(Integer.parseInt(y.getValue()),
                                                Integer.parseInt(m.getValue()),
                                                Integer.parseInt(d.getValue()));
            }
        }
        freeQuery = XMLSupport.getAttrib(dbQueryElem, "freeQuery");
    }


}
