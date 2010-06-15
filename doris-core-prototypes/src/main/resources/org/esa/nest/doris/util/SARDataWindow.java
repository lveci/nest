package org.esa.nest.doris.util;

/**
 * Created by IntelliJ IDEA.
 * User: pmar
 * Date: Apr 13, 2010
 * Time: 6:07:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class SARDataWindow {


//TODO: this is obsolete and only for legacy reasons, should be better integrated in NEST

    // sourceProduct.getSceneRasterWidth()
    // targetProduct.getSceneRasterWidth()

/*

        /***************************************************************
         *                                                              *
         *        Class window + functions                              *
         *        image starts at 1, matrix starts at 0                 *
         *                                                              *
         *    Bert Kampes, 19-Dec-1999                                  *
         *    [MA] Window extend error check - 2008                     *
         ****************************************************************//*

        class window                            // window file={1,N,1,N}
          {
          public:
          uint          linelo,                         // min. line coord.
                        linehi,                         // max. line coord.
                        pixlo,                          // min. pix coord.
                        pixhi;                          // max. pix coord.
          // ______ Constructors ______
          window()
            {linelo=0; linehi=0; pixlo=0; pixhi=0;}
          window(uint ll, uint lh, uint pl, uint ph)
            {
             TRACE_FUNCTION("window() (BK 19-Dec-1998)");

             linelo=ll; linehi=lh; pixlo=pl; pixhi=ph;

             // ______ Check window extend ______ 
             if(  int32(linelo) > int32(linehi) )
               {
                ERROR << "Window: Impossible to continue... l0 coordinate [" << linelo << "] >  linehi [" << linehi << "].";
                ERROR.print();
                throw(usage_error) ;
               }
             else if( int32(pixlo) > int32(pixhi) )
               {
                ERROR << "Window: Impossible to continue... p0 coordinate [" << pixlo << "] > pixelhi [" << pixhi << "].";
                ERROR.print();
                throw(usage_error) ;
               }
             }

          // ______ Copy constructor ______
          window(const window& w)
            {linelo=w.linelo; linehi=w.linehi; pixlo=w.pixlo; pixhi=w.pixhi;}
          // ______ Destructor ______
          ~window()
            {;};// nothing to destruct that isn't destructed automatically
          // ______ Public function in struct ______
          inline void disp() const                      // show content
            {
            DEBUG << "window [l0:lN, p0:pN] = ["
                 << linelo << ":" << linehi << ", " << pixlo  << ":" << pixhi << "]";
            DEBUG.print();
            }
          inline uint lines() const                     // return number of lines
            {return linehi-int32(linelo)+1;}
          inline uint pixels() const                    // return number of pixels
            {return pixhi-int32(pixlo)+1;}
          inline window& operator = (const window &X)// assignment operator
            {if (this != &X)
              {linelo=X.linelo;linehi=X.linehi;pixlo=X.pixlo;pixhi=X.pixhi;}
             return *this;}
          inline bool operator == (const window &X) const
            {return (linelo==X.linelo&&linehi==X.linehi &&
                      pixlo==X.pixlo && pixhi==X.pixhi) ?   true : false;}
          inline bool operator != (const window &X) const
            {return (linelo==X.linelo&&linehi==X.linehi &&
                      pixlo==X.pixlo && pixhi==X.pixhi) ?   false : true;}
          // --- Test program for coordinate class ----------------------------
          // --- This test is executed in inittest() --------------------------
          inline void test()
            {
            // constructors
            window X;
            DEBUG << "window X:      "; X.disp();
            window Y(11,21,103,114);
            DEBUG << "window Y(11,21,103,114): "; Y.disp();
            // functions
            X=Y;
            DEBUG << "X=Y:           "; X.disp();
            DEBUG << "X.lines():     " <<  X.lines(); DEBUG.print();
            DEBUG << "X.pixels():    " <<  X.pixels(); DEBUG.print();
            DEBUG << "X==Y:          " <<  (X==Y); DEBUG.print();
            DEBUG << "X!=Y:          " <<  (X!=Y); DEBUG.print();
            }
          };

*/

}
