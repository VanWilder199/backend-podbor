package by.marketplace.notification;


import by.marketplace.auth.dto.Channel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final Logger logger = LoggerFactory.getLogger(NotificationService.class);


    public void sendOtp(Channel channel, String destination, String code) {
        logger.info("NotificationService.sendOtp: {},via {}:, {}", channel, destination, code);    }
}
