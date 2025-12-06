package com.stylemate.config;

import com.stylemate.config.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;

    // ✅ 1️⃣ 정적 리소스는 시큐리티 필터 제외
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().antMatchers(
            "/favicon.ico",
            "/css/**", "/js/**", "/images/**", "/img/**", "/webjars/**", "/uploads/**"
        );
    }

    // ✅ 2️⃣ 로그인 성공 시 리디렉션 핸들러
    @Bean
    public AuthenticationSuccessHandler authSuccessHandler() {
        return (request, response, authentication) ->
            response.sendRedirect(request.getContextPath() + "/home");
    }

    // ✅ 3️⃣ 핵심 보안 설정
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable() // (나중에 CSRF 토큰 추가 시 enable 권장)
            .authorizeRequests()
                // 🔓 비회원 접근 허용
                .antMatchers(
                    "/", "/error",
                    "/user/login", "/login",
                    "/user/register", "/user/join"
                ).permitAll()

                // 🔓 네이버 이미지 API는 공개 (검색용)
                .antMatchers("/api/images/**").permitAll()

                // 🔒 프로필 및 피팅룸은 로그인 필요
                .antMatchers("/user/profile/**").authenticated()
                .antMatchers("/fittingroom/**").authenticated()

                // 🔒 나머지 페이지들도 기본적으로 로그인 필요
                .anyRequest().authenticated()
            .and()

            // ✅ 로그인 설정
            .formLogin()
                .loginPage("/user/login")           // GET 로그인 페이지
                .loginProcessingUrl("/user/login")  // POST 로그인 처리
                .usernameParameter("email")         // input name="email"
                .passwordParameter("password")      // input name="password"
                .defaultSuccessUrl("/home", true)   // 로그인 성공 후 홈 이동
                .failureUrl("/user/login?error")
                .permitAll()
            .and()

            // ✅ 로그아웃 설정
            .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/user/login?logout")
                .permitAll();

        return http.build();
    }

    // ✅ 4️⃣ AuthenticationManager (Spring Security 내부 인증)
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.userDetailsService(customUserDetailsService).passwordEncoder(passwordEncoder());
        return builder.build();
    }

    // ✅ 5️⃣ 비밀번호 암호화
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
