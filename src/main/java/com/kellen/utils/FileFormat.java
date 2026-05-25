package com.kellen.utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import java.util.Set;

/**
 * 文件判断工具类
 * @author sunkailun
 * @DateTime 2019/1/30  5:55 PM
 * @email 376253703@qq.com
 * 
 * @explain
 */
public class FileFormat {

    private static final Set<String> PICTURE_FORMATS = CollUtil.newHashSet("png", "jpg", "jpeg", "bmp", "gif", "webp", "psd", "svg", "tiff");

    private static final Set<String> DOCUMENT_FORMATS = CollUtil.newHashSet("txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf");

    private static final Set<String> VIDEO_FORMATS = CollUtil.newHashSet("mkv", "rm", "rmvb", "wmv", "avi", "mp4", "flv");

    /**
     * 图片判断
     * @author      sunkailun
     * @DateTime    2019/1/31  10:16 AM
     * @email       376253703@qq.com
     * 
     * @param format:
     * @return      java.lang.Boolean
     */
    public static Boolean picture(String format){
        return PICTURE_FORMATS.contains(normalize(format));
    }
    /**
     * 文档判断
     * @author      sunkailun
     * @DateTime    2019/1/31  10:16 AM
     * @email       376253703@qq.com
     * 
     * @param format:
     * @return      java.lang.Boolean
     */
    public static Boolean document(String format){
        return DOCUMENT_FORMATS.contains(normalize(format));
    }

    /**
     * 视频判断
     * @author      sunkailun
     * @DateTime    2019/1/31  10:16 AM
     * @email       376253703@qq.com
     * 
     * @param format:
     * @return      java.lang.Boolean
     */
    public static Boolean video(String format){
        return VIDEO_FORMATS.contains(normalize(format));
    }

    private static String normalize(String format) {
        return StrUtil.removePrefix(StrUtil.trimToEmpty(format), ".").toLowerCase();
    }
}
