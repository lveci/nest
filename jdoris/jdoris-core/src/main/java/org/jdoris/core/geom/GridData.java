package org.jdoris.core.geom;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import org.jdoris.core.delaunay.FastDelaunayTriangulator;
import org.jdoris.core.delaunay.Triangle;
import org.jdoris.core.delaunay.TriangulationException;
import org.jblas.DoubleMatrix;

import java.util.ArrayList;
import java.util.List;

/**
 * User: pmar@ppolabs.com
 * Date: 2/16/11
 * Time: 4:45 PM
 */
public class GridData {

    static DoubleMatrix x_in;
    static DoubleMatrix y_in;
    static double[][] z_in;
    static double x_min, x_max;
    static double y_min, y_max;
    static long x_inc, y_inc;
    static double r_az_ratio;
    static double offset;
    static double NODATA;
    static double[][] grd;

    // code for
   	//***************************************************************
	// *    griddatalinear (naming after Matlab function)             *
	// *                                                              *
	// *    Implementation after GMT function triangulate.c           *
	// ***************************************************************
    public static void GridData()//(x_in, y_in, z_in, x_min, x_max, y_min, y_max, x_inc, y_inc, r_az_ratio, offset, NODATA, grd)
	{
        int i; int j; int k; int ij; int p; int i_min; int i_max; int j_min; int j_max;
        int n; int nx; int ny; int zLoops; int zLoop; int zBlockSize; int indexFirstPoint; int zInterpolateBlockSize;
	    double[] vx = new double[4];
	    double[] vy = new double[4];
//        DoubleMatrix vx = new DoubleMatrix(4);
//        DoubleMatrix vy = new DoubleMatrix(4);
        double xkj; double xlj; double ykj; double ylj; double zj; double zk; double zl; double zlj; double zkj; double xp; double yp; double f; // linear interpolation parameters
//      a = null;
//	    b = null;
//	    c = null;
//      structure of triangle
//	    triangulateio In;
//	    triangulateio Out;
//	    triangulateio vorOut;

        Coordinate[] In = null;
        Coordinate[] Out = null;

        // Initialize variables
        n = zBlockSize = x_in.length; // block size of x and y coordination

        // How many groups of z value should be interpolated
        if ((z_in.length % zBlockSize) != 0) {
            System.out.println("The input of the DEM buffer and z is not the same...");
            return;
        } else {
            zLoops = z_in.length / x_in.length;
        }

        double[] a = new double[zLoops];
        double[] b = new double[zLoops];
        double[] c = new double[zLoops];

//        DoubleMatrix a = new DoubleMatrix(zLoops);
//        DoubleMatrix b = new DoubleMatrix(zLoops);
//        DoubleMatrix c = new DoubleMatrix(zLoops);

/*
        if (a == null || b == null || c == null)
		{
		  ERROR << "Memory ERROR in source file: " << __FILE__ << " at line: " << __LINE__;
		  PRINT_ERROR(ERROR.get_str());
		  throw(memory_error);
		}
*/
        // TODO: check the orientation
        // if JBLASS used
//        nx = grd.rows / zLoops;
//        ny = grd.columns;
//        zInterpolateBlockSize = grd.length / zLoops;

        // if ARRAYS used
        nx = grd.length / zLoops;
        ny = grd[0].length;
        zInterpolateBlockSize = grd.length * ny / zLoops;

//	  // Set everything to 0 and NULL
////C++ TO JAVA CONVERTER TODO TASK: The memory management function 'memset' has no equivalent in Java:
//	  memset ((Object) In, 0, sizeof (triangulateio));
////C++ TO JAVA CONVERTER TODO TASK: The memory management function 'memset' has no equivalent in Java:
//	  memset ((Object) Out, 0, sizeof (triangulateio));
////C++ TO JAVA CONVERTER TODO TASK: The memory management function 'memset' has no equivalent in Java:
//	  memset ((Object) vorOut, 0, sizeof (triangulateio));
//
/*
	  // Allocate memory for input points
	  In.numberofpoints = n;
	  In.pointlist = new real8 [2 * n];

	// save pointlist into temp.dat file for testing and debugging
	FILE * fp;
	fp = fopen("/d2/test.processing/temp.dat","w");

	*/
/* Copy x,y points to In structure array *//*

	for (i = j = 0; i < n; i++) {
		In.pointlist[j++] = *(x_in[0] + i);
		In.pointlist[j++] = (*(y_in[0] + i)) * r_az_ratio;
		// to eliminate the effect of difference in range and azimuth spacing;

		if (fp != NULL) {
			fprintf(fp, "%d\t%f\t%f\n", i + 1, *(x_in[0] + i), (*(y_in[0] + i)) * r_az_ratio);
		}

	}
*/
//        In = new Coordinate[2 * n];
        In = new Coordinate[n];

        // Copy x,y points to In structure array
        for (i = 0; i < n; i++) {
            In[i] = new Coordinate(x_in.get(i), y_in.get(i)*r_az_ratio);
        }

//	//   Call Jonathan Shewchuk's triangulate algorithm.  This is 64-bit safe since
//	//   * all the structures use 4-byte ints (longs are used internally).
//	  triangulate ("zIQB", In, Out, vorOut);

        System.out.println("testFastDelaunayTriangulator with " + In.length + " points");
        long t0 = System.currentTimeMillis();
        List<Geometry> list = new ArrayList<Geometry>();
        GeometryFactory gf = new GeometryFactory();
        for (Coordinate coord : In) list.add(gf.createPoint(coord));
        long t1 = System.currentTimeMillis();
        System.out.printf("Input set constructed in %10.3f sec\n", (0.001 * (t1 - t0)));

        long t2 = System.currentTimeMillis();
        FastDelaunayTriangulator FDT = new FastDelaunayTriangulator();
        try {
            FDT.triangulate(list.iterator());
        } catch (TriangulationException te) {
            te.printStackTrace();
        }
        long t3 = System.currentTimeMillis();

        System.out.printf("   triangulated in %10.3f sec\n", (0.001 * (t3 - t2)));


//        long np = FDT.triangles.size();

//	  int32 link = Out.trianglelist; // List of node numbers to return via link
//	  int32 np = Out.numberoftriangles;

//        for (Triangle triangleObject : FDT.triangles) {
//            String.valueOf(triangleObject.getA().x) + "\t" +
//                    String.valueOf(triangleObject.getA().y) + "\t" +
//                    String.valueOf(triangleObject.getB().x) + "\t" +
//                    String.valueOf(triangleObject.getB().y) + "\t" +
//                    String.valueOf(triangleObject.getC().x) + "\t" +
//                    String.valueOf(triangleObject.getC().y) + "\t"
//            );
//        }

        // here it loops through triangles!

        for (Triangle triangle : FDT.triangles) {

            // store the index of the first Point of this triangle
            indexFirstPoint = triangle.getIndex(triangle.getA());

            // get coordinates from trianglelist
            vx[0] = vx[3] = triangle.getA().x;
            vy[0] = vy[3] = triangle.getA().y;

            vx[1] = triangle.getB().x;
            vy[1] = triangle.getB().y;

            vx[2] = triangle.getB().x;
            vy[2] = triangle.getB().y;

    		// check whether something is no-data
	    	if (vx[0] == NODATA || vx[1] == NODATA || vx[2] == NODATA)
		    	continue;
		    if (vy[0] == NODATA || vy[1] == NODATA || vy[2] == NODATA)
    			continue;

	    	/* Compute grid indices the current triangle may cover.*/
		    xp = Math.min(Math.min(vx[0], vx[1]), vx[2]);
		    i_min = (int) coordToIndex(xp, x_min, x_inc, offset);
		    //INFO << "xp: " << xp;
		    //INFO.print();

		    xp = Math.max(Math.max(vx[0], vx[1]), vx[2]);
		    i_max = (int) coordToIndex(xp, x_min, x_inc, offset);
		    //INFO << "xp: " << xp;
		    //INFO.print();

		    yp = Math.min(Math.min(vy[0], vy[1]), vy[2]);
		    j_min = (int) coordToIndex(yp, y_min, y_inc, offset);
		    //INFO << "yp: " << yp;
		    //INFO.print();

		    yp = Math.max(Math.max(vy[0], vy[1]), vy[2]);
            j_max = (int) coordToIndex(yp, y_min, y_inc, offset);
		    //INFO << "yp: " << yp;
		    //INFO.print();

            /* Adjustments for triangles outside -R region. */
            /* Triangle to the left or right. */
            if ((i_max < 0) || (i_min >= nx))
                continue;
            /* Triangle Above or below */
            if ((j_max < 0) || (j_min >= ny))
                continue;
            /* Triangle covers boundary, left or right. */
            if (i_min < 0)
                i_min = 0;
            if (i_max >= nx)
                i_max = nx - 1;
            /* Triangle covers boundary, top or bottom. */
            if (j_min < 0)
                j_min = 0;
            if (j_max >= ny)
                j_max = ny - 1;

            /* Find equation for the plane as z = ax + by + c */
            xkj = vx[1] - vx[0];
            ykj = vy[1] - vy[0];
            xlj = vx[2] - vx[0];
            ylj = vy[2] - vy[0];

            f = 1.0 / (xkj * ylj - ykj * xlj);

            for (zLoop = 0; zLoop < zLoops; zLoop++) {
                zj = z_in[zLoop][triangle.getIndex(triangle.getA())];
                zk = z_in[zLoop][triangle.getIndex(triangle.getB())];
                zl = z_in[zLoop][zBlockSize + triangle.getIndex(triangle.getC())];
//                zj =*(z_in[0] + zLoop * zBlockSize + link[indexFirstPoint]);
//                zk =*(z_in[0] + zLoop * zBlockSize + link[indexFirstPoint + 1]);
//                zl =*(z_in[0] + zLoop * zBlockSize + link[indexFirstPoint + 2]);
                zkj = zk - zj;
                zlj = zl - zj;
                a[zLoop] = -f * (ykj * zlj - zkj * ylj);
                b[zLoop] = -f * (zkj * xlj - xkj * zlj);
                c[zLoop] = -a[zLoop] * vx[1] - b[zLoop] * vy[1] + zk;
            }

            for (i = i_min; i <= i_max; i++) {

                // xp = i_to_x(i, x_min, x_max, x_inc, offset);
                xp = indexToCoord(i, x_min, x_inc, offset);
//                p = i * ny + j_min;

//                for (j = j_min; j <= j_max; j++, p++) {
                for (j = j_min; j <= j_max; j++) {

                    // yp = j_to_y (j, y_min, y_max, y_inc, offset);
                    yp = indexToCoord(j, y_min, y_inc, offset);

                    if (!pointInTriangle(vx, vy, xp, yp))
                        continue; /* Outside */

                    for (zLoop = 0; zLoop < zLoops; zLoop++) {
//                        *(grd[0] + zLoop * zInterpolateBlockSize + p) = a[zLoop]
//                                * xp + b[zLoop] * yp + c[zLoop];
//                        grd[0 + zLoop * zInterpolateBlockSize + p] = a[zLoop] * xp + b[zLoop] * yp + c[zLoop];

                        grd[i][j] = a[zLoop] + xp + b[zLoop] * yp + c[zLoop];
                    }
                }
            }
        }


    }

