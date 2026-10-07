package com.streaming.core.domain.quiz;

import com.streaming.core.domain.schedule.Schedule;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** 편성 퀴즈: 방송(스케줄)에서 출제할 퀴즈 목록과 순서 */
@Getter
@Entity
@Table(name = "schedule_quiz", uniqueConstraints = @UniqueConstraint(columnNames = {"schedule_id", "quiz_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleQuiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Quiz quiz;

    @Column(nullable = false)
    private int orderIndex;

    public static ScheduleQuiz of(Schedule schedule, Quiz quiz, int orderIndex) {
        ScheduleQuiz sq = new ScheduleQuiz();
        sq.schedule = schedule;
        sq.quiz = quiz;
        sq.orderIndex = orderIndex;
        return sq;
    }

    public void changeOrder(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
