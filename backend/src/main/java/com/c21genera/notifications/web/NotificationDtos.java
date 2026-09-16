package com.c21genera.notifications.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class NotificationDtos {

  private NotificationDtos() {}

  public record SendDocumentsEmailRequest(@NotBlank @Email String recipientEmail) {}
}
