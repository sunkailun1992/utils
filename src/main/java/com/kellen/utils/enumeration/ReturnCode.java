package com.kellen.utils.enumeration;

import lombok.Getter;

/**
 * 统一返回码：编码按阿里巴巴 Java 开发手册分为用户端（A）、当前系统（B）、第三方服务（C）三类，成功为 {@code "0"}。
 *
 * <p>常量名为英文大写下划线，由文案意译；编码与文案一经发布不再修改，改名不得改动二者。</p>
 *
 * @author 孙凯伦
 */
@Getter
public enum ReturnCode {

    /**
     * 成功
     */
    SUCCESS("0", "成功"),
    /**
     * A表示错误来源于用户，比如参数错误，用户安装版本过低，用户支付/超时等问题---------------------------------------------------
     */
    CLIENT_ERROR("A0001", "用户端错误"),
    USER_REGISTRATION_ERROR("A0100", "用户注册错误"), // 去除历史尾部空格，保证实际返回文案与错误码文档一致。
    PRIVACY_AGREEMENT_NOT_ACCEPTED("A0101", "用户未同意隐私协议"),
    REGISTRATION_REGION_RESTRICTED("A0102", "注册国家或地区受限"),
    USERNAME_VALIDATION_FAILED("A0110", "用户名校验失败"),
    USERNAME_ALREADY_EXISTS("A0111", "用户名已存在"),
    USERNAME_CONTAINS_SENSITIVE_WORD("A0112", "用户名包含敏感词"),
    USERNAME_CONTAINS_SPECIAL_CHARACTER("A0113", "用户名包含特殊字符"),
    PHONE_ALREADY_REGISTERED("A0115", "该手机号已注册"),
    INVALID_TOKEN("A0116", "无效token，请重新登录！"),
    PASSWORD_VALIDATION_FAILED("A0120", "密码校验失败"),
    PASSWORD_TOO_SHORT("A0121", "密码长度不够"),
    PASSWORD_TOO_WEAK("A0122", "密码强度不够"),
    VERIFICATION_CODE_ERROR("A0130", "校验码输入错误"),
    SMS_VERIFICATION_CODE_ERROR("A0131", "短信校验码输入错误"),
    EMAIL_VERIFICATION_CODE_ERROR("A0132", "邮件校验码输入错误"),
    VOICE_VERIFICATION_CODE_ERROR("A0133", "语音校验码输入错误"),
    USER_ID_DOCUMENT_ERROR("A0140", "用户证件异常"),
    ID_DOCUMENT_TYPE_NOT_SELECTED("A0141", "用户证件类型未选择"),
    INVALID_MAINLAND_ID_CARD_NUMBER("A0142", "大陆身份证编号校验非法"),
    INVALID_PASSPORT_NUMBER("A0143", "护照编号校验非法"),
    INVALID_MILITARY_ID_NUMBER("A0144", "军官证编号校验非法"),
    USER_BASIC_INFO_VALIDATION_FAILED("A0150", "用户基本信息校验失败"),
    INVALID_PHONE_FORMAT("A0151", "手机格式校验失败"),
    INVALID_ADDRESS_FORMAT("A0152", "地址格式校验失败"),
    INVALID_EMAIL_FORMAT("A0153", "邮箱格式校验失败"),
    USER_LOGIN_ERROR("A0200", "用户登录异常"), // 去除历史尾部空格，保证实际返回文案与错误码文档一致。
    USER_ACCOUNT_NOT_FOUND("A0201", "用户账户不存在"),
    USER_ACCOUNT_FROZEN("A0202", "用户账户被冻结"),
    USER_ACCOUNT_INVALIDATED("A0203", "用户账户已作废"),
    USER_PASSWORD_ERROR("A0210", "用户密码错误"),
    PASSWORD_RETRY_LIMIT_EXCEEDED("A0211", "用户输入密码错误次数超限"),
    USER_AUTHENTICATION_FAILED("A0220", "用户身份校验失败"),
    FINGERPRINT_RECOGNITION_FAILED("A0221", "用户指纹识别失败"),
    FACE_RECOGNITION_FAILED("A0222", "用户面容识别失败"),
    THIRD_PARTY_LOGIN_NOT_AUTHORIZED("A0223", "用户未获得第三方登录授权"),
    USER_LOGIN_EXPIRED("A0230", "用户登录已过期"),
    CAPTCHA_ERROR("A0240", "用户验证码错误"),
    CAPTCHA_RETRY_LIMIT_EXCEEDED("A0241", "用户验证码尝试次数超限"),
    ACCESS_PERMISSION_ERROR("A0300", "访问权限异常"), // 去除历史尾部空格，保证实际返回文案与错误码文档一致。
    ACCESS_UNAUTHORIZED("A0301", "访问未授权"),
    AUTHORIZATION_IN_PROGRESS("A0302", "正在授权中"),
    AUTHORIZATION_REQUEST_REJECTED("A0303", "用户授权申请被拒绝"),
    BLOCKED_BY_PRIVACY_SETTINGS("A0310", "因访问对象隐私设置被拦截"),
    AUTHORIZATION_EXPIRED("A0311", "授权已过期"),
    PERMISSION_DENIED("A0312", "无权限使用"), // 去除历史尾部空格，保证实际返回文案与错误码文档一致。
    USER_ACCESS_BLOCKED("A0320", "用户访问被拦截"),
    BLACKLISTED_USER("A0321", "黑名单用户"),
    ACCOUNT_FROZEN("A0322", "账号被冻结"),
    ILLEGAL_IP_ADDRESS("A0323", "非法IP地址"),
    GATEWAY_ACCESS_RESTRICTED("A0324", "网关访问受限"),
    REGION_BLACKLISTED("A0325", "地域黑名单"),
    SERVICE_PAYMENT_OVERDUE("A0330", "服务已欠费"),
    USER_SIGNATURE_ERROR("A0340", "用户签名异常"),
    RSA_SIGNATURE_ERROR("A0341", "RSA签名错误"),
    REQUEST_PARAMETER_ERROR("A0400", "用户请求参数错误"),
    MALICIOUS_REDIRECT_LINK("A0401", "包含非法恶意跳转链接"),
    INVALID_USER_INPUT("A0402", "无效的用户输入"),
    REQUIRED_PARAMETER_MISSING("A0410", "请求必填参数为空"),
    ORDER_NUMBER_MISSING("A0411", "用户订单号为空"),
    ORDER_QUANTITY_MISSING("A0412", "订购数量为空"),
    TIMESTAMP_PARAMETER_MISSING("A0413", "缺少时间戳参数"),
    INVALID_TIMESTAMP_PARAMETER("A0414", "非法的时间戳参数"),
    PARAMETER_OUT_OF_RANGE("A0420", "请求参数值超出允许的范围"),
    PARAMETER_FORMAT_MISMATCH("A0421", "参数格式不匹配"),
    ADDRESS_OUT_OF_SERVICE_AREA("A0422", "地址不在服务范围"),
    TIME_OUT_OF_SERVICE_RANGE("A0423", "时间不在服务范围"),
    AMOUNT_LIMIT_EXCEEDED("A0424", "金额超出限制"),
    QUANTITY_LIMIT_EXCEEDED("A0425", "数量超出限制"),
    BATCH_SIZE_LIMIT_EXCEEDED("A0426", "请求批量处理总个数超出限制"),
    REQUEST_JSON_PARSE_FAILED("A0427", "请求JSON解析失败"),
    ILLEGAL_USER_INPUT("A0430", "用户输入内容非法"),
    CONTAINS_PROHIBITED_WORD("A0431", "包含违禁敏感词"),
    IMAGE_CONTAINS_PROHIBITED_CONTENT("A0432", "图片包含违禁信息"),
    FILE_COPYRIGHT_INFRINGEMENT("A0433", "文件侵犯版权"),
    USER_OPERATION_ERROR("A0440", "用户操作异常"),
    USER_PAYMENT_TIMEOUT("A0441", "用户支付超时"),
    ORDER_CONFIRMATION_TIMEOUT("A0442", "确认订单超时"),
    ORDER_CLOSED("A0443", "订单已关闭"),
    USER_REQUEST_SERVICE_ERROR("A0500", "用户请求服务异常"),
    REQUEST_COUNT_LIMIT_EXCEEDED("A0501", "请求次数超出限制"),
    REQUEST_CONCURRENCY_LIMIT_EXCEEDED("A0502", "请求并发数超出限制"),
    OPERATION_PLEASE_WAIT("A0503", "用户操作请等待"),
    WEBSOCKET_CONNECTION_ERROR("A0504", "WebSocket连接异常"),
    WEBSOCKET_DISCONNECTED("A0505", "WebSocket连接断开"),
    DUPLICATE_REQUEST("A0506", "用户重复请求"),
    USER_RESOURCE_ERROR("A0600", "用户资源异常"),
    INSUFFICIENT_ACCOUNT_BALANCE("A0601", "账户余额不足"),
    INSUFFICIENT_USER_DISK_SPACE("A0602", "用户磁盘空间不足"),
    INSUFFICIENT_USER_MEMORY("A0603", "用户内存空间不足"),
    INSUFFICIENT_USER_OSS_CAPACITY("A0604", "用户OSS容量不足"),
    USER_QUOTA_EXHAUSTED("A0605", "用户配额已用光"),
    USER_UPLOAD_ERROR("A0700", "用户上传文件异常"),
    UPLOAD_FILE_TYPE_MISMATCH("A0701", "用户上传文件类型不匹配"),
    UPLOAD_FILE_TOO_LARGE("A0702", "用户上传文件太大"),
    UPLOAD_IMAGE_TOO_LARGE("A0703", "用户上传图片太大"),
    UPLOAD_VIDEO_TOO_LARGE("A0704", "用户上传视频太大"),
    UPLOAD_ARCHIVE_TOO_LARGE("A0705", "用户上传压缩文件太大"),
    CLIENT_VERSION_ERROR("A0800", "用户当前版本异常"),
    CLIENT_VERSION_MISMATCH("A0801", "用户安装版本与系统不匹配"),
    CLIENT_VERSION_TOO_LOW("A0802", "用户安装版本过低"),
    CLIENT_VERSION_TOO_HIGH("A0803", "用户安装版本过高"),
    CLIENT_VERSION_EXPIRED("A0804", "用户安装版本已过期"),
    API_VERSION_MISMATCH("A0805", "用户API请求版本不匹配"),
    API_VERSION_TOO_HIGH("A0806", "用户API请求版本过高"),
    API_VERSION_TOO_LOW("A0807", "用户API请求版本过低"),
    PRIVACY_NOT_AUTHORIZED("A0900", "用户隐私未授权"),
    PRIVACY_NOT_SIGNED("A0901", "用户隐私未签署"),
    WEBCAM_NOT_AUTHORIZED("A0902", "用户摄像头未授权"),
    CAMERA_NOT_AUTHORIZED("A0903", "用户相机未授权"),
    PHOTO_LIBRARY_NOT_AUTHORIZED("A0904", "用户图片库未授权"),
    FILE_ACCESS_NOT_AUTHORIZED("A0905", "用户文件未授权"),
    LOCATION_NOT_AUTHORIZED("A0906", "用户位置信息未授权"),
    CONTACTS_NOT_AUTHORIZED("A0907", "用户通讯录未授权"),
    USER_DEVICE_ERROR("A1000", "用户设备异常"),
    CAMERA_ERROR("A1001", "用户相机异常"),
    MICROPHONE_ERROR("A1002", "用户麦克风异常"),
    EARPIECE_ERROR("A1003", "用户听筒异常"),
    SPEAKER_ERROR("A1004", "用户扬声器异常"),
    GPS_LOCATION_ERROR("A1005", "用户GPS定位异常"),
    /**
     * B表示错误来源于当前系统，往往是业务逻辑出错，或程序健壮性差等问题---------------------------------------------------
     */
    SYSTEM_EXECUTION_ERROR("B0001", "系统执行出错"),
    SYSTEM_EXECUTION_TIMEOUT("B0100", "系统执行超时"),
    SYSTEM_ORDER_PROCESSING_TIMEOUT("B0101", "系统订单处理超时"),
    SYSTEM_DISASTER_RECOVERY_TRIGGERED("B0200", "系统容灾功能被触发"),
    SYSTEM_RATE_LIMITED("B0210", "系统限流"),
    SYSTEM_DEGRADED("B0220", "系统功能降级"),
    SYSTEM_RESOURCE_ERROR("B0300", "系统资源异常"),
    SYSTEM_RESOURCE_EXHAUSTED("B0310", "系统资源耗尽"),
    SYSTEM_DISK_SPACE_EXHAUSTED("B0311", "系统磁盘空间耗尽"),
    SYSTEM_MEMORY_EXHAUSTED("B0312", "系统内存耗尽"),
    FILE_HANDLES_EXHAUSTED("B0313", "文件句柄耗尽"),
    CONNECTION_POOL_EXHAUSTED("B0314", "系统连接池耗尽"),
    THREAD_POOL_EXHAUSTED("B0315", "系统线程池耗尽"),
    SYSTEM_RESOURCE_ACCESS_ERROR("B0320", "系统资源访问异常"),
    SYSTEM_DISK_FILE_READ_FAILED("B0321", "系统读取磁盘文件失败"),
    /**
     * C表示错误来源于第三方服务，比如 CDN 服务出错，消息投递超时等问题---------------------------------------------------
     */
    THIRD_PARTY_SERVICE_ERROR("C0001", "调用第三方服务出错"),
    MIDDLEWARE_SERVICE_ERROR("C0100", "中间件服务出错"),
    RPC_SERVICE_ERROR("C0110", "RPC服务出错"),
    RPC_SERVICE_NOT_FOUND("C0111", "RPC服务未找到"),
    RPC_SERVICE_NOT_REGISTERED("C0112", "RPC服务未注册"),
    INTERFACE_NOT_FOUND("C0113", "接口不存在"),
    MESSAGE_SERVICE_ERROR("C0120", "消息服务出错"),
    MESSAGE_DELIVERY_ERROR("C0121", "消息投递出错"),
    MESSAGE_CONSUMPTION_ERROR("C0122", "消息消费出错"),
    MESSAGE_SUBSCRIPTION_ERROR("C0123", "消息订阅出错"),
    MESSAGE_GROUP_NOT_FOUND("C0124", "消息分组未查到"),
    CACHE_SERVICE_ERROR("C0130", "缓存服务出错"),
    CACHE_KEY_TOO_LONG("C0131", "key长度超过限制"),
    CACHE_VALUE_TOO_LONG("C0132", "value长度超过限制"),
    STORAGE_CAPACITY_FULL("C0133", "存储容量已满"),
    UNSUPPORTED_DATA_FORMAT("C0134", "不支持的数据格式"),
    CONFIG_SERVICE_ERROR("C0140", "配置服务出错"),
    NETWORK_RESOURCE_SERVICE_ERROR("C0150", "网络资源服务出错"),
    VPN_SERVICE_ERROR("C0151", "VPN服务出错"),
    CDN_SERVICE_ERROR("C0152", "CDN服务出错"),
    DNS_SERVICE_ERROR("C0153", "域名解析服务出错"),
    GATEWAY_SERVICE_ERROR("C0154", "网关服务出错"),
    THIRD_PARTY_EXECUTION_TIMEOUT("C0200", "第三方系统执行超时"),
    RPC_EXECUTION_TIMEOUT("C0210", "RPC执行超时"),
    MESSAGE_DELIVERY_TIMEOUT("C0220", "消息投递超时"),
    CACHE_SERVICE_TIMEOUT("C0230", "缓存服务超时"),
    CONFIG_SERVICE_TIMEOUT("C0240", "配置服务超时"),
    DATABASE_SERVICE_TIMEOUT("C0250", "数据库服务超时"),
    DATABASE_SERVICE_ERROR("C0300", "数据库服务出错"),
    TABLE_NOT_FOUND("C0311", "表不存在"),
    COLUMN_NOT_FOUND("C0312", "列不存在"),
    AMBIGUOUS_COLUMN_IN_JOIN("C0321", "多表关联中存在多个相同名称的列"),
    DATABASE_DEADLOCK("C0331", "数据库死锁"),
    PRIMARY_KEY_CONFLICT("C0341", "主键冲突"),
    THIRD_PARTY_DISASTER_RECOVERY_TRIGGERED("C0400", "第三方容灾系统被触发"),
    THIRD_PARTY_RATE_LIMITED("C0401", "第三方系统限流"),
    THIRD_PARTY_DEGRADED("C0402", "第三方功能降级"),
    NOTIFICATION_SERVICE_ERROR("C0500", "通知服务出错"),
    SMS_NOTIFICATION_FAILED("C0501", "短信提醒服务失败"),
    VOICE_NOTIFICATION_FAILED("C0502", "语音提醒服务失败"),
    EMAIL_NOTIFICATION_FAILED("C0503", "邮件提醒服务失败");


    /**
     * 返回码编码，例如 {@code A0210}。
     */
    private final String code;

    /**
     * 返回码默认文案。
     */
    private final String message;

    ReturnCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
