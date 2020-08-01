package org.matsim.mosaik2.prepare;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NodeMatcher {

    Map<String, matchedLinkID> parseNodeMatching(String filePath) throws IOException {

        Map<String, matchedLinkID> hashMap = new HashMap<>();

        try (var reader = new FileReader(filePath)) {

            CSVParser records = CSVFormat
                    .newFormat(';')
                    .withAllowMissingColumnNames()
                    .withFirstRecordAsHeader()
                    .parse(reader);

            for (CSVRecord record : records)

                if (StringUtils.isNoneBlank(record.get("Node_from_R1"))) {

                    var dzNumber_1 = record.get("DZ_Nr") + "_R1";
                    var fromID_1 = record.get("Node_from_R1");
                    var toID_1 = record.get("Node_to_R1");
                    var linkID_1 = record.get("Link_ID_R1");

                    var matchedCount1 = new matchedLinkID(fromID_1, toID_1, linkID_1);

                    hashMap.put(dzNumber_1, matchedCount1);

                    var dzNumber_2 = record.get("DZ_Nr") + "_R2";
                    var fromID_2 = record.get("Node_from_R2");
                    var toID_2 = record.get("Node_to_R2");
                    var linkID_2 = record.get("Link_ID_R2");

                    var matchedCount2 = new matchedLinkID(fromID_2, toID_2, linkID_2);

                    hashMap.put(dzNumber_2, matchedCount2);

                }

        }

        return hashMap;

    }

    static class matchedLinkID {

        private final String fromID;
        private final String toID;
        private final String linkID;

        public matchedLinkID(String fromID, String toID, String linkID) {

            this.fromID = fromID;
            this.toID = toID;
            this.linkID = linkID;

        }

        public String getLinkID() {

            return linkID;

        }

        public String getFromID() {

            return fromID;

        }

        public String getToID() {

            return toID;

        }

        @Override
        public String toString() {

            // return "Link_ID: " + this.linkID + "; Node_from_ID: " + this.fromID + "; Node_to_ID: " + this.toID;
            return this.linkID;

        }

    }

}
