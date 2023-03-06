package org.matsim.mosaik2;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.hprtree.HPRtree;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class SpatialIndex<T> {

    private static final PreparedGeometryFactory fact = new PreparedGeometryFactory();

    private final HPRtree index = new HPRtree();

    public SpatialIndex(Collection<Geometry> geometries, java.util.function.Function<Geometry, T> itemFunction) {

        geometries.stream()
                .map(geom -> fact.create(geom))
                .forEach(geom -> {

                    var item = itemFunction.apply(geom.getGeometry());
                    index.insert(geom.getGeometry().getEnvelopeInternal(), new Entry<>(item, geom));
                });

        index.build();
    }

    public Collection<T> overlaps(Geometry geom) {

        Set<T> result = new HashSet<>();
        index.query(geom.getEnvelopeInternal(), rawEntry -> {
            @SuppressWarnings("unchecked") // we are confident that the entry is what we have put inside.
            var entry = (Entry<T>) rawEntry;
            if (entry.prepGeom.overlaps(geom)) {
                result.add(entry.item());
            }
        });
        return result;
    }

    record Entry<T>(T item, PreparedGeometry prepGeom) {
    }
}
