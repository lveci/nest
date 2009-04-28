package org.esa.beam.dataio.envi;


public class EnviMapInfo {

    public void setProjectionName(String projectionName) {
        this.projectionName = projectionName;
    }

    public String getProjectionName() {
        return projectionName;
    }

    public void setReferencePixelX(double referencePixelX) {
        this.referencePixelX = referencePixelX;
    }

    public double getReferencePixelX() {
        return referencePixelX;
    }

    public void setReferencePixelY(double referencePixelY) {
        this.referencePixelY = referencePixelY;
    }

    public double getReferencePixelY() {
        return referencePixelY;
    }

    public void setEasting(double easting) {
        this.easting = easting;
    }

    public double getEasting() {
        return easting;
    }

    public double getNorthing() {
        return northing;
    }

    public void setNorthing(double northing) {
        this.northing = northing;
    }

    public void setPixelSizeX(double pixelSizeX) {
        this.pixelSizeX = pixelSizeX;
    }

    public double getPixelSizeX() {
        return pixelSizeX;
    }

    public void setPixelSizeY(double pixelSizeY) {
        this.pixelSizeY = pixelSizeY;
    }

    public double getPixelSizeY() {
        return pixelSizeY;
    }

    public String getDatum() {
        return datum;
    }

    public void setDatum(String datum) {
        this.datum = datum;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    ///////////////////////////////////////////////////////////////////////////
    /////// END OF PUBLIC
    ///////////////////////////////////////////////////////////////////////////

    private String projectionName;
    private double referencePixelX;
    private double referencePixelY;
    private double easting;
    private double northing;
    private double pixelSizeX;
    private double pixelSizeY;
    private String datum;
    private String unit;
}
