package com.gb.utils.enumeration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.gb.utils.enumeration.WordCodeTypeEnum.WORD_ENTERPRISE;
import static com.gb.utils.enumeration.WordCodeTypeEnum.WORD_PROJECT;

/**
 * @author: ranyang
 * @Date: 2021/3/15 10:41
 * @descript: 字段编码属性枚举
 */
@Getter
@NoArgsConstructor
@Slf4j
public enum WordCodeEnum {
    /*保险txtstart*/
    INSURANCE_MONEY("insurance_money", "保险金额"),
    INSURANCE_DAY("insurance_day", "保障天数"),
    INSURANCE_TIME("insurance_time", "起保日期"),
    INSURANCE_RATE("insurance_rate", "保险费率"),
    PAY_MONEY("pay_money", "保费金额"),
    END_TIME("end_time", "终保日期"),
    /*------------------------------------------------------------------------保险txt___end------------------------------------------------------------------------*/

    /*保险file___start*/
    CAST_INSURANCE_SINGLE("cast_insurance_single", "投保单"),
    COUNTER_GUARANTEE("counter_guarantee", "反担保"),
    /*------------------------------------------------------------------------保险file___end------------------------------------------------------------------------*/

    /*项目txtstart*/
    PROJECT_CODE("xmbh", "项目编号", WORD_PROJECT),
    PROJECT_NAME("project_name", "项目名称", WORD_PROJECT),
    BID_OPEN_TIME("bid_open_time", "开标时间", WORD_PROJECT),
    TENDER_AMOUNT("tender_amount", "保证金金额", WORD_PROJECT),
    BDBH("bdbh", "标段编号"),
    BDMC("bdmc", "标段名称"),
    PROJECT_ADD("project_add", "项目地址"),
    ZBR("zbr", "招标人"),
    ZBLXR("zbrlxr", "招标联系人"),
    ZBRPHONE("zbr_phone", "招标人联系电话"),
    TENDEREE_CODE("tenderee_code", "招标人统一社会信用代码"),
    TENDERDAY("tender_day", "投标有效期"),
    ZBRDZ("zbrdz", "招标人地址"),
    START_DATE("start_date", "计划开工日期"),
    END_DATE("end_date", "计划竣工日期"),
    PROJECY_TIME("project_time", "工期"),
    CLOSE_TIME("close_time", "保证金缴纳截止时间"),
    CONTRACT_NO("contract_no", "工程合同编号"),
    CONTRACT_DATE("contract_date", "合同签订日期"),
    PROJECT_DOCUMENT("project_document", "立项文号"),
    PROJECT_CONT("project_cont", "项目内容"),
    PROJECT_CONST_NATURE("project_const_nature", "项目建设性质", true),
    EG_COST("eg_cost", "工程造价"),
    PROJECT_AMOUNT_WAGE_DEPOSIT_MIGRANT_WORKERS("project_amount_wage_deposit_migrant_workers", "农民工工资保证金金额"),
    PROJECT_AMOUNT_PERFORMANCE_BOND("project_amount_performance_bond", "履约保证金金额"),
    PROJECT_AMOUNT_OF_BID_BOND("project_amount_of_bid_Bond", "投标保证金金额"),
    PROJECT_CONSTRUCTION_IN_UNIT("project_construction_in_unit", "施工单位"),
    PROJECT_CONSTRUCTION_UNIT("project_construction_unit", "建设单位"),
    PROJECT_POSSESSION("project_possession", "项目属地"),
    BID_DATE("bid_date", "投标日期"),
    CONTRACTING_TYPE("contracting_type", "反担保承包方式", true),
    PROJECT_TYPES("project_types", "工程类型", true),
    CONSTRUCTION_CONTRACT_AMOUNT("construction_contract_amount", "施工合同金额"),
    GUARANTEE_RECEIVER("guarantee_receiver", "保函接收方"),
    DELAYED_PROJECT("delayed_project","延期项目"),
    CONTRACT_SIGNING_STATUS("contract_signing_status","合同签署情况"),
    STARTING_STATUS("starting_status","开工状态"),
    RENEWAL_PROGRAM("renewal_program","续保项目"),

