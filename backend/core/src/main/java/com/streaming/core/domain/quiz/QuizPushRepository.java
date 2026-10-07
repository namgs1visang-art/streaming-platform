package com.streaming.core.domain.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface QuizPushRepository extends JpaRepository<QuizPush, Long> {

    @Query("select p from QuizPush p join fetch p.channel where p.id = :id")
    Optional<QuizPush> findWithChannel(@Param("id") Long id);

    /** 채널에서 진행 중인 퀴즈 */
    @Query("select p from QuizPush p join fetch p.channel c where c.id = :channelId and p.closed = false " +
           "and p.closesAt > :now order by p.pushedAt desc")
    List<QuizPush> findOpenByChannel(@Param("channelId") Long channelId, @Param("now") LocalDateTime now);

    @Query("select p from QuizPush p join fetch p.channel c where c.code = :code and p.closed = false " +
           "and p.closesAt > :now order by p.pushedAt desc")
    List<QuizPush> findOpenByChannelCode(@Param("code") String channelCode, @Param("now") LocalDateTime now);

    /** 마감 시간이 지났는데 아직 결과 발표 전 */
    @Query("select p.id from QuizPush p where p.closed = false and p.closesAt <= :now")
    List<Long> findIdsToClose(@Param("now") LocalDateTime now);

    @Query("select p from QuizPush p where p.schedule.id = :scheduleId order by p.pushedAt desc")
    List<QuizPush> findBySchedule(@Param("scheduleId") Long scheduleId);

    /** 다시보기: 방송 시간대에 출제된 퀴즈 */
    @Query("select p from QuizPush p where p.channel.id = :channelId and p.pushedAt >= :from and p.pushedAt < :to order by p.pushedAt")
    List<QuizPush> findInWindow(@Param("channelId") Long channelId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    long countByChannelIdAndPushedAtBetween(Long channelId, LocalDateTime from, LocalDateTime to);

    boolean existsByQuizId(Long quizId);

    boolean existsByChannelId(Long channelId);

    /** 여러 서버가 동시에 마감해도 한 번만 성공 */
    @Modifying(clearAutomatically = true)
    @Query("update QuizPush p set p.closed = true where p.id = :id and p.closed = false")
    int markClosed(@Param("id") Long id);

    /** 조기 종료: 마감 시각을 지금으로 당긴다 */
    @Modifying(clearAutomatically = true)
    @Query("update QuizPush p set p.closesAt = :now where p.id = :id and p.closesAt > :now")
    int closeEarly(@Param("id") Long id, @Param("now") LocalDateTime now);
}
