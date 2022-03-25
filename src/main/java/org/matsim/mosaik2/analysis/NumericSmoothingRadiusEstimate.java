package org.matsim.mosaik2.analysis;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.special.Erf;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;

@Log4j2
public class NumericSmoothingRadiusEstimate {

    private static final double R_THRESHOLD =  1E-9;
    private static final double BISECT_THRESHOLD = 1E-3; // I think we are fine with such coarse values for now.
    private static final double h = 1E-10; // this is the smallest number one could add and still get a difference for R + h

    public static double estimateR(Object2DoubleMap<Link> emissions, Coord receiverPoint, final double xj, final double initialR) {

        double Rn = initialR;
        double Rprev;
        int counter = 0;

        do {
            counter++;
            Rprev = Rn;
            Rn = Rn(emissions, receiverPoint, Rprev, xj);
        } while (Math.abs(Rn - Rprev) > R_THRESHOLD);

        log.info("Took " + counter + " iterations with newton procedure. R is: " + Rn);

        return Rn;
    }

    public static double estimateRWithBisect(Object2DoubleMap<Link> emissions, Coord receiverPoint, final double xj) {

        double lowerBound = 0.0001; // chose a value close to 0. With 0.0 the whole thing didn't work out anymore.
        double upperBound = 50;
        double lowerBoundResult = sumf(emissions, receiverPoint, lowerBound) - xj;
        double upperBoundResult = sumf(emissions, receiverPoint, upperBound) - xj;
        double center;
        double centerResult;
        int counter = 0;

        if (Math.signum(lowerBoundResult) == Math.signum(upperBoundResult)) {
            log.warn("There is no zero point in the intervall between [" + lowerBound + "," +  upperBound + "]. Consider changing the interval or check whether the input is plausible.");
            log.warn("ReceiverPoint: " + receiverPoint.toString() + " , xj: " + xj + ", lowerBoundResult: " + lowerBoundResult + " , upperBoundResult: " + upperBoundResult);
            log.warn("Returning -1.0 as R-Value.");
            return -1.0;
        }

        do {
            counter++;
            // find center
            center = (upperBound + lowerBound) / 2;

            // calculate result for center
            centerResult = sumf(emissions, receiverPoint, center) - xj;

            // choose new bounds
            if (Math.signum(centerResult) == Math.signum(lowerBoundResult)) {
                lowerBound = center;
                lowerBoundResult = centerResult;
            } else {
                upperBound = center;
                // not necessary to also copy upper bounds since it is never used again.
            }
        } while (upperBound - lowerBound > BISECT_THRESHOLD);

        //log.info("Took " + counter + " iterations with bisect. Switching to Newton");

        // newton method converges faster than bisect. After having a plausible interval do the remaining steps with newton
        // use center because this was the last boundary which was updated and is likely to be on a slope suitable for the
        // newton method.
        //return estimateR(emissions, receiverPoint, xj, center);
        return center;
    }

    static double Rn(Object2DoubleMap<Link> emissions, Coord receiverPoint, double Rprev, double xj) {

        var lowerR = Rprev - h;
        var upperR = Rprev + h;
        var sumRprev = sumf(emissions, receiverPoint, Rprev) - xj;
        var sumLowerR = sumf(emissions, receiverPoint, lowerR);
        var sumUpperR = sumf(emissions, receiverPoint, upperR);

        return Rprev - (sumRprev * 2 * h / (sumUpperR - sumLowerR));
    }

    static double sumf(Object2DoubleMap<Link> emissions, Coord receiverPoint, double R) {
        return emissions.object2DoubleEntrySet().stream()
                .mapToDouble(entry -> {
                    var weight = calculateWeight(
                            entry.getKey().getFromNode().getCoord(),
                            entry.getKey().getToNode().getCoord(),
                            receiverPoint,
                            entry.getKey().getLength(),
                            R
                    );
                    var emission = entry.getDoubleValue();
                    return emission * weight;
                })
                .sum();
    }

    static double calculateWeight(final Coord from, final Coord to, final Coord receiverPoint, final double le, final double R) {

        double A = calculateA(from, receiverPoint);
        double B = calculateB(from, to, receiverPoint);
        double C = calculateC(le);

        double upperLimit = le + B / le;
        double lowerLimit = B / le;
        double integrationUpperLimit = Erf.erf(upperLimit / R);
        double integrationLowerLimit = Erf.erf(lowerLimit / R);
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
}
