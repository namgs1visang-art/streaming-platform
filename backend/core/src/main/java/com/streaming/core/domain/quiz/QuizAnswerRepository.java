package com.streaming.core.domain.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {

    boolean existsByPushIdAndClientId(Long pushId, String clientId);

    /** [answerIndex, count] 목록 */
    @Query("select a.answerIndex, count(a) from QuizAnswer a where a.push.id = :pushId group by a.answerIndex")
    List<Object[]> countByAnswer(@Param("pushId") Long pushId);
}
