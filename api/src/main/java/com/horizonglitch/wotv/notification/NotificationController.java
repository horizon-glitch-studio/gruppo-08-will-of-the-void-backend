package com.horizonglitch.wotv.notification;

import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.security.CurrentPlayer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/notifications")
@Tag(name = "Notifications")
@SecurityRequirement(name = "bearer")
public class NotificationController {

	private final NotificationService notifications;

	public NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	@GetMapping
	@Operation(summary = "Notifications shown when the Summoner comes back")
	public PageResponse<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) Boolean unreadOnly,
			@RequestParam(required = false) NotificationType type,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return notifications.list(CurrentPlayer.id(jwt), unreadOnly, type, page, size);
	}

	@GetMapping("/unread-count")
	@Operation(summary = "Unread notification count")
	public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
		return Map.of("count", notifications.unreadCount(CurrentPlayer.id(jwt)));
	}

	@PatchMapping("/{notificationId}")
	@Operation(summary = "Mark a notification as read")
	public NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable long notificationId,
			@Valid @RequestBody NotificationUpdate update) {
		return notifications.markRead(CurrentPlayer.id(jwt), notificationId, update.isRead());
	}

	@PostMapping("/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Mark every notification as read")
	public void readAll(@AuthenticationPrincipal Jwt jwt) {
		notifications.markAllRead(CurrentPlayer.id(jwt));
	}

	@DeleteMapping("/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a notification")
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long notificationId) {
		notifications.delete(CurrentPlayer.id(jwt), notificationId);
	}
}
