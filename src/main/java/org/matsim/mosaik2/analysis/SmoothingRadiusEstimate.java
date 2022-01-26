package org.matsim.mosaik2.analysis;

import org.apache.commons.math.MathException;
import org.apache.commons.math.special.Erf;
import org.matsim.api.core.v01.Coord;

/**
 * This class is going to be used to estimate the smoothing radius R for the spatial distribution of emissions explained
 * in https://depositonce.tu-berlin.de/handle/11303/6266 Appendix A. The Methods are named according to the naming in the
 * Thesis. Hence the cryptic names like A, B and so on.
 */
public class SmoothingRadiusEstimate {

    double F(double E, double xj, Coord from, Coord to, Coord centroid, double R, double le) {

        var A = calculateA(from, centroid);
        var B = calculateB(from, to, centroid);

        return g(E, R, le, A, B) * h(R, le, B) - xj;
    }

    double FDerived(double E, double xj, Coord from, Coord to, Coord centroid, double R, double le) {

        var A = calculateA(from, centroid);
        var B = calculateB(from, to, centroid);

        return gDerived(E, A, B, R, le) * h(R, le, B) + g(E, R, le, A, B) * hDerived();
    }

    double g(double E, double R, double le, double A, double B) {

        var middlePart = calculateMiddlePart(R, le); // TODO find better variable name
        var exponent = calculateExponent(A, B, R, le);

        return E * Math.exp(exponent) * middlePart;
    }

    double gDerived(double E, double A, double B, double R, double le) {

        var exponent = calculateExponent(A, B, R, le);
        var circleStuff = (Math.sqrt(Math.PI)) / 2 / le;

        return circleStuff * E * 2 * exponent * Math.exp(exponent * (-1)) + circleStuff * E * Math.exp(exponent * (-1));
    }

    double h(double R, double le, double B) {

        var D = calculateD(B, R,le);
        var E = calculateE(B, R, le);

        return erf(D) - erf(E);
    }

    double hDerived() {
        throw new RuntimeException("h' is not yet implemented");
    }

    private double calculateA(Coord from, Coord centroid) {

        var x0 = centroid.getX();
        var y0 = centroid.getY();
        var x1 = from.getX();
        var y1 = from.getY();

        return (x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0);
    }

    private double calculateB(Coord from, Coord to, Coord centroid) {

        var x0 = centroid.getX();
        var y0 = centroid.getY();
        var x1 = from.getX();
        var y1 = from.getY();
        var x2 = to.getX();
        var y2 = to.getY();

        return (x2 - x1) * (x1 - x0) + (y2 - y1) * (y1 - y0);
    }

    private double calculateExponent(double A, double B,  double R, double le) {
        return -1 * (A - B*B / le*le) / R*R;
    }

    private double calculateE(double B, double R, double le) {
        return B / le / R;
    }

    private double calculateD(double B, double R, double le) {
        return le / R + B / le / R;
    }

    private double calculateMiddlePart(double R, double le) {
        return R * Math.sqrt(Math.PI) / le / 2; // TODO find better variable name, pre-compute sqrt(PI)? or wil the compiler do it for us?
    }

    private double erf(double x) {
        try {
            return Erf.erf(x);
        } catch (MathException e) {
            throw new RuntimeException(e);
        }
    }

}
