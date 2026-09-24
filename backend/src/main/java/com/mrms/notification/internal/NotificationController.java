package com.mrms.notification.internal;

import com.mrms.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    List<View> latest() {
        return service.latest(CurrentUser.id()).stream()
                .map(n -> new View(n.getId(), n.getTitle(), n.getMessage(), n.getLink(), n.getReadAt() != null,
                        n.getCreatedAt()))
                .toList();
    }

    @GetMapping("/unread-count")
    Map<String, Long> unread() {
        return Map.of("count", service.unread(CurrentUser.id()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(@PathVariable Long id) {
        service.markRead(CurrentUser.id(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void readAll() {
        service.markAllRead(CurrentUser.id());
    }

    record View(Long id, String title, String message, String link, boolean read, Instant createdAt) {
    }
}