    //G-3.3.1
    APPROVED_EFFECTIVE_DATE("approved_effective_date","核定生效日期"),
    APPROVED_EXPIRATION_DATE("approved_expiration_date","核定到期日期"),
    APPROVED_INSURANCE_DAYS("approved_insurance_days","核定投保天数"),
    //G-3.3.5
    ATTENTION_LINE("attention_line","经办人姓名"),
    CONTACT_NUMBER_OF_HANDLING_PERSON("contact_number_of_handling_person","经办人联系电话"),
    //临时需求
    PRO_TYPE("pro_type", "项目类型"),
    //临时需求
    CONSTRUCTION_PERSONNEL("construction_personnel","施工人数"),
    // 国寿财 农民工需求
    INVESTOR_TYPE("investor_type","项目投资方类型"),

    /*------------------------------------------------------------------------项目txt___end------------------------------------------------------------------------*/

    /*------------------------------------------------------------------------项目file___start------------------------------------------------------------------------*/
    BIDDING_DOCUMENT("bidding_document", "招标文件"),
    BIDDING_NOTICE("bidding_notice", "中标通知书"),
    CONSTRUCTION_CONTRACT("construction_contract", "施工合同"),
    WORKERS("workers", "农民工花名册"),
    SCHEDULE_FORM("schedule_form", "用工计划表"),
    OW_STRUCTURE_CERTIFICATE("ow_structure_certificate", "股权结构证明"),
    RISK_TABLE("risk_table", "风控单"),
    RISK_REPORT("risk_report", "风控报告"),
    AUTHED_LICENCE("authed_licence", "被授权经办人身份证"),
    ANTI_GUARANTEE_DOC("anti_guarantee_doc", "反担保人身份证及资产证明"),
    CASH_DEPOSIT_DOC("cash_deposit_doc", "保证金缴纳情况表"),
    EMPLOYMENT_CONTRACT("employment_contract", "劳务合同"),
    PLAN_OF_LABOR_WORK("plan_of_labor_work", "劳务用工计划"),
    BILL_OF_QUANTITIES("bill_of_quantities", "工程量清单"),
    APPROVAL_DOCUMENTS("approval_documents", "项目立项批复文件"),
    STATE_OWNED_CONSTRUCTION_LAND_USE_CERTIFICATE("state_owned_construction_land_use_certificate", "国有建设用地使用证"),
    CONSTRUCTION_LAND_PLANNING_PERMIT("construction_land_planning_permit", "建设用地规划许可证"),
    PLANNING_LICENSE("planning_license", "建设工程规划许可证"),
    CONSTRUCTION_PERMIT("construction_permit", "建设工程施工许可证"),
    LETTER_OF_ACCEPTANCE("letter_of_acceptance", "投保人承诺函"),
    FIELD_PHOTOS_OF_THE_PROJECT("field_photos_of_the_project", "项目实地照片"),
    PAYMENT_APPROVE("payment_approve", "缴纳审批单"),
    NOTICE_OF_DEPOSIT_PAYMENT("notice_of_deposit_payment","保证金支付通知书"),
    OTHER("other",  "其他"),
    FUNDING_SOURCE("funding_source","资金来源说明"),
    COMPLETION_ACCEPTANCE_REPORT("completion_acceptance_report","竣工验收报告"),
    ENGINEERING_PROGRESS_CONFIRMATION_FORM("engineering_progress_confirmation_form","工程进度监理确认表"),
    DESIGN_AND_CONSTRUCTION_DRAWINGS("design_and_construction_drawings","设计或施工图纸"),
    CONSTRUCTION_ARRANGEMENT_PLAN("construction_arrangement_plan","施工安排计划"),
    CONSTRUCTION_ORGANIZATION_DESIGN("construction_organization_design","施工组织设计"),
    HYDROGEOLOGY_ROUTE_MAP_PLAN("hydrogeology_route_map_plan","水文地质线路图或平面图"),
    DESIGN_DATA("design_data","设计资料"),
    DESIGN_CONTRACT_AND_DESIGN_SPECIFICATION("design_contract_and_design_specification","设计合同及设计说明书"),
    LIST_OF_DESIGN_PROJECTS("list_of_design_projects","追溯期内设计项目清单"),


    /*------------------------------------------------------------------------项目file__end------------------------------------------------------------------------*/

