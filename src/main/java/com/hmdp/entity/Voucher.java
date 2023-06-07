package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_voucher")
public class Voucher implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(notes = "主键", name = "id")
    private Long id;

    /**
     * 商铺id
     */
    @ApiModelProperty(notes = "商铺id", name = "shopId")
    private Long shopId;

    /**
     * 代金券标题
     */
    @ApiModelProperty(notes = "代金券标题", name = "title")
    private String title;

    /**
     * 副标题
     */
    @ApiModelProperty(notes = "副标题", name = "subTitle")
    private String subTitle;

    /**
     * 使用规则
     */
    @ApiModelProperty(notes = "使用规则", name = "rules")
    private String rules;

    /**
     * 支付金额
     */
    @ApiModelProperty(notes = "支付金额", name = "payValue")
    private Long payValue;

    /**
     * 抵扣金额
     */
    @ApiModelProperty(notes = "抵扣金额", name = "actualValue")
    private Long actualValue;

    /**
     * 优惠券类型
     */
    @ApiModelProperty(notes = "优惠券类型", name = "type")
    private Integer type;

    /**
     * 优惠券类型
     */
    @ApiModelProperty(notes = "优惠券类型", name = "status")
    private Integer status;

    /**
     * 库存
     */
    @ApiModelProperty(notes = "库存", name = "stock")
    @TableField(exist = false)
    private Integer stock;

    /**
     * 生效时间
     */
    @ApiModelProperty(notes = "生效时间", name = "beginTime")
    @TableField(exist = false)
    private LocalDateTime beginTime;

    /**
     * 失效时间
     */
    @ApiModelProperty(notes = "失效时间", name = "endTime")
    @TableField(exist = false)
    private LocalDateTime endTime;

    /**
     * 创建时间
     */
    @ApiModelProperty(notes = "创建时间", name = "createTime")
    private LocalDateTime createTime;


    /**
     * 更新时间
     */
    @ApiModelProperty(notes = "更新时间", name = "updateTime")
    private LocalDateTime updateTime;


}
