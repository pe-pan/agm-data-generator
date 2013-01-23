package com.hp.demo.ali.config;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.FileNotFoundException;

/**
 * Created by panuska on 10/12/12.
 */
public class ConfigFactory {

    private static Config config;

    public Config getConfig(String configFile) {
        if (config == null) {
            try {
                JAXBContext jc = JAXBContext.newInstance(Config.class);
                Unmarshaller um = jc.createUnmarshaller();
                config = (Config) um.unmarshal(new java.io.FileInputStream(configFile));
            } catch (JAXBException e) {
                throw new IllegalArgumentException(e);
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return config;
    }
}
