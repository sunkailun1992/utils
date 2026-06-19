package com.kellen.utils.methods;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.kellen.utils.convert.GeneralConvertor;
import com.kellen.utils.json.JsonUtil;
import com.kellen.utils.annotations.Methods;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;


/**
 * 基于 {@link com.kellen.utils.annotations.Methods @Methods} 注解的方法元信息解析与扩展点执行。
 *
 * <p>从目标类的 {@code @Methods} 注解中按方法标识查找接口展示名、业务描述，并解析模板中的
 * {@code #{占位符}#} 与 {@code ${方法表达式}$}，主要由请求日志切面调用。</p>
 *
 * @author 孙凯伦
 * @since 1.0.0
 */
public class MethodsJudge {


    private static final List<Class> WRAP_CLASS = Arrays.asList(Integer.class, Boolean.class, Double.class, Byte.class, Short.class, Long.class, Float.class, Double.class, BigDecimal.class, String.class);


    /**
     * 从目标类的 {@code @Methods} 注解中按方法标识返回接口展示名称。
     *
     * @param entity  携带 {@code @Methods} 注解的目标类
     * @param methods 方法标识
     * @return 接口展示名称，未匹配返回空串
     * @throws Exception 注解反射解析异常
     */
    public static String getInterfaceName(Class entity, String methods) throws Exception {
        //类注解的属性和内容
        List<MethodsParam> list = MethodsInit.init(entity);
        //循环所有属性注解和内容
        for (MethodsParam mp : list) {
            Methods m = mp.getMeta();
            if (m.methods().equals(methods)) {
                return m.methodsName();
            }
        }
        return "";
    }


    /**
     * 从目标类的 {@code @Methods} 注解中按方法标识返回业务描述，并解析其中占位符。
     *
     * @param entity  携带 {@code @Methods} 注解的目标类
     * @param methods 方法标识
     * @param map     占位符取值
     * @return 解析后的业务描述，未匹配返回空串
     * @throws Exception 注解反射解析异常
     */
    public static String description(Class entity, String methods, Map<String, String> map) throws Exception {
        //类注解的属性和内容
        List<MethodsParam> list = MethodsInit.init(entity);
        //循环所有属性注解和内容
        for (MethodsParam mp : list) {
            Methods m = mp.getMeta();
            if (m.methods().equals(methods)) {
                return description(m.description(),map);
            }
        }
        return "";
    }

    /**
     * 执行目标方法的前置扩展点表达式。
     *
     * @param entity  携带 {@code @Methods} 注解的目标类
     * @param methods 方法标识
     * @param map     表达式取值
     * @return 前置扩展点执行结果，未匹配返回空串
     * @throws Exception 表达式解析或反射调用异常
     */
    public static String performBefore(Class entity, String methods, Map<String, String> map) throws Exception {
        //类注解的属性和内容
        List<MethodsParam> list = MethodsInit.init(entity);
        //循环所有属性注解和内容
        for (MethodsParam mp : list) {
            Methods m = mp.getMeta();
            if (m.methods().equals(methods)) {
                return content(m.performBefore(), map);
            }
        }
        return "";
    }


    /**
     * 执行目标方法的后置扩展点表达式。
     *
     * @param entity  携带 {@code @Methods} 注解的目标类
     * @param methods 方法标识
     * @param map     表达式取值
     * @return 后置扩展点执行结果，未匹配返回空串
     * @throws Exception 表达式解析或反射调用异常
     */
    public static String performAfter(Class entity, String methods, Map<String, String> map) throws Exception {
        //类注解的属性和内容
        List<MethodsParam> list = MethodsInit.init(entity);
        //循环所有属性注解和内容
        for (MethodsParam mp : list) {
            Methods m = mp.getMeta();
            if (m.methods().equals(methods)) {
                return content(m.performAfter(), map);
            }
        }
        return "";
    }

    /**
     * 解析模板中的 {@code #{key}#} 占位符，用取值映射替换为实际内容。
     *
     * @param parameter 含占位符的模板
     * @param value     占位符取值
     * @return 替换后的字符串
     */
    public static String description(String parameter, Map<String, String> value) {
        String content = "";
        Boolean b = parameter.contains("#");
        //判断是否存在参数
        if (b) {
            //切割#号参数
            String[] totalList = parameter.split("#");
            //循环
            for (String total : totalList) {
                Boolean judge = total.contains("}");
                //判断是否存在}
                if (judge) {
                    //切割
                    String[] keyList = total.split("}");
                    //获得参数循环
                    for (String key : keyList) {
                        Boolean a = key.contains("{");
                        //判断是否参数
                        if (a) {
                            //实际参数
                            String p = StrUtil.sub(key, 1, key.length());
                            content = StringUtils.isBlank(content) ? value.get(p) : content + value.get(p);
                        } else {
                            content = StringUtils.isBlank(content) ? key : content + key;
                        }
                    }
                } else {
                    content = StringUtils.isBlank(content) ? total : content + total;
                }
            }
        } else {
            content = parameter;
        }
        return content;
    }

