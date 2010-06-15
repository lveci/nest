package org.esa.nest.doris.util;

/**
 * Created by IntelliJ IDEA.
 * User: pmar
 * Date: Apr 13, 2010
 * Time: 5:17:16 PM
 * To change this template use File | Settings | File Templates.
 */

// Collection of SAR interpolation kernels: limited support for testing

//TODO: document and integrate c++ code bellow

//TODO: generalize and link to general NEST interpolators

//TODO: wrap around an interface?

public class InterpolationKernels {

    // data types for all kernels:
    //   const matrix<real4> &x // use JBlass or some other LA/matrix class
    //
    // data types for knab and rc_kernel
    //   const real4 CHI
    //   const int32 N

    //constructor

    /* methods

        0) basic interpolators: core of all the functions below

            0.1) sinc(const real4 &x)
            0.2) sinc(const real8 &x)
            0.3) rect(const real4 &x)
            0.4) rect(const real8 &x)

        --------------
        1) rect function for matrix: stepping function
        2) tri function for matrix: piecewise linear, triangle
        3) cc4: cubic convolution 4 points
        4) cc6: cubic convolution 6 points
        5) ts6: truncated sinc 6 points
        6) ts8: truncated sinc 8 points
        7) ts16: truncated sinc 16 points
        8) knab interpolator
        9) rc_kernel: raised cosine window of N points

     */

    /*
       dependencies: strong dependency on JBLAS : Implement replacement!
          - complex data types
          - complex arythmetics

            http://mikiobraun.github.com/jblas/javadoc/org/jblas/MatrixFunctions.html
             static double	        cosh(double x)
             static DoubleMatrix	cosh(DoubleMatrix x)
             static float	        cosh(float x)
             static FloatMatrix	    cosh(FloatMatrix x)

     */

}

