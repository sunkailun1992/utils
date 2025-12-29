package com.gb.utils.methods;

import com.gb.utils.ReflectionUtils;
import com.gb.utils.annotations.Methods;

import java.lang.reflect.Field;

/**
 * 
 *	@Description 注解和反射,设置类
 *  @author 孙凯伦
 *  @CreatTime 2016年7月12日 下午2:55:38
 *  @since version 1.0.0
 */
public class MethodsParam {
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
     * @description 获得注解的全部信息
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
     * @description 设置注解的全部信息
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
     * @description 获得反射出的类信息
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
     * @description 设置反射出的类信息
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
     * @description 获得字段名称
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
     * @description 设置字段名称
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
     * @description 获得字段属性
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
     * @description 设置字段属性
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
     * @description 获得字段值
     * @author 孙凯伦
     * @return void
     * @since  1.0.0
     */
	public Object getParam() {
		return param;
	}
    /**
     * 
     * @description 设置字段值
     * @author 孙凯伦
     * @return void
     * @since  1.0.0
     */
	public void setParam(Object param) {
		this.param = param;
	}  
}
