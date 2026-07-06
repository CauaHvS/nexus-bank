package com.nexusbank.notifications.application.dto;

import java.util.UUID;

public record DlqEntry(UUID id, String topic, String payload, int retryCount) {}
