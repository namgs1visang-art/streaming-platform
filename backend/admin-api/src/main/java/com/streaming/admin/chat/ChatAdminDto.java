package com.streaming.admin.chat;

import com.streaming.core.chat.MessageType;
import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.chat.ChatMessage;
import com.streaming.core.domain.chat.Sticker;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

public class ChatAdminDto {

    /** 채팅방 = 채널 */
    public record Room(Long channelId, String channelCode, String name, String status,
                       boolean chatFrozen, boolean stickerFrozen) {
        public static Room of(Channel c) {
            return new Room(c.getId(), c.getCode(), c.getName(), c.getStatus().name(), c.isChatFrozen(), c.isStickerFrozen());
        }
    }

    /** null 인 값은 변경하지 않음 */
    public record FreezeRequest(Boolean chatFrozen, Boolean stickerFrozen) {
    }

    public record AdminSendRequest(@NotBlank String channelCode, @NotBlank @Size(max = 200) String content) {
    }

    /** 관리자 화면용 메시지 (시청자 이벤트 형태 + 상태) */
    public record Message(String id, MessageType type, String channelCode, String sender, String clientId,
                          String content, Map<String, Object> payload, Instant sentAt,
                          String status, String moderatedBy, LocalDateTime moderatedAt) {
        public static Message of(ChatMessage m) {
            return new Message(m.getId(), m.getType(), m.getChannelCode(), m.getSender(), m.getClientId(),
                    m.getContent(), m.getPayload(), m.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                    m.getStatus().name(), m.getModeratedBy(), m.getModeratedAt());
        }
    }

    public record StickerRequest(@NotBlank @Size(max = 50) String name,
                                 @Size(max = 16) String emoji,
                                 @Size(max = 500) String imageUrl,
                                 boolean enabled,
                                 int sortOrder) {
    }

    public record StickerResponse(Long id, String name, String emoji, String imageUrl, boolean enabled, int sortOrder) {
        public static StickerResponse of(Sticker s) {
            return new StickerResponse(s.getId(), s.getName(), s.getEmoji(), s.getImageUrl(), s.isEnabled(), s.getSortOrder());
        }
    }
}
