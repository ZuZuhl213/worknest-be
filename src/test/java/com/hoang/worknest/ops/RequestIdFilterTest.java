package com.hoang.worknest.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.slf4j.MDC;

class RequestIdFilterTest {
    @Test
    void propagatesValidRequestIdAndClearsMdc() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "trace_123");
        var response = new MockHttpServletResponse();

        new RequestIdFilter().doFilter(request, response, new MockFilterChain());

        assertEquals("trace_123", response.getHeader("X-Request-Id"));
        assertFalse(MDC.getCopyOfContextMap() != null && MDC.getCopyOfContextMap().containsKey("requestId"));
    }
}