/*

inline real4 sinc(const real4 &x)
  {return ((x==0) ? 1 : sin(PI*x)/(PI*x));}

inline real8 sinc(const real8 &x)
  {return ((x==0) ? 1 : sin(PI*x)/(PI*x));}

inline real4 rect(const real4 &x)
  {
    real4 ans = 0.0;
    if (x<0.5 && x>-0.5) ans = 1;
    else if (x==0.5 || x==-0.5) ans = 0.5;
    return ans;
  }

inline real4 tri(const real4 &x)
  {
    real4 ans = 0.0;
    if (x<1.0 && x>-1.0)
    {(x<0) ? ans=1+x : ans=1-x;}
    return ans;
  }

****************************************************************
 *    rect                                                      *
 *                                                              *
 * rect function for matrix (stepping function?)                *
 *                                                              *
 * input:                                                       *
 *  - x-axis                                                    *
 * output:                                                      *
 *  - y=f(x); function evaluated at x                           *
 *                                                              *
 *    Bert Kampes, 16-Mar-1999                                  *
 ****************************************************************
matrix<real4> rect(
		   const matrix<real4> &x)
{
  TRACE_FUNCTION("rect (BK 16-Mar-1999)");
  if (x.pixels() != 1)
    {
      PRINT_ERROR("rect: standing vectors only.");
      throw(input_error);
    }

  matrix<real4> y(x.lines(),1);
  for (register uint i=0;i<y.lines();i++)
    y(i,0) = rect(x(i,0));
  return y;
} // END rect


****************************************************************
 *    tri                                                       *
 *                                                              *
 * tri function for matrix (piecewize linear?, triangle)        *
 *                                                              *
 * input:                                                       *
 *  - x-axis                                                    *
 * output:                                                      *
 *  - y=f(x); function evaluated at x                           *
 *                                                              *
 *    Bert Kampes, 16-Mar-1999                                  *
 ****************************************************************
matrix<real4> tri(
		  const matrix<real4> &x)
{
  TRACE_FUNCTION("tri (BK 16-Mar-1999)")
    if (x.pixels() != 1)
      {
	PRINT_ERROR("tri: standing vectors only.")
	  throw(input_error);
      }
  matrix<real4> y(x.lines(),1);
  for (register uint i=0;i<y.lines();i++)
    y(i,0) = tri(x(i,0));
  return y;
} // END tri



 ****************************************************************
 *    cc6                                                       *
 *                                                              *
 * cubic convolution 6 points                                   *
 *                                                              *
 * input:                                                       *
 *  - x-axis                                                    *
 * output:                                                      *
 *  - y=f(x); function evaluated at x                           *
 *                                                              *
 *    Bert Kampes, 16-Mar-1999                                  *
 * corrected (alfa+beta)->(alfa-beta) after correction in paper *
 * by Ramon Hanssen                                             *
 *    Bert Kampes, 16-Mar-1999                                  *
 ****************************************************************
matrix<real4> cc6(
		  const matrix<real4> &x)
{
  TRACE_FUNCTION("cc6 (BK 16-Mar-1999)");
  if (x.pixels() != 1)
    {
      PRINT_ERROR("cc6: standing vectors only.")
	throw(input_error);
    }

  real4 alpha = -.5;
  real4 beta  =  .5;
  matrix<real4> y(x.lines(),1);
  for (register uint i=0;i<y.lines();i++)
    {
      real4 xx2 = sqr(x(i,0));
      real4 xx  = sqrt(xx2);
      if      (xx < 1)
	y(i,0) = (alpha-beta+2)*xx2*xx - (alpha-beta+3)*xx2 + 1;
      //y(i,0) = (alpha+beta+2)*xx2*xx - (alpha+beta+3)*xx2 + 1;??wrong in paper?
      else if (xx < 2)
	y(i,0) =   alpha*xx2*xx - (5*alpha-beta)*xx2
	  + (8*alpha-3*beta)*xx - (4*alpha-2*beta);
      else if (xx < 3)
	y(i,0) = beta*xx2*xx - 8*beta*xx2 + 21*beta*xx - 18*beta;
      else
	y(i,0) = 0.;
    }
  return y;
} // END cc6


****************************************************************
 *    ts6                                                       *
 *                                                              *
 * truncated sinc 6 points                                      *
 *                                                              *
 * input:                                                       *
 *  - x-axis                                                    *
 * output:                                                      *
 *  - y=f(x); function evaluated at x                           *
 *                                                              *
 *    Bert Kampes, 16-Mar-1999                                  *
 ****************************************************************
matrix<real4> ts6(
		  const matrix<real4> &x)
{
  TRACE_FUNCTION("ts6 (BK 16-Mar-1999)");
  if (x.pixels() != 1)
    {
      PRINT_ERROR("ts6: standing vectors only.")
	throw(input_error);
    }

  matrix<real4> y(x.lines(),1);
  for (register uint i=0;i<y.lines();i++)
    y(i,0) = sinc(x(i,0)) * rect(x(i,0)/6.0);
  return y;
} // END ts6


****************************************************************
 *    ts8                                                       *
 *                                                              *
 * truncated sinc 8 points                                      *
 *                                                              *
 * input:                                                       *
 *  - x-axis                                                    *
 * output:                                                      *
 *  - y=f(x); function evaluated at x                           *
 *                                                              *
 *    Bert Kampes, 16-Mar-1999                                  *
 ****************************************************************
matrix<real4> ts8(
		  const matrix<real4> &x)
{
  TRACE_FUNCTION("ts8 (BK 16-Mar-1999)");
  if (x.pixels() != 1)
    {
      PRINT_ERROR("ts8: standing vectors only.")
	throw(input_error);
    }

  matrix<real4> y(x.lines(),1);
  for (register uint i=0;i<y.lines();i++)
    y(i,0) = sinc(x(i,0)) * rect(x(i,0)/8.0);
  return y;
} // END ts8


****************************************************************
 *    ts16                                                      *
 *                                                              *
 * truncated sinc 16 points                                     *
 *                                                              *
 * input:                                                       *
 *  - x-axis                                                    *
 * output:                                                      *
 *  - y=f(x); function evaluated at x                           *
 *                                                              *
 *    Bert Kampes, 16-Mar-1999                                  *
 ****************************************************************
matrix<real4> ts16(
		   const matrix<real4> &x)
{
  TRACE_FUNCTION("ts16 (BK 16-Mar-1999)");
  if (x.pixels() != 1)
    {
      PRINT_ERROR("ts16: standing vectors only.")
	throw(input_error);
    }
  matrix<real4> y(x.lines(),1);
  for (register uint i=0;i<y.lines();i++)
    y(i,0) = sinc(x(i,0)) * rect(x(i,0)/16.0);
  return y;
} // END ts16


****************************************************************
 *    knab                                                      *
 *                                                              *
 * KNAB window of N points, oversampling factor CHI             *
 *                                                              *
 * defined by: Migliaccio IEEE letters vol41,no5, pp1105,1110, 2003 *
 * k = sinc(x).*(cosh((pi*v*L/2)*sqrt(1-(2.*x./L).^2))/cosh(pi*v*L/2));
 *                                                              *
 * input:                                                       *
 *  - x-axis                                                    *
 *  - oversampling factor of bandlimited sigal CHI              *
 *  - N points of kernel size                                   *
 * output:                                                      *
 *  - y=f(x); function evaluated at x                           *
 *                                                              *
 *    Bert Kampes, 22-DEC-2003                                  *
 ****************************************************************
matrix<real4> knab(
		   const matrix<real4> &x,
		   const real4 CHI,
		   const int32 N)
{
  TRACE_FUNCTION("knab (BK 22-Dec-2003)");
  if (x.pixels() != 1)
    {
      PRINT_ERROR("knab: standing vectors only.")
	throw(input_error);
    }
  matrix<real4> y(x.lines(),1);
  real4 v  = 1.0-1.0/CHI;
  real4 vv = PI*v*real4(N)/2.0;
  for (register uint i=0;i<y.lines();i++)
    y(i,0) = sinc(x(i,0))*cosh(vv*sqrt(1.0-sqr(2.0*x(i,0)/real4(N))))/cosh(vv);
  return y;
} // END knab



****************************************************************
 *    rc_kernel                                                 *
 *                                                              *
 * Raised Cosine window of N points, oversampling factor CHI    *
 *                                                              *
 * defined by: Cho, Kong and Kim, J.Elektromagn.Waves and appl  *
 *  vol19, no.1, pp, 129-135, 2005;                             *
 * claimed to be best, 0.9999 for 6 points kernel.              *
 * k(x) = sinc(x).*[cos(v*pi*x)/(1-4*v^2*x^2)]*rect(x/L)     *
 *  where v = 1-B/fs = 1-1/Chi (roll-off factor; ERS: 15.55/18.96)*
 *        L = 6 (window size)                                   *
 *                                                              *
 * input:                                                       *
 *  - x-axis                                                    *
 *  - oversampling factor of bandlimited sigal CHI              *
 *  - N points of kernel size                                   *
 * output:                                                      *
 *  - y=f(x); function evaluated at x                           *
 *                                                              *
 #%// Bert Kampes, 28-Jul-2005
****************************************************************
matrix<real4> rc_kernel(
			const matrix<real4> &x,
			const real4 CHI,
			const int32 N)
{
  TRACE_FUNCTION("rc_kernel (BK 28-Jul-2005)");
  if (x.pixels() != 1)
    {
      PRINT_ERROR("rc_kernel: standing vectors only.")
	throw(input_error);
    }
  matrix<real4> y(x.lines(),1);
  real4 v  = 1.0-1.0/CHI;// alpha in paper cho05
  for (register uint i=0;i<y.lines();i++)
    y(i,0) = sinc(x(i,0)) * rect(x(i,0)/real4(N))*
      cos(v*PI*x(i,0)) / (1.0-sqr(2.0*v*x(i,0)));
  return y;
} // END rc_kernel

*/