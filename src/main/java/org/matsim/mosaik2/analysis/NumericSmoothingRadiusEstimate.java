package org.matsim.mosaik2.analysis;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math.MathException;
import org.apache.commons.math.special.Erf;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;

@Log4j2
public class NumericSmoothingRadiusEstimate {

    private static final double R_THRESHOLD =  10E-9;
    private static final double BISECT_THRESHOLD = 5;
    private static final double h = 1E-9; // this is the smallest number one could add and still get a difference for R + h

    public static double estimateR(Object2DoubleMap<Link> emissions, Coord receiverPoint, final double xj, final double initialR) {

        double Rn = initialR;
        double Rprev;

        do {
            Rprev = Rn;
            Rn = Rn(emissions, receiverPoint, Rprev, xj);
        } while (Math.abs(Rn - Rprev) > R_THRESHOLD);

        return Rn;
    }

    public static double estimateRWithBisect(Object2DoubleMap<Link> emissions, Coord receiverPoint, final double xj) {

        double lowerBound = 0.1; // set this close to 0. If 0, we miss a few of the zero points
        double upperBound = 500;
        double lowerBoundResult = sumF(emissions, receiverPoint, lowerBound, xj);
        double upperBoundResult = sumF(emissions, receiverPoint, upperBound, xj);
        double center;
        double centerResult;

        if (Math.signum(lowerBoundResult) == Math.signum(upperBoundResult))
            throw new RuntimeException("There is no zero point in the intervall between [0.1, 500]. Consider changing the interval or check whether the input is plausible.");

        do {
            // find center
            center = (upperBound + lowerBound) / 2;

            // calculate result for center
            centerResult = sumF(emissions, receiverPoint, center, xj);

            // choose new bounds
            if (Math.signum(centerResult) == Math.signum(lowerBoundResult)) {
                lowerBound = center;
                lowerBoundResult = centerResult;
            } else {
                upperBound = center;
                // not necessary to also copy upper bounds since it is never used again.
            }
        } while (upperBound - lowerBound > BISECT_THRESHOLD);

        // newton method converges faster than bisect. After having a plausible interval do the remaining steps with newton
        // use center because this was the last boundary which was updated and is likely to be on a slope suitable for the
        // newton method.
        return estimateR(emissions, receiverPoint, xj, center);
    }

    static double Rn(Object2DoubleMap<Link> emissions, Coord receiverPoint, double Rprev, double xj) {

        var lowerR = Rprev - h;
        var upperR = Rprev + h;
        var sumRprev = sumF(emissions, receiverPoint, Rprev, xj);
        var sumLowerR = sumf(emissions, receiverPoint, lowerR, xj);
        var sumUpperR = sumf(emissions, receiverPoint, upperR, xj);

        return Rprev - (sumRprev * 2 * h / (sumUpperR - sumLowerR));
    }

    static double sumF(Object2DoubleMap<Link> emissions, Coord receiverPoint, double R, double xj) {
        return emissions.object2DoubleEntrySet().stream()
                .mapToDouble(entry -> F(
                        entry.getKey().getFromNode().getCoord(),
                        entry.getKey().getToNode().getCoord(),
                        receiverPoint,
                        entry.getKey().getLength(),
                        R,
                        entry.getDoubleValue(),
                        xj
                ))
                .sum();
    }

    static double sumf(Object2DoubleMap<Link> emissions, Coord receiverPoint, double R, double xj) {
        return emissions.object2DoubleEntrySet().stream()
                .mapToDouble(entry -> entry.getDoubleValue() * calculateWeight(
                        entry.getKey().getFromNode().getCoord(),
                        entry.getKey().getToNode().getCoord(),
                        receiverPoint,
                        entry.getKey().getLength(),
                        R
                ))
                .sum();
    }

    static double F(final Coord from, final Coord to, final Coord receiverPoint, final double le, final double R, final double E, final double xj) {
        return E * calculateWeight(from, to, receiverPoint, le, R) - xj;
    }

    static double calculateWeight(final Coord from, final Coord to, final Coord receiverPoint, final double le, final double R) {

        double A = calculateA(from, receiverPoint);
        double B = calculateB(from, to, receiverPoint);
        double C = calculateC(le);

        double upperLimit = le + B / le;
        double lowerLimit = B / le;
        double integrationUpperLimit = erf(upperLimit / R);
        double integrationLowerLimit = erf(lowerLimit / R);
        double exponent = -(A - (B*B) / (le*le)) / (R*R);

        return Math.exp(exponent) * R * C * (integrationUpperLimit - integrationLowerLimit);
    }

    private static double calculateA(Coord from, Coord receiverPoint) {

        var x0 = receiverPoint.getX();
        var y0 = receiverPoint.getY();
        var x1 = from.getX();
        var y1 = from.getY();

        return (x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0);
    }

    private static double calculateB(Coord from, Coord to, Coord receiverPoint) {

        var x0 = receiverPoint.getX();
        var y0 = receiverPoint.getY();
        var x1 = from.getX();
        var y1 = from.getY();
        var x2 = to.getX();
        var y2 = to.getY();

        return (x2 - x1) * (x1 - x0) + (y2 - y1) * (y1 - y0);
    }

    private static double calculateC(double le) {
        return Math.sqrt(Math.PI) / le / 2;
    }

    private static double erf(double x) {
        try {
            return Erf.erf(x);
        } catch (MathException e) {
            throw new RuntimeException(e);
        }
    }
}
