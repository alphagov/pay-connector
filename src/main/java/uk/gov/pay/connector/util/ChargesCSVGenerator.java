package uk.gov.pay.connector.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.function.Function;

import static com.google.common.collect.Lists.newArrayList;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.mapFromStatus;
import static uk.gov.pay.connector.util.DateTimeUtils.toUTCDateString;

public class ChargesCSVGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChargesCSVGenerator.class);

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.withRecordSeparator("\n");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");

    public static String generate(List<ChargeEntity> charges) {
        StringBuilder builder = new StringBuilder();

        try (CSVPrinter csvPrinter = new CSVPrinter(builder, CSV_FORMAT)) {
            printHeaders(csvPrinter);
            printCharges(charges, csvPrinter);
            csvPrinter.close();
        } catch (IOException e) {
            LOGGER.error("Creating CSVPrinterException occurred while writing the file", e);
        }

        return builder.toString();
    }

    private static void printCharges(List<ChargeEntity> charges, CSVPrinter csvPrinter) {
        charges.forEach(charge -> {
            try {
                csvPrinter.printRecord(asArray().apply(charge));
            } catch (IOException e) {
                LOGGER.error("Writing row. Exception occurred while writing the file", e);
            }
        });
    }

    private static void printHeaders(CSVPrinter csvPrinter) throws IOException {
        csvPrinter.printRecord(newArrayList("Service Payment Reference", "Amount", "Status", "Gateway Transaction ID", "GOV.UK Pay ID", "Date Created"));
    }

    private static Function<ChargeEntity, String[]> asArray() {
        return charge -> {
            String[] csvChargeArray = new String[6];
            csvChargeArray[0] = defaultString(charge.getReference());
            csvChargeArray[1] = DECIMAL_FORMAT.format(Double.valueOf(charge.getAmount().toString()) / 100);
            csvChargeArray[2] = mapFromStatus(charge.getStatus()).getValue();
            csvChargeArray[3] = defaultString(charge.getGatewayTransactionId());
            csvChargeArray[4] = charge.getExternalId();
            csvChargeArray[5] = toUTCDateString(charge.getCreatedDate());
            return csvChargeArray;
        };
    }
}
