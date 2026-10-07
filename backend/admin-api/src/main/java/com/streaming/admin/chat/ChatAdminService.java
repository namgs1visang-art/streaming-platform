package com.streaming.admin.chat;

import com.streaming.core.chat.ChatEvent;
import com.streaming.core.chat.ChatEventPublisher;
import com.streaming.core.chat.MessageType;
import com.streaming.core.common.ConflictException;
import com.streaming.core.common.NotFoundException;
import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.chat.ChatMessage;
import com.streaming.core.domain.chat.ChatMessageRepository;
import com.streaming.core.domain.chat.ChatMessageStatus;
import com.streaming.core.service.ChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 관리자 채팅 관리: 얼리기, 메시지 숨김/삭제, 관리자 메시지 전송, 이력 검색.
 * DB 를 바꾼 뒤 Redis 로 이벤트를 발행하면 chat-server 가 시청자 화면에 즉시 반영한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatAdminService {

    public static final String ADMIN_SENDER = "관리자";
    private static final String ACTOR = "admin"; // TODO(관리자 계정): 로그인한 관리자 ID

    private final ChannelService channelService;
    private final ChatMessageRepository messageRepository;
    private final ChatEventPublisher publisher;

    public List<ChatAdminDto.Room> rooms() {
        return channelService.findAll().stream()
                .sorted(Comparator.comparing((Channel c) -> c.getStatus().name()))   // LIVE 먼저
                .map(ChatAdminDto.Room::of).toList();
    }

    // ─────────────────────────────────────────────
    // 얼리기
    // ─────────────────────────────────────────────

    @Transactional
    public ChatAdminDto.Room freeze(Long channelId, ChatAdminDto.FreezeRequest req) {
        Channel channel = channelService.get(channelId);
        boolean prevChat = channel.isChatFrozen();
        boolean prevSticker = channel.isStickerFrozen();
        if (req.chatFrozen() != null) channel.freezeChat(req.chatFrozen());
        if (req.stickerFrozen() != null) channel.freezeSticker(req.stickerFrozen());

        if (prevChat != channel.isChatFrozen() || prevSticker != channel.isStickerFrozen()) {
            String text;
            if (prevChat != channel.isChatFrozen()) {
                text = channel.isChatFrozen() ? "관리자가 채팅을 얼렸습니다." : "채팅 얼리기가 해제되었습니다.";
            } else {
                text = channel.isStickerFrozen() ? "관리자가 스티커 사용을 중지했습니다." : "스티커 사용이 다시 가능합니다.";
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chatFrozen", channel.isChatFrozen());
            payload.put("stickerFrozen", channel.isStickerFrozen());
            publisher.publish(ChatEvent.system(MessageType.CHAT_FREEZE, channel.getCode(), text, payload));
        }
        return ChatAdminDto.Room.of(channel);
    }

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    /** 실시간 피드 초기 로딩 — 최근 24시간 중 최신 limit 건 (숨김/삭제 포함, 시간순) */
    public List<ChatAdminDto.Message> recent(String channelCode, int limit) {
        List<ChatMessage> rows = messageRepository.findRecent(channelCode, EnumSet.allOf(ChatMessageStatus.class),
                LocalDateTime.now().minusDays(1), PageRequest.of(0, Math.min(500, Math.max(1, limit))));
        List<ChatAdminDto.Message> list = new ArrayList<>(rows.stream().map(ChatAdminDto.Message::of).toList());
        Collections.reverse(list);
        return list;
    }

    public Page<ChatAdminDto.Message> search(String channelCode, String keyword, ChatMessageStatus status,
                                             LocalDateTime from, LocalDateTime to, int page, int size) {
        Collection<ChatMessageStatus> statuses = status == null ? EnumSet.allOf(ChatMessageStatus.class) : EnumSet.of(status);
        return messageRepository.search(channelCode, statuses, keyword == null ? "" : keyword.trim(), from, to,
                        PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size))))
                .map(ChatAdminDto.Message::of);
    }

    // ─────────────────────────────────────────────
    // 관리자 메시지 / 조치
    // ─────────────────────────────────────────────

    @Transactional
    public ChatAdminDto.Message sendAdmin(String channelCode, String content) {
        channelService.getByCode(channelCode);
        ChatEvent event = ChatEvent.of(MessageType.ADMIN, channelCode, ADMIN_SENDER, ACTOR, content.strip(), null);
        ChatMessage saved = messageRepository.save(ChatMessage.from(event));
        publisher.publish(event);
        return ChatAdminDto.Message.of(saved);
    }

    @Transactional
    public ChatAdminDto.Message hide(String id) {
        ChatMessage m = get(id);
        if (m.getStatus() == ChatMessageStatus.DELETED) throw new ConflictException("삭제된 메시지는 숨길 수 없습니다.");
        if (m.getStatus() != ChatMessageStatus.HIDDEN) {
            m.hide(ACTOR);
            publisher.publish(ChatEvent.system(MessageType.MESSAGE_HIDE, m.getChannelCode(), null, Map.of("ids", List.of(id))));
        }
        return ChatAdminDto.Message.of(m);
    }

    @Transactional
    public ChatAdminDto.Message unhide(String id) {
        ChatMessage m = get(id);
        if (m.getStatus() == ChatMessageStatus.HIDDEN) {
            m.unhide(ACTOR);
            publisher.publish(ChatEvent.system(MessageType.MESSAGE_UNHIDE, m.getChannelCode(), null, Map.of("message", m.toEvent())));
        }
        return ChatAdminDto.Message.of(m);
    }

    @Transactional
    public ChatAdminDto.Message delete(String id) {
        ChatMessage m = get(id);
        if (m.getStatus() != ChatMessageStatus.DELETED) {
            m.delete(ACTOR);
            publisher.publish(ChatEvent.system(MessageType.MESSAGE_DELETE, m.getChannelCode(), null, Map.of("ids", List.of(id))));
        }
        return ChatAdminDto.Message.of(m);
    }

    private ChatMessage get(String id) {
        // 채팅 로그는 chat-server 가 0.3초 주기로 일괄 저장하므로, 방금 올라온 메시지는 잠시 없을 수 있다
        return messageRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("메시지가 아직 저장되지 않았거나 없습니다. 잠시 후 다시 시도해 주세요."));
    }
}
