package com.kellen.utils.excel;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.kellen.utils.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * excel导出工具类
 *
 * @author 孙凯伦
 * @Date 2021-08-17
 */
@Slf4j
public class ExcelExportUtil {
    /**
     * 导出excel工具类
     *
     * @param list       导出数据
     * @param title      文件名称
     * @param header     文件头名称
     * @param columnName 文件列名
     * @param width      宽度
     * @param response   void
     */
    public static void excelExport(List<?> list, String title, String[] header, String[] columnName, int width, HttpServletResponse response) {
        //获取输出流

        try (ExcelWriter excelWriter = ExcelUtil.getBigWriter()) {
            doExcel(list, title, header, columnName, width, excelWriter);
            response.reset();
            response.setContentType("application/vnd.ms-excel;charset=utf-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(title, "UTF-8") + ".xlsx");
            //中文名称需要特殊处理
            excelWriter.flush(response.getOutputStream());
        } catch (Exception e) {
            log.error("【导出报表】异常：{}", e.getMessage());
            throw new BusinessException("【导出报表】异常" + e.getMessage());
        }

    }

    /**
     * excel数据处理
     *
     * @param list       导出数据
     * @param title      文件名称
     * @param header     文件头名称
     * @param columnName 文件列名
     */
    private static void doExcel(List<?> list, String title, String[] header, String[] columnName, int width, ExcelWriter excelWriter) {
        excelWriter.merge(header.length - 1, title);
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < columnName.length; i++) {
            excelWriter.addHeaderAlias(columnName[i], header[i]);
            map.put(header[i], columnName[i]);
            excelWriter.setColumnWidth(i, width<=0?24:width);
        }
        //不加会出现多一个id列
        excelWriter.setOnlyAlias(true);
        excelWriter.setFreezePane(2);
        if (CollectionUtils.isNotEmpty(list)) {
            excelWriter.write(list, true);
        } else {
            //没有数据对时候打印头跟标题
            excelWriter.writeRow(map.keySet(), true);
        }

    }
}
