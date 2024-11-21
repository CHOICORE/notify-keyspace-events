package me.choicore.samples.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Component
public class BufferedParkingAccessSignalProcessor {
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    public void buffer(ParkingEntryTime parkingEntryTime) {
        log.info("Buffering parking entry time: {}", parkingEntryTime);
        String key = "license_plate:" + parkingEntryTime.licensePlate();
        String buffered = stringRedisTemplate.opsForValue().get(key);
        if (buffered != null) {
            try {
                String stored = stringRedisTemplate.opsForValue().get(key + ":buffered");
                List<ParkingEntryTime> messageBuffer = objectMapper.readValue(stored, new TypeReference<>() {
                });
                messageBuffer.add(parkingEntryTime);
                stringRedisTemplate.opsForValue().set(key + ":buffered", objectMapper.writeValueAsString(messageBuffer));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

        } else {
            stringRedisTemplate.delete(key + ":buffered");
            MessageBuffer<ParkingEntryTime> messageBuffer = new MessageBuffer<>(key, Collections.singletonList(parkingEntryTime));
            try {
                stringRedisTemplate.opsForValue().set(key, "hold", 5, TimeUnit.SECONDS);
                stringRedisTemplate.opsForValue().set(key + ":buffered", objectMapper.writeValueAsString(messageBuffer.values()));
            } catch (Exception e) {
                log.error("Failed to serialize message buffer", e);
            }
        }
    }

    @Component
    static class BufferedParkingAccessKeyExpirationEventMessageListener extends KeyExpirationEventMessageListener {
        private final StringRedisTemplate stringRedisTemplate;
        private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        public BufferedParkingAccessKeyExpirationEventMessageListener(RedisMessageListenerContainer listenerContainer, StringRedisTemplate stringRedisTemplate) {
            super(listenerContainer);
            this.stringRedisTemplate = stringRedisTemplate;
        }

        @Override
        public void onMessage(@Nonnull Message message, byte[] pattern) {
            String key = new String(message.getBody());
            if (key.startsWith("license_plate:")) {
                String licensePlate = key.split(":")[1]; // 차량 번호 추출
                log.info("Detected expiration event for license plate: {}", licensePlate);

                String bufferedData = stringRedisTemplate.opsForValue().get(key + ":buffered");
                if (bufferedData == null) {
                    log.warn("No buffered data found for key: {}", key);
                    return;
                }

                try {
                    List<ParkingEntryTime> parkingEntryTimes = objectMapper.readValue(bufferedData, new TypeReference<>() {
                    });
                    parkingEntryTimes.sort(Comparator.comparing(ParkingEntryTime::entryTime));
                    ParkingEntryTime lastEntry = parkingEntryTimes.getLast();

                    // 가장 최근 입차 시간 로그
                    log.info("Successfully processed license plate '{}'. Last parking entry time: {}", licensePlate, lastEntry);

                    // Redis 키 삭제
                    stringRedisTemplate.delete(key);
                    log.info("Redis key '{}' and its buffered data have been deleted.", key);
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse buffered data for license plate '{}'. Key: {}", licensePlate, key, e);
                    throw new RuntimeException("Error processing buffered data for key: " + key, e);
                }
            } else {
                log.warn("Received an unexpected key pattern: {}", key);
            }
        }
    }
}
