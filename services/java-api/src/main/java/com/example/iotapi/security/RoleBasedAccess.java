package com.example.iotapi.security;

import com.example.iotapi.model.UserRole;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * Utility bean for role-based method security checks.
 * 
 * Usage example in a service method:
 * 
 * {@code
 * @PreAuthorize("@roleBasedAccess.hasRole('ADMIN')")
 * public void deleteDevice(String deviceId) { ... }
 * }
 */
@Component("roleBasedAccess")
public class RoleBasedAccess {

    public boolean hasRole(String role) {
        return org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication()
                .getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    public boolean isAdmin() {
        return hasRole(UserRole.ADMIN.name());
    }

    public boolean isOperator() {
        return hasRole(UserRole.OPERATOR.name());
    }

    public boolean isViewer() {
        return hasRole(UserRole.VIEWER.name());
    }
}
