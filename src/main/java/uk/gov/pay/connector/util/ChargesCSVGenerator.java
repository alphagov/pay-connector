package uk.gov.pay.connector.util;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;

public class ChargesCSVGenerator {

    private static final String NEW_LINE_SEPARATOR = "\n";
    private static final Logger logger = LoggerFactory.getLogger(ChargesCSVGenerator.class);

    // this map is to map the database header fileds to the header to be exported into the CSV file
    private static final Map chargeHeaderMap = ImmutableMap.<String, String>builder()
            .put("reference", "Service Payment Reference")
            .put("charge_id", "Charge ID")
            .put("gateway_transaction_id", "Gateway Transaction ID")
            .put("gateway_account_id", "gateway_account_id")
            .put("amount", "Amount")
            .put("date_created", "Date Created")
            .put("updated", "Date Last Updated")
            .put("status", "Status")
            .put("payment_provider", "Provider")
            .put("description", "Description")
            .build();

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
        List<Object> csvHeader = headerKeys.stream()
                .map(header -> (chargeHeaderMap.get(header) != null ? chargeHeaderMap.get(header) : header))
                .collect(toList());

        csvPrinter.printRecord(csvHeader); // prints the header

        for (Map<String, Object> charge : objectMapList) {
            List values = new ArrayList();
            for (String key : headerKeys) {
                values.add(charge.get(key));
            }
            csvPrinter.printRecord(values); //prints rows
        }
    }
}
