package com.qa.api.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CSVReaderUtil {

    /*
     * Reads a CSV file and returns its data as a 2D Object array for TestNG @DataProvider.
     * The first row is treated as a header and is skipped.
     *
     * @param filePath absolute or relative path to the CSV file
     * @return Object[][] where each row is one data set
     */
    public static Object[][] readDataFromCSV(String filePath) {
        List<Object[]> data = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                data.add(line.split(","));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read CSV file: " + filePath, e);
        }
        return data.toArray(new Object[0][0]);
    }
}

