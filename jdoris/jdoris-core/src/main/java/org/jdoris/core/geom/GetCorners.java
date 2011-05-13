package org.jdoris.core.geom;

import org.esa.beam.framework.dataop.maptransf.Ellipsoid;

/**
 * User: pmar@ppolabs.com
 * Date: 2/17/11
 * Time: 11:39 AM
 */
public class GetCorners {

//    Ellipsoid inputEll;
    private Ellipsoid ellips;


///****************************************************************
// *    getcorners                                                *
// *                                                              *
// * Get corners of window (approx) to select DEM in radians (if  *
// * height were zero)                                            *
// *                                                              *
// * Implementation:                                              *
// * 1) calculate phi, lambda of corners                          *
// * 2) select the extreme values                                 *
// * 3) add extra overlap                                         *
// * 4) determine the indices in the file                         *
// ****************************************************************/


    void getCorners(double l0,
                    double lN,
                    double p0,
                    double pN,
                    double extralat,
                    double extralong,
                    double lat0,
                    double long0,
                    double DEMdeltalat,
                    double DEMdeltalong,
                    long Nlatpixels,
                    long Nlongpixels,
                    Ellipsoid ellips,
                    SLCimage master,
                    Orbit masterorbit,
                    double phimin,
                    double phimax,
                    double lambdamin,
                    double lambdamax,
                    long indexphi0DEM,
                    long indexphiNDEM,
                    long indexlambda0DEM,
                    long indexlambdaNDEM) {

                  double phi = 0;
                  double lambda = 0;
                  double height = 0;

        double[] phi_and_lambda = lp2ell(l0, p0, ellips, master, masterorbit, phi, lambda, height);

        double phil0p0 = phi_and_lambda[0];
        double lambdal0p0 = phi_and_lambda[1];

        phi_and_lambda = lp2ell(lN,p0,ellips, master, masterorbit,phi, lambda, height);          // returned
        double philNp0 = phi_and_lambda[0];
        double lambdalNp0 = phi_and_lambda[1];

        phi_and_lambda =lp2ell(lN, pN, ellips, master, masterorbit, phi, lambda, height);          // returned
        double philNpN = phi_and_lambda[0];
        double lambdalNpN = phi_and_lambda[1];

        phi_and_lambda = lp2ell(l0, pN, ellips, master, masterorbit, phi, lambda, height);          // returned
        double phil0pN = phi_and_lambda[0];
        double lambdal0pN = phi_and_lambda[1];


        // ______ Select DEM values based on rectangle outside l,p border ______
        phimin = Math.min(Math.min(Math.min(phil0p0, philNp0), philNpN), phil0pN);
        phimax = Math.max(Math.max(Math.max(phil0p0, philNp0), philNpN), phil0pN);
        lambdamin = Math.min(Math.min(Math.min(lambdal0p0, lambdalNp0), lambdalNpN), lambdal0pN);
        lambdamax = Math.max(Math.max(Math.max(lambdal0p0, lambdalNp0), lambdalNpN), lambdal0pN);

        // ______ a little bit extra at edges to be sure ______
        phimin -= extralat;
        phimax += extralat;
        lambdamax += extralong;
        lambdamin -= extralong;


        // ______ Get indices of DEM needed ______
        // ______ Index boundary: [0:numberofx-1] ______

        indexphi0DEM = (long)(Math.floor((lat0-phimax)/DEMdeltalat));
        if (indexphi0DEM < 0) {
            System.out.println("WARNING :: indexphi0DEM: " + indexphi0DEM);
            indexphi0DEM = 0;   // default start at first
            System.out.println("DEM does not cover entire interferogram.");
            System.out.println("input DEM should be extended to the North.");
        }

        indexphiNDEM = (long)(Math.ceil((lat0-phimin)/DEMdeltalat));
        if (indexphiNDEM > Nlatpixels-1){
            System.out.println("WARNING :: indexphiNDEM: " + indexphiNDEM);
            System.out.println("WARNING :: indexphiNDEM: " + indexphiNDEM);
            indexphiNDEM=Nlatpixels-1;
            System.out.println("DEM does not cover entire interferogram.");
            System.out.println("input DEM should be extended to the South.");
        }

        indexlambda0DEM = (long) (Math.floor((lambdamin - long0) / DEMdeltalong));
        if (indexlambda0DEM < 0) {
            System.out.println("WARNING :: indexlambda0DEM: " + indexlambda0DEM);
            indexlambda0DEM = 0;    // default start at first
            System.out.println("DEM does not cover entire interferogram.");
            System.out.println("input DEM should be extended to the West.");
        }
        indexlambdaNDEM = (long) (Math.ceil((lambdamax - long0) / DEMdeltalong));
        if (indexlambdaNDEM > Nlongpixels - 1) {
            System.out.println("WARNING :: indexlambdaNDEM: " + indexlambdaNDEM);
            indexlambdaNDEM = Nlongpixels - 1;
            System.out.println("DEM does not cover entire interferogram.");
            System.out.println("input DEM should be extended to the East.");
        }
    }

    private double[] lp2ell(double l0, double p, Ellipsoid ellips, SLCimage master, Orbit masterorbit,
                            double phi, double lambda, double height) {
        return new double[2];

    }

    private class Orbit {
    }

    private class SLCimage {
    }

} // END getcorners