    private static boolean pointInTriangle(double[] xt, double[] yt, double x, double y) {
        int iRet0 = ((xt[2] - xt[0]) * (y - yt[0])) >  ((x - xt[0]) * ( yt[2] - yt[0])) ? 1:-1;
	    int iRet1 = ((xt[0] - xt[1]) * (y - yt[1])) >  ((x - xt[1]) * ( yt[0] - yt[1])) ? 1:-1;
	    int iRet2 = ((xt[1] - xt[2]) * (y - yt[2])) >  ((x - xt[2]) * ( yt[1] - yt[2])) ? 1:-1;

    	if ((iRet0 >0 && iRet1 > 0 && iRet2 > 0 ) || (iRet0 <0 && iRet1 < 0 && iRet2 < 0 ))
	    	return true;
	    else
            return false;

    }

//	  for (k = ij = 0; k < np; k++)
//		{
//		  DEBUG << "k of np, ij: " << k << " of " << np << ", :" << ij;
//		  DEBUG.print();
//		  //Store the Index of the first Point of this triangle.
//		  indexFirstPoint = ij;
//
//		  vx[0] = vx[3] = *(x_in[0] + link[ij]);
//		  vy[0] = vy[3] = *(y_in[0] + link[ij]);
//		  ij++;
//		  vx[1] = *(x_in[0] + link[ij]);
//		  vy[1] = *(y_in[0]+link[ij]);
//		  ij++;
//		  vx[2] = *(x_in[0] + link[ij]);
//		  vy[2] = *(y_in[0]+link[ij]);
//		  ij++;
//
//		  if (vx[0] == NODATA || vx[1] == NODATA || vx[2] == NODATA)
//			  continue;
//		  if (vy[0] == NODATA || vy[1] == NODATA || vy[2] == NODATA)
//			  continue;
//
//		  // Compute grid indices the current triangle may cover.
//		  xp = min (min (vx[0], vx[1]), vx[2]);
//		  i_min = x_to_i (xp, x_min, x_inc, offset, nx);
//		  //INFO << "xp: " << xp;
//		  //INFO.print();
//		  xp = max (max (vx[0], vx[1]), vx[2]);
//		  i_max = x_to_i (xp, x_min, x_inc, offset, nx);
//		  //INFO << "xp: " << xp;
//		  //INFO.print();
//		  yp = min (min (vy[0], vy[1]), vy[2]);
//		  j_min = y_to_j (yp, y_min, y_inc, offset, ny);
//		  //INFO << "yp: " << yp;
//		  //INFO.print();
//		  yp = max (max (vy[0], vy[1]), vy[2]);
//		  j_max = y_to_j (yp, y_min, y_inc, offset, ny);
//		  //INFO << "yp: " << yp;
//		  //INFO.print();
//		  // Adjustments for triangles outside -R region.
//		  // Triangle to the left or right.
//		  if ((i_max < 0) || (i_min >= nx))
//			  continue;
//		  // Triangle Above or below
//		  if ((j_max < 0) || (j_min >= ny))
//			  continue;
//		  // Triangle covers boundary, left or right.
//		  if (i_min < 0)
//			  i_min = 0;
//		  if (i_max >= nx)
//			  i_max = nx - 1;
//		  // Triangle covers boundary, top or bottom.
//		  if (j_min < 0)
//			  j_min = 0;
//		  if (j_max >= ny)
//			  j_max = ny - 1;
//		  // for (kk = 0; kk<npar;kk++) {  //do for each parameter LIUG
//		  // read zj, zk, zl (instead of above) LIUG
//		  // Find equation for the plane as z = ax + by + c
//		  xkj = vx[1] - vx[0];
//		  ykj = vy[1] - vy[0];
//		  xlj = vx[2] - vx[0];
//		  ylj = vy[2] - vy[0];
//
//		  f = 1.0 / (xkj * ylj - ykj * xlj);
//
//		  for(zLoop = 0 ; zLoop < zLoops; zLoop++)
//	  {
//		zj = *(z_in[0] + zLoop * zBlockSize + link[indexFirstPoint]);
//		zk = *(z_in[0] + zLoop * zBlockSize + link[indexFirstPoint + 1]);
//		zl = *(z_in[0] + zLoop * zBlockSize + link[indexFirstPoint + 2]);
//		zkj = zk - zj;
//		zlj = zl - zj;
//		a[zLoop] = -f * (ykj * zlj - zkj * ylj);
//		b[zLoop] = -f * (zkj * xlj - xkj * zlj);
//		c[zLoop] = -a[zLoop] * vx[1] - b[zLoop] * vy[1] + zk;
//	  }
//
//		  for (i = i_min; i <= i_max; i++)
//			{
//		xp = i_to_x (i, x_min, x_max, x_inc, offset, nx);
//		p = i * ny + j_min;
//		for (j = j_min; j <= j_max; j++, p++)
//				{
//				  yp = j_to_y (j, y_min, y_max, y_inc, offset, ny);
//				  if (pointInTriangle(vx, vy, xp, yp) == 0) // Outside
//					  continue;
//
//			for(zLoop = 0 ; zLoop < zLoops; zLoop++)
//		*(grd.argvalue[0] + zLoop * zInterpolateBlockSize + p) = a[zLoop] * xp + b[zLoop] * yp + c[zLoop];
//				} //LIUG
//			}
//		}
//
//	  if (a != null)
//		  a = null;
//	  if (b != null)
//		  b = null;
//	  if (c != null)
//		  c = null;
//	  if(In.pointlist) // only this use delete
//		  In.pointlist = null;
//	  if(Out.pointlist)
////C++ TO JAVA CONVERTER TODO TASK: The memory management function 'free' has no equivalent in Java:
//		  free(Out.pointlist);
//	  if(Out.trianglelist)
////C++ TO JAVA CONVERTER TODO TASK: The memory management function 'free' has no equivalent in Java:
//		  free(Out.trianglelist);
//
//


    private static long coordToIndex(double coord, double coord0, long deltacoord, double offset) {
        return (irint(((((coord) - (coord0)) / (deltacoord)) - (offset))));
    }
    private static double indexToCoord(long idx, double coord0, long deltacoord, double offset) {
        return (coord0 + idx * deltacoord + offset);
    }


    private static long irint(double coord) {
        return ((long) rint(coord));
    }

    private static double rint(double coord) {
        return Math.floor(coord + 0.5);
    }

} //END griddata