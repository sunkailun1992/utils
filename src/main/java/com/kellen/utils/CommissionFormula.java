package com.kellen.utils;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import com.google.common.collect.Maps;
import com.googlecode.aviator.AviatorEvaluator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName Formula
 * @Description 公式类
 * @Author 孙凯伦
 * @Email 376253703@qq.com
 * @Time 2021/6/9 10:35 上午
 */
public class CommissionFormula {
    /**
     * 计算含税比例
     */
    public static final String COMPANY_TAX_INCLUDED_PROPORTION = "companyProportion/1.06";
    /**
     * 计算含不税比例
     */
    public static final String COMPANY_PROPORTION = "companyProportion/1.06/(1+taxRate)";
    /**
     * 个人比例制分佣
     */
    public static final String INDIVIDUAL_PROPORTIONAL_SYSTEM = "actualCompanyProportion*individualProportion";
    /**
     * 留点制分佣（税点渠道承担）（含税）
     */
    public static final String CHANNEL_BEARING_TAX_INCLUDED_TAX = "actualCompanyProportion-individualProportion";
    /**
     * 留点制分佣（税点渠道承担）（不含税）
     */
    public static final String CHANNEL_BEARING_TAX_EXEMPTION_TAX = "actualCompanyProportion-individualProportion/(1+taxRate)";
    /**
     * 留点制分佣（税点公司承担）（含税）
     */
    public static final String COMPANY_BEARING_TAX_INCLUDED_TAX = "companyProportion-individualProportion";
    /**
     * 留点制分佣（税点公司承担）（不含税）
     */
    public static final String COMPANY_BEARING_TAX_EXEMPTION_TAX = "companyProportion/(1+taxRate)-individualProportion/(1+taxRate)";
    /**
     * 费率位数
     */
    public static final Integer RATE = 4;
    /**
     * 金额位数
     */
    public static final Integer MONEY = 2;


    /**
     * TODO 计算公司佣金比例
     *
     * @param proportion  比例
     * @param taxIncluded 是否含税
     * @param taxRate     含税比例
     * @return java.math.BigDecimal
     * @author 孙凯伦
     * @methodName companyProportion
     * @time 2023/3/13 9:34 AM
     */
    public static BigDecimal company(BigDecimal proportion, Boolean taxIncluded, BigDecimal taxRate) {
        if (!taxIncluded) {
            if (taxRate == null) {
                throw new NullPointerException("含税费率为空");
            }
        }
        Map<String, Object> paramMap = Maps.newHashMap();
        paramMap.put("companyProportion", proportion);
        paramMap.put("taxRate", taxRate);
        //判断是否含税
        if (taxIncluded) {
            return Convert.toBigDecimal(AviatorEvaluator.execute(CommissionFormula.COMPANY_TAX_INCLUDED_PROPORTION, paramMap)).setScale(RATE, RoundingMode.HALF_UP);
        } else {
            return Convert.toBigDecimal(AviatorEvaluator.execute(CommissionFormula.COMPANY_PROPORTION, paramMap)).setScale(RATE, RoundingMode.HALF_UP);
        }
    }


    /**
     * TODO 计算公司佣金金额
     *
     * @param payMoney    金额
     * @param proportion  比例
     * @param taxIncluded 是否含税
     * @param taxRate     含税比例
     * @return java.util.Map<java.lang.String, java.math.BigDecimal>
     * @author 孙凯伦
     * @methodName companyMoney
     * @time 2023/3/13 10:11 AM
     */
    public static Map<String, BigDecimal> company(BigDecimal payMoney, BigDecimal proportion, Boolean taxIncluded, BigDecimal taxRate) {
        Map<String, BigDecimal> map = Maps.newHashMap();
        //公司佣金比例
        BigDecimal companyProportion = company(proportion, taxIncluded, taxRate);
        //返回参数
        map.put("companyProportion", companyProportion);
        map.put("companyMoney", payMoney.multiply(companyProportion).setScale(MONEY, RoundingMode.HALF_UP));
        return map;
    }

