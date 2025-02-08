package com.guide.run.global.jwt;

import com.guide.run.global.exception.auth.authorize.NotExistAuthorizationException;
import com.guide.run.global.exception.auth.authorize.NotValidRefreshTokenException;
import com.guide.run.global.redis.TmpToken;
import com.guide.run.global.redis.TmpTokenRepository;
import com.guide.run.global.security.user.CustomUserDetailsService;
import com.guide.run.global.redis.RefreshToken;
import com.guide.run.global.redis.RefreshTokenRepository;
import com.guide.run.user.dto.request.RefreshTokenDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;

import java.util.Base64;
import java.util.Date;


@Log4j2
@Component
@RequiredArgsConstructor
public class JwtProvider {
    @Value("${spring.jwt.secretKey}")
    private String secretKey;
   // public static final long TOKEN_VALID_TIME = 1000L * 60 * 30 ; // 30분
    public static final long TOKEN_VALID_TIME = 1000L *60 *60 ;
    public static final long REFRESH_TOKEN_VALID_TIME = 1000L * 60 * 60 * 24 * 30; // 한 달

    public static final long TMP_VALID_TIME = 1000L*60*30; //문자인증 후 주는 임시토큰 30분


    private final CustomUserDetailsService customUserDetailsService;
    private final RefreshTokenRepository refreshTokenRepository;

    private final TmpTokenRepository tmpTokenRepository;

    @PostConstruct
    public void init(){
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    public String createAccessToken(String socialId){
        Claims claims = Jwts.claims().setSubject(socialId);
        Date now = new Date();

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime()+TOKEN_VALID_TIME))
                .signWith(SignatureAlgorithm.HS256,secretKey)
                .compact();
    }
    public String createRefreshToken(String privateId){
        Date now = new Date();
       RefreshToken refreshToken= new RefreshToken(Jwts.builder()
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + REFRESH_TOKEN_VALID_TIME))
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact(), privateId);
        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }
    public Authentication getAuthentication(String token){
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(getSocialId(token));
        return new UsernamePasswordAuthenticationToken(userDetails,"",userDetails.getAuthorities());
    }

    public String getSocialId(String token){
        try {
            return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().getSubject();
        }catch (ExpiredJwtException e){
            System.out.println(e.getClaims().getSubject()+"-----------------------------");
            return e.getClaims().getSubject();
        }
    }

    public String extractUserId(HttpServletRequest httpServletRequest){
        String accessToken = resolveToken(httpServletRequest);
        return getSocialId(accessToken);
    }

    public String resolveToken(HttpServletRequest request) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return null;
        }else {
            String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (bearer != null && bearer.startsWith("Bearer "))
                return bearer.substring("Bearer ".length());
            throw new NotExistAuthorizationException("인증 에러");
        }
    }

    public boolean validateTokenExpiration(String token){
        try{
            Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
            return true;
        } catch (Exception e){
            return false;
        }
    }

    public String getPrivateIdForCookie(Cookie[] cookies){
        for(Cookie cookie: cookies){
            if(cookie.getName().equals("refreshToken")){
                RefreshToken token = refreshTokenRepository.findById(cookie.getValue()).orElseThrow(() -> new NotValidRefreshTokenException());
                return token.getPrivateId();
            }
        }
        throw new NotValidRefreshTokenException();
    }

    public String createTmpToken(String authPhone, String privateId, String type){
        Claims claims = Jwts.claims().setSubject(authPhone);
        Date now = new Date();
        TmpToken token= new TmpToken(Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + TMP_VALID_TIME))
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact(), privateId, type);
        tmpTokenRepository.save(token);
        return token.getToken();
    }


    public String getPrivateIdForRefreshToken(String refreshToken) {

        RefreshToken token = refreshTokenRepository.findById(refreshToken).orElseThrow(() -> new NotValidRefreshTokenException());
        return token.getPrivateId();
    }
}
