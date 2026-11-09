package iuh.fit.watchstore.security;

import iuh.fit.watchstore.entity.User;
import iuh.fit.watchstore.entity.enums.Role;
import iuh.fit.watchstore.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OidcUserService oidcUserService = new OidcUserService();

    public CustomOAuth2UserService(UserRepository userRepository, @Lazy PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        return processOAuth2User(oauth2User, null, null);
    }

    public OidcUser loadOidcUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = oidcUserService.loadUser(userRequest);
        return processOAuth2User(oidcUser, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }

    private CustomUserDetails processOAuth2User(OAuth2User oauth2User, OidcIdToken idToken, OidcUserInfo userInfo) {
        Map<String, Object> attributes = oauth2User.getAttributes();

        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String picture = oauth2User.getAttribute("picture");

        if (email == null || email.trim().isEmpty()) {
            throw new OAuth2AuthenticationException("Không thể xác thực: Không nhận được Email từ Google.");
        }

        final String cleanEmail = email.trim();
        User user = userRepository.findByEmail(cleanEmail).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(cleanEmail);
            newUser.setFullName(name != null && !name.trim().isEmpty() ? name.trim() : cleanEmail.split("@")[0]);
            newUser.setAvatar(picture != null && !picture.trim().isEmpty() ? picture.trim() : "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200");
            newUser.setRole(Role.CUSTOMER);
            newUser.setStatus(true);
            newUser.setPhone("");
            newUser.setAddress("");
            newUser.setProvince("");
            newUser.setDistrict("");
            newUser.setWard("");
            newUser.setStreetAddress("");
            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            return userRepository.save(newUser);
        });

        // Ensure role, status, fullName, and avatar are properly populated for existing users
        boolean needUpdate = false;
        if (user.getRole() == null) {
            user.setRole(Role.CUSTOMER);
            needUpdate = true;
        }
        if (user.getStatus() == null || !Boolean.TRUE.equals(user.getStatus())) {
            user.setStatus(true);
            needUpdate = true;
        }
        if ((user.getAvatar() == null || user.getAvatar().trim().isEmpty()) && picture != null && !picture.trim().isEmpty()) {
            user.setAvatar(picture.trim());
            needUpdate = true;
        }
        if ((user.getFullName() == null || user.getFullName().trim().isEmpty()) && name != null && !name.trim().isEmpty()) {
            user.setFullName(name.trim());
            needUpdate = true;
        }
        if (needUpdate) {
            user = userRepository.save(user);
        }

        // Build authorities set explicitly containing ROLE_CUSTOMER and existing OAuth2 authorities
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        if (user.getRole() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        }
        if (oauth2User.getAuthorities() != null) {
            authorities.addAll(oauth2User.getAuthorities());
        }

        return new CustomUserDetails(user, attributes, authorities, idToken, userInfo);
    }
}
