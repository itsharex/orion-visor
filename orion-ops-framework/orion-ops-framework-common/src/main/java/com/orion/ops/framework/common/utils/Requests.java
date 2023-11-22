package com.orion.ops.framework.common.utils;

import com.orion.ops.framework.common.entity.RequestIdentity;
import com.orion.web.servlet.web.Servlets;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * 请求工具类
 *
 * @author Jiahang Li
 * @version 1.0.0
 * @since 2023/11/2 16:26
 */
public class Requests {

    private Requests() {
    }

    /**
     * 填充请求身份信息
     *
     * @param identity identity
     */
    public static void fillIdentity(RequestIdentity identity) {
        Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .map(s -> (ServletRequestAttributes) s)
                .map(ServletRequestAttributes::getRequest)
                .ifPresent(request -> {
                    String address = Servlets.getRemoteAddr(request);
                    identity.setAddress(address);
                    identity.setLocation(IpUtils.getLocation(address));
                    identity.setUserAgent(Servlets.getUserAgent(request));
                });
    }

}
