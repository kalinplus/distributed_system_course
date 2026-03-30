package com.course.ecommerce.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(0)
public class ReadOnlyRoutingAspect {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyRoutingAspect.class);

    @Around("@annotation(ReadOnly)")
    public Object routeToSlave(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            DataSourceContextHolder.setDataSource(DataSourceNames.SLAVE);
            log.debug("[READ-SLAVE] routing to slave: {}", joinPoint.getSignature().getName());
            return joinPoint.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }
}
