package org.esa.nest.dat.views.polarview;


public class PolarGraphAxis extends GraphAxis
{

    PolarGraphAxis()
    {
        super(5);
    }

    void setLocation(int orientation)
    {
        switch(orientation)
        {
        case 5: // '\005'
        default:
            isX = true;
            break;
        }
        name = "R";
        isBottomLeft = false;
        gr = new GraphAxis.XAxisGraphics();
    }

    public static final int RADIAL = 5;
}
