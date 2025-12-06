package com.stylemate.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    // ✅ 로그인 페이지 매핑
    @GetMapping("/login")
    public String login() {
        // /templates/user/login.html 로 연결
        return "user/login";
    }

}
