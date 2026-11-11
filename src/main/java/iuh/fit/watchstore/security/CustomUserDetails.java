package iuh.fit.watchstore.security;

import iuh.fit.watchstore.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CustomUserDetails implements UserDetails, OAuth2User, OidcUser {

    @Getter
    private final User user;
    private final Map<String, Object> attributes;
    private final Set<GrantedAuthority> authorities;
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;

    public CustomUserDetails(User user) {
        this(user, Collections.emptyMap(), null, null, null);
    }

    public CustomUserDetails(User user, Map<String, Object> attributes) {
        this(user, attributes, null, null, null);
    }

    public CustomUserDetails(User user, Map<String, Object> attributes, Collection<? extends GrantedAuthority> authorities) {
        this(user, attributes, authorities, null, null);
    }

    public CustomUserDetails(User user,
                             Map<String, Object> attributes,
                             Collection<? extends GrantedAuthority> extraAuthorities,
                             OidcIdToken idToken,
                             OidcUserInfo userInfo) {
        this.user = user;
        this.attributes = attributes != null ? attributes : Collections.emptyMap();
        this.idToken = idToken;
        this.userInfo = userInfo;

        Set<GrantedAuthority> mergedAuthorities = new HashSet<>();
        mergedAuthorities.add(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        if (user != null && user.getRole() != null) {
            mergedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        }
        if (extraAuthorities != null) {
            mergedAuthorities.addAll(extraAuthorities);
        }
        this.authorities = Collections.unmodifiableSet(mergedAuthorities);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes != null ? attributes : Collections.emptyMap();
    }

    @Override
    public Map<String, Object> getClaims() {
        return getAttributes();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    @Override
    public String getName() {
        if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName().trim();
        }
        if (attributes != null && attributes.get("name") != null && !String.valueOf(attributes.get("name")).isBlank()) {
            return String.valueOf(attributes.get("name")).trim();
        }
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim();
        }
        if (attributes != null && attributes.get("email") != null && !String.valueOf(attributes.get("email")).isBlank()) {
            return String.valueOf(attributes.get("email")).trim();
        }
        return "Tài khoản";
    }

    public String getFullName() {
        return getName();
    }

    public String getAvatar() {
        if (user != null && user.getAvatar() != null && !user.getAvatar().isBlank()) {
            return user.getAvatar().trim();
        }
        if (attributes != null && attributes.get("picture") != null && !String.valueOf(attributes.get("picture")).isBlank()) {
            return String.valueOf(attributes.get("picture")).trim();
        }
        return null;
    }

    public String getEmail() {
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim();
        }
        if (attributes != null && attributes.get("email") != null && !String.valueOf(attributes.get("email")).isBlank()) {
            return String.valueOf(attributes.get("email")).trim();
        }
        return "";
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return (user != null && user.getPassword() != null) ? user.getPassword() : "";
    }

    @Override
    public String getUsername() {
        return getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        if (user == null) {
            return true;
        }
        return user.getStatus() == null || user.getStatus();
    }
}
