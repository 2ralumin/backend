package com.guide.run.user.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.guide.run.event.service.EventService;
import com.guide.run.global.cookie.service.CookieService;
import com.guide.run.global.exception.auth.authorize.NotValidRefreshTokenException;
import com.guide.run.global.jwt.JwtProvider;
import com.guide.run.user.dto.ReissuedAccessTokenDto;
import com.guide.run.user.dto.request.*;
import com.guide.run.user.dto.response.FindAccountIdDto;
import com.guide.run.user.dto.response.TokenResponse;
import com.guide.run.user.service.LoginInfoService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;


@CrossOrigin(origins = {"https://dev.guiderun.org", "https://guiderun.org","https://www.guiderun.org", "http://localhost:3000", "http://localhost:8080"},
        maxAge = 3600,
        allowCredentials = "true")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class LoginInfoController {
    private final LoginInfoService loginInfoService;
    private final JwtProvider jwtProvider;
    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    //인증번호 요청(아이디 찾기)
    @PostMapping("/sms/accountId")
    public ResponseEntity<String> getNumberForAccountId(@RequestBody PhoneNumberRequest request) throws UnsupportedEncodingException, URISyntaxException, NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        loginInfoService.getNumberForAccountId(request.getPhoneNum());
        return ResponseEntity.ok("");
    }

    //인증번호 요청(비밀번호 재설정)
    @PostMapping("/sms/password")
    public ResponseEntity<String> getNumberForPassword(@RequestBody AccountIdPhoneRequest request)
            throws UnsupportedEncodingException, URISyntaxException, NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        loginInfoService.getNumberForPassword(request);
        return ResponseEntity.ok("");
    }

    //인증번호 확인(아이디 찾기, 비밀번호 재설정용 토큰 발급)
    @PostMapping("/sms/token")
    public ResponseEntity<TokenResponse> getToken(@RequestBody AuthNumRequest request){
        return ResponseEntity.ok(loginInfoService.getToken(request.getNumber()));
    }

    //아이디 찾기
    @PostMapping("/accountId")
    public ResponseEntity<FindAccountIdDto> findAccountId(@RequestBody TmpTokenDto tmpTokenDto){
        return ResponseEntity.ok(loginInfoService.findAccountId(tmpTokenDto.getToken()));
    }
    //비밀번호 재설정
    @PatchMapping("/new-password")
    public ResponseEntity<String> createNewPassword(@RequestBody NewPasswordDto newPasswordDto){
        loginInfoService.createNewPassword(newPasswordDto.getToken(),newPasswordDto.getNewPassword());
        return ResponseEntity.ok("");
    }
    @PostMapping("/logout")
    public ResponseEntity<String> createNewPassword(HttpServletRequest request, HttpServletResponse response){
        String privateId = jwtProvider.extractUserId(request);
        if(request.getCookies() !=null){
            for(Cookie cookie: request.getCookies()){
                if(cookie.getName().equals("refreshToken")){
                    Cookie removedCookie = new Cookie("refreshToken", null);
                    removedCookie.setMaxAge(0);
                    response.addCookie(removedCookie);
                }
            }
        }
        log.error("refresh 토큰이 없습니다. privateId :" + jwtProvider.extractUserId(request));
        throw new NotValidRefreshTokenException();
    }

}
