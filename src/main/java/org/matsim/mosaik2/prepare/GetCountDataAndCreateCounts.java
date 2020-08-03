package org.matsim.mosaik2.prepare;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.counts.Count;
import org.matsim.counts.Counts;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trim;

public class GetCountDataAndCreateCounts {

    private static final Logger logger = Logger.getLogger(CreateCounts.class);

    private final Integer[] emptyHours = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private final String[] R1 = {"KFZ_R1", "K_KFZ_R1", "Lkw_R1", "K_Lkw_R1", "PLZ_R1", "K_PLZ_R1", "Pkw_R1", "K_Pkw_R1", "Lfw_R1", "K_Lfw_R1",
            "Mot_R1", "K_Mot_R1", "PmA_R1", "K_PmA_R1", "Bus_R1", "K_Bus_R1", "LoA_R1", "K_LoA_R1",
            "Lzg_R1", "K_Lzg_R1", "Sat_R1", "K_Sat_R1", "Son_R1", "K_Son_R1"};

    private final String[] R2 = {"KFZ_R2", "K_KFZ_R2", "Lkw_R2", "K_Lkw_R2", "PLZ_R2", "K_PLZ_R2", "Pkw_R2", "K_Pkw_R2", "Lfw_R2", "K_Lfw_R2", "Mot_R2", "K_Mot_R2",
            "PmA_R2", "K_PmA_R2", "Bus_R2", "K_Bus_R2", "LoA_R2"};

    Map<String, Count<Link>> countData(String filePath1, String filePath2, HashMap nodeMatcher) throws IOException {

        CountingData countingData_R1 = new CountingData("", emptyHours.clone(), 0, "");
        CountingData countingData_R2 = new CountingData("", emptyHours.clone(), 0, "");

        Map<String, Count<Link>> countsResult = new HashMap<>();

        int allStations = 0;

        allStations = readData(filePath1, nodeMatcher, countingData_R1, countingData_R2, countsResult, allStations);

        allStations = readData(filePath2, nodeMatcher, countingData_R1, countingData_R2, countsResult, allStations);

        logger.info("################################################");
        logger.info("#\t\t\t\t\t\t\t\t\t\t\t\t#");
        logger.info("#\t\t\t All Counts were imported! \t\t\t#");
        logger.info("#  The dataset  contains " + allStations + " countinstations\t#");
        logger.info("#\t\t\t  " + countsResult.keySet().size() + " stations were valid!\t\t\t#");
        logger.info("#\t\t\t " + (allStations - countsResult.keySet().size()) + " stations were invalid!\t\t\t#");
        logger.info("#\t\t\t\t\t\t\t\t\t\t\t\t#");
        logger.info("################################################");
        return countsResult;

    }

    private int readData(String filePath, HashMap nodeMatcher, CountingData countingData_R1, CountingData countingData_R2, Map<String, Count<Link>> countsResult, int allStations) {

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

                        allStations += 2;

                        logger.info("Found new countstaion: " + record.get("Zst"));

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

                            Count<Link> count;
                            var counts = new Counts<Link>();

                            count = counts.createAndAddCount(Id.createLinkId(countingData_R1.linkID), countingData_R1.stationID);
                            counts.setYear(2018);

                            for (int i = 0; i < 24; i++) {

                                count.createVolume((i + 1), countingData_R1.getSpecificHour(i));

                            }

                            logger.info("Created new Counts object. (Link ID:" + count.getId() + ", Station ID: " + countingData_R1.stationID + ")");
                            countsResult.put(countingData_R1.stationID, count);

                        } else {

                            logger.error("Removed " + countingData_R1.stationID + " because the counting data was invalid.");

                        }

                        countingData_R1.stationID = "";
                        countingData_R1.hour = emptyHours.clone();
                        countingData_R1.countHour = 0;

                        if (countingData_R2.checksumIsNotEmpty()) {

                            Count<Link> count;
                            var counts = new Counts<Link>();

                            count = counts.createAndAddCount(Id.createLinkId(countingData_R2.linkID), countingData_R2.stationID);
                            counts.setYear(2018);

                            for (int i = 0; i < 24; i++) {

                                count.createVolume((i + 1), countingData_R2.getSpecificHour(i));

                            }

                            logger.info("Created new Counts object. (Link ID:" + count.getId() + ", Station ID: " + countingData_R2.stationID + ")");
                            countsResult.put(countingData_R2.stationID, count);

                        } else {

                            logger.error("Removed " + countingData_R2.stationID + " because the counting data was invalid.");

                        }

                        countingData_R2.stationID = "";
                        countingData_R2.hour = emptyHours.clone();
                        countingData_R2.countHour = 0;

                    }

                    index += 1;

                }

            }

        } catch (IOException e) {

            e.printStackTrace();

        }

        return allStations;

    }

    private void addCounts(CSVRecord record, CountingData countingData_R1, CountingData countingData_R2, Integer hour) {

        for (int i = 0; i <= R1.length - 1; i++) {

            if (checkIsCount(record, R1, i)) {

                countingData_R1.hour[hour] += Integer.parseInt(trim(record.get(R1[i])));

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

        public Integer getSpecificHour(Integer index) {

            return hour[index];

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