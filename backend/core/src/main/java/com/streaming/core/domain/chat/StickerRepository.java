package com.streaming.core.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StickerRepository extends JpaRepository<Sticker, Long> {

    List<Sticker> findByEnabledTrueOrderBySortOrderAscIdAsc();

    List<Sticker> findAllByOrderBySortOrderAscIdAsc();
}
