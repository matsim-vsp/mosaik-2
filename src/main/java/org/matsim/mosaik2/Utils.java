package org.matsim.mosaik2;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.Getter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import ucar.ma2.ArrayDouble;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Utils {

    public static List<PlanCalcScoreConfigGroup.ActivityParams> createTypicalDurations(String type, long minDurationInSeconds, long maxDurationInSeconds, long durationDifferenceInSeconds) {

        List<PlanCalcScoreConfigGroup.ActivityParams> result = new ArrayList<>();
        for (long duration = minDurationInSeconds; duration <= maxDurationInSeconds; duration += durationDifferenceInSeconds) {
            final PlanCalcScoreConfigGroup.ActivityParams params = new PlanCalcScoreConfigGroup.ActivityParams(type + "_" + duration + ".0");
            params.setTypicalDuration(duration);
            result.add(params);
        }
        return result;
    }

    public static ArrayDouble.D1 writeDoubleArray(double min, double max, double intervallSize, int size) {
        var result = new ArrayDouble.D1(size);
        var i = 0;
        for (var v = min; v <= max; v += intervallSize) {
            result.set(i, v);
            i++;
        }
        return result;
    }

    public static SharedSvnARg parseSharedSvnArg(String[] args) {
        var arg = new SharedSvnARg();
        JCommander.newBuilder().addObject(arg).build().parse(args);
        return arg;
    }

    public static class SharedSvnARg {

        @Getter
        @Parameter(names = "-sharedSvn", required = true)
        private String sharedSvn;
    }
}
