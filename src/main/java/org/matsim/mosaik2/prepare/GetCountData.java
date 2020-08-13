package org.matsim.mosaik2.prepare;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trim;

public class GetCountData {

    private static final Logger logger = Logger.getLogger(CreateCounts.class);

    private final Integer[] emptyHours = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private final String[] R1 = {"KFZ_R1", "K_KFZ_R1", "Lkw_R1", "K_Lkw_R1", "PLZ_R1", "K_PLZ_R1", "Pkw_R1", "K_Pkw_R1", "Lfw_R1", "K_Lfw_R1",
            "Mot_R1", "K_Mot_R1", "PmA_R1", "K_PmA_R1", "Bus_R1", "K_Bus_R1", "LoA_R1", "K_LoA_R1",
            "Lzg_R1", "K_Lzg_R1", "Sat_R1", "K_Sat_R1", "Son_R1", "K_Son_R1"};

    private final String[] R2 = {"KFZ_R2", "K_KFZ_R2", "Lkw_R2", "K_Lkw_R2", "PLZ_R2", "K_PLZ_R2", "Pkw_R2", "K_Pkw_R2", "Lfw_R2", "K_Lfw_R2", "Mot_R2", "K_Mot_R2",
            "PmA_R2", "K_PmA_R2", "Bus_R2", "K_Bus_R2", "LoA_R2"};

    Map<String, CountingData> countData(String filePath1, String filePath2, Map<String, NodeMatcher.MatchedLinkID> nodeMatcher) throws IOException {

        CountingData countingData_R1 = new CountingData("", emptyHours.clone(), 0, "");
        CountingData countingData_R2 = new CountingData("", emptyHours.clone(), 0, "");

        Map<String, CountingData> countHashMap = new HashMap<>();

        var result1 = readData(filePath1, nodeMatcher);

        var result2 = readData(filePath2, nodeMatcher);

        // merge two datasets
        for (Map.Entry<String, CountingData2> entry : result1.entrySet()) {

            var countData = result2.get(entry.getKey());
            for (var hourEntry : entry.getValue().values.entrySet()) {
                var hour = hourEntry.getKey();

                for (var value : hourEntry.getValue()) {
                    countData.addValue(hour, value);
                }
            }
        }

      // convert into counts now
        for (var entry : result1.entrySet()) {
            
            var id = entry.getKey();

            // create a counting station with this id

            for (var hourEntry: entry.getValue().values.entrySet()) {
                var hour = hourEntry.getKey();
                var average = entry.getValue().averageForHour(hour);

                // add average value to counting station
            }
        }

        // do something to merge those counts

        logger.info("###############################################");
        logger.info("#\t\t\t\t\t\t\t\t\t\t\t\t#");
        logger.info("#\t\t\t All Counts were imported! \t\t\t#");
        logger.info("#\t\t\t  " + countHashMap.keySet().size() + " stations were found!\t\t\t#");
        logger.info("#\t\t\t\t\t\t\t\t\t\t\t\t#");
        logger.info("###############################################");
        return countHashMap;

    }

