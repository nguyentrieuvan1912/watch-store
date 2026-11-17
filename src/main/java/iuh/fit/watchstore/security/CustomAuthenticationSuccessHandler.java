package iuh.fit.watchstore.security;

import iuh.fit.watchstore.entity.User;
import iuh.fit.watchstore.entity.enums.Role;
import iuh.fit.watchstore.repository.UserRepository;
import iuh.fit.watchstore.service.CartService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final CartService cartService;
    private final UserRepository userRepository;

    public CustomAuthenticationSuccessHandler(CartService cartService, UserRepository userRepository) {
        this.cartService = cartService;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (authentication == null) {
            response.sendRedirect("/");
            return;
        }

        // Hợp nhất giỏ hàng tạm (Guest Session) vào giỏ hàng DB ngay khi đăng nhập thành công
        Long userId = resolveUserId(authentication);
        HttpSession session = request.getSession(false);
        if (userId != null && session != null) {
            cartService.mergeGuestCartOnLogin(session, userId);
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

    private Long resolveUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails customUserDetails && customUserDetails.getUser() != null) {
            return customUserDetails.getUser().getId();
        }
        String email = null;
        if (principal instanceof OAuth2User oauth2User) {
            email = oauth2User.getAttribute("email");
        }
        if (email == null || email.isBlank()) {
            email = authentication.getName();
        }
        if (email != null && !email.isBlank()) {
            return userRepository.findByEmail(email.trim()).map(User::getId).orElse(null);
        }
        return null;
    }
}
