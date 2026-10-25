package com.aiworkbench.backend.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(PasswordEncoder encoder, Environment environment) {
        String password = environment.acceptsProfiles(Profiles.of("dev","test"))
            ? environment.getProperty("auth.dev-password", "dev-password-change-me") : environment.getRequiredProperty("APP_ACCOUNT_PASSWORD");
        return new InMemoryUserDetailsManager(
            User.withUsername("admin").password(encoder.encode(password)).roles("ADMIN").build(),
            User.withUsername("pm").password(encoder.encode(password)).roles("PM").build(),
            User.withUsername("dev").password(encoder.encode(password)).roles("DEV").build(),
            User.withUsername("release").password(encoder.encode(password)).roles("RELEASE_MANAGER").build());
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.requestMatchers("/api/auth/csrf", "/api/auth/login").permitAll().anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint((request,response,exception) ->
                writeJson(response, 401, "UNAUTHENTICATED", "请先登录")
            ).accessDeniedHandler((request,response,exception) ->
                writeJson(response, 403, "FORBIDDEN", exception instanceof CsrfException
                    ? "页面安全令牌已失效，请刷新页面后重试"
                    : "当前账号没有该操作权限，请联系管理员")
            ))
            .formLogin(login -> login.loginProcessingUrl("/api/auth/login").successHandler((request,response,authentication) -> response.setStatus(204)).failureHandler((request,response,exception) -> response.sendError(401)))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").logoutSuccessHandler((request,response,authentication) -> response.setStatus(204)))
            .build();
    }

    private void writeJson(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