    /**
     * TODO 计算个人佣金金额
     *
     * @param payMoney             保费
     * @param companyProportion    公司比例
     * @param taxIncluded          是否含税
     * @param taxRate              含税比例
     * @param type                 类型
     * @param individualProportion 个人比例
     * @return java.util.Map<java.lang.String, java.math.BigDecimal>
     * @author 孙凯伦
     * @methodName individual
     * @time 2023/3/13 4:16 PM
     */
    public static Map<String, BigDecimal> individual(BigDecimal payMoney, BigDecimal companyProportion, Boolean taxIncluded, BigDecimal taxRate, Integer type, BigDecimal individualProportion) {
        //返回集合
        Map<String, BigDecimal> map = Maps.newHashMap();
        //公司佣金结算结果
        Map<String, BigDecimal> company = company(payMoney, companyProportion, taxIncluded, taxRate);
        //公司比例
        BigDecimal actualCompanyProportion = Convert.toBigDecimal(company.get("companyProportion"));
        map.put("companyProportion", actualCompanyProportion);
        //公司佣金
        BigDecimal companyMoney = Convert.toBigDecimal(company.get("companyMoney"));
        map.put("companyMoney", companyMoney);
        if (type == 0) {
            //比例制分佣
            proportionalSystem(payMoney, individualProportion, map, actualCompanyProportion);
        } else if (type == 1) {
            //留点制分佣（税点渠道承担）
            channelBearingTax(payMoney, taxIncluded, taxRate, individualProportion, map, actualCompanyProportion);
        } else if (type == 2) {
            //留点制分佣（税点公司承担）
            companyBearingTax(payMoney, companyProportion, taxIncluded, taxRate, individualProportion, map);
        } else if (type == 3) {
            //固定比例制分佣
            BigDecimal individualMoney = payMoney.multiply(individualProportion).setScale(MONEY, RoundingMode.HALF_UP);
            map.put("individualProportion", individualProportion);
            map.put("individualMoney", individualMoney);
        } else {
            throw new NullPointerException("佣金类型为空");
        }
        if (Convert.toBigDecimal(map.get("individualMoney")).compareTo(new BigDecimal(0)) < 1) {
            map.put("individualMoney", new BigDecimal(0));
        }
        return map;
    }


    /**
     * TODO 留点制分佣（税点公司承担）
     *
     * @param payMoney
     * @param companyProportion
     * @param taxIncluded
     * @param taxRate
     * @param individualProportion
     * @param map
     * @return void
     * @author 孙凯伦
     * @methodName companyBearingTax
     * @time 2023/3/13 5:45 PM
     */
    private static void companyBearingTax(BigDecimal payMoney, BigDecimal companyProportion, Boolean taxIncluded, BigDecimal taxRate, BigDecimal individualProportion, Map<String, BigDecimal> map) {
        //公式参数
        Map<String, Object> paramMap = MapUtil.builder(new HashMap<String, Object>())
                .put("companyProportion", companyProportion)
                .put("individualProportion", individualProportion)
                .put("taxRate", taxRate)
                .build();
        if (taxIncluded) {
            //实际个人比例
            BigDecimal actualIndividualProportion = Convert.toBigDecimal(AviatorEvaluator.execute(CommissionFormula.COMPANY_BEARING_TAX_INCLUDED_TAX, paramMap)).setScale(RATE, RoundingMode.HALF_UP);
            map.put("individualProportion", actualIndividualProportion);
            //个人佣金
            BigDecimal individualMoney = payMoney.multiply(actualIndividualProportion).setScale(MONEY, RoundingMode.HALF_UP);
            map.put("individualMoney", individualMoney);
        } else {
            //实际个人比例
            BigDecimal actualIndividualProportion = Convert.toBigDecimal(AviatorEvaluator.execute(CommissionFormula.COMPANY_BEARING_TAX_EXEMPTION_TAX, paramMap)).setScale(RATE, RoundingMode.HALF_UP);
            map.put("individualProportion", actualIndividualProportion);
            //个人佣金
            BigDecimal individualMoney = payMoney.multiply(actualIndividualProportion).setScale(MONEY, RoundingMode.HALF_UP);
            map.put("individualMoney", individualMoney);
        }
    }


