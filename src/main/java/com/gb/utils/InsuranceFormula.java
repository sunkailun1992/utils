package com.gb.utils;

import cn.hutool.core.convert.Convert;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.googlecode.aviator.AviatorEvaluator;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * @ClassName Formula
 * @Description 公式类
 * @Author 孙凯伦
 * @Email 376253703@qq.com
 * @Time 2021/6/9 10:35 上午
 */
public class InsuranceFormula {
    /**
     * 保费计算
     */
    public static final String INSURANCE_FORMULA = "amount*rate";
    /**
     * 年费率
     */
    public static final String DAY_RATE = "days*rate/365";
    /**
     * 费率位数
     */
    public static final Integer NEW_SCALE = 5;

    /**
     * @param rate
     * @param days
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: quarter
     * @description: TODO  季度计算
     * @return: java.math.BigDecimal
     * @date: 2021/6/8 6:51 下午
     */
    public static BigDecimal quarter(BigDecimal rate, Integer days) {
        Map<String, Object> paramMap = Maps.newHashMap();
        paramMap.put("rate", rate);
        paramMap.put("days", days);
        BigDecimal a = Convert.toBigDecimal(AviatorEvaluator.execute("days/91.25", paramMap)).setScale(0, BigDecimal.ROUND_UP);
        paramMap.put("countingDays", a);
        BigDecimal b = Convert.toBigDecimal(AviatorEvaluator.execute("countingDays*rate/4", paramMap)).setScale(NEW_SCALE, BigDecimal.ROUND_HALF_UP);
        return b;
    }

    /**
     * @param rate
     * @param days
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: month
     * @description: TODO  月计算
     * @return: java.math.BigDecimal
     * @date: 2021/6/8 6:49 下午
     */
    public static BigDecimal month(BigDecimal rate, Integer days) {
        Map<String, Object> paramMap = Maps.newHashMap();
        paramMap.put("rate", rate);
        paramMap.put("days", days);
        BigDecimal a = Convert.toBigDecimal(AviatorEvaluator.execute("days/30.4166666666667", paramMap)).setScale(0, BigDecimal.ROUND_UP);
        paramMap.put("countingDays", a);
        BigDecimal b = Convert.toBigDecimal(AviatorEvaluator.execute("countingDays*rate/12", paramMap)).setScale(NEW_SCALE, BigDecimal.ROUND_HALF_UP);
        return b;
    }

    /**
     * @param rate
     * @param days
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: day
     * @description: TODO  日计算
     * @return: java.math.BigDecimal
     * @date: 2021/6/8 6:49 下午
     */
    public static BigDecimal day(BigDecimal rate, Integer days) {
        Map<String, Object> paramMap = Maps.newHashMap();
        paramMap.put("rate", rate);
        paramMap.put("days", days);
        return Convert.toBigDecimal(AviatorEvaluator.execute(InsuranceFormula.DAY_RATE, paramMap)).setScale(NEW_SCALE, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * @param amount
     * @param rate
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: payMoney
     * @description: TODO  计算费率
     * @return: java.math.BigDecimal
     * @date: 2021/6/9 10:48 上午
     */
    public static BigDecimal payMoney(BigDecimal amount, BigDecimal rate) {
        Map<String, Object> paramMap = Maps.newHashMap();
        paramMap.put("amount", amount);
        paramMap.put("rate", rate);
        BigDecimal money = Convert.toBigDecimal(AviatorEvaluator.execute(InsuranceFormula.INSURANCE_FORMULA, paramMap));
        return money.setScale(2, BigDecimal.ROUND_HALF_UP);
    }


    /**
     * TODO 报价费率计算
     *
     * @param amount             保证金金额
     * @param date               工期
     * @param rate               基准费率
     * @param additionalRateList 附加费率
     * @param limitAmount        限制金额
     * @return java.math.BigDecimal
     * @author 孙凯伦
     * @methodName quotationCalculatedRate
     * @time 2023/2/6 11:41 AM
     */
    public static BigDecimal quotationCalculatedRate(BigDecimal amount, Integer date, BigDecimal rate, List<BigDecimal> additionalRateList, BigDecimal limitAmount) {
        //基础参数
        Map<String, Object> paramMap = Maps.newHashMap();
        paramMap.put("amount", amount);
        paramMap.put("date", date);
        paramMap.put("rate", rate);
        //附加费率
        String additionalRate = "";
        for (BigDecimal bigDecimal : additionalRateList) {
            if (StringUtils.isBlank(additionalRate)) {
                additionalRate += "*(" + bigDecimal;
            } else {
                additionalRate += "+" + bigDecimal;
            }
        }
        if (StringUtils.isNotBlank(additionalRate)) {
            additionalRate += ")";
        }
        //计算公式
        BigDecimal result = Convert.toBigDecimal(AviatorEvaluator.execute("amount*date*rate+100" + additionalRate, paramMap)).setScale(2, BigDecimal.ROUND_HALF_UP);
        //判断限制金额
        if (limitAmount != null) {
            Integer integer = result.compareTo(limitAmount);
            if (integer == 1 || integer == 0) {
                return limitAmount;
            }
        }
        return result;
    }
}
