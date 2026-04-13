package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.service.MessageStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

@Tag(name = "Messages")
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageStatusService messageStatusService;

    @Operation(summary = "Check message status",
               description = "Queries the AIP to retrieve the processing status of a previously sent message, identified by its msgId.")
    @GetMapping("/status/{msgId}")
    public ApiResponse<Map<String, Object>> getMessageStatus(
            @Parameter(description = "The unique message identifier (msgId) to check status for")
            @PathVariable String msgId) {
        Map<String, Object> status = messageStatusService.checkMessageStatus(msgId);
        return ApiResponse.ok(status);
    }
}
