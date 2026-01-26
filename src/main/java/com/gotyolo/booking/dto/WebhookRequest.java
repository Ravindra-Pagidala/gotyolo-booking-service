package com.gotyolo.booking.dto;

public record WebhookRequest(
    String bookingId,
    String status,  // "success" | "failed"
    String idempotencyKey
) {}
