package com.course.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存实体，对应 t_inventory 表
 */
@Data
@TableName("t_inventory")
public class Inventory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private Integer stock;

    /** 预留数量 */
    private Integer reservedStock;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
