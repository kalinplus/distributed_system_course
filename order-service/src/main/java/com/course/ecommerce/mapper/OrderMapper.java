package com.course.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.ecommerce.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单数据访问层
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 根据状态和创建时间查询订单
     *
     * @param status    订单状态
     * @param before    创建时间之前
     * @return 订单列表
     */
    @Select("SELECT * FROM t_order WHERE status = #{status} AND created_at < #{before}")
    List<Order> selectByStatusAndCreatedBefore(@Param("status") String status, @Param("before") LocalDateTime before);
}
