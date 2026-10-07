package com.streaming.core.domain.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScheduleQuizRepository extends JpaRepository<ScheduleQuiz, Long> {

    @Query("select sq from ScheduleQuiz sq join fetch sq.quiz where sq.schedule.id = :scheduleId order by sq.orderIndex, sq.id")
    List<ScheduleQuiz> findBySchedule(@Param("scheduleId") Long scheduleId);

    boolean existsByScheduleIdAndQuizId(Long scheduleId, Long quizId);

    long countByScheduleId(Long scheduleId);

    void deleteByQuizId(Long quizId);
}
