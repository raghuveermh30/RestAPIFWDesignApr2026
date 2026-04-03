package com.qa.api.utils;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ExcelUtils {

    private static final String TEST_DATA_PATH = "./src/test/resources/testdata/APITestData.xlsx";

    private static Workbook book;
    private static Sheet sheet;


    public static Object[][] readDataFromExcel(String sheetName) {
        Object data[][] = null;

        try {
            FileInputStream inputStream = new FileInputStream(TEST_DATA_PATH);

            book = WorkbookFactory.create(inputStream);
            sheet = book.getSheet(sheetName);
            data = new Object[sheet.getLastRowNum()][sheet.getRow(0).getLastCellNum()];

            for (int i = 0; i < sheet.getLastRowNum(); i++) {
                for (int j = 0; j < sheet.getRow(0).getLastCellNum(); j++) {
                    data[i][j] = sheet.getRow(i + 1).getCell(j).toString();
                }
            }

            return data;

        } catch (IOException | InvalidFormatException e) {
            throw new RuntimeException(e);
        }

    }


}
