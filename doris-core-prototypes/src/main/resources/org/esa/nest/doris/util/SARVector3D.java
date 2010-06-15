package org.esa.nest.doris.util;

import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

/**
 * Created by IntelliJ IDEA.
 * User: pmar
 * Date: Mar 29, 2010
 * Time: 7:19:04 PM
 * To change this template use File | Settings | File Templates.
 */
public class SARVector3D extends Vector3d {

    public SARVector3D(double v, double v1, double v2) {
        super(v, v1, v2);
    }

    public SARVector3D(double[] doubles) {
        super(doubles);
    }

    public SARVector3D(Vector3d vector3d) {
        super(vector3d);
    }

    public SARVector3D() {
    }

    public SARVector3D(Vector3f vector3f) {
        super(vector3f);
    }

    /**
     * getters: full array
     */
    public double[] getXYZ(){
        double[] outputArray = new double[3];
        this.get(outputArray);
        return outputArray;
    }

    /**
     * getters: element
     */
    public double getElement(int i){
        return this.getXYZ()[i];
    }

    /**
     * getters: X
      */
    public double getX(){
        return this.getXYZ()[0];
    }

    /**
     * getters: Y
     * @return
     */
    public double getY(){
        return this.getXYZ()[1];
    }

    /**
     * getters: Z
     * @return
     */
    public double getZ(){
        return this.getXYZ()[2];
    }

    /**
     *  Checks if this SARVector3D is equal to the specified
     *  x, y, and z coordinates.
     */
    public boolean equals(double x, double y, double z) {
        return (this.x == x && this.y == y && this.z == z);
    }

    /**
        Adds the specified (x, y, z) values to this vector.
    */
    public void add(double x, double y, double z) {
        this.x += x;
        this.y += y;
        this.z += z;
    }

    /**
        Adds the specified vector to this vector.
    */
    public void add(SARVector3D v) {
        add(v.x, v.y, v.z);
    }

    /**
        Subtracts the specified (x, y, z) values to this vector.
    */
    public void subtract(double x, double y, double z) {
        add(-x, -y, -z);
    }

    /**
        Subtracts the specified vector from this vector.
    */
    public void subtract(SARVector3D v) {
        add(-v.x, -v.y, -v.z);
    }

    /**
        Multiplies this vector by scalar.
    */
    public void multiply(double s) {
       x*=s;
       y*=s;
       z*=s;
    }
    /**
        Divides this vector by scalar.
    */
    public void divide(float s) {
       x/=s;
       y/=s;
       z/=s;
    }

}

//     /**
//        Creates a new SARVector3D with the same values as the
//        specified SARVector3D.
//      * @param v
//      */
//    public SARVector3D(SARVector3D v) {
//        this(v.x, v.y, v.z);
//    }
//    /**
//        Creates a new SARVector3D with the specified (x, y, z) values.
//     * @param x
//     * @param y
//     * @param z
//     */
//    public SARVector3D(double x, double y, double z) {
//        setTo(x, y, z);
//    }

//    /**
//        Checks if this SARVector3D is equal to the specified Object.
//        They are equal only if the specified Object is a SARVector3D
//        and the two SARVector3D's x, y, and z coordinates are equal.
//    */
//    public boolean equals(Object obj) {
//        SARVector3D v = (SARVector3D)obj;
//        return (v.x == x && v.y == y && v.z == z);
//    }




//    /**
//        Sets the vector to the same values as the specified
//        SARVector3D.
//    */
//    public void setTo(SARVector3D v) {
//        setTo(v.x, v.y, v.z);
//    }
//
//    /**
//        Sets this vector to the specified (x, y, z) values.
//    */
//    public void setTo(double x, double y, double z) {
//        this.x = x;
//        this.y = y;
//        this.z = z;
//    }
//    /**
//        Returns the length of this vector as a double.
//    */
//    public double length() {
//        return Math.sqrt(x*x + y*y + z*z);
//    }
//
//    /**
//        Converts SARVector3D to a unit vector ~ length 1.
//        Same as calling v.divide(v.length()).
//    */
//    public void normalize() {
//        divide(length());
//    }
//
//    /**
//        Converts this SARVector3D to a String representation.
//    */
//    public String toString() {
//        return "(" + x + ", v + y + ", " + z + ")";
//    }
//
//        public void assignToPlus(Vector3d point){
//            this.x += point.x;
//            this.y += point.y;
//            this.z += point.z;
//        }
//
//        public void assignToMinus(Vector3d point){
//            this.x -= point.x;
//            this.y -= point.y;
//            this.z -= point.z;
//        }
//
//        public void multiplyByScalar(double scalar){
//            this.x *= scalar;
//            this.y *= scalar;
//            this.z *= scalar;
//        }
//
//        public void divideByScalar(double scalar){
//            this.x /= scalar;
//            this.y /= scalar;
//            this.z /= scalar;
//        }
//
//        public void multiplyByPoint(Vector3d point){
//            this.x *= point.x;
//            this.y *= point.y;
//            this.z *= point.z;
//        }
//
//        public void divideByPoint(Vector3d point){
//            this.x /= point.x;
//            this.y /= point.y;
//            this.z /= point.z;
//        }
//
////        public void binaryPlus(Vector3d point){
////            this += point;
////        }
////
////        public void binaryMinus(Vector3d point){
////            this -= point;
////        }
////
//        public boolean isEqualTo(Vector3d point){
//            return this == point;
//        }
//
//        public boolean isNotEqualTo(Vector3d point){
//            return this != point;
//        }
//
//
//        // scalar product
//        public double dotProduct(Vector3d point){
//            return this.dot(point);
//        }
//
//        // cross product
//        public void crossProduct(Vector3d point){
//            cross(this,point);
//        }
//
//        //distance
//        public double distance(Vector3d point){
//            return Math.sqrt(
//                            Math.pow(this.x - point.x,2) +
//                            Math.pow(this.y - point.y,2) +
//                            Math.pow(this.z - point.z,2)
//                            );
//        }
//
//    }
//