    /**
     * TODO 公司固定留点--税点渠道承担
     *
     * @param payMoney
     * @param taxIncluded
     * @param taxRate
     * @param individualProportion
     * @param map
     * @param actualCompanyProportion
     * @return void
     * @author 孙凯伦
     * @methodName channelBearingTax
     * @time 2023/3/13 5:43 PM
     */
    private static void channelBearingTax(BigDecimal payMoney, Boolean taxIncluded, BigDecimal taxRate, BigDecimal individualProportion, Map<String, BigDecimal> map, BigDecimal actualCompanyProportion) {
        //公式参数
        Map<String, Object> paramMap = MapUtil.builder(new HashMap<String, Object>())
                .put("actualCompanyProportion", actualCompanyProportion)
                .put("individualProportion", individualProportion)
                .put("taxRate", taxRate)
                .build();
        if (taxIncluded) {
            //实际个人比例
            BigDecimal actualIndividualProportion = Convert.toBigDecimal(AviatorEvaluator.execute(CommissionFormula.CHANNEL_BEARING_TAX_INCLUDED_TAX, paramMap)).setScale(RATE, RoundingMode.HALF_UP);
            map.put("individualProportion", actualIndividualProportion);
            //个人佣金
            BigDecimal individualMoney = payMoney.multiply(actualIndividualProportion).setScale(MONEY, RoundingMode.HALF_UP);
            map.put("individualMoney", individualMoney);
        } else {
            //实际个人比例
            BigDecimal actualIndividualProportion = Convert.toBigDecimal(AviatorEvaluator.execute(CommissionFormula.CHANNEL_BEARING_TAX_EXEMPTION_TAX, paramMap)).setScale(RATE, RoundingMode.HALF_UP);
            map.put("individualProportion", actualIndividualProportion);
            //个人佣金
            BigDecimal individualMoney = payMoney.multiply(actualIndividualProportion).setScale(MONEY, RoundingMode.HALF_UP);
            map.put("individualMoney", individualMoney);
        }
    }


    /**
     * TODO 比例制分佣
     *
     * @param payMoney
     * @param individualProportion
     * @param map
     * @param actualCompanyProportion
     * @return void
     * @author 孙凯伦
     * @methodName proportionalSystem
     * @time 2023/3/13 5:44 PM
     */
    private static void proportionalSystem(BigDecimal payMoney, BigDecimal individualProportion, Map<String, BigDecimal> map, BigDecimal actualCompanyProportion) {
        //公式参数
        Map<String, Object> paramMap = MapUtil.builder(new HashMap<String, Object>())
                .put("actualCompanyProportion", actualCompanyProportion)
                .put("individualProportion", individualProportion)
                .build();
        //实际个人比例
        BigDecimal actualIndividualProportion = Convert.toBigDecimal(AviatorEvaluator.execute(CommissionFormula.INDIVIDUAL_PROPORTIONAL_SYSTEM, paramMap)).setScale(RATE, RoundingMode.HALF_UP);
        map.put("individualProportion", actualIndividualProportion);
        //个人佣金
        BigDecimal individualMoney = payMoney.multiply(actualIndividualProportion).setScale(MONEY, RoundingMode.HALF_UP);
        map.put("individualMoney", individualMoney);
    }

    public static void main(String[] args) {
        Map<String, BigDecimal> map = individual(new BigDecimal(100), new BigDecimal(0.4), true, new BigDecimal(0.06), 3, new BigDecimal(0.05));
        System.out.printf(JsonUtil.json(map));
    }
}
