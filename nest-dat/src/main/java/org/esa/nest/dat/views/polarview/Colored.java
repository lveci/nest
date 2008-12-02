package org.esa.nest.dat.views.polarview;

public interface Colored
{

    public abstract ColorScale getColorScale();

    public abstract void updatedColorScale();

    public abstract void updatedColorMap();
}
