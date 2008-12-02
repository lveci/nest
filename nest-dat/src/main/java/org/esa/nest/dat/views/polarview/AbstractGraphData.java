package org.esa.nest.dat.views.polarview;

import java.awt.Color;
import java.awt.Graphics;

public abstract class AbstractGraphData
{
    protected static final class AxisData
    {

        protected final void setAxis(GraphAxis axis)
        {
            this.axis = axis;
            touched = true;
        }

        protected final void setArrayType(Object oPoints[])
        {
            type = AbstractGraphData.getArrayType(oPoints);
            touched = true;
        }

        protected final void touch()
        {
            touched = true;
        }

        protected final void untouch()
        {
            touched = false;
        }

        protected final void checkTouchedAxis()
        {
            int id = axis.getTouchId();
            if(touchId != id)
            {
                touched = true;
                touchId = id;
            }
        }

        protected GraphAxis axis;
        protected boolean touched;
        protected int touchId;
        protected int type;

        protected AxisData()
        {
            axis = null;
            touched = true;
            touchId = -1;
            type = -1;
        }
    }


    public AbstractGraphData()
    {
        lineColor = Color.black;
        connectPoints = true;
        cData = new AxisData();
        cScale = null;
    }

    public void setCAxis(GraphAxis cAxis)
    {
        cData.setAxis(cAxis);
    }

    public ColorScale getColorScale()
    {
        return cScale;
    }

    public void setColorScale(ColorScale cScale)
    {
        this.cScale = cScale;
        cData.touch();
    }

    public void setColor(Color c)
    {
        lineColor = c;
        cScale = null;
    }

    public void setConnectedPoints(boolean connectedPoints)
    {
        connectPoints = connectedPoints;
    }

    public boolean getConnectedPoints()
    {
        return connectPoints;
    }

    public abstract void preparePlot();

    public abstract void draw(Graphics g);

    protected void prepareColors(Object cValues[])
    {
        if(cScale == null)
            return;
        if(colors == null || colors.length != plotCount)
        {
            colors = new Color[plotCount][];
            cData.touched = true;
        }
        if(cData.axis != null)
        {
            int TouchId = cData.axis.getTouchId();
            if(TouchId != cData.touchId)
            {
                cData.touched = true;
                cData.touchId = TouchId;
            }
        }
        if(!cData.touched)
        {
            return;
        } else
        {
            cData.touched = false;
            cScale.setRange(cData.axis.getRange());
            computeColors(cValues, cData.type, colors, cScale);
            return;
        }
    }

    protected static void computeScreenPoints(Object oPoints[], int oType, double scaleFactor, int points[][], GraphAxis axis)
    {
        switch(oType)
        {
        case 0: // '\0'
            computeScreenPoints((int[][])oPoints, scaleFactor, points, axis);
            break;

        case 1: // '\001'
            computeScreenPoints((short[][])oPoints, scaleFactor, points, axis);
            break;

        case 2: // '\002'
            computeScreenPoints((float[][])oPoints, scaleFactor, points, axis);
            break;

        case 3: // '\003'
            computeScreenPoints((double[][])oPoints, scaleFactor, points, axis);
            break;

        case 4: // '\004'
            computeScreenPoints((long[][])oPoints, scaleFactor, points, axis);
            break;
        }
    }

    protected static final void computeColors(Object oPoints[], int oType, Color colors[][], ColorScale scale)
    {
        switch(oType)
        {
        case 0: // '\0'
            computeColors((int[][])oPoints, colors, scale);
            break;

        case 1: // '\001'
            computeColors((short[][])oPoints, colors, scale);
            break;

        case 2: // '\002'
            computeColors((float[][])oPoints, colors, scale);
            break;

        case 3: // '\003'
            computeColors((double[][])oPoints, colors, scale);
            break;

        case 4: // '\004'
            computeColors((long[][])oPoints, colors, scale);
            break;
        }
    }

    protected static final double getValue(Object oPoints, int oType, int index)
    {
        switch(oType)
        {
        case 0: // '\0'
            return (double)((int[])oPoints)[index];

        case 1: // '\001'
            return (double)((short[])oPoints)[index];

        case 2: // '\002'
            return (double)((float[])oPoints)[index];

        case 4: // '\004'
            return (double)((long[])oPoints)[index];

        case 3: // '\003'
        default:
            return ((double[])oPoints)[index];
        }
    }

    private static final void computeScreenPoints(int values[][], double scaleFactor, int points[][], GraphAxis axis)
    {
        if(axis == null)
            return;
        int np = values.length;
        for(int i = 0; i < np; i++)
            if(values[i] == null)
            {
                points[i] = null;
            } else
            {
                int n = values[i].length;
                if(points[i] == null || points[i].length != n)
                    points[i] = new int[n];
                if(scaleFactor == 1.0D)
                {
                    for(int j = 0; j < n; j++)
                        points[i][j] = axis.computeScreenPoint(values[i][j]);

                } else
                {
                    for(int j = 0; j < n; j++)
                        points[i][j] = axis.computeScreenPoint((double)values[i][j] * scaleFactor);

                }
            }

    }

    private static final void computeScreenPoints(short values[][], double scaleFactor, int points[][], GraphAxis axis)
    {
        if(axis == null)
            return;
        int np = values.length;
        for(int i = 0; i < np; i++)
            if(values[i] == null)
            {
                points[i] = null;
            } else
            {
                int n = values[i].length;
                if(points[i] == null || points[i].length != n)
                    points[i] = new int[n];
                if(scaleFactor == 1.0D)
                {
                    for(int j = 0; j < n; j++)
                        points[i][j] = axis.computeScreenPoint(values[i][j]);

                } else
                {
                    for(int j = 0; j < n; j++)
                        points[i][j] = axis.computeScreenPoint((double)values[i][j] * scaleFactor);

                }
            }

    }

