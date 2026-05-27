package com.kellen.utils.email;

import cn.hutool.extra.mail.MailAccount;
import cn.hutool.extra.mail.MailUtil;
import com.kellen.utils.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.regex.Pattern;

/**
 * @author 孙凯伦
 * 
 * @email: 376253703@qq.com
 * @description: 邮件工具类
 * @date: 2022/1/18 10:24 AM
 */
public class EmailUtils {


    /**
     * 发送邮件
     *
     * @param address 目标地址
     * @param title   标题
     * @param content 内容
     * @return
     */
    public static String sendEmail(String address, String title, String content) {
        //对邮件地址进行校验
        if (!isEmail(address)) {
            throw new BusinessException("邮箱格式有误,请检查！");
        }
        //创建发送账户
        MailAccount mailAccount = new MailAccount();
        //设置主机
        mailAccount.setHost(EmailAccount.host);
        //设置端口号
        mailAccount.setPort(EmailAccount.port);
        //设置用户
        mailAccount.setUser(EmailAccount.user);
        //设置密码
        mailAccount.setPass(EmailAccount.pass);
        //设置目标
        mailAccount.setFrom(EmailAccount.from);
        //设置
        mailAccount.setSslEnable(true);
        //直接返回
        return MailUtil.send(mailAccount, address, title, content, false);
    }


    /**
     * 发送邮件
     *
     * @param address 目标地址
     * @param title   标题
     * @param content 内容
     * @return
     */
    public static String sendEmail(String address, String title, String content, File... files) {
        //对邮件地址进行校验
        if (!isEmail(address)) {
            throw new BusinessException("邮箱格式有误,请检查！");
        }
        //创建发送账户
        MailAccount mailAccount = new MailAccount();
        //设置主机
        mailAccount.setHost(EmailAccount.host);
        //设置端口号
        mailAccount.setPort(EmailAccount.port);
        //设置用户
        mailAccount.setUser(EmailAccount.user);
        //设置密码
        mailAccount.setPass(EmailAccount.pass);
        //设置目标
        mailAccount.setFrom(EmailAccount.from);
        //设置
        mailAccount.setSslEnable(true);
        //直接返回
        return MailUtil.send(mailAccount, address, title, content, false, files);
    }


    /**
     * 发送邮件
     *
     * @param address 目标地址
     * @param title   标题
     * @param html    内容
     * @return
     */
    public static String sendEmailHtml(String address, String title, String html) {
        //对邮件地址进行校验
        if (!isEmail(address)) {
            throw new BusinessException("邮箱格式有误,请检查！");
        }
        //创建发送账户
        MailAccount mailAccount = new MailAccount();
        //设置主机
        mailAccount.setHost(EmailAccount.host);
        //设置端口号
        mailAccount.setPort(EmailAccount.port);
        //设置用户
        mailAccount.setUser(EmailAccount.user);
        //设置密码
        mailAccount.setPass(EmailAccount.pass);
        //设置目标
        mailAccount.setFrom(EmailAccount.from);
        //设置
        mailAccount.setSslEnable(true);
        //直接返回
        return MailUtil.send(mailAccount, address, title, html, true);
    }


    /**
     * 发送邮件
     *
     * @param address 目标地址
     * @param title   标题
     * @param html    内容
     * @return
     */
    public static String sendEmailHtml(String address, String title, String html, File... files) {
        //对邮件地址进行校验
        if (!isEmail(address)) {
            throw new BusinessException("邮箱格式有误,请检查！");
        }
        //创建发送账户
        MailAccount mailAccount = new MailAccount();
        //设置主机
        mailAccount.setHost(EmailAccount.host);
        //设置端口号
        mailAccount.setPort(EmailAccount.port);
        //设置用户
        mailAccount.setUser(EmailAccount.user);
        //设置密码
        mailAccount.setPass(EmailAccount.pass);
        //设置目标
        mailAccount.setFrom(EmailAccount.from);
        //设置
        mailAccount.setSslEnable(true);
        //直接返回
        return MailUtil.send(mailAccount, address, title, html, true, files);
    }


    /**
     * 校验是不是邮件格式
     *
     * @param emailAddress 邮箱地址
     * @return
     */
    public static boolean isEmail(String emailAddress) {
        //进行非空判断
        if (StringUtils.isBlank(emailAddress)) {
            return false;
        }
        //定义邮箱地址的正则表达式
        String regEx1 = "^([a-z0-9A-Z]+[-|\\.]?)+[a-z0-9A-Z]@([a-z0-9A-Z]+(-[a-z0-9A-Z]+)?\\.)+[a-zA-Z]{2,}$";
        //判断是否匹配
        return Pattern.compile(regEx1).matcher(emailAddress).matches();
    }


}
