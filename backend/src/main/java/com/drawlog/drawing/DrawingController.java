package com.drawlog.drawing;

import com.drawlog.auth.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/groups/{groupId}/drawings")
public class DrawingController {
    private final DrawingService drawingService;

    public DrawingController(DrawingService drawingService) {
        this.drawingService = drawingService;
    }

    @PostMapping("/today")
    @ResponseStatus(HttpStatus.CREATED)
    public DrawingDtos.DrawingResponse submit(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long groupId,
            @RequestParam("strokeJson") String strokeJson,
            @RequestParam("thumbnail") MultipartFile thumbnail
    ) {
        return drawingService.toResponse(drawingService.submitToday(user.id(), groupId, strokeJson, thumbnail));
    }

    @PutMapping("/today")
    public DrawingDtos.DrawingResponse update(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long groupId,
            @RequestParam("strokeJson") String strokeJson,
            @RequestParam("thumbnail") MultipartFile thumbnail
    ) {
        return drawingService.toResponse(drawingService.updateToday(user.id(), groupId, strokeJson, thumbnail));
    }
}
