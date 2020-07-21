package org.matsim.mosaik2.prepare;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReadBAStCount {

    List<BAStData> readBAStData(String filePath) throws IOException {

        List<BAStData> result = new ArrayList<>();

        SimpleDateFormat formatter = new SimpleDateFormat("yyMMddkk", Locale.GERMANY);

        try (var reader = new FileReader(filePath)) {

            CSVParser records = CSVFormat
                    .newFormat(';')
                    .withAllowMissingColumnNames()
                    .withFirstRecordAsHeader()
                    .parse(reader);

            for (CSVRecord record : records) {

                if (record.get("Wotag").equals(" 2") || record.get("Wotag").equals(" 3") || record.get("Wotag").equals(" 4")) {

                    var tknr = record.get("TKNR");
                    var zst = record.get("Zst");
                    var datum = formatter.parse(record.get("Datum") + record.get("Stunde"));
                    var wotag = record.get("Wotag");
                    var kfzR1 = record.get("KFZ_R1") +
                            record.get("K_KFZ_R1") +
                            record.get("Lkw_R1") +
                            record.get("K_Lkw_R1") +
                            record.get("PLZ_R1") +
                            record.get("K_PLZ_R1") +
                            record.get("Pkw_R1") +
                            record.get("K_Pkw_R1") +
                            record.get("Lfw_R1") +
                            record.get("K_Lfw_R1") +
                            record.get("Mot_R1") +
                            record.get("K_Mot_R1") +
                            record.get("PmA_R1") +
                            record.get("K_PmA_R1") +
                            record.get("Bus_R1") +
                            record.get("K_Bus_R1") +
                            record.get("LoA_R1") +
                            record.get("K_LoA_R1") +
                            record.get("Lzg_R1") +
                            record.get("K_Lzg_R1") +
                            record.get("Sat_R1") +
                            record.get("K_Sat_R1") +
                            record.get("Son_R1") +
                            record.get("K_Son_R1") +
                            record.get("K_LoA_R1") +
                            record.get("K_LoA_R1") +
                            record.get("K_LoA_R1") +
                            record.get("K_LoA_R1") +
                            record.get("K_LoA_R1") +
                            record.get("K_LoA_R1");
                    var kfzR2 = record.get("KFZ_R2") +
                            record.get("K_KFZ_R2") +
                            record.get("Lkw_R2") +
                            record.get("K_Lkw_R2") +
                            record.get("PLZ_R2") +
                            record.get("K_PLZ_R2") +
                            record.get("Pkw_R2") +
                            record.get("K_Pkw_R2") +
                            record.get("Lfw_R2") +
                            record.get("K_Lfw_R2") +
                            record.get("Mot_R2") +
                            record.get("K_Mot_R2") +
                            record.get("PmA_R2") +
                            record.get("K_PmA_R2") +
                            record.get("Bus_R2") +
                            record.get("K_Bus_R2") +
                            record.get("LoA_R2") +
                            record.get("K_LoA_R2") +
                            record.get("Lzg_R2") +
                            record.get("K_Lzg_R2") +
                            record.get("Sat_R2") +
                            record.get("K_Sat_R2") +
                            record.get("Son_R2");

                    var bastData = new BAStData(tknr, zst, datum, wotag, kfzR1, kfzR2);

                    result.add(bastData);

                }

            }

        } catch (ParseException e) {
            e.printStackTrace();
        }

        return result;

    }

    static class BAStData {

        private final String tknr;
        private final String zst;
        private final Date datum;
        private final String wotag;
        private final String kfzR1;
        private final String kfzR2;

        public BAStData(String tknr, String zst, Date datum, String wotag, String kfzR1, String kfzR2) {

            this.tknr = tknr;
            this.zst = zst;
            this.datum = datum;
            this.wotag = wotag;
            this.kfzR1 = kfzR1;
            this.kfzR2 = kfzR2;

        }

    }

}
