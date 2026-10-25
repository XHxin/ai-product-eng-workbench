package com.aiworkbench.backend.integration;

import com.aiworkbench.backend.service.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Service
public class EndpointSafetyService {
    private static final String REQUIRED_SCHEME = "https";
    private static final byte IPV6_UNIQUE_LOCAL_PREFIX = (byte) 0xfc;

    public void validate(URI endpoint) {
        if (endpoint == null || endpoint.getScheme() == null || !REQUIRED_SCHEME.equalsIgnoreCase(endpoint.getScheme())) {
            throw new BusinessException("UNSAFE_ENDPOINT", "集成地址必须使用HTTPS", HttpStatus.BAD_REQUEST);
        }
        String host = endpoint.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException("UNSAFE_ENDPOINT", "集成地址缺少主机名", HttpStatus.BAD_REQUEST);
        }
        if (endpoint.getUserInfo() != null) {
            throw new BusinessException("UNSAFE_ENDPOINT", "集成地址不能包含凭证", HttpStatus.BAD_REQUEST);
        }
        InetAddress[] resolvedAddresses;
        try {
            resolvedAddresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new BusinessException("UNSAFE_ENDPOINT", "集成地址无法解析", HttpStatus.BAD_REQUEST);
        }
        for (InetAddress address : resolvedAddresses) {
            if (isPrivate(address)) {
                throw new BusinessException("UNSAFE_ENDPOINT", "集成地址不允许访问内网地址", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private boolean isPrivate(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] rawAddress = address.getAddress();
        if (address instanceof Inet6Address && (rawAddress[0] & 0xfe) == IPV6_UNIQUE_LOCAL_PREFIX) {
            return true;
        }
        return false;
    }
}