    private Map<String, CountingData2> readData(String filePath, Map<String, NodeMatcher.MatchedLinkID> nodeMatcher) throws IOException {

        Map<String, CountingData2> data = new HashMap<>();

        try (var reader = new FileReader(filePath)) {
            try(var parser = CSVFormat.newFormat(';').withAllowMissingColumnNames().withFirstRecordAsHeader().parse(reader)) {

                for (var record: parser) {

                    var idR1 = record.get("Zst") + "_R1";
                    var idR2 = record.get("Zst") + "_R2";
                    if (nodeMatcher.containsKey(idR1) || nodeMatcher.containsKey(idR2) &&
                            record.get("Wotag").trim().equals("2") || record.get("Wotag").trim().equals("3") || record.get("Wotag").trim().equals("4")){

                        // direction 1
                        var linkId = nodeMatcher.get(idR1).getLinkID();
                        var countData = data.computeIfAbsent(idR1 + "_R1", key -> new CountingData2(key, linkId));

                        // get the hourly count value
                        var day = record.get("Wotag");
                        var hour = record.get("Stunde");
                        var value = Integer.parseInt(record.get("PLZ_R1"));

                        countData.addValue(hour, value);
                    }
                }
            }
        }

        return data;





/*

        try (var reader = new FileReader(filePath)) {

            int index = 0;

            CSVParser records = CSVFormat
                    .newFormat(';')
                    .withAllowMissingColumnNames()
                    .withFirstRecordAsHeader()
                    .parse(reader);

            for (CSVRecord record : records) {

                if (nodeMatcher.containsKey(record.get("Zst") + "_R1") || nodeMatcher.containsKey(record.get("Zst") + "_R2")) {

                    // First day
                    if (index % 8760 == 0) {

                        countingData_R1.stationID = record.get("Zst");
                        countingData_R1.stationID = countingData_R1.stationID + "_R1";
                        countingData_R1.linkID = nodeMatcher.get(countingData_R1.stationID).toString();

                        countingData_R2.stationID = record.get("Zst");
                        countingData_R2.stationID = countingData_R2.stationID + "_R2";
                        countingData_R2.linkID = nodeMatcher.get(countingData_R2.stationID).toString();

                        logger.info("Found new countingstaion: " + record.get("Zst"));

                    }

                    if (record.get("Wotag").trim().equals("2") || record.get("Wotag").trim().equals("3") || record.get("Wotag").trim().equals("4")) {

                        switch (record.get("Stunde")) {
                            case ("01"):
                                countingData_R1.countHour += 1;
                                countingData_R2.countHour += 1;
                                addCounts(record, countingData_R1, countingData_R2, 0);

                                break;
                            case ("02"):
                                addCounts(record, countingData_R1, countingData_R2, 1);
                                break;
                            case ("03"):
                                addCounts(record, countingData_R1, countingData_R2, 2);
                                break;
                            case ("04"):
                                addCounts(record, countingData_R1, countingData_R2, 3);
                                break;
                            case ("05"):
                                addCounts(record, countingData_R1, countingData_R2, 4);
                                break;
                            case ("06"):
                                addCounts(record, countingData_R1, countingData_R2, 5);
                                break;
                            case ("07"):
                                addCounts(record, countingData_R1, countingData_R2, 6);
                                break;
                            case ("08"):
                                addCounts(record, countingData_R1, countingData_R2, 7);
                                break;
                            case ("09"):
                                addCounts(record, countingData_R1, countingData_R2, 8);
                                break;
                            case ("10"):
                                addCounts(record, countingData_R1, countingData_R2, 9);
                                break;
                            case ("11"):
                                addCounts(record, countingData_R1, countingData_R2, 10);
                                break;
                            case ("12"):
                                addCounts(record, countingData_R1, countingData_R2, 11);
                                break;
                            case ("13"):
                                addCounts(record, countingData_R1, countingData_R2, 12);
                                break;
                            case ("14"):
                                addCounts(record, countingData_R1, countingData_R2, 13);
                                break;
                            case ("15"):
                                addCounts(record, countingData_R1, countingData_R2, 14);
                                break;
                            case ("16"):
                                addCounts(record, countingData_R1, countingData_R2, 15);
                                break;
                            case ("17"):
                                addCounts(record, countingData_R1, countingData_R2, 16);
                                break;
                            case ("18"):
                                addCounts(record, countingData_R1, countingData_R2, 17);
                                break;
                            case ("19"):
                                addCounts(record, countingData_R1, countingData_R2, 18);
                                break;
                            case ("20"):
                                addCounts(record, countingData_R1, countingData_R2, 19);
                                break;
                            case ("21"):
                                addCounts(record, countingData_R1, countingData_R2, 20);
                                break;
                            case ("22"):
                                addCounts(record, countingData_R1, countingData_R2, 21);
                                break;
                            case ("23"):
                                addCounts(record, countingData_R1, countingData_R2, 22);
                                break;
                            case ("24"):
                                addCounts(record, countingData_R1, countingData_R2, 23);
                                break;
                            default:
                                logger.error("The specified hour is invalid. The hours must be in the format 01-24!");
                        }

                    }

                    // Last day
                    if ((index + 1) % 8760 == 0) {

                        for (int i = 0; i < 24; i++) {

                            countingData_R1.hour[i] = (countingData_R1.hour[i] / countingData_R1.countHour);
                            countingData_R2.hour[i] = (countingData_R2.hour[i] / countingData_R2.countHour);

                        }

                        if (countingData_R1.checksumIsNotEmpty()) {

                            countHashMap.put(countingData_R1.stationID, new CountingData(countingData_R1.stationID, countingData_R1.hour.clone(), countingData_R1.countHour, countingData_R1.linkID));
                            logger.info("Added " + countingData_R1.stationID + " with the Link ID " + countingData_R1.linkID + " to the countHashMap.");

                        } else {

                            logger.error("Removed " + countingData_R1.stationID + " because the counting data was invalid.");

                        }

                        countingData_R1.stationID = "";
                        countingData_R1.hour = emptyHours.clone();
                        countingData_R1.countHour = 0;

                        if (countingData_R2.checksumIsNotEmpty()) {

                            countHashMap.put(countingData_R2.stationID, new CountingData(countingData_R2.stationID, countingData_R2.hour.clone(), countingData_R2.countHour, countingData_R2.linkID));
                            logger.info("Added " + countingData_R2.stationID + " with the Link ID " + countingData_R2.linkID + " to the countHashMap.");

                        } else {

                            logger.error("Removed " + countingData_R2.stationID + " because the counting data was invalid.");

                        }

                        countingData_R2.stationID = "";
                        countingData_R2.hour = emptyHours.clone();
                        countingData_R2.countHour = 0;

                    }

                }

                index += 1;

            }

        } catch (IOException e) {

            e.printStackTrace();

        }
        */


    }

