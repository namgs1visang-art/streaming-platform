package com.streaming.admin.chat;

import com.streaming.core.service.StickerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 관리자 > 채팅 관리 > 스티커 관리 */
@RestController
@RequestMapping("/api/admin/stickers")
@RequiredArgsConstructor
public class StickerAdminController {

    private final StickerService stickerService;

    @GetMapping
    public List<ChatAdminDto.StickerResponse> list() {
        stickerService.findEnabled(); // 비어 있으면 기본 스티커 등록
        return stickerService.findAll().stream().map(ChatAdminDto.StickerResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatAdminDto.StickerResponse create(@Valid @RequestBody ChatAdminDto.StickerRequest req) {
        return ChatAdminDto.StickerResponse.of(stickerService.create(req.name(), req.emoji(), req.imageUrl(), req.enabled(), req.sortOrder()));
    }

    @PutMapping("/{id}")
    public ChatAdminDto.StickerResponse update(@PathVariable Long id, @Valid @RequestBody ChatAdminDto.StickerRequest req) {
        return ChatAdminDto.StickerResponse.of(stickerService.update(id, req.name(), req.emoji(), req.imageUrl(), req.enabled(), req.sortOrder()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        stickerService.delete(id);
    }
}
