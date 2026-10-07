package com.streaming.core.service;

import com.streaming.core.common.NotFoundException;
import com.streaming.core.domain.chat.Sticker;
import com.streaming.core.domain.chat.StickerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StickerService {

    /** 처음 실행 시 기본 스티커 (이모지) */
    private static final String[][] DEFAULTS = {
            {"좋아요", "👍"}, {"하트", "❤️"}, {"박수", "👏"}, {"웃음", "😂"}, {"감동", "😭"},
            {"놀람", "😮"}, {"불꽃", "🔥"}, {"축하", "🎉"}, {"정답", "⭕"}, {"오답", "❌"},
    };

    private final StickerRepository stickerRepository;

    public List<Sticker> findAll() {
        return stickerRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    /** 시청자용 (사용 중인 스티커만). 하나도 없으면 기본 스티커 등록 */
    @Transactional
    public List<Sticker> findEnabled() {
        if (stickerRepository.count() == 0) {
            for (int i = 0; i < DEFAULTS.length; i++) {
                stickerRepository.save(Sticker.create(DEFAULTS[i][0], DEFAULTS[i][1], null, true, i));
            }
        }
        return stickerRepository.findByEnabledTrueOrderBySortOrderAscIdAsc();
    }

    public Sticker get(Long id) {
        return stickerRepository.findById(id).orElseThrow(() -> new NotFoundException("스티커가 없습니다. id=" + id));
    }

    @Transactional
    public Sticker create(String name, String emoji, String imageUrl, boolean enabled, int sortOrder) {
        return stickerRepository.save(Sticker.create(name, emoji, imageUrl, enabled, sortOrder));
    }

    @Transactional
    public Sticker update(Long id, String name, String emoji, String imageUrl, boolean enabled, int sortOrder) {
        Sticker s = get(id);
        s.update(name, emoji, imageUrl, enabled, sortOrder);
        return s;
    }

    @Transactional
    public void delete(Long id) {
        stickerRepository.delete(get(id)); // 이미 보낸 메시지는 payload 에 스냅샷이 있어 영향 없음
    }
}
