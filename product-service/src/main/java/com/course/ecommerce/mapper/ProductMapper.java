package com.course.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.ecommerce.config.ReadOnly;
import com.course.ecommerce.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 商品数据访问层
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @ReadOnly
    @Select("SELECT * FROM t_product WHERE id = #{id}")
    Product selectById(Long id);
}