    /**
     * 解析模板中的 {@code ${...}$} 方法表达式，反射调用对应 Service 取得动态内容。
     *
     * @param parameter 含方法表达式的模板
     * @param value     表达式参数取值
     * @return 解析后的字符串
     * @throws Exception 表达式解析或反射调用异常
     */
    public static String content(String parameter, Map<String, String> value) throws Exception {
        String content = "";
        MethodsBean methodsBean = new MethodsBean();
        //判断是否存在方法
        Boolean b = parameter.contains("$");
        if (b) {
            //截取
            String[] totalSplit = parameter.split("\\$");
            //下一层循环
            for (String ts : totalSplit) {
                //判断是否是方法字符串
                Boolean classJudge = ts.startsWith("{");
                if (classJudge) {
                    //截取出方法
                    String classSplit = StrUtil.sub(ts, 1, ts.length() - 1);
                    //截取出参数
                    String[] methodsSplit = classSplit.split("#");
                    for (String methods : methodsSplit) {
                        //判断参数执行下面操作，否则认为方法
                        if (methods.startsWith("{")) {
                            //实际参数
                            String p = StrUtil.sub(methods, 1, methods.indexOf("}"));
                            //参数
                            Map<String, Object> map = Maps.newHashMap();
                            //取出参数
                            if (p.contains(",")) {
                                String[] pp = p.split(",");
                                for (String s : pp) {
                                    if (s.contains(":")) {
                                        map.put(s.split(":")[0], value.get(s.split(":")[1]));
                                    }
                                }
                            } else {
                                map.put(p.split(":")[0], value.get(p.split(":")[1]));
                            }
                            methodsBean.setParamMap(map);
                        } else {
                            //实际类和方法
                            String m = StrUtil.sub(methods, 0, methods.length() - 1);
                            if (m.contains(".")) {
                                methodsBean.setClassz(m.split("\\.")[0]);
                                methodsBean.setMethodName(m.split("\\.")[1]);
                            }
                        }
                    }
                    //执行成功后的json
                    if (StringUtils.isNotBlank(content)) {
                        content += invokeService(methodsBean.getClassz(), methodsBean.getMethodName(), methodsBean.getParamMap());
                    } else {
                        content = content + invokeService(methodsBean.getClassz(), methodsBean.getMethodName(), methodsBean.getParamMap());
                    }

                    if (StringUtils.isNotBlank(content)) {
                        content += ts.substring(ts.lastIndexOf("}") + 1);
                    } else {
                        content = content + ts.substring(ts.lastIndexOf("}") + 1);
                    }
                } else {
                    if (StringUtils.isNotBlank(content)) {
                        content += ts;
                    } else {
                        content = content + ts;
                    }
                }
            }
        } else {
            content = parameter;
        }
        return content;
    }


    /**
     * 反射调用spring bean方法的入口
     *
     * @param classz     类名
     * @param methodName 方法名
     * @param paramMap   实际参数
     * @throws Exception
     */
    public static String invokeService(String classz, String methodName, Map<String, Object> paramMap) throws Exception {
        // 从Spring中获取代理对象（可能被JDK或者CGLIB代理）
        Object proxyObject = SpringUtil.getBean(classz);

        // 获取代理对象执行的方法
        Method method = getMethod(proxyObject.getClass(), methodName);

        // 获取代理对象中的目标对象
        Class target = AopUtils.getTargetClass(proxyObject);

        // 获取目标对象的方法，为什么获取目标对象的方法：只有目标对象才能通过 DefaultParameterNameDiscoverer 获取参数的方法名，代理对象由于可能被JDK或CGLIB代理导致获取不到参数名
        Method targetMethod = getMethod(target, methodName);

        if (method == null) {
            throw new RuntimeException(String.format("没有找到%s方法", methodName));
        }

        // 获取方法执行的参数
        List<Object> objects = getMethodParamList(targetMethod, paramMap);
        // 执行方法
        Object o = method.invoke(proxyObject, objects.toArray());
        // 转换json
        return JsonUtil.json(o);
    }

    /**
     * 获取方法实际参数，不支持基本类型
     *
     * @param method
     * @param paramMap
     * @return
     */
    private static List<Object> getMethodParamList(Method method, Map<String, Object> paramMap) throws Exception {
        List<Object> objectList = Lists.newArrayList();
        //设置排序字段
        paramMap.put("collationFields", "create_date_time");

        // 利用Spring提供的类获取方法形参名
        DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();
        String[] param = nameDiscoverer.getParameterNames(method);

        for (int i = 0; i < method.getParameterTypes().length; i++) {
            Class<?> parameterType = method.getParameterTypes()[i];

            Object object = null;
            // 基本类型不支持，支持包装类
            if (WRAP_CLASS.contains(parameterType)) {
                if (param != null && paramMap.containsKey(param[i])) {
                    object = paramMap.get(param[i]);

                    object = ConvertUtils.convert(object, parameterType);
                }

            } else if (!parameterType.isPrimitive()) {
                // 复制
                object = GeneralConvertor.convertor(paramMap, parameterType);
            }

            objectList.add(object);
        }

        return objectList;
    }

    /**
     * 获取类型实例
     *
     * @param parameterType
     * @return
     * @throws Exception
     */
    private static Object getInstance(Class<?> parameterType) throws Exception {
        if (parameterType.isAssignableFrom(List.class)) {
            return new ArrayList<>();

        } else if (parameterType.isAssignableFrom(Map.class)) {
            return new HashMap<>();
        } else if (parameterType.isAssignableFrom(Set.class)) {
            return new HashSet();
        }
        return parameterType.newInstance();
    }

    /**
     * 获取目标方法
     *
     * @param proxyObject
     * @param methodStr
     * @return
     */
    private static Method getMethod(Class proxyObject, String methodStr) {
        Method[] methods = proxyObject.getMethods();
        for (Method method : methods) {
            if (method.getName().equalsIgnoreCase(methodStr)) {
                return method;
            }
        }
        return null;
    }

}
