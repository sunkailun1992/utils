package com.kellen.utils;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.convert.Convert;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;
import cn.hutool.crypto.asymmetric.Sign;
import cn.hutool.crypto.asymmetric.SignAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created with IntelliJ IDEA.
 *
 * @author 孙凯伦
 * @DateTime 2018/5/9  下午1:53
 * @email 376253703@qq.com
 * 
 * @explain
 */
public class RsaUtils {

    /**
     * 加密
     *
     * @param data
     * @param privateKey
     * @return
     */
    public static String encryptionRsa(String data, String privateKey) {
        RSA rsa = SecureUtil.rsa(privateKey, null);
        byte[] encrypt = rsa.encrypt(data, KeyType.PrivateKey);
        return Base64.encode(encrypt);
    }

    /**
     * 验签
     *
     * @param orgData
     * @param signData
     * @param publicKey
     * @return
     */
    public static boolean checkRsa(String orgData, String signData, String publicKey) {
        RSA rsa = SecureUtil.rsa(null, publicKey);
        String data = new String(rsa.decrypt(signData, KeyType.PublicKey));
        if (StringUtils.hasText(data) && orgData.equals(data)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 解密
     *
     * @param data
     * @param publicKey
     * @return
     */
    public static String decodeRsa(String data, String publicKey) {
        RSA rsa = SecureUtil.rsa(null, publicKey);
        return new String(rsa.decrypt(data, KeyType.PublicKey));
    }

    /**
     * 解密
     *
     * @param param:
     * @return java.util.TreeMap
     * @author 孙凯伦
     * @DateTime 2018/5/9  下午2:36
     * @email 376253703@qq.com
     * 
     */
    public static TreeMap<String, Object> decodeAes(String param, String aesKey) {
        //构建
        SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, Base64.decode(aesKey));
        //ase加密
        byte[] decode = aes.decrypt(param);
        return JsonUtil.bean(new String(decode), TreeMap.class);
    }

    /**
     * 加密
     *
     * @param map:
     * @return java.lang.Stringdata
     * @author 孙凯伦
     * @DateTime 2018/5/9  下午2:36
     * @email 376253703@qq.com
     * 
     */
    public static String encryptionAes(TreeMap<String, Object> map, String aesKey) {
        //构建
        SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, Base64.decode(aesKey));
        //json化
        String param = JsonUtil.json(map);
        //解密
        byte[] data = aes.encrypt(param);
        //加密
        return Base64.encode(data);
    }

    /**
     * 生成签名
     *
     * @param map: 参数
     * @return java.lang.String
     * @author 孙凯伦
     * @DateTime 2018/5/9  下午2:01
     * @email 376253703@qq.com
     * 
     */
    public static String generateSign(TreeMap<String, Object> map, String privateKey) {
        //签名规则
        Sign sign = SecureUtil.sign(SignAlgorithm.SHA256withRSA, privateKey, null);
        //参数值
        StringBuffer param = new StringBuffer();
        //循环拼接参数
        mapToString(map,param);
        //将String转换为byte
        byte[] data = Convert.toStr(param).getBytes();
        //签名
        byte[] signed = sign.sign(data);
        return Base64.encode(signed);
    }


    /**
     * 生成签名
     *
     * @param json: 参数
     * @return java.lang.String
     * @author 孙凯伦
     * @DateTime 2018/5/9  下午2:01
     * @email 376253703@qq.com
     * 
     */
    public static String generateSign(String json, String privateKey) {
        //签名规则
        Sign sign = SecureUtil.sign(SignAlgorithm.SHA256withRSA, privateKey, null);
        //参数值
        StringBuffer param = new StringBuffer();
        //循环拼接参数
        mapToString(JsonUtil.bean(json,TreeMap.class),param);
        //将String转换为byte
        byte[] data = Convert.toStr(param).getBytes();
        //签名
        byte[] signed = sign.sign(data);
        return Base64.encode(signed);
    }

    /**
     * 生成签名
     *
     * @param param: 参数
     * @return java.lang.String
     * @author 孙凯伦
     * @DateTime 2018/5/9  下午2:01
     * @email 376253703@qq.com
     * 
     */
    public static String generateSignOnly(String param, String privateKey) {
        //签名规则
        Sign sign = SecureUtil.sign(SignAlgorithm.SHA256withRSA, privateKey, null);
        //将String转换为byte
        byte[] data = param.getBytes();
        //签名
        byte[] signed = sign.sign(data);
        return Base64.encode(signed);
    }

    /**
     * 签名验证
     *
     * @param map:      参数
     * @param signData: 签名
     * @return boolean
     * @author 孙凯伦
     * @DateTime 2018/5/9  下午1:59
     * @email 376253703@qq.com
     * 
     */
    public static Boolean verifySign(TreeMap<String, Object> map, String signData, String publicKey) {
        //签名规则
        Sign sign = SecureUtil.sign(SignAlgorithm.SHA256withRSA, null, publicKey);
        //参数值
        StringBuffer param = new StringBuffer();
        //循环拼接参数
        mapToString(map,param);
        //将String转换为byte
        byte[] data = Convert.toStr(param).getBytes();
        //验证签名
        Boolean verify = sign.verify(data, Base64.decode(signData));
        //返回
        return verify;
    }


    /**
     * 签名验证
     *
     * @param json:    参数
     * @param signData: 签名
     * @return boolean
     * @author 孙凯伦
     * @DateTime 2018/5/9  下午1:59
     * @email 376253703@qq.com
     * 
     */
    public static Boolean verifySign(String json, String signData, String publicKey) {
        //签名规则
        Sign sign = SecureUtil.sign(SignAlgorithm.SHA256withRSA, null, publicKey);
        //参数值
        StringBuffer param = new StringBuffer();
        //循环拼接参数
        mapToString(JsonUtil.bean(json,TreeMap.class),param);
        //将String转换为byte
        byte[] data = Convert.toStr(param).getBytes();
        //验证签名
        Boolean verify = sign.verify(data, Base64.decode(signData));
        //返回
        return verify;
    }


    /**
     * map转字符串
     *
     * @param map   集合
     * @param param 追加参数
     * @return
     */
    public static void mapToString(TreeMap<String, Object> map, StringBuffer param) {
        //循环集合
        for (String key : map.keySet()) {
            //值
            Object obj = map.get(key);
            //判断不同类型，执行不同参数转换
            if (obj instanceof List) {
                // 转list
                List<TreeMap<String,Object>> list = Convert.convert(List.class,obj);
                // 递归遍历
                for (Object m : list) {
                    mapToString(Convert.convert(TreeMap.class,m), param);
                }
            } else if (obj instanceof Map) {
                // 递归遍历
                mapToString(Convert.convert(TreeMap.class,obj), param);
            } else {
                //判断是否为空
                if (org.apache.commons.lang3.StringUtils.isNotBlank(Convert.toStr(obj))) {
                    //附值
                    param.append(Convert.toStr(obj));
                }
            }
        }
    }
}
