package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.dto.notification.NotificationDto;
import ci.sycapay.pispi.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @Operation(summary = "Send connectivity test (ping)",
               description = "Generates an ADMI.004 PING event, logs it locally, and saves it to the database. Note: this AIP deployment does not expose a notification endpoint — the ping is recorded locally only.")
    @PostMapping("/ping")
    public ApiResponse<NotificationDto> sendPing() {
        return ApiResponse.ok(service.sendPing());
    }

    @Operation(summary = "List notifications", description = "Returns a paginated list of all inbound and outbound notification events, ordered by creation date descending.")
    @GetMapping
    public ApiResponse<Page<NotificationDto>> listNotifications(Pageable pageable) {
        return ApiResponse.ok(service.listNotifications(pageable));
    }
}