    private static final void computeScreenPoints(float values[][], double scaleFactor, int points[][], GraphAxis axis)
    {
        if(axis == null)
            return;
        int np = values.length;
        for(int i = 0; i < np; i++)
            if(values[i] == null)
            {
                points[i] = null;
            } else
            {
                int n = values[i].length;
                if(points[i] == null || points[i].length != n)
                    points[i] = new int[n];
                if(scaleFactor == 1.0D)
                {
                    for(int j = 0; j < n; j++)
                        points[i][j] = axis.computeScreenPoint(values[i][j]);

                } else
                {
                    for(int j = 0; j < n; j++)
                        points[i][j] = axis.computeScreenPoint((double)values[i][j] * scaleFactor);

                }
            }

    }

    private static void computeScreenPoints(double values[][], double scaleFactor, int points[][], GraphAxis axis)
    {
        if(axis == null)
            return;
        int np = values.length;
        for(int i = 0; i < np; i++)
            if(values[i] == null)
            {
                points[i] = null;
            } else
            {
                int n = values[i].length;
                if(points[i] == null || points[i].length != n)
                    points[i] = new int[n];
                if(scaleFactor == 1.0D)
                {
                    for(int j = 0; j < n; j++)
                        points[i][j] = axis.computeScreenPoint(values[i][j]);

                } else
                {
                    for(int j = 0; j < n; j++)
                        points[i][j] = axis.computeScreenPoint(values[i][j] * scaleFactor);

                }
            }

    }

    private static void computeScreenPoints(long values[][], double scaleFactor, int points[][], GraphAxis axis)
    {
        if(axis == null)
            return;
        int np = values.length;
        for(int i = 0; i < np; i++)
            if(values[i] == null)
            {
                points[i] = null;
            } else
            {
                int n = values[i].length;
                if(points[i] == null || points[i].length != n)
                    points[i] = new int[n];
                if(scaleFactor == 1.0D)
                {
                    for(int j = 0; j < n; j++)
                        points[i][j] = axis.computeScreenPoint(values[i][j]);

                } else
                {
                    for(int j = 0; j < n; j++)
                        points[i][j] = axis.computeScreenPoint((double)values[i][j] * scaleFactor);

                }
            }

    }

    private static void computeColors(int values[][], Color colors[][], ColorScale cScale)
    {
        if(cScale == null)
            return;
        int np = values.length;
        for(int i = 0; i < np; i++)
            if(values[i] == null)
            {
                colors[i] = null;
            } else
            {
                int n = values[i].length;
                if(colors[i] == null || colors[i].length != n)
                    colors[i] = new Color[n];
                for(int j = 0; j < n; j++)
                    colors[i][j] = cScale.getColor(values[i][j]);

            }

    }

    private static void computeColors(short values[][], Color colors[][], ColorScale cScale)
    {
        if(cScale == null)
            return;
        int np = values.length;
        for(int i = 0; i < np; i++)
            if(values[i] == null)
            {
                colors[i] = null;
            } else
            {
                int n = values[i].length;
                if(colors[i] == null || colors[i].length != n)
                    colors[i] = new Color[n];
                for(int j = 0; j < n; j++)
                    colors[i][j] = cScale.getColor(values[i][j]);

            }

    }

    private static void computeColors(float values[][], Color colors[][], ColorScale cScale)
    {
        if(cScale == null)
            return;
        int np = values.length;
        for(int i = 0; i < np; i++)
            if(values[i] == null)
            {
                colors[i] = null;
            } else
            {
                int n = values[i].length;
                if(colors[i] == null || colors[i].length != n)
                    colors[i] = new Color[n];
                for(int j = 0; j < n; j++)
                    colors[i][j] = cScale.getColor(values[i][j]);

            }

    }

    private static void computeColors(double values[][], Color colors[][], ColorScale cScale)
    {
        if(cScale == null)
            return;
        int np = values.length;
        for(int i = 0; i < np; i++)
            if(values[i] == null)
            {
                colors[i] = null;
            } else
            {
                int n = values[i].length;
                if(colors[i] == null || colors[i].length != n)
                    colors[i] = new Color[n];
                for(int j = 0; j < n; j++)
                    colors[i][j] = cScale.getColor(values[i][j]);

            }

    }

    private static void computeColors(long values[][], Color colors[][], ColorScale cScale)
    {
        if(cScale == null)
            return;
        int np = values.length;
        for(int i = 0; i < np; i++)
            if(values[i] == null)
            {
                colors[i] = null;
            } else
            {
                int n = values[i].length;
                if(colors[i] == null || colors[i].length != n)
                    colors[i] = new Color[n];
                for(int j = 0; j < n; j++)
                    colors[i][j] = cScale.getColor(values[i][j]);

            }

    }

    private static int getArrayType(Object oPoints[])
    {
        if(oPoints == null)
            return -1;
        if(oPoints instanceof double[][])
            return 3;
        if(oPoints instanceof int[][])
            return 0;
        if(oPoints instanceof float[][])
            return 2;
        if(oPoints instanceof short[][])
            return 1;
        return !(oPoints instanceof long[][]) ? -1 : 4;
    }

    protected int xScreenPoints[][];
    protected int yScreenPoints[][];
    protected Color colors[][];
    protected Color lineColor;
    protected boolean connectPoints;
    protected AxisData cData;
    protected ColorScale cScale;
    protected int plotCount;
    private static final int NONE = -1;
    private static final int INT = 0;
    private static final int SHORT = 1;
    private static final int FLOAT = 2;
    private static final int DOUBLE = 3;
    private static final int LONG = 4;
}