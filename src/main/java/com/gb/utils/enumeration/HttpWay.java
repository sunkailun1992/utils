package com.gb.utils.enumeration;

/**
 * @ClassName HttpWay
 * @Description 请求方式
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/4/13 2:32 下午
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
