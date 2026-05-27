package com.kellen.utils;

import cn.hutool.extra.spring.SpringUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 反射工具类
 * @author 孙凯伦
 * @DateTime    2020/12/27  下午4:36
 * @email       376253703@qq.com
 * 
 */
public class ReflectionUtils {
	/**
	 *
	 * @description			得到某个对象的公共属性
	 * @param owner			new 某个的对象
	 * @param fieldName		参数名称
	 * @return				返回参数
	 * @throws Exception
	 * @author 孙凯伦
	 * @return Object
	 * @since  1.0.0
	 */
	public static Object getPublicAttribute(Object owner, String fieldName) throws Exception {
		//获得Class
		Class <?> ownerClass = owner.getClass();
		//获得public的参数
		Field field = ownerClass.getDeclaredField(fieldName);
		//获得参数值
		Object property = field.get(owner);
		return property;
	}


	/**
	 *
	 * @description			得到某个对象的私有属性
	 * @param owner			new 某个的对象
	 * @param fieldName		参数名称
	 * @return				返回参数
	 * @throws Exception
	 * @author 孙凯伦
	 * @return Object
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @since  1.0.0
	 */
	public static Object getPrivateAttribute(Object owner, String fieldName) throws Exception {
		//获得Class
		Class <?> ownerClass = owner.getClass();
		//获得public的参数
		Field field = ownerClass.getDeclaredField(fieldName);
		//封装解封
		field.setAccessible(true);
		//获得参数值
		Object property = field.get(owner);
		return property;
	}


	public static Object getParentField(Object owner, String fieldName) throws Exception {
		try {
			return getPrivateAttribute(owner, fieldName);
		}catch (NoSuchFieldException e){
			Class <?> ownerClass = owner.getClass();
			// 遍历所有父类字节码对象
			while (ownerClass != null) {
				// 获取字节码对象的属性对象数组
				Field[] declaredFields = ownerClass.getDeclaredFields();
				for(Field field : declaredFields){
					if(fieldName.equals(field.getName())){
						field.setAccessible(true);
						Object property = field.get(owner);
						return property;
					}
				}
				// 获得父类的字节码对象
				ownerClass = ownerClass.getSuperclass();
			}
			return null;
		}

	}

	/**
	 *
	 * @description			访问private,私有的对象
	 * @param owner			new 某个的对象
	 * @param fieldName		属性名
	 * @param set			是否更改属性参数
	 * @return				属性结果
	 * @author 孙凯伦
	 * @return Object
	 * @since  1.0.0
	 */
	public static Object getAndSet(Object owner, String fieldName,Object set){
		try {
			//获得Class
			Class <?> ownerClass = owner.getClass();
			//得到一个实例
			Object student = ownerClass.newInstance();
			//获得属性值
			Field field = ownerClass.getDeclaredField(fieldName);
			//封装解封
			field.setAccessible(true);
			//判断传入参数了,就重新改值
			if(set != null) {
				field.set(student, set);
			}
			//返回参数
			return field.get(student);
		} catch (Exception e) {
			System.err.println(e);
		}
		return null;
	}

	/**
	 *
	 * @description		访问private,私有的对象
	 * @param className	类名 (xxx.xxx.xx)
	 * @param fieldName	属性名
	 * @param set		是否更改参数
	 * @return			参数属性
	 * @author 孙凯伦
	 * @return Object
	 * @since  1.0.0
	 */
	public static Object getAndSet(String className, String fieldName,Object set){
		try {
			//获得Class
			Class <?> ownerClass = Class.forName(className);
			//得到一个实例
			Object student = ownerClass.newInstance();
			//获得属性值
			Field field = ownerClass.getDeclaredField(fieldName);
			//封装解封
			field.setAccessible(true);
			//判断传入参数了,就重新改值
			if(set != null) {
				field.set(student, set);
			}
			//返回参数
			return field.get(student);
		} catch (Exception e) {
			System.err.println(e);
		}
		return null;
	}

