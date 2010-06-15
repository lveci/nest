package org.esa.nest.doris.util;

/**
 * Created by IntelliJ IDEA.
 * User: pmar
 * Date: Apr 13, 2010
 * Time: 6:01:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class SARGeometryUtils {

    /*


        ****************************************************************
         * getoverlap                                                   *
         *                                                              *
         * overlap of 2 windows in same coord. system                   *
         #%// BK 21-Sep-2000
         ****************************************************************
        window getoverlap(
                const window &master,
                const window &slave)
          {
          TRACE_FUNCTION("getoverlap (BK 10-Mar-1999)")
          window overlap = slave;
          if (master.linelo>overlap.linelo) overlap.linelo=master.linelo;
          if (master.linehi<overlap.linehi) overlap.linehi=master.linehi;
          if (master.pixlo >overlap.pixlo)  overlap.pixlo =master.pixlo;
          if (master.pixhi <overlap.pixhi)  overlap.pixhi =master.pixhi;
          return overlap;
          } // getoverlap



        ****************************************************************
         * getoverlap                                                   *
         *                                                              *
         * compute approx. rectangular overlap (master coord.)          *
         * between master/slave with help of transformation polynomial  *
         * Bert Kampes 10-Mar-99                                        *
         ****************************************************************
        window getoverlap(
                const slcimage      &master,
                const slcimage      &slave,
                const matrix<real8> &cpmL,
                const matrix<real8> &cpmP)
          {
          TRACE_FUNCTION("getoverlap (BK 10-Mar-1999)")

        // ______ Normalize data for polynomial ______
          const real8 minL = master.originalwindow.linelo;
          const real8 maxL = master.originalwindow.linehi;
          const real8 minP = master.originalwindow.pixlo;
          const real8 maxP = master.originalwindow.pixhi;

          INFO << "getoverlap: polynomial normalized by factors: "
               << minL << " " << maxL << " " << minP << " " << maxP
               << " to [-2,2]";
          INFO.print();

        // ______offset = A(slave system) - A(master system)______
        // ______Corners of slave in master system______
        // ______Offsets for slave corners (approx.)______
        // ______ approx: defined as offset = f(l,p)_M in master system not slave.
          real8 approxoffL = cpmL(0,0);                         // zero order term;
          real8 approxoffP = cpmP(0,0);                         // zero order term;

        //  real8 sL00 = slave.currentwindow.linelo -
        //                polyval(slave.currentwindow.linelo - approxoffL,
        //                        slave.currentwindow.pixlo  - approxoffP, cpmL);
        // ______ Use normalized polynomial ______
          const real8 sL00 = slave.currentwindow.linelo -
               polyval(normalize(real8(slave.currentwindow.linelo)-approxoffL,minL,maxL),
                       normalize(real8(slave.currentwindow.pixlo) -approxoffP,minP,maxP),
                       cpmL);
          const real8 sP00 = slave.currentwindow.pixlo -
               polyval(normalize(real8(slave.currentwindow.linelo)-approxoffL,minL,maxL),
                       normalize(real8(slave.currentwindow.pixlo) -approxoffP,minP,maxP),
                       cpmP);
          const real8 sL0N = slave.currentwindow.linelo -
               polyval(normalize(real8(slave.currentwindow.linelo)-approxoffL,minL,maxL),
                       normalize(real8(slave.currentwindow.pixhi) -approxoffP,minP,maxP),
                       cpmL);
          const real8 sP0N = slave.currentwindow.pixhi -
               polyval(normalize(real8(slave.currentwindow.linelo)-approxoffL,minL,maxL),
                       normalize(real8(slave.currentwindow.pixhi) -approxoffP,minP,maxP),
                       cpmP);
          const real8 sLN0 = slave.currentwindow.linehi -
               polyval(normalize(real8(slave.currentwindow.linehi)-approxoffL,minL,maxL),
                       normalize(real8(slave.currentwindow.pixlo) -approxoffP,minP,maxP),
                       cpmL);
          const real8 sPN0 = slave.currentwindow.pixlo -
               polyval(normalize(real8(slave.currentwindow.linehi)-approxoffL,minL,maxL),
                       normalize(real8(slave.currentwindow.pixlo) -approxoffP,minP,maxP),
                       cpmP);
          const real8 sLNN = slave.currentwindow.linehi -
               polyval(normalize(real8(slave.currentwindow.linehi)-approxoffL,minL,maxL),
                       normalize(real8(slave.currentwindow.pixhi) -approxoffP,minP,maxP),
                       cpmL);
          const real8 sPNN = slave.currentwindow.pixhi -
               polyval(normalize(real8(slave.currentwindow.linehi)-approxoffL,minL,maxL),
                       normalize(real8(slave.currentwindow.pixhi) -approxoffP,minP,maxP),
                       cpmP);


        //  // ______make window rectangular + int______
        //  // ______window is uint type______
        //    if (sL00 < 0.) sL00 = 0.;
        //    if (sP00 < 0.) sP00 = 0.;
        //    window slaveinmaster = {ceil(max(sL00,sL0N)),
        //                            floor(min(sLN0,sLNN)),
        //                            ceil(max(sP00,sPN0)),
        //                            floor(min(sP0N,sPNN))};


          // ______Corners of overlap master,slave in master system______
          window win;
          win.linelo = max(int32(master.currentwindow.linelo),
                           int32(ceil(max(sL00,sL0N))));
          win.linehi = min(int32(master.currentwindow.linehi),
                           int32(floor(min(sLN0,sLNN))));
          win.pixlo  = max(int32(master.currentwindow.pixlo),
                           int32(ceil(max(sP00,sPN0))));
          win.pixhi  = min(int32(master.currentwindow.pixhi),
                           int32(floor(min(sP0N,sPNN))));

        #ifdef __DEBUG
          DEBUG.print("Finished getoverlap");
          DEBUG.print("Approximate overlap master/slave window:");
          win.disp();
        #endif
          return win;
          } // END getoverlap


        ****************************************************************
         * getoverlap                                                   *
         *                                                              *
         * compute rectangular overlap between master and slave         *
         * (master coordinate system)                                   *
         ****************************************************************
        window getoverlap(
                const slcimage      &master,
                const slcimage      &slave,
                const real8         &Npointsd2,
                const real8         &timing_L,
                const real8         &timing_P)
          {
            TRACE_FUNCTION("getoverlap (FvL 22-SEP-07)")

            real8 ml0 = master.currentwindow.linelo;
            real8 mlN = master.currentwindow.linehi;
            real8 mp0 = master.currentwindow.pixlo;
            real8 mpN = master.currentwindow.pixhi;

            real8 sl00 = slave.currentwindow.linelo+slave.slavemasteroffsets.l00+Npointsd2-timing_L;
            real8 sp00 = slave.currentwindow.pixlo+slave.slavemasteroffsets.p00+Npointsd2-timing_P;
            real8 sl0N = slave.currentwindow.linelo+slave.slavemasteroffsets.l0N+Npointsd2-timing_L;
            real8 sp0N = slave.currentwindow.pixhi+slave.slavemasteroffsets.p0N-Npointsd2-timing_P;
            real8 slN0 = slave.currentwindow.linehi+slave.slavemasteroffsets.lN0-Npointsd2-timing_L;
            real8 spN0 = slave.currentwindow.pixlo+slave.slavemasteroffsets.pN0+Npointsd2-timing_P;
            real8 slNN = slave.currentwindow.linehi+slave.slavemasteroffsets.lNN-Npointsd2-timing_L;
            real8 spNN = slave.currentwindow.pixhi+slave.slavemasteroffsets.pNN-Npointsd2-timing_P;

            matrix<real8> mh1sv1(2,1), mh1sv2(2,1), mh2sv1(2,1), mh2sv2(2,1),
              mv1sh1(2,1), mv1sh2(2,1), mv2sh1(2,1), mv2sh2(2,1);

            lineintersect(ml0,mp0,ml0,mpN,sl00,sp00,slN0,spN0,mh1sv1);
            lineintersect(ml0,mp0,ml0,mpN,sl0N,sp0N,slNN,spNN,mh1sv2);
            lineintersect(mlN,mp0,mlN,mpN,sl00,sp00,slN0,spN0,mh2sv1);
            lineintersect(mlN,mp0,mlN,mpN,sl0N,sp0N,slNN,spNN,mh2sv2);
            lineintersect(ml0,mp0,mlN,mp0,sl00,sp00,sl0N,sp0N,mv1sh1);
            lineintersect(ml0,mp0,mlN,mp0,slN0,spN0,slNN,spNN,mv1sh2);
            lineintersect(ml0,mpN,mlN,mpN,sl00,sp00,sl0N,sp0N,mv2sh1);
            lineintersect(ml0,mpN,mlN,mpN,slN0,spN0,slNN,spNN,mv2sh2);

            real8 overlap_l0 = max(max(max(max(max(max(ml0,sl00),sl0N),mh1sv1(0,0)),mh1sv2(0,0)),mv1sh1(0,0)),mv2sh1(0,0));
            real8 overlap_p0 = max(max(max(max(max(max(mp0,sp00),spN0),mh1sv1(1,0)),mh2sv1(1,0)),mv1sh1(1,0)),mv1sh2(1,0));
            real8 overlap_lN = min(min(min(min(min(min(mlN,slN0),slNN),mh2sv1(0,0)),mh2sv2(0,0)),mv1sh2(0,0)),mv2sh2(0,0));
            real8 overlap_pN = min(min(min(min(min(min(mpN,sp0N),spNN),mh1sv2(1,0)),mh2sv2(1,0)),mv2sh1(1,0)),mv2sh2(1,0));

            // ______Corners of overlap master,slave in master system______
            window overlap;
            overlap.linelo = int32(ceil(overlap_l0));
            overlap.linehi = int32(floor(overlap_lN));
            overlap.pixlo = int32(ceil(overlap_p0));
            overlap.pixhi = int32(floor(overlap_pN));

            return overlap;
          } // END getoverlap

        ****************************************************************
         * lineintersect                                                *
         *                                                              *
         * compute intersection point of two line segments              *
         * (master coordinate system)                                   *
          ****************************************************************
        void lineintersect(
                           const real8   &ax,
                           const real8   &ay,
                           const real8   &bx,
                           const real8   &by,
                           const real8   &cx,
                           const real8   &cy,
                           const real8   &dx,
                           const real8   &dy,
                           matrix<real8> &exy)
              {
                TRACE_FUNCTION("lineintersect (FvL 22-SEP-2007)")

                real8 u1 = bx-ax;
                real8 u2 = by-ay;
                real8 v1 = dx-cx;
                real8 v2 = dy-cy;
                real8 w1 = ax-cx;
                real8 w2 = ay-cy;

                real8 s = (v2*w1-v1*w2)/(v1*u2-v2*u1);
                exy(0,0) = ax+s*u1;
                exy(1,0) = ay+s*u2;
              } // END lineintersect



     */

}
