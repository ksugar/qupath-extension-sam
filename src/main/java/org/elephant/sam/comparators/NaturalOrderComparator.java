package org.elephant.sam.comparators;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NaturalOrderComparator implements Comparator<String> {
    private static final Pattern PATTERN = Pattern.compile("(\\d+)|(\\D+)");

    @Override
    public int compare(String s1, String s2) {
        Matcher m1 = PATTERN.matcher(s1);
        Matcher m2 = PATTERN.matcher(s2);

        while (m1.find() && m2.find()) {
            String part1 = m1.group();
            String part2 = m2.group();

            int cmp;
            // If both parts are numbers, compare them as integers
            if (Character.isDigit(part1.charAt(0)) && Character.isDigit(part2.charAt(0))) {
                cmp = Integer.compare(Integer.parseInt(part1), Integer.parseInt(part2));
            } else {
                // Otherwise, compare them lexically
                cmp = part1.compareTo(part2);
            }

            if (cmp != 0) {
                return cmp;
            }
        }

        // Handle the remaining unmatched parts
        return Integer.compare(s1.length(), s2.length());
    }
}
