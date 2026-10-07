package com.streaming.admin.chat;

import com.streaming.admin.common.PageResponse;
import com.streaming.core.domain.chat.ChatMessageStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** 관리자 > 채팅 관리 > 채팅 메시지 관리 */
@RestController
@RequestMapping("/api/admin/chat")
@RequiredArgsConstructor
public class ChatAdminController {

    private final ChatAdminService chatAdminService;

    /** 채팅방(채널) 목록 + 얼리기 상태 */
    @GetMapping("/rooms")
    public List<ChatAdminDto.Room> rooms() {
        return chatAdminService.rooms();
    }

    /** 채팅 얼리기 / 스티커 얼리기 ON·OFF — 예: {"chatFrozen": true} */
    @PutMapping("/rooms/{channelId}/freeze")
    public ChatAdminDto.Room freeze(@PathVariable Long channelId, @RequestBody ChatAdminDto.FreezeRequest req) {
        return chatAdminService.freeze(channelId, req);
    }

    /** 실시간 피드 초기 로딩 */
    @GetMapping("/messages/recent")
    public List<ChatAdminDto.Message> recent(@RequestParam String channelCode,
                                             @RequestParam(defaultValue = "100") int limit) {
        return chatAdminService.recent(channelCode, limit);
    }

    /** 이력 검색: /api/admin/chat/messages?channelCode=channel1&keyword=&status=&from=2026-10-01&to=2026-10-07&page=0 */
    @GetMapping("/messages")
    public PageResponse<ChatAdminDto.Message> search(
            @RequestParam String channelCode,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) ChatMessageStatus status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(chatAdminService.search(channelCode, keyword, status,
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(), page, size), m -> m);
    }

    /** 관리자 메시지 전송 (채팅창에 "관리자" 로 강조 표시) */
    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatAdminDto.Message send(@Valid @RequestBody ChatAdminDto.AdminSendRequest req) {
        return chatAdminService.sendAdmin(req.channelCode(), req.content());
    }

    @PostMapping("/messages/{id}/hide")
    public ChatAdminDto.Message hide(@PathVariable String id) {
        return chatAdminService.hide(id);
    }

    @PostMapping("/messages/{id}/unhide")
    public ChatAdminDto.Message unhide(@PathVariable String id) {
        return chatAdminService.unhide(id);
    }

    @DeleteMapping("/messages/{id}")
    public ChatAdminDto.Message delete(@PathVariable String id) {
        return chatAdminService.delete(id);
    }
}
