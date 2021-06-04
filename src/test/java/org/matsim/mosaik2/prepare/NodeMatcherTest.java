package org.matsim.mosaik2.prepare;

import org.apache.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class NodeMatcherTest {

    private static final Logger logger = Logger.getLogger(NodeMatcherTest.class);

    @Test@Ignore
    public void parseNodeMatching() throws IOException {

        // Setup
        String path = "/Users/friedrich/SVN/shared-svn/projects/mosaik-2/raw-data/calibration-data/countstation-osm-node-matching.csv";
        NodeMatcher testObject = new NodeMatcher();

        // Excecution
        var result = testObject.parseNodeMatching(path);

        // Überprüfen
        assertEquals(186, result.size());
        var resultTest10 = result.get(10);
        assertEquals("8004", resultTest10.getLinkID());
        assertEquals("8004", resultTest10.getFromID());
        assertEquals("8004", resultTest10.getToID());

    }

}