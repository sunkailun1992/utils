package com.gb.utils;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.actor.Props;
import com.alibaba.schedulerx.shade.scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @ClassName AkkaUtils
 * @BelongPackage user
 * @Description akka异步框架欧工具类
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/3/29 1:50 下午
 */
public class AkkaUtils {
    /**
     * akka创建管理器,饿汉
     */
    public static ActorSystem actorSystem = ActorSystem.create("gbw_akka");

    /**
     * 懒汉（弃用）
     * @Description: 执行内容:初始化
     * @Title: startAkka    方法名
     * @return void        返回类型
     */
//	public static void startAkka(){
//		if(actorSystem == null){
//			synchronized (AkkaUtils.class) {
//				if(actorSystem == null) {
//					actorSystem = ActorSystem.create("gbw_akka");
//				}
//			}
//		}
//	}

    /**
     * @param c
     * @param name
     * @return ActorRef        返回类型
     * @Description: 执行内容:获得akka的ActorRef
     * @Title: getActorRef    方法名
     */
    public static ActorRef getActorRef(Class<?> c, String name) {
        return actorSystem.actorOf(Props.create(c), name);
    }


    /**
     * @param send 发送方,执行onReceive
     * @param data 发送参数
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: eachOtherComm
     * @description: TODO  akka异步执行
     * @return: void
     * @date: 2021/3/29 11:43 上午
     */
    public static void eachOtherComm(ActorRef send, Object data) {
        send.tell(data, ActorRef.noSender());
    }


    /**
     * @param send    发送方,执行onReceive
     * @param receive 接收者,执行onReceive
     * @param data    发送方参数
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: eachOtherComm
     * @description: TODO   执行内容:akka互相通信
     * @return: void
     * @date: 2021/3/29 11:44 上午
     */
    public static void eachOtherComm(ActorRef send, ActorRef receive, Object data) {
        send.tell(data, receive);
    }


    /**
     * @param c    发送方,执行onReceive
     * @param name 发送方别名
     * @param data 发送方参数
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: eachOtherComm
     * @description: TODO   akka异步执行
     * @return: void
     * @date: 2021/3/29 11:45 上午
     */
    public static void eachOtherComm(Class<?> c, String name, Object data) {
        getActorRef(c, name).tell(data, ActorRef.noSender());
    }


    /**
     * @param c       发送方,执行onReceive
     * @param name    发送方别名
     * @param receive 接收者,执行onReceive
     * @param data    发送方参数
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: eachOtherComm
     * @description: TODO   akka互相通信
     * @return: void
     * @date: 2021/3/29 11:43 上午
     */
    public static void eachOtherComm(Class<?> c, String name, ActorRef receive, Object data) {
        getActorRef(c, name).tell(data, receive);
    }


    /**
     * @param actorRef 发送方,执行onReceive
     * @param data     发送方参数
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: singleComm
     * @description: TODO   先执行,最后通过Inbox取出参数
     * @return: akka.actor.Inbox
     * @date: 2021/3/29 11:47 上午
     */
    public static Inbox singleComm(ActorRef actorRef, Object data) {
        //内容管理器
        Inbox inbox = Inbox.create(actorSystem);
        // 异步执行onReceive方法内容
        inbox.send(actorRef, data);
        return inbox;
    }


    /**
     * @param c    发送方,执行onReceive
     * @param name 发送方别名
     * @param data 参数
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: singleComm
     * @description: TODO   先执行,最后通过Inbox取出参数
     * @return: akka.actor.Inbox
     * @date: 2021/3/29 11:48 上午
     */
    public static Inbox singleComm(Class<?> c, String name, Object data) {
        //内容管理器
        Inbox inbox = Inbox.create(actorSystem);
        // 异步执行onReceive方法内容
        inbox.send(getActorRef(c, name), data);
        return inbox;
    }


    /**
     * @param inbox    异步返回执行类
     * @param wait     等待秒数
     * @param timeUnit 参数时间单位
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: getData
     * @description: TODO   取出参数
     * @return: java.lang.Object
     * @date: 2021/3/29 11:49 上午
     */
    public static Object getData(Inbox inbox, Integer wait, TimeUnit timeUnit) throws TimeoutException {
        // 等待执行完毕的返回参数
        return inbox.receive(Duration.create(wait, timeUnit));
    }

    public static void main(String[] args) throws TimeoutException {
//        Inbox inbox = AkkaUtils.singleComm(Greeter.class, "greeter", "test");
//        System.out.println("异步执行中");
//        Object o = AkkaUtils.getData(inbox, 10, TimeUnit.SECONDS);
//        System.out.printf("成功");
    }
}
