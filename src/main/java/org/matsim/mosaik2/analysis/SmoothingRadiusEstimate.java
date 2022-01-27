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

    static double f(double E, Coord from, Coord to, Coord receiverPoint, double R, double le) {

        var A = calculateA(from, receiverPoint);
        var B = calculateB(from, to, receiverPoint);

        return g(A, B, E, R, le) * h(B, R, le);
    }

    static double fDerived(double E, Coord from, Coord to, Coord receiverPoint, double R, double le) {

        var A = calculateA(from, receiverPoint);
        var B = calculateB(from, to, receiverPoint);

        return gDerived(A, B, E, R, le) * h(B, R, le) + g(A, B, E, R, le) * hDerived(B, R, le);
    }

    private static double g(double A, double B, double E, double R, double le) {

        var C = calculateC(A, B, R, le);
        var D = calculateD(le);

        return E * Math.exp(C * -1) * R * D;
    }

    private static double gDerived(double A, double B, double E, double R, double le) {

        var C = calculateC(A, B, R, le);
        var D = calculateD(le);

        return E * D * 2 * C * Math.exp(C * -1) + E * D * Math.exp(C * -1);
    }

    private static double h(double B, double R, double le) {

        var F = calculateF(B, R, le);
        var G = calculateG(B, R, le);

        return erf(F) - erf(G);
    }

    private static double hDerived(double B, double R, double le) {

        var G = calculateG(B, R, le);
        var H = calculateH(B, R, le);
        var I = calculateI(B, R, le);
        var J = 2 * B / (Math.sqrt(Math.PI) * le * R*R);

        return -1 * H * Math.exp(I * -1) + J * Math.exp(G*G * -1);
    }


    private static double calculateA(Coord from, Coord centroid) {

        var x0 = centroid.getX();
        var y0 = centroid.getY();
        var x1 = from.getX();
        var y1 = from.getY();

        return (x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0);
    }

    private static double calculateB(Coord from, Coord to, Coord centroid) {

        var x0 = centroid.getX();
        var y0 = centroid.getY();
        var x1 = from.getX();
        var y1 = from.getY();
        var x2 = to.getX();
        var y2 = to.getY();

        return (x2 - x1) * (x1 - x0) + (y2 - y1) * (y1 - y0);
    }

    private static double calculateC(double A, double B, double R, double le) {
        return (A - B*B / (le*le)) / R*R;
    }

    private static double calculateD(double le) {
        return Math.sqrt(Math.PI) / 2 / le;
    }

    private static double calculateF(double B, double R, double le) {
        return le / R + B / le * R;
    }

    private static double calculateG(double B, double R, double le) {
        return B / le / R;
    }

    private static double calculateH(double B, double R, double le) {
        return 2 * (B + le*le) / (Math.sqrt(Math.PI) * le * R*R);
    }

    private static double calculateI(double B, double R, double le) {
        return (B + le*le) * (B + le*le) / (le*le * R*R);
    }

    private static double erf(double x) {
        try {
            return Erf.erf(x);
        } catch (MathException e) {
            throw new RuntimeException(e);
        }
    }

}
