package org.esa.nest.dat.views.polarview;


class AxisPolar extends Axis {

    AxisPolar() {
        super(5);
    }

    void setLocation(int orientation) {
        switch (orientation) {
            case 5: // '\005'
            default:
                isX = true;
                break;
        }
        name = "R";
        isBottomLeft = false;
        gr = new Axis.XAxisGraphics();
    }

    public static final int RADIAL = 5;
}
