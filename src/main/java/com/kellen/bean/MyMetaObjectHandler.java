package com.kellen.bean;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Created with IntelliJ IDEA.
 *
 * @author sunkailun
 * @DateTime 2020/1/3  11:07 上午
 * @email 376253703@qq.com
 * 
 * @explain
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        //第一个对应实体属性名, 第二个参数需要填充的值
        setFieldValByName("createDateTime", LocalDateTime.now(), metaObject);
        //第一个对应实体属性名, 第二个参数需要填充的值
        setFieldValByName("modifyDateTime", LocalDateTime.now(), metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        //第一个对应实体属性名, 第二个参数需要填充的值
        setFieldValByName("modifyDateTime", LocalDateTime.now(), metaObject);
    }
}
