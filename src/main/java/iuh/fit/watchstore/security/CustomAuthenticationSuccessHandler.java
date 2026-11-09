package iuh.fit.watchstore.security;

import iuh.fit.watchstore.entity.enums.Role;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (authentication == null) {
            response.sendRedirect("/");
            return;
        }

        Object principal = authentication.getPrincipal();

        // 1. Safe check CustomUserDetails
        if (principal instanceof CustomUserDetails customUserDetails) {
            if (customUserDetails.getUser() != null && customUserDetails.getUser().getRole() == Role.ADMIN) {
                response.sendRedirect("/admin/dashboard");
                return;
            } else {
                response.sendRedirect("/");
                return;
            }
        }

        // 2. Safe check GrantedAuthorities
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities != null) {
            for (GrantedAuthority authority : authorities) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    response.sendRedirect("/admin/dashboard");
                    return;
                } else if ("ROLE_CUSTOMER".equals(authority.getAuthority())) {
                    response.sendRedirect("/");
                    return;
                }
            }
        }

        // 3. Safe check OAuth2User (defaults to Customer)
        if (principal instanceof OAuth2User) {
            response.sendRedirect("/");
            return;
        }

        // Default redirect
        response.sendRedirect("/");
    }
}
