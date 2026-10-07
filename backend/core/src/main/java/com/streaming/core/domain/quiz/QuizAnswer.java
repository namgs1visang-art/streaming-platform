package com.streaming.core.domain.quiz;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/** 시청자 응답 — 출제 1회당 시청자(clientId) 1번 */
@Getter
@Entity
@Table(name = "quiz_answer", uniqueConstraints = @UniqueConstraint(columnNames = {"push_id", "clientId"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "push_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private QuizPush push;

    @Column(nullable = false, length = 64)
    private String clientId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false)
    private int answerIndex;

    private Boolean correct; // 투표면 null

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static QuizAnswer of(QuizPush push, String clientId, String nickname, int answerIndex) {
        QuizAnswer a = new QuizAnswer();
        a.push = push;
        a.clientId = clientId;
        a.nickname = nickname;
        a.answerIndex = answerIndex;
        a.correct = push.isVote() ? null : push.getAnswerIndex() == answerIndex;
        a.createdAt = LocalDateTime.now();
        return a;
    }
}
