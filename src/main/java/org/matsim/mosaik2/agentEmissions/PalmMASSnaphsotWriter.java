package org.matsim.mosaik2.agentEmissions;

import it.unimi.dsi.fastutil.doubles.Double2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.vis.snapshotwriters.AgentSnapshotInfo;
import org.matsim.vis.snapshotwriters.SnapshotWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class PalmMASSnaphsotWriter implements SnapshotWriter {

    private final AgentMapper mapper = new AgentMapper();

    private double currentTime = -1;

    public Map<Double, List<Position>> getPositionsByTimeStep() {
        return mapper.getTime2Position();
    }

    @Override
    public void beginSnapshot(double time) {
        currentTime = time;
    }

    @Override
    public void endSnapshot() {
    }

    @Override
    public void addAgent(AgentSnapshotInfo agentSnapshotInfo) {

        mapper.addInfo(currentTime, agentSnapshotInfo);

        // write ag_id

        // write ag_x

        // write ag_y

        // write ag_NO2

        // write ag_PM10
    }

    @Override
    public void finish() {

    }

    private static class AgentMapper {

        private final Object2IntMap<Id<Person>> id2Number = new Object2IntOpenHashMap<>();

        @Getter
        private final Map<Double, List<Position>> time2Position = new Double2ObjectOpenHashMap<>();

        private int nextId = 0;

        void addInfo(double time, AgentSnapshotInfo snapshotInfo) {

            var mappedId = getIdMapping(snapshotInfo.getId());
            var position = new Position(snapshotInfo.getEasting(), snapshotInfo.getNorthing(), mappedId);
            getPositions(time).add(position);
        }

        int getIdMapping(Id<Person> id) {

            if (id2Number.containsKey(id)) {
                return id2Number.get(id);
            }

            var currentId = nextId;
            id2Number.put(id, currentId);
            nextId++;
            return currentId;
        }

        List<Position> getPositions(double time) {
            if (time2Position.containsKey(time)) {
                return time2Position.get(time);
            }
            List<Position> list = new ArrayList<>();
            time2Position.put(time, list);
            return list;
        }
    }

    @RequiredArgsConstructor
    private static class Position {

        private final double x;
        private final double y;
        private final int id;

        // this will need pollution as well but we don't know how to do that yet
    }
}
