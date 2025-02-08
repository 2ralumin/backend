package com.guide.run.user.controller;

import com.guide.run.global.cookie.service.CookieService;
import com.guide.run.global.exception.auth.authorize.NotValidRefreshTokenException;
import com.guide.run.global.exception.user.dto.DuplicatedUserIdException;
import com.guide.run.global.jwt.JwtProvider;
import com.guide.run.global.sms.cool.CoolSmsService;
import com.guide.run.user.dto.GuideSignupDto;
import com.guide.run.user.dto.ReissuedAccessTokenDto;
import com.guide.run.user.dto.ViSignupDto;
import com.guide.run.user.dto.request.AccountIdDto;
import com.guide.run.user.dto.request.GeneralLoginRequest;
import com.guide.run.user.dto.request.RefreshTokenDto;
import com.guide.run.user.dto.request.WithdrawalRequest;
import com.guide.run.user.dto.response.ATAInfo;
import com.guide.run.user.dto.response.IsDuplicatedResponse;
import com.guide.run.user.dto.response.LoginResponse;
import com.guide.run.user.dto.response.SignupResponse;
import com.guide.run.user.profile.OAuthProfile;
import com.guide.run.user.service.GuideService;
import com.guide.run.user.service.ProviderService;
import com.guide.run.user.service.UserService;
import com.guide.run.user.service.ViService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.naming.CommunicationException;
import javax.security.auth.RefreshFailedException;

