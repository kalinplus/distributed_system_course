package com.course.ecommerce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceIdInterceptorTest {

    @Test
    @DisplayName("uses explicit instance id when configured")
    void preHandle_usesExplicitInstanceIdWhenConfigured() throws Exception {
        InstanceIdInterceptor interceptor = new InstanceIdInterceptor();
        ReflectionTestUtils.setField(interceptor, "port", "8082");
        ReflectionTestUtils.setField(interceptor, "instanceId", "product-service-2");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        assertThat(response.getHeader("X-Instance-Id")).isEqualTo("product-service-2");
    }
}
