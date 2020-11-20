package uk.gov.pay.connector.util;

import org.apache.commons.net.util.SubnetUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import static java.util.stream.Collectors.toUnmodifiableSet;

public class CidrUtils {
    public static Set<String> getIpAddresses(Collection<String> cidrs) {
        return cidrs.stream()
                .map(SubnetUtils::new)
                .peek(subnet -> subnet.setInclusiveHostCount(true))
                .map(SubnetUtils::getInfo)
                .map(SubnetUtils.SubnetInfo::getAllAddresses)
                .flatMap(Arrays::stream)
                .collect(toUnmodifiableSet());
    }
}
