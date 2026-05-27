package com.kellen.aliyun.oss;

import cn.hutool.core.io.IoUtil;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.*;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.kellen.aliyun.AliyunKey;
import com.kellen.aliyun.Oss;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URI;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author 孙凯伦
 * @email: 376253703@qq.com
 * @description: 阿里云文件oss
 * @date: 2022/1/18 10:26 AM
 */
@Slf4j
public class OssUtils {


    /**
     * 删除
     *
     * @param name oss地址
     */
    public static void delete(String name) {
        // 创建OSSClient实例。
        OSS client = new OSSClientBuilder().build(Oss.endpoint, AliyunKey.accessKeyId, AliyunKey.accessKeySecret);
        // 删除文件。
        client.deleteObject(Oss.bucket, name);
        // 关闭OSSClient。
        client.shutdown();
    }


    /**
     * 上传
     *
     * @param file 文件地址
     * @param name 上传地址
     * @param tags 标签
     * @throws Exception
     */
    public static void upload(String file, String name, Map<String, String> tags) throws Exception {
        OSS client = new OSSClientBuilder().build(Oss.endpoint, AliyunKey.accessKeyId, AliyunKey.accessKeySecret);
        // 在HTTP header中设置标签信息。
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setObjectTagging(tags);
        // 上传文件流。
        InputStream inputStream = new FileInputStream(file);
        PutObjectResult putObjectResult = client.putObject(Oss.bucket, name, inputStream, metadata);
        // 关闭OSSClient。
        client.shutdown();
    }


    /**
     * 上传
     *
     * @param file 文件地址
     * @param name 上传地址
     * @throws Exception
     */
    public static void upload(String file, String name) throws Exception {
        OSS client = new OSSClientBuilder().build(Oss.endpoint, AliyunKey.accessKeyId, AliyunKey.accessKeySecret);
        // 上传文件流。
        InputStream inputStream = new FileInputStream(file);
        PutObjectResult putObjectResult = client.putObject(Oss.bucket, name, inputStream);
        // 关闭OSSClient。
        client.shutdown();
    }

    /**
     * 上传
     *
     * @param file 文件地址 D:/app/xx.pdf
     * @param name 上传地址 template/aa.pdf
     * @throws Exception
     */
    public static String uploadFileReturnUrl(String file, String name) throws Exception {
        return uploadFileReturnUrl(new FileInputStream(file), name);
    }

    /**
     * 上传
     *
     * @param bytes 上传文件的字节数组
     * @param name  上传地址 signedContract/xx.pdf
     * @throws Exception
     */
    public static String uploadFileReturnUrl(byte[] bytes, String name) throws Exception {
        return uploadFileReturnUrl(IoUtil.toStream(bytes), name);
    }

    /**
     * 上传
     *
     * @param inputStream 文件字节输入流
     * @param name        上传地址 signedContract/xx.pdf
     * @throws Exception
     */
    public static String uploadFileReturnUrl(InputStream inputStream, String name) throws Exception {
        //创建客户端
        OSS client = new OSSClientBuilder().build(Oss.endpoint, AliyunKey.accessKeyId, AliyunKey.accessKeySecret);
        //上传
        client.putObject(Oss.bucket, name, inputStream);
        //设置URL过期时间为24小时。
        Date expiration = new Date(System.currentTimeMillis() + 24 * 3600 * 1000);
        //设置
        client.setBucketAcl(Oss.bucket, CannedAccessControlList.PublicRead);
        //生成以GET方法访问的签名URL，访客可以直接通过浏览器访问相关内容。
        String url = client.generatePresignedUrl(Oss.bucket, name, expiration).toString();
        //判断是否为空
        if (StringUtils.isNotBlank(url)) {
            //截取?前面的字符串 "http://aliyunoss/aaa.pdf?exprie=xjijxisajiodja"
            url = url.split("\\?")[0];
        }
        //关闭ossClient
        client.shutdown();
        //返回
        return url;
    }

