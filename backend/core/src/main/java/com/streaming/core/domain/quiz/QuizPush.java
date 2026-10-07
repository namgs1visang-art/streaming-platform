package com.streaming.core.domain.quiz;

import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.schedule.Schedule;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 퀴즈 출제(송출) 1회. 출제 시점의 문제/보기/정답을 스냅샷으로 저장해서
 * 이후 문제 은행이 수정돼도 통계·다시보기가 그대로 유지된다.
 */
@Getter
@Entity
@Table(name = "quiz_push", indexes = @Index(name = "idx_quiz_push_channel_pushed", columnList = "channel_id, pushedAt"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Schedule schedule;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> options = new ArrayList<>();

    private Integer answerIndex;

    @Column(nullable = false)
    private int timeLimitSec;

    @Column(nullable = false)
    private LocalDateTime pushedAt;

    @Column(nullable = false)
    private LocalDateTime closesAt;

    /** 결과 발표까지 끝났는지 (조건부 update 로 한 번만 true) */
    @Column(nullable = false)
    private boolean closed;

    public static QuizPush of(Quiz quiz, Channel channel, Schedule schedule, LocalDateTime now) {
        QuizPush p = new QuizPush();
        p.quiz = quiz;
        p.channel = channel;
        p.schedule = schedule;
        p.title = quiz.getTitle();
        p.question = quiz.getQuestion();
        p.options = new ArrayList<>(quiz.getOptions());
        p.answerIndex = quiz.getAnswerIndex();
        p.timeLimitSec = quiz.getTimeLimitSec();
        p.pushedAt = now;
        p.closesAt = now.plusSeconds(quiz.getTimeLimitSec());
        return p;
    }

    public boolean isVote() {
        return answerIndex == null;
    }

    public boolean isOpen(LocalDateTime now) {
        return !closed && closesAt.isAfter(now);
    }
}
