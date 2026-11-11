package iuh.fit.watchstore.config;

import iuh.fit.watchstore.security.CustomAuthenticationSuccessHandler;
import iuh.fit.watchstore.security.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler successHandler;
    private final CustomOAuth2UserService customOAuth2UserService;

    public SecurityConfig(UserDetailsService userDetailsService, 
                          CustomAuthenticationSuccessHandler successHandler,
                          CustomOAuth2UserService customOAuth2UserService) {
        this.userDetailsService = userDetailsService;
        this.successHandler = successHandler;
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/compare/api/**", "/cart/api/**", "/api/chat/**", "/api/recommendations/**", "/api/tracking/**", "/checkout/api/**"))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/checkout/api/**", "/tracking/**", "/api/tracking/**").permitAll()
                .requestMatchers("/profile/**", "/orders/**", "/checkout/**", "/customer/**").authenticated()
                .requestMatchers("/", "/products/**", "/about", "/contact", "/size-guide", "/cart/**", "/compare/**", "/api/chat/**", "/api/recommendations/**", "/login", "/register", "/css/**", "/js/**", "/images/**", "/uploads/**", "/oauth2/**", "/login/oauth2/**").permitAll()
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(successHandler)
                .permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .successHandler(successHandler)
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                    .oidcUserService(customOAuth2UserService::loadOidcUser)
                )
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .authenticationProvider(authenticationProvider());

        return http.build();
    }
}
