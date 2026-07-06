package com.nexusbank.fraud.adapter.in.web;

import jakarta.validation.constraints.Size;

public record RejectReviewRequest(
        @Size(max = 500) String reason
) {}
