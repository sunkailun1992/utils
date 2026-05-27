package com.kellen.utils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 *
 * @author 孙凯伦
 * 
 * @email: 376253703@qq.com
 * @description: 动态代理
 * @date: 2022/1/18 10:25 AM
 *
 */
public class Invoker implements InvocationHandler{
    /**
     * 获得的实体类
     */
    public Object entity;

    public Invoker(Object packageName){
            this.entity = packageName;
    }
    /**
     * 动态代理实现
     * @param proxy
     * @param method
     * @param args
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result ;
        //当代理对象调用真实对象的方法时，其会自动的跳转到代理对象关联的handler对象的invoke方法来进行调用
        result = method.invoke(entity,args);
        return result;
    }


}
