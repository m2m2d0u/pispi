package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.notification.NotificationDto;
import ci.sycapay.pispi.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @PostMapping("/ping")
    public NotificationDto sendPing() {
        return service.sendPing();
    }

    @GetMapping
    public Page<NotificationDto> listNotifications(Pageable pageable) {
        return service.listNotifications(pageable);
    }
}
