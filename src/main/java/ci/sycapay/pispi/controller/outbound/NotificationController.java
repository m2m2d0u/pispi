package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.dto.notification.NotificationDto;
import ci.sycapay.pispi.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notifications")
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @Operation(summary = "Send connectivity test (ping)",
               description = "Generates an ADMI.004 PING event, sends it to the AIP, and saves the notification locally.")
    @PostMapping("/ping")
    public ApiResponse<NotificationDto> sendPing() {
        return ApiResponse.ok(service.sendPing());
    }

    @Operation(summary = "Send maintenance notification",
               description = "Generates an ADMI.004 MAIN event, sends it to the AIP, and saves the notification locally.")
    @PostMapping("/main")
    public ApiResponse<NotificationDto> sendMain() {
        return ApiResponse.ok(service.sendMain());
    }

    @Operation(summary = "List notifications", description = "Returns a paginated list of all inbound and outbound notification events, ordered by creation date descending.")
    @GetMapping
    public ApiResponse<Page<NotificationDto>> listNotifications(Pageable pageable) {
        return ApiResponse.ok(service.listNotifications(pageable));
    }
}
