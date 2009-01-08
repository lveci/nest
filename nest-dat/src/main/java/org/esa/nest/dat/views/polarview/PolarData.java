package org.esa.nest.dat.views.polarview;

import org.esa.beam.util.Debug;

import java.awt.*;

public class PolarData extends AbstractData {

    private float firstDir;
    private float dirStep;
    private float radii[];
    private int rScreenPoints[];
    private int Nr;
    private int Nth;

    private Object[] rPoints;
    private Object[] tPoints;
    Object[] cValues;
    float dirOffset;
    final AxisInfo rData;
    private final AxisInfo tData;

    public PolarData(float cValues[][], float firstDir, float dirStep, float radii[]) {
        this.rPoints = null;
        this.tPoints = null;
        this.cValues = null;
        rData = new AxisInfo();
        tData = new AxisInfo();
        setRPoints(rPoints);
        setTPoints(tPoints);
        setColorValues(cValues);

        this.firstDir = firstDir;
        this.dirStep = dirStep;
        this.radii = radii;
        Nth = cValues.length;
        Nr = cValues[0].length;
    }

    public void setRAxis(Axis rAxis) {
        rData.setAxis(rAxis);
    }

    void setRPoints(Object rPoints[]) {
        this.rPoints = rPoints;
        rData.setArrayType(rPoints);
    }

    void setTPoints(Object tPoints[]) {
        this.tPoints = tPoints;
        tData.setArrayType(tPoints);
    }

    void setColorValues(Object cValues[]) {
        this.cValues = cValues;
        connectPoints = cValues == null;
        cData.setArrayType(cValues);
        colors = null;
    }

    public void setColor(Color c) {
        setColorValues(null);
        super.setColor(c);
    }

    public void setDirOffset(float dirOffset) {
        this.dirOffset = dirOffset;
    }

    public void preparePlot() {
        plotCount = cValues.length;
        prepareColors(cValues);
        prepareRTPoints();
    }

    public void draw(Graphics g) {
        if (rData.axis == null)
            return;
        try {
            rData.axis.getAxisGraphics(g);
            preparePlot();
            int lastRad = 0x7fffffff;
            int arcAngle = (int) dirStep;
            if (rScreenPoints[0] < rScreenPoints[Nr]) {
                int rad;
                int rad2;
                for (int ri = Nr - 1; ri >= 0; ri--) {
                    float th = firstDir + dirOffset;
                    float r = rScreenPoints[ri + 1];
                    rad = (int) r;
                    if (rad >= lastRad) {
                        rad = lastRad - 1;
                        rScreenPoints[ri + 1] = rad;
                    }
                    lastRad = rad;
                    rad2 = rad + rad;
                    System.out.println("Ri " + ri + " " + r + " " + rad + " " + radii[ri + 1]);
                    for (int thi = 0; thi < Nth;) {
                        int angle = (int) th;
                        g.setColor(colors[thi][ri]);
                        g.fillArc(-rad, -rad, rad2, rad2, angle, arcAngle);
                        th += dirStep;
                        thi++;
                    }
                }

                rad = rScreenPoints[0];
                rad2 = rad + rad;
                g.setColor(Color.white);
                g.fillOval(-rad, -rad, rad2, rad2);
            } else {
                int rad;
                int rad2;
                for (int ri = 0; ri < Nr; ri++) {
                    float th = firstDir + dirOffset;
                    float r = rScreenPoints[ri + 1];
                    rad = (int) r;
                    if (rad >= lastRad) {
                        rad = lastRad - 1;
                        rScreenPoints[ri + 1] = rad;
                    }
                    lastRad = rad;
                    rad2 = rad + rad;
                    System.out.println("Ri " + ri + " " + r + " " + rad + " " + radii[ri + 1]);
                    for (int thi = 0; thi < Nth;) {
                        int angle = (int) th;
                        g.setColor(colors[thi][ri]);
                        g.fillArc(-rad, -rad, rad2, rad2, angle, arcAngle);
                        th += dirStep;
                        thi++;
                    }
                }

                rad = rScreenPoints[Nr];
                rad2 = rad + rad;
                g.setColor(Color.white);
                g.fillOval(-rad, -rad, rad2, rad2);
            }
        }
        catch (Throwable e) {
            Debug.trace(e);
        }
    }

    protected void prepareRTPoints() {
        if (rData.axis == null)
            return;
        if (rScreenPoints == null || rScreenPoints.length != radii.length) {
            rScreenPoints = new int[radii.length];
            rData.touch();
        }
        rData.checkTouchedAxis();
        if (rData.touched) {
            rData.untouch();
            int rP[][] = {
                    rScreenPoints
            };
            float r[][] = {
                    radii
            };
            computeScreenPoints(r, rP, rData.axis);
        }
    }

    protected double valueFromScreenPoint(int r) {
        if (rScreenPoints[0] < rScreenPoints[Nr]) {
            if (r < rScreenPoints[0])
                return (double) radii[0];
            for (int ri = 1; ri <= Nr; ri++)
                if (rScreenPoints[ri - 1] <= r && rScreenPoints[ri] >= r) {
                    if (rScreenPoints[ri - 1] == r)
                        return (double) radii[ri - 1];
                    if (rScreenPoints[ri] == r)
                        return (double) radii[ri];
                    else
                        return (double) (((radii[ri] - radii[ri - 1]) * ((float) r - (float) rScreenPoints[ri - 1])) / (float) (rScreenPoints[ri] - rScreenPoints[ri - 1]) + radii[ri - 1]);
                }

            return (double) radii[Nr];
        }
        if (r > rScreenPoints[0])
            return (double) radii[0];
        for (int ri = 1; ri <= Nr; ri++)
            if (rScreenPoints[ri - 1] >= r && rScreenPoints[ri] <= r) {
                if (rScreenPoints[ri - 1] == r)
                    return (double) radii[ri - 1];
                if (rScreenPoints[ri] == r)
                    return (double) radii[ri];
                else
                    return (double) (((radii[ri] - radii[ri - 1]) * ((float) r - (float) rScreenPoints[ri])) / (float) (rScreenPoints[ri - 1] - rScreenPoints[ri]) + radii[ri - 1]);
            }

        return (double) radii[Nr];
    }

    private static void computeScreenPoints(float values[][], int points[][], Axis axis) {
        if (axis == null)
            return;
        int np = values.length;
        for (int i = 0; i < np; i++)
            if (values[i] == null) {
                points[i] = null;
            } else {
                int n = values[i].length;
                if (points[i] == null || points[i].length != n)
                    points[i] = new int[n];
                for (int j = 0; j < n; j++)
                    points[i][j] = axis.computeScreenPoint(values[i][j]);

            }

    }
}
