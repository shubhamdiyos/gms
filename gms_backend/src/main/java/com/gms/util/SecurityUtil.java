package com.gms.util;

import com.gms.security.JwtTokenProvider;
import com.gms.security.SecurityConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Security utility class for extracting token information.
 * Follows the established pattern: SecurityUtil.getEmpIdFromToken(), SecurityUtil.getSchoolIdFromToken()
 */
@Component
public class SecurityUtil {
    
    private static JwtTokenProvider jwtTokenProvider;
    
    @Autowired
    public void setJwtTokenProvider(JwtTokenProvider jwtTokenProvider) {
        SecurityUtil.jwtTokenProvider = jwtTokenProvider;
    }
    
    /**
     * Gets the current HTTP request
     */
    private static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest();
    }
    
    /**
     * Extracts Bearer token from Authorization header
     */
    private static String extractTokenFromRequest() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }
        
        String authHeader = request.getHeader(SecurityConstants.AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return authHeader.substring(SecurityConstants.BEARER_PREFIX.length());
        }
        return null;
    }
    
    /**
     * Gets employee ID from JWT token
     * Usage: Integer empId = SecurityUtil.getEmpIdFromToken();
     */
    public static Integer getEmpIdFromToken() {
        String token = extractTokenFromRequest();
        if (token != null && jwtTokenProvider != null) {
            return jwtTokenProvider.getEmployeeId(token);
        }
        return null;
    }
    
    /**
     * Gets school ID from JWT token (equivalent to companyId in reference)
     * Usage: Integer schoolId = SecurityUtil.getSchoolIdFromToken();
     */
    public static Integer getSchoolIdFromToken() {
        String token = extractTokenFromRequest();
        if (token != null && jwtTokenProvider != null) {
            return jwtTokenProvider.getSchoolId(token);
        }
        return null;
    }
    
    /**
     * Gets username from JWT token
     */
    public static String getUsernameFromToken() {
        String token = extractTokenFromRequest();
        if (token != null && jwtTokenProvider != null) {
            return jwtTokenProvider.getUsername(token);
        }
        return null;
    }
    
    /**
     * Gets roles from JWT token
     */
    public static java.util.Set<String> getRolesFromToken() {
        String token = extractTokenFromRequest();
        if (token != null && jwtTokenProvider != null) {
            return jwtTokenProvider.getRoles(token);
        }
        return null;
    }
}
