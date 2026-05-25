package com.kellen.utils.methods;

import com.kellen.utils.annotations.Methods;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 *	@Description 通过注解,初始化
 *  @author 孙凯伦
 *  @CreatTime 2016年7月12日 下午2:51:47
 *  @since version 1.0.0
 */
public class MethodsInit{
	/**
	 * 
	 * @description 初始为设置
	 * @param entity
	 * @return
	 * @throws Exception
	 * @author 孙凯伦
	 * @return List<SortableField>
	 * @since  1.0.0
	 */
    public static <T> List<MethodsParam> init(Class entity) throws Exception{
    	//获得的所有参数
        List<MethodsParam> list = new ArrayList<MethodsParam>();
        //返回对象所表示的类或接口的所有可访问公共方法
        Method[] methods = entity.getMethods();  
        //循环所有方法,拿出方法的注解内容
        for(Method m:methods){
        	//获得注解信息
			Methods meta = m.getAnnotation(Methods.class);
            //判断是否有注解信息
            if(meta!=null){
				MethodsParam sf = new MethodsParam(meta,m.getName(),m.getReturnType());
                list.add(sf);  
            }  
        }
        return list;  
    }
}
