package com.CS335_Project3.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    void canSetAndGetKey() {
        redisTemplate.opsForValue().set("smoke:test", "hello");
        String result = redisTemplate.opsForValue().get("smoke:test");
        assertThat(result).isEqualTo("hello");

        // Clean up after test — don't leave test keys in Redis
        redisTemplate.delete("smoke:test");
    }
}