@CrossOrigin(origins = {"https://dev.guiderun.org", "https://guiderun.org","https://www.guiderun.org", "http://localhost:3000", "http://localhost:8080"},
maxAge = 3600,
allowCredentials = "true")

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class SignController {
    private final ProviderService providerService;
    private final JwtProvider jwtProvider;
    private final CookieService cookieService;
    private final UserService userService;
    private final ViService viService;
    private final GuideService guideService;


    @PostMapping("/login")
    public LoginResponse generalLogin(@RequestBody GeneralLoginRequest request,HttpServletRequest httpServletRequest,
                                      HttpServletResponse httpServletResponse){

        String privateId = userService.generalLogin(request.getAccountId(), request.getPassword());
        boolean isExist = userService.getUserStatus(privateId);

        boolean isExistCookie =false;


        if(httpServletRequest.getCookies() !=null){
            for(Cookie cookie: httpServletRequest.getCookies()){
                if(cookie.getName().equals("refreshToken")){
                    isExistCookie=true;
                }
            }
        }
        if(!isExistCookie) {
            cookieService.createCookie("refreshToken", httpServletResponse, privateId);
        }

        return LoginResponse.builder()
                .accessToken(jwtProvider.createAccessToken(privateId))
                .refreshToken(jwtProvider.createRefreshToken(privateId))
                .isExist(isExist)
                .build();
    }

    @PostMapping("/oauth/login/kakao")
    public LoginResponse kakaoLogin(String code, HttpServletRequest request,HttpServletResponse response) throws CommunicationException {
        String accessToken = providerService.getAccessToken(code, "kakao").getAccess_token();
        OAuthProfile oAuthProfile = providerService.getProfile(accessToken,"kakao");
        String privateId = oAuthProfile.getSocialId();
        boolean isExist = userService.getUserStatus(privateId);

        boolean isExistCookie =false;


        if(request.getCookies() !=null){
            for(Cookie cookie: request.getCookies()){
                if(cookie.getName().equals("refreshToken")){
                    isExistCookie=true;
                }
            }
        }
        if(!isExistCookie) {
            cookieService.createCookie("refreshToken", response, privateId);
        }


        return LoginResponse.builder()
                .accessToken(jwtProvider.createAccessToken(privateId))
                .refreshToken(jwtProvider.createRefreshToken(privateId))
                .isExist(isExist)
                .build();
    }

    @PostMapping("/signup/vi")
    public ResponseEntity<SignupResponse> viSignup(@RequestBody @Valid ViSignupDto viSignupDto, HttpServletRequest httpServletRequest){
        String privateId = jwtProvider.extractUserId(httpServletRequest);
        if(userService.isAccountIdExist(viSignupDto.getAccountId())){
            throw new DuplicatedUserIdException();
        }else{
            SignupResponse response = viService.viSignup(privateId, viSignupDto);
            userService.signUpATA(privateId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
    }


    @PostMapping("/signup/guide")
    public ResponseEntity<SignupResponse> guideSignup(@RequestBody @Valid GuideSignupDto guideSignupDto, HttpServletRequest httpServletRequest){
        String privateId = jwtProvider.extractUserId(httpServletRequest);
        if(userService.isAccountIdExist(guideSignupDto.getAccountId())){
            throw new DuplicatedUserIdException();
        }else{
            SignupResponse response = guideService.guideSignup(privateId, guideSignupDto);
            userService.signUpATA(privateId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
    }


    @PostMapping("/oauth/token/google")
    public LoginResponse googleSignup(String code,HttpServletResponse response) throws CommunicationException {
        String accessToken = providerService.getAccessToken(code, "google").getAccess_token();
        OAuthProfile oAuthProfile = providerService.getProfile(accessToken,"google");
        String userId = oAuthProfile.getSocialId();

        return LoginResponse.builder()
                .accessToken(jwtProvider.createAccessToken(userId))
                .build();
    }

    @GetMapping("/oauth/login/reissue")
    public ReissuedAccessTokenDto accessTokenReissue(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) {
                    String refreshToken = cookie.getValue();
                    try {
                        // refresh 토큰의 유효성 및 만료 여부 체크 (만료된 경우 예외 발생)
                        String privateId = jwtProvider.getPrivateIdForRefreshToken(refreshToken);
                        boolean isExist = userService.getUserStatus(privateId);

                        // 유효한 토큰인 경우 엑세스 토큰 재발급
                        return ReissuedAccessTokenDto.builder()
                                .accessToken(jwtProvider.createAccessToken(privateId))
                                .isExist(isExist)
                                .build();
                    } catch (ExpiredJwtException e) {
                        // refresh 토큰이 만료된 경우 쿠키 삭제 후 예외 발생 (로그아웃 처리)
                        cookieService.deleteRefreshTokenCookie(response);
                        log.error("토큰 만료. privateId: {}", jwtProvider.extractUserId(request));
                        throw new NotValidRefreshTokenException();
                    } catch (JwtException e) {
                        // 토큰 파싱이나 기타 JWT 오류 발생 시에도 쿠키 삭제 후 예외 발생
                        cookieService.deleteRefreshTokenCookie(response);
                        log.error(" 파싱 에러. privateId: {}", jwtProvider.extractUserId(request));
                        throw new NotValidRefreshTokenException();
                    }
                }
            }
        }
        log.error("Refresh 토큰이 존재하지 않습니다. privateId: {}", jwtProvider.extractUserId(request));
        throw new NotValidRefreshTokenException();
    }

    //아이디 중복확인
    @PostMapping("/signup/duplicated")
    public ResponseEntity<IsDuplicatedResponse> isIdDuplicated(@RequestBody AccountIdDto aa){
        IsDuplicatedResponse response =
                IsDuplicatedResponse.builder()
                        .isUnique(!userService.isAccountIdExist(aa.getAccountId()))
                        .build();
        return ResponseEntity.ok().body(response);
    }

    @DeleteMapping("/withdrawal")
    public ResponseEntity<String> withDrawal(@RequestBody WithdrawalRequest request, HttpServletRequest httpServletRequest){
        String privateId = jwtProvider.extractUserId(httpServletRequest);
        userService.withDrawal(request, privateId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body("");
    }

    @PostMapping("/oauth/login/naver")
    public LoginResponse naverLogin(String code, HttpServletRequest request,HttpServletResponse response) throws CommunicationException {
        String accessToken = providerService.getAccessToken(code, "naver").getAccess_token();
        OAuthProfile oAuthProfile = providerService.getProfile(accessToken,"naver");
        String privateId = oAuthProfile.getSocialId();
        boolean isExist = userService.getUserStatus(privateId);


        return LoginResponse.builder()
                .accessToken(jwtProvider.createAccessToken(privateId))
                .refreshToken(jwtProvider.createRefreshToken(privateId))
                .isExist(isExist)
                .build();
    }

}
