package com.guide.run.global.redis;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@Getter
@AllArgsConstructor
@RedisHash(value = "refreshToken", timeToLive = 1000L * 60 * 60 * 24 * 30)
//@RedisHash(value = "refreshToken", timeToLive = 1L)
public class RefreshToken {
    @Id
    private String token;
    private String privateId;
}
