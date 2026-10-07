package com.streaming.core.domain.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    /** 최근 메시지 (최신순) — 시청자 입장 시/관리자 실시간 피드 초기 로딩 */
    @Query("select m from ChatMessage m where m.channelCode = :code and m.status in :statuses " +
           "and m.createdAt >= :since order by m.createdAt desc")
    List<ChatMessage> findRecent(@Param("code") String channelCode,
                                 @Param("statuses") Collection<ChatMessageStatus> statuses,
                                 @Param("since") LocalDateTime since,
                                 Pageable pageable);

    /** 관리자 이력 검색 — keyword 는 빈 문자열이면 전체 */
    @Query(value = "select m from ChatMessage m where m.channelCode = :code and m.status in :statuses " +
                   "and (lower(m.content) like lower(concat('%', :keyword, '%')) " +
                   "  or lower(m.sender) like lower(concat('%', :keyword, '%'))) " +
                   "and m.createdAt >= :from and m.createdAt < :to order by m.createdAt desc",
           countQuery = "select count(m) from ChatMessage m where m.channelCode = :code and m.status in :statuses " +
                   "and (lower(m.content) like lower(concat('%', :keyword, '%')) " +
                   "  or lower(m.sender) like lower(concat('%', :keyword, '%'))) " +
                   "and m.createdAt >= :from and m.createdAt < :to")
    Page<ChatMessage> search(@Param("code") String channelCode,
                             @Param("statuses") Collection<ChatMessageStatus> statuses,
                             @Param("keyword") String keyword,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to,
                             Pageable pageable);

    /** 다시보기: 방송 시간대 구간의 노출 메시지 (시간순) */
    @Query("select m from ChatMessage m where m.channelCode = :code and m.status = :status " +
           "and m.createdAt >= :from and m.createdAt < :to order by m.createdAt asc")
    List<ChatMessage> findInWindow(@Param("code") String channelCode,
                                   @Param("status") ChatMessageStatus status,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to,
                                   Pageable pageable);

    long countByChannelCodeAndStatusAndCreatedAtBetween(String channelCode, ChatMessageStatus status,
                                                        LocalDateTime from, LocalDateTime to);
}
