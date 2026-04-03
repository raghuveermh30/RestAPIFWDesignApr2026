package com.qa.api.manager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigManager {

    private static Properties properties = new Properties();

    static {

        //Reflection Property in Java

        //mvn clean install -Denv=qa/dev/stage/uat/prod

        //mvn clean install -> if we are not passing any env then by default, we need to run in QA env

        //env - environment variables(system)


        String envName = System.getProperty("env", "qa");
        System.out.println("Running the test on env : "+envName);

        String fileName = "config_"+envName+".properties"; //config_stage, config_qa, config_dev

        InputStream inputStream = ConfigManager.class.getClassLoader().getResourceAsStream(fileName);

        if (inputStream != null) {
            try {
                properties.load(inputStream);
                System.out.println("properties ====>" + properties);
                System.out.println("");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static String getProp(String key) {
        return properties.getProperty(key).trim();
    }

    public static void setProp(String key, String value) {
        properties.setProperty(key, value);
    }


}
