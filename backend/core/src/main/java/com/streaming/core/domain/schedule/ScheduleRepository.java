package com.streaming.core.domain.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    @Query("select s from Schedule s join fetch s.channel where s.id = :id")
    Optional<Schedule> findWithChannel(@Param("id") Long id);

    /** 기간 내 편성표 (모든 채널) */
    @Query("select s from Schedule s join fetch s.channel " +
           "where s.startAt < :to and s.endAt > :from order by s.startAt")
    List<Schedule> findInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 같은 채널 내 시간 겹침 검사 (신규 등록 시 excludeId = -1) */
    @Query("select case when count(s) > 0 then true else false end from Schedule s " +
           "where s.channel.id = :channelId and s.startAt < :endAt and s.endAt > :startAt " +
           "and s.id <> :excludeId")
    boolean existsOverlap(@Param("channelId") Long channelId,
                          @Param("startAt") LocalDateTime startAt,
                          @Param("endAt") LocalDateTime endAt,
                          @Param("excludeId") Long excludeId);

    /** 지금 송출 가능한 스케줄 (시작 earlyMinutes 분 전부터 허용) */
    @Query("select s from Schedule s where s.channel.id = :channelId " +
           "and s.startAt <= :nowPlusEarly and s.endAt >= :now order by s.startAt")
    List<Schedule> findOnAir(@Param("channelId") Long channelId,
                             @Param("now") LocalDateTime now,
                             @Param("nowPlusEarly") LocalDateTime nowPlusEarly);
}