    /**
     * 从阿里云地址读取模板的字节数组
     *
     * @param filePath template/xx.pdf
     * @return
     */
    public static byte[] getBytesFromOssFile(String filePath) {
        //创建客户端
        OSS client = new OSSClientBuilder().build(Oss.endpoint, AliyunKey.accessKeyId, AliyunKey.accessKeySecret);
        //创建oss对象
        OSSObject ossObject = client.getObject(Oss.bucket, filePath);
        //返回字节数组
        return IoUtil.readBytes(ossObject.getObjectContent());
    }


    /**
     * 上传excel
     *
     * @param outputStream 字节数组输出流
     * @param fileName     文件名称
     * @throws Exception
     */
    public static void upload(ByteArrayOutputStream outputStream, String fileName, Map<String, String> tags) {
        OSS client = new OSSClientBuilder().build(Oss.endpoint, AliyunKey.accessKeyId, AliyunKey.accessKeySecret);
        // 在HTTP header中设置标签信息。
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setObjectTagging(tags);
        // 上传文件流。
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        client.putObject(new PutObjectRequest(Oss.bucket, fileName, inputStream, metadata));
        // 关闭OSSClient。
        client.shutdown();
    }

    /**
     * 上传excel
     *
     * @param outputStream 字节数组输出流
     * @param fileName     文件名称
     * @throws Exception
     */
    public static void upload(ByteArrayOutputStream outputStream, String fileName) {
        OSS client = new OSSClientBuilder().build(Oss.endpoint, AliyunKey.accessKeyId, AliyunKey.accessKeySecret);
        // 上传文件流。
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        client.putObject(new PutObjectRequest(Oss.bucket, fileName, inputStream));
        // 关闭OSSClient。
        client.shutdown();
    }

