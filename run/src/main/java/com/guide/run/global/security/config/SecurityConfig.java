package com.guide.run.global.security.config;

import com.guide.run.global.jwt.JwtAuthenticationFilter;
import com.guide.run.global.jwt.JwtExceptionFilter;
import com.guide.run.global.jwt.JwtProvider;
import com.guide.run.user.entity.type.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;


@RequiredArgsConstructor
@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {
    private final JwtProvider jwtProvider;
    @Value("${cors.origin}")
    private String origin;

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }



    @Bean
    public WebSecurityCustomizer webSecurityCustomizer(){
        return web -> {
            web.ignoring()
                    .requestMatchers("/healty")
                    .requestMatchers("/favicon.ico")
                    .requestMatchers("/member-upload")
                    .requestMatchers("/event-upload")
                    .requestMatchers("/attendance-upload")
                    .requestMatchers("/api/oauth/**")
                    .requestMatchers("/api/sms/**")
                    .requestMatchers("/api/accountId")
                    .requestMatchers("/api/new-password")
                    .requestMatchers("/tmp/**")
                    .requestMatchers("/api/login");
        };
    }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)throws Exception{
        http
                .csrf(csrf->csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(httpSecuritySessionManagementConfigurer ->
                        httpSecuritySessionManagementConfigurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests((authz) -> authz
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/signup/**").hasRole("NEW")
                        .requestMatchers("/api/oauth/login/reissue").hasAnyRole("ADMIN", "USER", "COACH", "WAIT", "REJECT")
                        .requestMatchers(
                                "/api/sms/**",
                                "/api/oauth/**",
                                "/api/login/**",
                                "/api/logout/**",
                                "/api/accountId/**",
                                "/api/new-password/**").permitAll()
                        .requestMatchers(
                                "/api/user/personal/**",
                                "/api/user/permission/**",
                                "/api/user/running/**",
                                "/api/withdrawal/**",
                                "/api/user/img/**")
                        .hasAnyRole("ADMIN", "USER", "COACH", "WAIT", "REJECT")

                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "USER", "COACH")

                        .anyRequest().permitAll())

                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtExceptionFilter(),JwtAuthenticationFilter.class);



        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.addAllowedOrigin(origin);
        config.addAllowedMethod("*"); // 모든 메소드 허용.
        config.addAllowedHeader("*");
        config.setMaxAge(3600L);
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
