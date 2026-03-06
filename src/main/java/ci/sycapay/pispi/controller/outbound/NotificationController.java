package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
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
    public ApiResponse<NotificationDto> sendPing() {
        return ApiResponse.ok(service.sendPing());
    }

    @GetMapping
    public ApiResponse<Page<NotificationDto>> listNotifications(Pageable pageable) {
        return ApiResponse.ok(service.listNotifications(pageable));
    }
}
