package com.kellen.utils.enumeration;

/**
 * okhttp枚举类
 * 
 */
public enum HttpType {
    //from
    FROM("from"),
    //json
    JSON("json"),
    //xml
    XML("xml"),

    ;

    HttpType(String type) {
        this.type = type;
    }

    private String type;

    public String getType() {
        return this.type;
    }
}
