package com.course.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.ecommerce.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存数据访问层
 */
@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {
}
