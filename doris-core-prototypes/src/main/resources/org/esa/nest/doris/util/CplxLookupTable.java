package org.esa.nest.doris.util;

/**
 * Created by IntelliJ IDEA.
 * User: pmar
 * Date: Apr 13, 2010
 * Time: 5:18:26 PM
 * To change this template use File | Settings | File Templates.
 */
public class CplxLookupTable {
}

/*
  // ====== Create lookup table ======
  // ______ e.g. four point interpolator
  // ______ interpolating point: p=6.4925
  // ______ required points: 5, 6, 7, 8
  // ______ kernel number from lookup table: floor(.4925*INTERVAL+.5)
  // ______  table[0]= 0 1 0 0 ;table[INTERVAL]= 0 0 1 0
  // ______ intervals in lookup table: dx
  // ______ for high doppler 100 is OK (fdc=3prf; 6pi --> 10deg error?)
  const int32 INTERVAL  = 127;                          // precision: 1./INTERVAL [pixel]
  const int32 Ninterval = INTERVAL + 1;                 // size of lookup table
  const real8 dx        = 1.0/INTERVAL;                 // interval look up table
  INFO << "resample: lookup table size: " << Ninterval;
  INFO.print();

  register int32 i;
  matrix<real4> x_axis(Npoints,1);
  for (i=0; i<Npoints; ++i)
    x_axis(i,0) = 1.0 - Npointsd2 + i;                  // start at [-1 0 1 2]

  // ______ Lookup table complex because of multiplication with complex ______
  // ______ Loopkup table for azimuth and range and ______
  // ______ shift spectrum of azi kernel with doppler centroid ______
  // ______ kernel in azimuth should be sampled higer ______
  // ______ and may be different from range due to different ______
  // ______ oversampling ratio and spectral shift (const) ______
  matrix<complr4> *pntKernelAz[Ninterval];// kernel in azimuth
  matrix<complr4> *pntKernelRg[Ninterval];// kernel in range
  // ______ same axis required for shift azimuth spectrum as used ______
  // ______ for kernel to avoid phase shift ______
  matrix<real4>   *pntAxis[Ninterval];

  for (i=0; i<Ninterval; ++i)
    {
      pntKernelAz[i] = new matrix<complr4> (Npoints,1);
      pntKernelRg[i] = new matrix<complr4> (Npoints,1);
      pntAxis[i]     = new matrix<real4>   (Npoints,1);// only used for azishift
      switch(resampleinput.method)
	{
	  // --- Extremely simple kernels (not good, but fast) ---
	case rs_rect:
	  (*pntKernelAz[i]) = mat2cr4(rect(x_axis));
	  (*pntKernelRg[i]) = mat2cr4(rect(x_axis));
	  break;
	case rs_tri:
	  (*pntKernelAz[i]) = mat2cr4(tri(x_axis));
	  (*pntKernelRg[i]) = mat2cr4(tri(x_axis));
	  break;
	  // --- Truncated sinc ---
	case rs_ts6p:
	  (*pntKernelAz[i]) = mat2cr4(ts6(x_axis));
	  (*pntKernelRg[i]) = mat2cr4(ts6(x_axis));
	  break;
	case rs_ts8p:
	  (*pntKernelAz[i]) = mat2cr4(ts8(x_axis));
	  (*pntKernelRg[i]) = mat2cr4(ts8(x_axis));
	  break;
	case rs_ts16p:
	  (*pntKernelAz[i]) = mat2cr4(ts16(x_axis));
	  (*pntKernelRg[i]) = mat2cr4(ts16(x_axis));
	  break;
	  // --- Cubic Convolution kernel: theoretical better than truncated sinc. ---
	case rs_cc4p:
	  (*pntKernelAz[i]) = mat2cr4(cc4(x_axis));
	  (*pntKernelRg[i]) = mat2cr4(cc4(x_axis));
	  break;
	case rs_cc6p:
	  (*pntKernelAz[i]) = mat2cr4(cc6(x_axis));
	  (*pntKernelRg[i]) = mat2cr4(cc6(x_axis));
	  break;
	  // --- KNAB kernel: theoretical better than cubic conv. ---
	case rs_knab4p:
	  (*pntKernelAz[i]) = mat2cr4(knab(x_axis,CHI_az,4));
	  (*pntKernelRg[i]) = mat2cr4(knab(x_axis,CHI_rg,4));
	  break;
	case rs_knab6p:
	  (*pntKernelAz[i]) = mat2cr4(knab(x_axis,CHI_az,6));
	  (*pntKernelRg[i]) = mat2cr4(knab(x_axis,CHI_rg,6));
	  break;
	case rs_knab8p:
	  (*pntKernelAz[i]) = mat2cr4(knab(x_axis,CHI_az,8));
	  (*pntKernelRg[i]) = mat2cr4(knab(x_axis,CHI_rg,8));
	  break;
	case rs_knab10p:
	  (*pntKernelAz[i]) = mat2cr4(knab(x_axis,CHI_az,10));
	  (*pntKernelRg[i]) = mat2cr4(knab(x_axis,CHI_rg,10));
	  break;
	case rs_knab16p:
	  (*pntKernelAz[i]) = mat2cr4(knab(x_axis,CHI_az,16));
	  (*pntKernelRg[i]) = mat2cr4(knab(x_axis,CHI_rg,16));
	  break;
	  // --- Raised cosine: theoretical best ---
	case rs_rc6p:
	  (*pntKernelAz[i]) = mat2cr4(rc_kernel(x_axis,CHI_az,6));
	  (*pntKernelRg[i]) = mat2cr4(rc_kernel(x_axis,CHI_rg,6));
	  break;
	case rs_rc12p:
	  (*pntKernelAz[i]) = mat2cr4(rc_kernel(x_axis,CHI_az,12));
	  (*pntKernelRg[i]) = mat2cr4(rc_kernel(x_axis,CHI_rg,12));
	  break;
	default:
	  PRINT_ERROR("impossible.")
	    throw(unhandled_case_error);
	}//kernel selector
      (*pntAxis[i]) = x_axis;// to shift kernelL use: k*=exp(-i*2pi*axis*fdc/prf)
      x_axis       -= dx;    // Note: 'wrong' way (mirrored)
    }
  // ====== Usage: pntKernelAz[0]->showdata(); or (*pntKernelAz[0][0]).showdata(); ======
  // ______ Log kernels to check sum, etc. ______
  DEBUG.print("Overview of LUT for interpolation kernel follows:");
  DEBUG.print("-------------------------------------------------");

  for (i=0; i<Ninterval; ++i)
    {
      // this extra loop is execed only if DEBUG? overkill
      for (int32 x=0; x<Npoints; ++x)
	DEBUG << ((*pntAxis[i])(x,0)) << "      ";
      DEBUG.print();
      real4 sum_az = 0.0;
      real4 sum_rg = 0.0;
      for (int32 x=0; x<Npoints; ++x)
	{
	  DEBUG << real((*pntKernelAz[i])(x,0)) << " ";// complex kernel
	  sum_az += real((*pntKernelAz[i])(x,0));
	  sum_rg += real((*pntKernelRg[i])(x,0));
	}
      DEBUG << "(sum=" << sum_az << ")";
      DEBUG.print();
      DEBUG.print("Normalizing kernel by dividing LUT elements by sum:");
      (*pntKernelAz[i]) /= sum_az;
      (*pntKernelRg[i]) /= sum_rg;
      // ______ Only show azimuth kernel ______
      for (int32 x=0; x<Npoints; ++x)
	DEBUG << real((*pntKernelAz[i])(x,0)) << " ";// complex kernel; normalized
      DEBUG.print();
    }
  PROGRESS.print("Resample: normalized lookup table created (kernel and axis).");

*/