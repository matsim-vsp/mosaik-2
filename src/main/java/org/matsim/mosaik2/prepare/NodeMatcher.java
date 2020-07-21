package org.matsim.mosaik2.prepare;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NodeMatcher {

    List<MatchedCount> parseNodeMatching(String filePath) throws IOException {

        List<MatchedCount> result = new ArrayList<>();

        try (var reader = new FileReader(filePath)) {

            CSVParser records = CSVFormat
                    .newFormat(';')
                    .withAllowMissingColumnNames()
                    .withFirstRecordAsHeader()
                    .parse(reader);
            for (CSVRecord record : records)

                if (StringUtils.isNoneBlank(record.get("Node_from_R1"))) {

                    var dzNumber_1 = record.get("DZ_Nr");
                    var fromID_1 = record.get("Node_from_R1");
                    var toID_1 = record.get("Node_to_R1");
                    var linkID_1 = record.get("Link_ID_R1");

                    var matchedCount = new MatchedCount(dzNumber_1, fromID_1, toID_1, linkID_1);

                    result.add(matchedCount);

                    var dzNumber_2 = record.get("DZ_Nr");
                    var fromID_2 = record.get("Node_from_R2");
                    var toID_2 = record.get("Node_to_R2");
                    var linkID_2 = record.get("Link_ID_R2");

                    var matchedCount2 = new MatchedCount(dzNumber_2, fromID_2, toID_2, linkID_2);

                    result.add(matchedCount2);

                }

            return result;

        }

    }

    static class MatchedCount {

        private final String stationID;
        private final String fromID;
        private final String toID;
        private final String linkID;

        public MatchedCount(String stationID, String fromID, String toID, String linkID) {

            this.stationID = stationID;
            this.fromID = fromID;
            this.toID = toID;
            this.linkID = linkID;

        }

        public String getStationID() {
            return stationID;
        }

        public String getFromID() {
            return fromID;
        }

        public String getToID() {
            return toID;
        }

        public String getLinkID() {
            return linkID;
        }


    }

}
