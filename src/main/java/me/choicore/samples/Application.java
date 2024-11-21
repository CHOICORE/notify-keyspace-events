package me.choicore.samples;

import me.choicore.samples.redis.BufferedParkingAccessSignalProcessor;
import me.choicore.samples.redis.ParkingEntryTime;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.LocalDateTime;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        ConfigurableApplicationContext ac = SpringApplication.run(Application.class, args);
        BufferedParkingAccessSignalProcessor bufferedParkingAccessSignalProcessor = ac.getBean(BufferedParkingAccessSignalProcessor.class);
        bufferedParkingAccessSignalProcessor.buffer(new ParkingEntryTime("123가1234", LocalDateTime.now()));
        bufferedParkingAccessSignalProcessor.buffer(new ParkingEntryTime("123가1234", LocalDateTime.now().plusMinutes(1)));
        bufferedParkingAccessSignalProcessor.buffer(new ParkingEntryTime("123가1234", LocalDateTime.now().plusMinutes(1)));
        bufferedParkingAccessSignalProcessor.buffer(new ParkingEntryTime("456나4567", LocalDateTime.now()));
        bufferedParkingAccessSignalProcessor.buffer(new ParkingEntryTime("456나4567", LocalDateTime.now()));
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory redisConnectionFactory) {
        RedisMessageListenerContainer redisMessageListenerContainer = new RedisMessageListenerContainer();
        redisMessageListenerContainer.setConnectionFactory(redisConnectionFactory);
        return redisMessageListenerContainer;
    }
}
