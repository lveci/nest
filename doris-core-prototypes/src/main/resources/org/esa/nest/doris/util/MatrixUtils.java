package org.esa.nest.doris.util;

import org.jblas.FloatMatrix;
import org.jblas.DoubleMatrix;

class MatrixUtils {
    
    MatrixUtils() {
    }

    /**
    * method: normalize
    * description: rescale data[min,max] to interval [-2,2]
    *
    * input:
    *  - data
    *  - min, max
    * output:
    *  - data matrix by reference
    */
    public void normalize(FloatMatrix data, double min, double max){
        // ______ zero mean, rescale ______
        data = data.mmul((float)(.5*(min+max)));    // [.5a-.5b, -.5a+.5b]
        data = data.divi((float)(.25*(max-min)));   // [-2 2]
    } // END normalize float

    public void normalize(DoubleMatrix data, double min, double max){
        // ______ zero mean, rescale ______
        data = data.mmul(.5*(min+max));    // [.5a-.5b, -.5a+.5b]
        data = data.divi(.25*(max-min));   // [-2 2]
    } // END normalize float

}
