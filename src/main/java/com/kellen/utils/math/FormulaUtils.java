package com.kellen.utils.math;

import com.googlecode.aviator.AviatorEvaluator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 通用公式计算工具。
 *
 * <p>本工具只保留表达式执行、BigDecimal 转换、金额计算、费率按天折算等通用能力，
 * 不承载佣金、保险、项目、企业等业务专属公式。</p>
 *
 * @author 孙凯伦
 */
public final class FormulaUtils {

    /**
     * 默认金额小数位。
     */
    public static final int DEFAULT_MONEY_SCALE = 2;

    /**
     * 默认费率小数位。
     */
    public static final int DEFAULT_RATE_SCALE = 5;

    /**
     * 默认一年天数。
     */
    public static final BigDecimal DEFAULT_YEAR_DAYS = BigDecimal.valueOf(365);

    /**
     * 工具类不允许实例化。
     */
    private FormulaUtils() {
        // 工具类只提供静态方法，私有构造方法防止外部创建无状态对象。
    }

    /**
     * 执行 Aviator 表达式并返回原始结果。
     *
     * @param expression Aviator 表达式，例如 {@code amount * rate}
     * @param variables  表达式变量，key 为变量名，value 为变量值
     * @return 表达式执行后的原始结果
     */
    public static Object evaluate(String expression, Map<String, ?> variables) {
        Objects.requireNonNull(expression, "公式表达式不能为空"); // 表达式是计算入口，缺失时直接失败。
        Map<String, Object> safeVariables = toSafeVariables(variables); // 复制变量，避免调用方 Map 被表达式执行过程意外影响。
        return AviatorEvaluator.execute(expression, safeVariables); // 使用 Aviator 执行通用表达式。
    }

    /**
     * 执行 Aviator 表达式并转换为 BigDecimal。
     *
     * @param expression Aviator 表达式
     * @param variables  表达式变量
     * @return BigDecimal 计算结果
     */
    public static BigDecimal evaluateToBigDecimal(String expression, Map<String, ?> variables) {
        Object result = evaluate(expression, variables); // 先执行表达式，保留统一的参数校验和变量复制逻辑。
        return toBigDecimal(result); // 将 Aviator 返回值转换成金额和费率计算更稳定的 BigDecimal。
    }

    /**
     * 执行 Aviator 表达式并按指定小数位取整。
     *
     * @param expression   Aviator 表达式
     * @param variables    表达式变量
     * @param scale        小数位
     * @param roundingMode 取整方式
     * @return 取整后的 BigDecimal 计算结果
     */
    public static BigDecimal evaluateToBigDecimal(String expression, Map<String, ?> variables, int scale, RoundingMode roundingMode) {
        BigDecimal result = evaluateToBigDecimal(expression, variables); // 先得到未取整的 BigDecimal 结果。
        return scale(result, scale, roundingMode); // 再统一执行小数位处理。
    }

    /**
     * 按金额和费率计算金额结果。
     *
     * @param amount 金额基数
     * @param rate   费率
     * @return 默认保留 2 位小数的金额结果
     */
    public static BigDecimal multiplyMoney(BigDecimal amount, BigDecimal rate) {
        return multiply(amount, rate, DEFAULT_MONEY_SCALE, RoundingMode.HALF_UP); // 金额默认四舍五入保留两位。
    }

    /**
     * 按金额和费率计算金额结果。
     *
     * @param amount       金额基数
     * @param rate         费率
     * @param scale        小数位
     * @param roundingMode 取整方式
     * @return 取整后的金额结果
     */
    public static BigDecimal multiply(BigDecimal amount, BigDecimal rate, int scale, RoundingMode roundingMode) {
        Objects.requireNonNull(amount, "金额不能为空"); // 金额是乘法计算左操作数，缺失时直接失败。
        Objects.requireNonNull(rate, "费率不能为空"); // 费率是乘法计算右操作数，缺失时直接失败。
        BigDecimal result = amount.multiply(rate); // 使用 BigDecimal 乘法避免 double 精度问题。
        return scale(result, scale, roundingMode); // 统一处理小数位和取整方式。
    }

