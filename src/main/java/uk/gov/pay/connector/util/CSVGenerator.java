package uk.gov.pay.connector.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CSVGenerator {

    private static final String NEW_LINE_SEPARATOR = "\n";
    private static final Logger logger = LoggerFactory.getLogger(CSVGenerator.class);

    public static String generate(List<Map<String, Object>> objectMapList) {
        if (objectMapList.isEmpty()) {
            return new String();
        }
        CSVFormat csvFileFormat = CSVFormat.DEFAULT.withRecordSeparator(NEW_LINE_SEPARATOR);
        StringBuilder builder = new StringBuilder();

        try (CSVPrinter csvPrinter = new CSVPrinter(builder, csvFileFormat)) {
            printRows(objectMapList, csvPrinter);
            csvPrinter.close();
        } catch (IOException e) {
            logger.error("Exception occurred while writing the file", e);
        }
        return builder.toString();
    }

    private static void printRows(List<Map<String, Object>> objectMapList, CSVPrinter csvPrinter) throws IOException {
        Set<String> headerKeys = objectMapList.get(0).keySet();
        csvPrinter.printRecord(headerKeys); // prints the header

        for (Map<String, Object> charge : objectMapList) {
            List values = new ArrayList();
            for (String key : headerKeys) {
                values.add(charge.get(key));
            }
            csvPrinter.printRecord(values); //prints rows
        }
    }
}
