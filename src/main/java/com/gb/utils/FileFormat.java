package com.gb.utils;

/**
 * 文件判断工具类
 * @author sunkailun
 * @DateTime 2019/1/30  5:55 PM
 * @email 376253703@qq.com
 * 
 * @explain
 */
public class FileFormat {
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
        if("png".equals(format)){
            return true;
        }else if("jpg".equals(format)){
            return true;
        }else if("jpeg".equals(format)){
            return true;
        }else if("bmp".equals(format)){
            return true;
        }else if("gif".equals(format)){
            return true;
        }else if("webp".equals(format)){
            return true;
        }else if("psd".equals(format)){
            return true;
        }else if("svg".equals(format)){
            return true;
        }else if("tiff".equals(format)){
            return true;
        }
        return false;
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
        if("txt".equals(format)){
            return true;
        }else if("doc".equals(format)){
            return true;
        }else if("docx".equals(format)){
            return true;
        }else if("xls".equals(format)){
            return true;
        }else if("xlsx".equals(format)){
            return true;
        }else if("ppt".equals(format)){
            return true;
        }else if("pptx".equals(format)){
            return true;
        }else if("pdf".equals(format)){
            return true;
        }
        return false;
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
        if("mkv".equals(format)){
            return true;
        }else if("rm".equals(format)){
            return true;
        }else if("rmvb".equals(format)){
            return true;
        }else if("wmv".equals(format)){
            return true;
        }else if("avi".equals(format)){
            return true;
        }else if("mp4".equals(format)){
            return true;
        }else if("flv".equals(format)){
            return true;
        }
        return false;
    }
}
