package com.streaming.core.domain.chat;

import com.streaming.core.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 채팅 스티커. 이미지 URL 이 있으면 이미지, 없으면 이모지로 표시 */
@Getter
@Entity
@Table(name = "sticker")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sticker extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 16)
    private String emoji;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int sortOrder;

    public static Sticker create(String name, String emoji, String imageUrl, boolean enabled, int sortOrder) {
        Sticker s = new Sticker();
        s.update(name, emoji, imageUrl, enabled, sortOrder);
        return s;
    }

    public void update(String name, String emoji, String imageUrl, boolean enabled, int sortOrder) {
        if ((emoji == null || emoji.isBlank()) && (imageUrl == null || imageUrl.isBlank())) {
            throw new IllegalArgumentException("이모지 또는 이미지 URL 중 하나는 필요합니다.");
        }
        this.name = name;
        this.emoji = blankToNull(emoji);
        this.imageUrl = blankToNull(imageUrl);
        this.enabled = enabled;
        this.sortOrder = sortOrder;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