	/**
	 *
	 * @description			执行某对象方法
	 * @param owner			new 的对象
	 * @param methodName	方法名
	 * @param args			参数
	 * @return				方法返回值
	 * @throws Exception
	 * @author 孙凯伦
	 * @return Object
	 * @since  1.0.0
	 */
	public static Object invokeMethod(Object owner, String methodName, Object[] args) throws Exception {
		//获得Class
		Class <?> ownerClass = owner.getClass();
		//获得方法
		Class <?> [] argsClass = new Class[args.length];
		//赋值参数
		for (int i = 0, j = args.length; i < j; i++) {
			argsClass[i] = args[i].getClass();
		}
		//调用方法
		Method method = ownerClass.getMethod(methodName, argsClass);
		//返回方法参数
		return method.invoke(owner, args);
	}
	/**
	 *
	 * @description			执行某类的静态方法
	 * @param className		类名
	 * @param methodName	方法名
	 * @param args			参数数组
	 * @return				执行方法返回的结果
	 * @throws Exception
	 * @author 孙凯伦
	 * @return Object
	 * @since  1.0.0
	 */
	public static Object invokeMethod(String className, String methodName,Object[] args) throws Exception {
		//获得Class
		Object ownerClass = injectSrping(className);
		//获得方法
		Class <?> [] argsClass = new Class[args.length];
		//赋值参数
		for (int i = 0, j = args.length; i < j; i++) {
			argsClass[i] = args[i].getClass();
		}
		//调用方法
		Method method = ownerClass.getClass().getMethod(methodName, argsClass);
		//返回方法参数
		return method.invoke(ownerClass, args);
	}

	/**
	 *
	 * @Description:  		执行内容:初始化对spring的重新注入
	 * @Title: inject	方法名
	 * @param className
	 * @return
	 * @throws Exception
	 * @return T    	返回类型
	 */
	public static <T> Object injectSrping(String className) {
		try {
			//获得Class
			Class <?> c = Class.forName(className);
			//注入后的对象
			Object object = c.newInstance();
			//获得所以字段
			Field[] fileds = c.getDeclaredFields();
			//循环字段
	        for (Field f : fileds) {
	        	//判断字段注释是否存在
	            if (f.isAnnotationPresent(Autowired.class)) {
		            //允许访问private字段
		            f.setAccessible(true);
		            //把引用对象注入属性
		            f.set(object, SpringUtil.getBean(f.getName()));
	            }
	        }
	        //注入完毕
	        return object;
		} catch (Exception e) {}
		return null;
    }

	/**
	 *
	 * @description		新建实例
	 * @param className	类名
	 * @param args		构造函数的参数
	 * @return			新建的实例
	 * @throws Exception
	 * @author 孙凯伦
	 * @return Object
	 * @since  1.0.0
	 */
	public Object newInstance(String className, Object[] args) throws Exception {
		Class <?> newoneClass = Class.forName(className);

		Class <?> [] argsClass = new Class[args.length];

		for (int i = 0, j = args.length; i < j; i++) {
			argsClass[i] = args[i].getClass();
		}

		Constructor <?> cons = newoneClass.getConstructor(argsClass);

		return cons.newInstance(args);
	}

	/**
	 *
	 * @description		是不是某个类的实例
	 * @param obj		实例
	 * @param cls		类
	 * @return			如果 obj 是此类的实例，则返回 true
	 * @author 孙凯伦
	 * @return boolean
	 * @since  1.0.0
	 */
	public boolean isInstance(Object obj, Class cls) {
		return cls.isInstance(obj);
	}

	/**
	 *
	 * @description		得到数组中的某个元素
	 * @param array		数组
	 * @param index		索引
	 * @return			返回指定数组对象中索引组件的值
	 * @author 孙凯伦
	 * @return Object
	 * @since  1.0.0
	 */
	public Object getByArray(Object array, int index) {
		return Array.get(array, index);
	}
}
