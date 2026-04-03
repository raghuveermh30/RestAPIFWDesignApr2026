package com.qa.api.utils;

public class StringUtils {

    public static String getRandomEmailId(){
        return "apiAutomation13"+ System.currentTimeMillis()+"@text.com";
    }

    public static String getRandomName(){
        return "apiAutomation"+ System.currentTimeMillis();
    }
}
