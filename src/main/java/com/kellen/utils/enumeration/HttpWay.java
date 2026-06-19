package com.kellen.utils.enumeration;

/**
 * 请求方式
 * 
 */
public enum HttpWay {
    //GET
    GET("GET"),
    //POST
    POST("POST"),
    //PUT
    PUT("PUT"),
    //DELETE
    DELETE("DELETE"),

    ;

    HttpWay(String type) {
        this.type = type;
    }

    private String type;

    public String getType() {
        return this.type;
    }
}
