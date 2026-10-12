package com.streaming.core.domain.broadcast;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {

    @Query("select b from Broadcast b join fetch b.channel left join fetch b.schedule where b.id = :id")
    Optional<Broadcast> findWithChannel(@Param("id") Long id);

    List<Broadcast> findByChannelIdAndEndedAtIsNull(Long channelId);

    @Query("select b from Broadcast b where b.channel.code = :code and b.endedAt is null order by b.startedAt desc")
    List<Broadcast> findOpenByChannelCode(@Param("code") String channelCode);

    /** 종료됐고 녹화 파일 완료를 기다리는 방송 (OME 녹화 info xml 확인 대상) */
    @Query("select b from Broadcast b join fetch b.channel where b.recordingStatus = com.streaming.core.domain.broadcast.RecordingStatus.RECORDING " +
           "and b.videoPath is null and b.endedAt is not null order by b.endedAt asc")
    List<Broadcast> findPendingRecordings();

    /** 종료됐는데 오래도록 녹화 파일이 안 들어온 방송 → FAILED 처리 대상 */
    @Query("select b from Broadcast b where b.recordingStatus = com.streaming.core.domain.broadcast.RecordingStatus.RECORDING " +
           "and b.videoPath is null and b.endedAt < :before")
    List<Broadcast> findStuckRecordings(@Param("before") LocalDateTime before);

    /** 관리자 목록 (전체) */
    @Query(value = "select b from Broadcast b join fetch b.channel left join fetch b.schedule order by b.startedAt desc",
           countQuery = "select count(b) from Broadcast b")
    Page<Broadcast> findPage(Pageable pageable);

    /** 관리자 목록 (채널별) */
    @Query(value = "select b from Broadcast b join fetch b.channel c left join fetch b.schedule " +
                   "where c.id = :channelId order by b.startedAt desc",
           countQuery = "select count(b) from Broadcast b where b.channel.id = :channelId")
    Page<Broadcast> findPageByChannel(@Param("channelId") Long channelId, Pageable pageable);

    /** 시청자 다시보기 목록 (녹화 완료 + 종료된 방송) */
    @Query(value = "select b from Broadcast b join fetch b.channel " +
                   "where b.recordingStatus = com.streaming.core.domain.broadcast.RecordingStatus.READY " +
                   "and b.endedAt is not null order by b.startedAt desc",
           countQuery = "select count(b) from Broadcast b where b.recordingStatus = com.streaming.core.domain.broadcast.RecordingStatus.READY " +
                   "and b.endedAt is not null")
    Page<Broadcast> findReplays(Pageable pageable);

    boolean existsByChannelId(Long channelId);
}
