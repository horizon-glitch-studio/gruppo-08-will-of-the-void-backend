package com.horizonglitch.wotv.notification;

import jakarta.validation.constraints.NotNull;

public record NotificationUpdate(@NotNull(message = "FIELD_REQUIRED") Boolean isRead) {
}
