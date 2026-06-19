package com.kellen.utils.methods;

import com.kellen.utils.reflect.ReflectionUtils;
import com.kellen.utils.annotations.Methods;

import java.lang.reflect.Field;

/**
 * {@link com.kellen.utils.annotations.Methods @Methods} 注解与字段反射的承载对象。
 *
 * <p>保存单个注解元数据及其对应字段的名称、类型与取值，供 {@code MethodsInit}/{@code MethodsJudge} 解析使用。</p>
 *
 * @author 孙凯伦
 * @since 1.0.0
 */
public class MethodsParam {
    /**
     * 构造空的注解参数对象。
     */
    public MethodsParam(){}
    /**
     * 
     * SortableField. 类字段赋值
     * @param <T>
     *
     * @param meta  注解类
     * @param field	反射
     * @throws Exception 
     */
    public <T> MethodsParam(Methods meta, Field field , T t) throws Exception {
        this.meta = meta;  
        this.field = field;  
        this.name=field.getName();
        this.type=field.getType();
        this.param=ReflectionUtils.getPrivateAttribute(t, field.getName());
    }  
      
    /**
     * 
     * SortableField. 类方法赋值
     *
     * @param meta
     * @param name
     * @param type
     */
    public MethodsParam(Methods meta, String name, Class<?> type) {
        this.meta = meta;
        this.name = name;  
        this.type = type;  
    }  
  
    /**
     * 注解的全部信息
     */
    private Methods meta;
    /**
     * 反射出的类信息
     */
    private Field field;
    /**
     * 字段名称
     */
    private String name;
    /**
     * 字段属性
     */
    private Class<?> type;
    /**
     * 字段的值
     */
    private Object param;
    /**
     * 
     * 获得注解的全部信息
     * @return
     * @author 孙凯伦
     * @return FieldMeta
     * @since  1.0.0
     */
    public Methods getMeta() {
        return meta;  
    }
    /**
     * 
     * 设置注解的全部信息
     * @param meta
     * @author 孙凯伦
     * @return void
     * @since  1.0.0
     */
    public void setMeta(Methods meta) {
        this.meta = meta;  
    }
    /**
     * 
     * 获得反射出的类信息
     * @return
     * @author 孙凯伦
     * @return Field
     * @since  1.0.0
     */
    public Field getField() {  
        return field;  
    }
    /**
     * 
     * 设置反射出的类信息
     * @param field
     * @author 孙凯伦
     * @return void
     * @since  1.0.0
     */
    public void setField(Field field) {  
        this.field = field;  
    }
    /**
     * 
     * 获得字段名称
     * @return
     * @author 孙凯伦
     * @return String
     * @since  1.0.0
     */
    public String getName() {  
        return name;  
    }
    /**
     * 
     * 设置字段名称
     * @param name
     * @author 孙凯伦
     * @return void
     * @since  1.0.0
     */
    public void setName(String name) {  
        this.name = name;  
    }  
    /**
     * 
     * 获得字段属性
     * @return
     * @author 孙凯伦
     * @return Class<?>
     * @since  1.0.0
     */
    public Class<?> getType() {  
        return type;  
    }  
    /**
     * 
     * 设置字段属性
     * @param type
     * @author 孙凯伦
     * @return void
     * @since  1.0.0
     */
    public void setType(Class<?> type) {  
        this.type = type;  
    }
    /**
     * 
     * 获得字段值
     * @author 孙凯伦
     * @return void
     * @since  1.0.0
     */
	public Object getParam() {
		return param;
	}
    /**
     * 
     * 设置字段值
     * @author 孙凯伦
     * @return void
     * @since  1.0.0
     */
	public void setParam(Object param) {
		this.param = param;
	}  
}