    /**
     * 按天数折算年费率。
     *
     * @param annualRate 年费率
     * @param days       天数
     * @return 默认保留 5 位小数的折算费率
     */
    public static BigDecimal annualRateByDays(BigDecimal annualRate, Integer days) {
        return annualRateByDays(annualRate, days, DEFAULT_RATE_SCALE, RoundingMode.HALF_UP); // 费率默认四舍五入保留五位。
    }

    /**
     * 按天数折算年费率。
     *
     * @param annualRate   年费率
     * @param days         天数
     * @param scale        小数位
     * @param roundingMode 取整方式
     * @return 取整后的折算费率
     */
    public static BigDecimal annualRateByDays(BigDecimal annualRate, Integer days, int scale, RoundingMode roundingMode) {
        Objects.requireNonNull(annualRate, "年费率不能为空"); // 年费率是折算基础，缺失时直接失败。
        Objects.requireNonNull(days, "天数不能为空"); // 天数是折算周期，缺失时直接失败。
        BigDecimal dayCount = BigDecimal.valueOf(days); // 将天数转换为 BigDecimal，保持后续运算类型一致。
        BigDecimal result = dayCount.multiply(annualRate).divide(DEFAULT_YEAR_DAYS, scale + 4, roundingMode); // 先用更高精度计算中间结果。
        return scale(result, scale, roundingMode); // 再按调用方要求输出最终精度。
    }

    /**
     * 对 BigDecimal 按指定小数位取整。
     *
     * @param value        原始值
     * @param scale        小数位
     * @param roundingMode 取整方式
     * @return 取整后的值
     */
    public static BigDecimal scale(BigDecimal value, int scale, RoundingMode roundingMode) {
        Objects.requireNonNull(value, "数值不能为空"); // 被取整值不能为空。
        Objects.requireNonNull(roundingMode, "取整方式不能为空"); // 取整方式不能为空，避免 JDK 抛出不清晰异常。
        return value.setScale(scale, roundingMode); // 使用 BigDecimal 原生取整能力。
    }

    /**
     * 将对象转换为 BigDecimal。
     *
     * @param value 原始值
     * @return BigDecimal 值
     */
    public static BigDecimal toBigDecimal(Object value) {
        Objects.requireNonNull(value, "数值不能为空"); // 空值无法明确转换为 BigDecimal。
        if (value instanceof BigDecimal bigDecimal) { // BigDecimal 类型直接返回，避免字符串转换损失语义。
            return bigDecimal; // 返回原始 BigDecimal。
        }
        if (value instanceof Number number) { // Number 类型统一使用字符串构造，避免 double 二进制精度问题。
            return new BigDecimal(number.toString()); // 通过字符串转换成 BigDecimal。
        }
        return new BigDecimal(value.toString()); // 其他类型按字符串解析，解析失败由 BigDecimal 抛出明确异常。
    }

    /**
     * 构建表达式变量 Map。
     *
     * @param key   第一个变量名
     * @param value 第一个变量值
     * @return 可继续追加变量的 Map
     */
    public static Map<String, Object> variables(String key, Object value) {
        Map<String, Object> variables = new HashMap<>(); // 创建可变 Map，方便调用方继续 put 变量。
        variables.put(key, value); // 写入第一个变量。
        return variables; // 返回变量 Map。
    }

    /**
     * 将外部变量复制为安全 Map。
     *
     * @param variables 外部变量
     * @return 表达式执行使用的变量 Map
     */
    private static Map<String, Object> toSafeVariables(Map<String, ?> variables) {
        if (variables == null || variables.isEmpty()) { // 允许无变量表达式，例如常量公式。
            return Collections.emptyMap(); // 返回不可变空 Map，避免创建多余对象。
        }
        return new HashMap<>(variables); // 复制变量，隔离调用方传入对象。
    }
}
