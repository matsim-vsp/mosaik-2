package org.matsim.mosaik2.analysis;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.special.Erf;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;

import java.util.Map;

/**
 * This class is going to be used to estimate the smoothing radius R for the spatial distribution of emissions explained
 * in https://depositonce.tu-berlin.de/handle/11303/6266 Appendix A. The Methods are named according to the naming in the
 * Thesis. Hence the cryptic names like A, B and so on.
 */
@Log4j2
public class SmoothingRadiusEstimate {

    private static final double THRESHOLD =  10E-2;

    static double estimateR(Map<Link, Double> emissions, Coord receiverPoint, final double initialR, double xj) {

        double Rn = initialR;
        double Rprev;

        int counter = 0;

        do {
            Rprev = Rn;
            Rn = Rn(emissions, receiverPoint, Rprev, xj);
            counter++;
        } while(Math.abs(Rn - Rprev) > THRESHOLD && counter < 10000);

        log.info("took " + counter + " iterations.");
        return Rn;
    }

    static double Rn(Map<Link, Double> emissions, Coord receiverPoint, double Rprev, double xj) {
        return Rprev - F(emissions, receiverPoint, Rprev, xj) / FDerived(emissions, receiverPoint, Rprev, xj);
    }

    static double F(Map<Link, Double> emissions, Coord receiverPoint, double R, double xj) {

        var sumWeightedEmissionsAtReceiverPoint = emissions.entrySet().stream()
                .mapToDouble(e -> f(e.getValue(), e.getKey().getFromNode().getCoord(), e.getKey().getToNode().getCoord(), receiverPoint, R, e.getKey().getLength()))
                .sum();

        return sumWeightedEmissionsAtReceiverPoint - xj;
    }

    static double FDerived(Map<Link, Double> emissions, Coord receiverPoint, double R, double xj) {

        var gradient = emissions.entrySet().stream()
                .mapToDouble(e -> fDerived(e.getValue(), e.getKey().getFromNode().getCoord(), e.getKey().getToNode().getCoord(), receiverPoint, R, e.getKey().getLength()))
                .sum();

        return gradient - xj;
    }

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

        return Erf.erf(F) - Erf.erf(G);
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
        return (A - B*B / (le*le)) / (R*R);
    }

    private static double calculateD(double le) {
        return Math.sqrt(Math.PI) / 2 / le;
    }

    private static double calculateF(double B, double R, double le) {
        return le / R + B / le / R;
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
}
