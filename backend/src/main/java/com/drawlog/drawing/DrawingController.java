package com.drawlog.drawing;

import com.drawlog.auth.CurrentUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DrawingController {
    private final DrawingService drawingService;

    public DrawingController(DrawingService drawingService) {
        this.drawingService = drawingService;
    }

    @PostMapping("/api/drawings")
    @ResponseStatus(HttpStatus.CREATED)
    public DrawingDtos.DrawingResponse submit(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) Long groupId,
            @RequestParam("file") MultipartFile file
    ) {
        return drawingService.toResponse(drawingService.submit(user.id(), groupId, file));
    }

    @DeleteMapping("/api/drawings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        drawingService.delete(user.id(), id);
    }

    @GetMapping("/api/feed")
    public DrawingDtos.FeedResponse feed(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) Long groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return drawingService.feed(user.id(), groupId, date);
    }
}
