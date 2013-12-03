package com.hp.demo.ali.excel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by panuska on 3.12.13.
 */
public class ExcelEntity {

//    private String name;
    private Map<String, String> fields;

    public ExcelEntity() {
//        this.name = name;
        this.fields = new HashMap<>();
    }

    public void setFieldValue(String name, String value) {
        this.fields.put(name, value);
    }

    public String getFieldValue(String name) {
        return this.fields.get(name);
    }

    public int getFieldIntValue(String name) {
        String value = getFieldValue(name);
        return value == null ? 0 : Integer.parseInt(value);
    }

    public long getFieldLongValue(String name) {
        String value = getFieldValue(name);
        return value == null ? 0 : Long.parseLong(value);
    }

    public String toUrlParameters() {        //todo similar to RestClient#serializeParameters(); remove code duplicate
        StringBuilder urlParameters = new StringBuilder("?");
        Set<String> names = fields.keySet();
        for (String name : names) {
            String value = getFieldValue(name);
            urlParameters.append(name).append('=').append(/*URLEncoder.encode(*/value/*, "UTF-8")*/).append('&');
        }
        return urlParameters.substring(0, urlParameters.length()-1);
    }
}