    /*企业*/
    /*------------------------------------------------------------------------企业txt___start------------------------------------------------------------------------*/
    ENTERPRISE_CODE("credit_code", "统一社会信用代码"),
    ENTERPRISE_NAME("enterpris_name", "企业名称"),
    REG_ADDRESS("Reg_address", "注册地址"),
    LEGAL_PERSON("legal_person", "法定代表人"),
    MOBILE("mobile", "联系电话"),
    FAREN_CODE("faren_code", "法定代表人身份证号"),
    ENTERPRISES_ECONOMIC_NATURE("enterprises_economic_nature", "企业经济性质", true),
    CS_QUALIFICATION_LEVEL("cs_qualification_level", "施工资质等级", true),
    BUSINESS_ADDRESS("Business_address", "经营地址"),
    NATURE_BUSINESS("Nature_Business", "经营范围"),
    ORGAINZE_CODE("Organization_code", "组织机构代码"),
    REGISTRATION_AUTHORITY("registration_authority", "登记机关"),
    PERSONNEL_SIZE("Personnel_size", "人员规模"),
    INDUSTRY("Industry", "所属行业"),
    CONTACTS("contacts", "联系人"),
    //CONTACT_NUMBER("contact_number", "联系电话"),
    REGISTERED_CAPITAL("registered_capital", "注册资本"),
    OWNERSHIP_STRUCTURE("ownership_structure", "股权结构"),
    DETAIL_ADDRESS("Detail_address", "详细地址"),
    BUSINESS_STATUS("Business_status", "营业状态"),
    REG_CAPITAL("reg_capital", "注册资本"),
    NATURE("nature", "性质"),
    APPLICANT_BUSINESS("applicant_business", "投保人企业性质"),
    BRIEF_INTRODUCTION("brief_introduction", "简介"),
    CONTACT_ADDRESS("contact_address", "联系地址"),
    BUSINESS_LIFE("business_life", "企业经营年限"),
    TRAVEL_AGENCY_RATING("travel_agency_rating", "旅行社评级"),
    BUSINESS_SCOPE("business_scope", "经营业务范围"),
    AMOUNT_OF_WARRANTY_REQUIRED("amount_of_warranty_required", "所需质保金额度"),
    NUMBER_OF_DOMESTIC_BRANCHES("number_of_domestic_branches", "国内分社数量"),
    NUMBER_OF_OVERSEAS_BRANCHES("number_of_overseas_branches", "境外分社数量"),
    ADDRESS_CODE("address_code", "地址"),
    POLICY_HOLDER_TYPE("policy_holder_type", "投保人类型"),
    EXECUTED_INFORMATION_NUMBER("executed_information_number", "失信记录"),
    LIMIT_HIGH_CONSUMPTION("limit_high_consumption", "限制高消费记录"),
    ADMINISTRATIVE_PENALTY("administrative_penalty", "行政处罚"),
    THREE_YEARS_BROKEN_PROMISES("three_years_broken_promises", "近三年失信"),
    THREE_YEARS_BE_EXECUTED("three_years_be_executed", "近三年被执行"),
    BLACKLIST("blacklist", "黑名单结果"),
    EXEMPTION_SAFETY_SUPERVISION_REQUIREMENTS("exemption_safety_supervision_requirements","免安监要求"),
    THIRD_PARTY_LIABILITY_INSURANCE("third_party_liability_insurance","第三者责任险"),
    EMPLOYMENT_INJURY_INSURANCE("employment_injury_insurance","工伤保险"),
    INTENDED_UNDERWRITERS("intended_underwriters","意向承保机构"),
    MACHINE_INSURANCE_PLAN("machine_insurance_plan","保险方案"),
    EMPLOYEE_TYPE_OF_WORK("employee_type_of_work","雇员工种"),
    SUBITEM_INSURANCE_AMOUNT("subitem_insurance_amount","分项保险金额"),
    DEDUCTIBLE("deductible","免赔额"),
    LIMIT_OF_THIRD_PARTY_LIABILITY_COMPENSATION("limit_of_third_party_liability_compensation","第三者责任赔偿限额"),
    EXCESS("excess","免赔率"),
    DEDUCTIBLE_THIRD_PARTY_LIABILITY_COMPENSATION_RATE("deductible_third_party_liability_compensation_rate","第三者责任赔偿率"),
    KINSHIP("kinship","亲属关系"),
    SEX("sex","性别"),
    BENEFICIARY("beneficiary","受益人"),
    HAVE_SOCIAL_SECURITY("have_social_security","是否有社保"),
    INSURANCE_SOURCE("insurance_source","投保来源"),
    /*------------------------------------------------------------------------企业txt___end------------------------------------------------------------------------*/

