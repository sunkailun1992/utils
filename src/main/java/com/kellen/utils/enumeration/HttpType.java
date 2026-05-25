package com.kellen.utils.enumeration;

/**
 * @ClassName HttpType
 * @Description okhttp枚举类
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/4/13 2:00 下午
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
