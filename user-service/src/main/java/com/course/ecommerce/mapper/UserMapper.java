package com.course.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.ecommerce.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问层
 *
 * 继承 BaseMapper 后自动拥有：insert、deleteById、updateById、selectById 等常用方法。
 * 自定义查询（如按用户名查）写在 UserMapper.xml 里。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按用户名查询用户
     * SQL 写在 src/main/resources/mapper/UserMapper.xml
     */
    User findByUsername(String username);
}