    /*------------------------------------------------------------------------企业file___start------------------------------------------------------------------------*/
    CREDIT_REPORT_INFO("enterprise_credit_investigation_report_info", "近3个月内企业征信报告"),
    FINANCIAL_REPORT_INFO("financial_audit_report_info", "近2年完整的审计报告"),
    FINANCIAL_ONE_REPORT("financial_reports_one_year_info", "近一年及近期财务报告"),
    CORPORATE_ID_CARD_INFO("corporate_id_card_info", "法定代表人身份证信息"),
    CORPORATE_ID_CARD_SIDE("corporate_id_card_info_side", "法定代表人身份证反面"),
    CORPORATE_ID_CARD_INFO_FRONT("corporate_id_card_info_front", "法定代表人身份证正面"),
    PROJECT_PERFORMAMCE_LIST_INFO("project_performance_list_info", "企业近2年业绩清单"),
    BUSINESS_LICENSE_INFO("business_license_info", "营业执照信息"),
    ENTERPRISE__EVOLUTION_FORM_INFO("enterprise_evolution_form_info", "企业沿革表"),
    POWER_ATTORNEY_INFO("power_attorney_info", "法定代表人授权书"),
    ARTICLES_OF_ASSOCIATION_INFO("articles_of_association_info", "公司章程/简介信息"),
    QUALIFE_CERT_INFO("qualife_cert_info", "资质等级证书信息"),
    BANK_FLOW_INFO("bank_flow_info", "银行流水"),
    TAX_CERT_ONE_YEAR_INFO("tax_cert_one_year_info", "近一年纳税证明"),
    ENTERPRISE_QUALIFE_FORM_INFO("enterprise_qualife_form_info", "企业资质表"),
    RELEVANT_ASSERT_CERTIFICATE_INFO("relevant_asset_certificate_info", "相关资产证明"),
    SAFETY_PERMIT("safety_permit", "安全许可证"),
    INSURANCE_CAST("insurance_cast", "投保单"),
    COUNTERGUARANTEE_AGREEMENT("counterguarantee_agreement", "反担保协议"),
    PERMIT("permit", "开户许可证"),
    FINANCE_REPORT("finance_report", "最近1期的财务报表"),
    TAX_REPORT("tax_report", "近两年纳税证明"),
    SHAREHOLDER("shareholder", "股东会决议"),
    AUTH_REPORT("auth_report", "授权书"),
    INDICTMENT("indictment","起诉状"),
    PRESERVATION_APPLICATION_FORM("preservation_application_form","保全申请书"),
    PRESERVATION_OF_PROPERTY_CLUES("preservation_of_property_clues","保全财产线索"),
    LIST_OF_EVIDENCE("list_of_evidence","证据目录"),
    EVIDENCE_MATERIAL("evidence_material","证据材料"),
    COMMITMENT_LETTER("commitment_letter","承诺函"),
    CERTIFICATE_OF_NO_ACCIDENT("certificate_of_no_accident","未出险证明"),
    GUARANTEE_FORMAT("guarantee_format","保函格式"),
    PERSONNEL_LIST("personnel_list","人员清单"),


    /*------------------------------------------------------------------------企业file___end------------------------------------------------------------------------*/

    NOT_FOUND("not_found", "未知类型");

    private String code;
    private String desc;
    private WordCodeTypeEnum type = WORD_ENTERPRISE;
    /**
     * 特殊类型（多选，单选，下拉）
     */
    private Boolean specialType;

    WordCodeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    WordCodeEnum(String code, String desc, Boolean specialType) {
        this.code = code;
        this.desc = desc;
        this.specialType = specialType;
    }

    WordCodeEnum(String code, String desc, WordCodeTypeEnum wordProject) {
        this.code = code;
        this.desc = desc;
        this.type = wordProject;
    }

    public static List<String> getAllCodeByType(WordCodeTypeEnum type) {
        return Arrays.stream(WordCodeEnum.values()).filter(x -> type.equals(x.getType())).map(WordCodeEnum :: getCode).collect(Collectors.toList());
    }

    public static WordCodeEnum getByCode(String code) {
        if (StringUtils.isBlank(code)) {
            log.debug("根据【code：{}】获取对应的【企业字段/附件】或【项目字段/附件】信息，code值为空", code);
            return NOT_FOUND;
        }
        Optional<WordCodeEnum> codeEnum = Arrays.stream(WordCodeEnum.values()).filter(x -> code.equals(x.getCode())).findFirst();
        return codeEnum.orElse(NOT_FOUND);
    }

}