    /**
     * 签名直传服务响应客户端发送给应用服务器的GET消息
     */
    public static AssumeRoleResponse.Credentials sts(String roleSessionName) {
        // STS接入地址，例如sts.cn-hangzhou.aliyuncs.com。
        String endpoint = "sts.cn-hangzhou.aliyuncs.com";
        // 请填写您的AccessKeyId。
        String accessKeyId = AliyunKey.accessKeyId;
        // 请填写您的AccessKeySecret。
        String accessKeySecret = AliyunKey.accessKeySecret;
        // 填写步骤3获取的角色ARN。
        String roleArn = "acs:ram::1308928102086701:role/ramoss";
        // 自定义角色会话名称，用来区分不同的令牌，例如可填写为SessionTest。
        // String roleSessionName = "RamOss";
        // 以下Policy用于限制仅允许使用临时访问凭证向目标存储空间examplebucket下的src目录上传文件。
        // 临时访问凭证最后获得的权限是步骤4设置的角色权限和该Policy设置权限的交集，即仅允许将文件上传至目标存储空间examplebucket下的src目录。
        // 如果policy为空，则用户将获得该角色下所有权限。
        String policy = null;
        // 设置临时访问凭证的有效时间为3600秒。
        Long durationSeconds = 3600L;
        try {
            // regionId表示RAM的地域ID。以华东1（杭州）地域为例，regionID填写为cn-hangzhou。也可以保留默认值，默认值为空字符串（""）。
            String regionId = "cn-hangzhou";
            // 添加endpoint。适用于Java SDK 3.12.0及以上版本。
            DefaultProfile.addEndpoint(regionId, "Sts", endpoint);
            // 添加endpoint。适用于Java SDK 3.12.0以下版本。
            // DefaultProfile.addEndpoint("", regionId, "Sts", endpoint);
            // 构造default profile。
            IClientProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret);
            // 构造client。
            DefaultAcsClient client = new DefaultAcsClient(profile);
            final AssumeRoleRequest request = new AssumeRoleRequest();
            // 适用于Java SDK 3.12.0及以上版本。
            request.setSysMethod(MethodType.POST);
            // 适用于Java SDK 3.12.0以下版本。
            // request.setMethod(MethodType.POST);
            request.setRoleArn(roleArn);
            request.setRoleSessionName(roleSessionName);
            request.setPolicy(policy);
            request.setDurationSeconds(durationSeconds);
            final AssumeRoleResponse response = client.getAcsResponse(request);
            //返回结果
            return response.getCredentials();
        } catch (ClientException e) {
            log.error("Failed：");
            log.error("Error code: " + e.getErrCode());
            log.error("Error message: " + e.getErrMsg());
            log.error("RequestId: " + e.getRequestId());
        }
        return null;
    }

    /**
     * 签名直传服务响应客户端发送给应用服务器的GET消息
     */
    public static void doGet(HttpServletRequest request, HttpServletResponse response, String url, String directory) {
        // 请填写您的AccessKeyId。
        String accessId = AliyunKey.accessKeyId;
        // 请填写您的AccessKeySecret。
        String accessKey = AliyunKey.accessKeySecret;
        // host的格式为 bucketname.endpoint
        String host = "https://" + Oss.bucket + "." + Oss.endpoint;
        // callbackUrl为 上传回调服务器的URL，请将下面的IP和Port配置为您自己的真实信息。
        String callbackUrl = url;
        // 用户上传文件时指定的前缀。
        String dir = directory;

        OSS ossClient = new OSSClientBuilder().build(Oss.endpoint, accessId, accessKey);
        try {
            long expireTime = 60;
            long expireEndTime = System.currentTimeMillis() + expireTime * 1000;
            Date expiration = new Date(expireEndTime);
            // PostObject请求最大可支持的文件大小为5 GB，即CONTENT_LENGTH_RANGE为5*1024*1024*1024。
            PolicyConditions policyConds = new PolicyConditions();
            policyConds.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, 0, 1048576000);
            policyConds.addConditionItem(MatchMode.StartWith, PolicyConditions.COND_KEY, dir);

            String postPolicy = ossClient.generatePostPolicy(expiration, policyConds);
            byte[] binaryData = postPolicy.getBytes("utf-8");
            String encodedPolicy = BinaryUtil.toBase64String(binaryData);
            String postSignature = ossClient.calculatePostSignature(postPolicy);

            Map<String, String> respMap = new LinkedHashMap<String, String>();
            respMap.put("accessid", accessId);
            respMap.put("policy", encodedPolicy);
            respMap.put("signature", postSignature);
            respMap.put("dir", dir);
            respMap.put("host", host);
            respMap.put("expire", String.valueOf(expireEndTime / 1000));
            respMap.put("bucket", Oss.bucket);
            respMap.put("endpoint", Oss.endpoint);

            JSONObject jasonCallback = new JSONObject();
            jasonCallback.put("callbackUrl", callbackUrl);
            jasonCallback.put("callbackBody",
                    "filename=${object}&size=${size}&mimeType=${mimeType}&height=${imageInfo.height}&width=${imageInfo.width}");
            jasonCallback.put("callbackBodyType", "application/x-www-form-urlencoded");
            String base64CallbackBody = BinaryUtil.toBase64String(jasonCallback.toString().getBytes());
            respMap.put("callback", base64CallbackBody);

            String ja1 = JSONObject.toJSONString(respMap);
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST");
            response(request, response, ja1);

        } catch (Exception e) {
            // Assert.fail(e.getMessage());
            System.out.println(e.getMessage());
        } finally {
            ossClient.shutdown();
        }
    }


    /**
     * 上传回调服务响应OSS发送给应用服务器的POST消息
     */
    public static void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String ossCallbackBody = getPostBody(request.getInputStream(), Integer.parseInt(request.getHeader("content-length")));
        boolean ret = verifyOssCallbackRequest(request, ossCallbackBody);
        System.out.println("verify result : " + ret);
        // System.out.println("OSS Callback Body:" + ossCallbackBody);
        if (ret) {
            response(request, response, "{\"Status\":\"OK\"}", HttpServletResponse.SC_OK);
        } else {
            response(request, response, "{\"Status\":\"verdify not ok\"}", HttpServletResponse.SC_BAD_REQUEST);
        }
    }


    /**
     * 获取public key
     *
     * @param url
     * @return
     */
    public static String executeGet(String url) {
        BufferedReader in = null;

        String content = null;
        try {
            // 定义HttpClient
            CloseableHttpClient client = HttpClients.createDefault();
            // 实例化HTTP方法
            HttpGet request = new HttpGet();
            request.setURI(new URI(url));
            HttpResponse response = client.execute(request);

            in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            StringBuffer sb = new StringBuffer("");
            String line = "";
            String nl = System.getProperty("line.separator");
            while ((line = in.readLine()) != null) {
                sb.append(line + nl);
            }
            in.close();
            content = sb.toString();
        } catch (Exception e) {
        } finally {
            if (in != null) {
                try {
                    // 最后要关闭BufferedReader
                    in.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return content;
    }

    /**
     * 获取Post消息体
     *
     * @param is
     * @param contentLen
     * @return
     */
    public static String getPostBody(InputStream is, int contentLen) {
        if (contentLen > 0) {
            int readLen = 0;
            int readLengthThisTime = 0;
            byte[] message = new byte[contentLen];
            try {
                while (readLen != contentLen) {
                    readLengthThisTime = is.read(message, readLen, contentLen - readLen);
                    // Should not happen.
                    if (readLengthThisTime == -1) {
                        break;
                    }
                    readLen += readLengthThisTime;
                }
                return new String(message);
            } catch (IOException e) {
            }
        }
        return "";
    }

    /**
     * 验证上传回调的Request
     *
     * @param request
     * @param ossCallbackBody
     * @return
     * @throws NumberFormatException
     * @throws IOException
     */
    public static boolean verifyOssCallbackRequest(HttpServletRequest request, String ossCallbackBody) throws NumberFormatException, IOException {
        boolean ret = false;
        String autorizationInput = new String(request.getHeader("Authorization"));
        String pubKeyInput = request.getHeader("x-oss-pub-key-url");
        byte[] authorization = BinaryUtil.fromBase64String(autorizationInput);
        byte[] pubKey = BinaryUtil.fromBase64String(pubKeyInput);
        String pubKeyAddr = new String(pubKey);
        if (!pubKeyAddr.startsWith("http://gosspublic.alicdn.com/")
                && !pubKeyAddr.startsWith("https://gosspublic.alicdn.com/")) {
            System.out.println("pub key addr must be oss addrss");
            return false;
        }
        String retString = executeGet(pubKeyAddr);
        retString = retString.replace("-----BEGIN PUBLIC KEY-----", "");
        retString = retString.replace("-----END PUBLIC KEY-----", "");
        String queryString = request.getQueryString();
        String uri = request.getRequestURI();
        String decodeUri = java.net.URLDecoder.decode(uri, "UTF-8");
        String authStr = decodeUri;
        if (queryString != null && !"".equals(queryString)) {
            authStr += "?" + queryString;
        }
        authStr += "\n" + ossCallbackBody;
        ret = doCheck(authStr, authorization, retString);
        return ret;
    }

    /**
     * 验证RSA
     *
     * @param content
     * @param sign
     * @param publicKey
     * @return
     */
    public static boolean doCheck(String content, byte[] sign, String publicKey) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            byte[] encodedKey = BinaryUtil.fromBase64String(publicKey);
            PublicKey pubKey = keyFactory.generatePublic(new X509EncodedKeySpec(encodedKey));
            java.security.Signature signature = java.security.Signature.getInstance("MD5withRSA");
            signature.initVerify(pubKey);
            signature.update(content.getBytes());
            boolean bverify = signature.verify(sign);
            return bverify;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 服务器响应结果
     *
     * @param request
     * @param response
     * @param results
     * @param status
     * @throws IOException
     */
    public static void response(HttpServletRequest request, HttpServletResponse response, String results, int status) throws IOException {
        String callbackFunName = request.getParameter("callback");
        response.addHeader("Content-Length", String.valueOf(results.length()));
        if (callbackFunName == null || "".equalsIgnoreCase(callbackFunName)) {
            response.getWriter().println(results);
        } else {
            response.getWriter().println(callbackFunName + "( " + results + " )");
        }
        response.setStatus(status);
        response.flushBuffer();
    }

    /**
     * 服务器响应结果
     */
    public static void response(HttpServletRequest request, HttpServletResponse response, String results) throws IOException {
        String callbackFunName = request.getParameter("callback");
        if (callbackFunName == null || "".equalsIgnoreCase(callbackFunName)) {
            response.getWriter().println(results);
        } else {
            response.getWriter().println(callbackFunName + "( " + results + " )");
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.flushBuffer();
    }
}