    private void addCounts(CSVRecord record, CountingData countingData_R1, CountingData countingData_R2, Integer hour) {

        for (int i = 0; i <= R1.length - 1; i++) {

            if (checkIsCount(record, R1, i)) {

                countingData_R1.hour[hour] = countingData_R1.hour[hour] + Integer.parseInt(trim(record.get(R1[i])));

            }

        }

        for (int i = 0; i <= R2.length - 1; i++) {

            if (checkIsCount(record, R2, i)) {

                countingData_R2.hour[hour] += Integer.parseInt(trim(record.get(R2[i])));

            }

        }

    }

    private boolean checkIsCount(CSVRecord record, String[] types, Integer i) {

        return !trim(record.get(types[i])).equals("s") && !trim(record.get(types[i])).equals("-") && !trim(record.get(types[i])).equals("a") && !trim(record.get(types[i])).equals("x") && !trim(record.get(types[i])).equals("u") && !trim(record.get(types[i])).equals("-1") && !trim(record.get(types[i])).equals("z");

    }

    static class CountingData2 {

        private final String stationId;
        private final String linkId;
        private final Map<String,  List<Integer>> values = new HashMap<>();

        public CountingData2(String stationId, String linkId) {
            this.stationId = stationId;
            this.linkId = linkId;
        }

        void addValue(String hour, int value) {
            values.computeIfAbsent(hour, k -> new ArrayList<>()).add(value);
        }

        double averageForHour(String hour) {

            var numberOfValues = 0;
            var sum = 0.0;

            for (var value : values.get(hour)) {
                sum += value;
                numberOfValues++;
            }

            return sum / numberOfValues;
        }
    }

    static class CountingData {

        private String stationID;
        private String linkID;
        private Integer[] hour;
        private Integer countHour;

        CountingData(String stationID, Integer[] hour, Integer countHour, String linkID) {

            this.stationID = stationID;
            this.hour = hour;
            this.countHour = countHour;
            this.linkID = linkID;

        }

        private boolean checksumIsNotEmpty() {

            Integer result = 0;

            for (int i = 0; i < 24; i++) {

                result += this.hour[i];

            }

            return result != 0;

        }

        public String getLinkID() {

            return linkID;

        }

        public String getStationID() {

            return stationID;

        }

        public Integer getCountHour() {

            return countHour;

        }

        public Integer getSpecificHour(Integer index) {

            return hour[index];

        }

        public Integer dtv() {

            Integer temp = 0;

            for (int i = 0; i < 24; i++) {

                temp += hour[i];

            }

            return temp;

        }

        @Override
        public String toString() {

            StringBuilder result = new StringBuilder();

            for (Integer integer : hour) {

                result.append(integer).append(", ");

            }

            return "Station ID:   " + this.stationID + "   Link ID:   " + this.linkID + "\t\tCounts per hour:   [" + result.substring(0, (result.length() - 2)) + "]";

        }

    }

}