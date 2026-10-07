package com.streaming.core.domain.quiz;

import com.streaming.core.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * 퀴즈 문제 은행.
 * answerIndex 가 null 이면 "투표"(정답 없음) — 시청자 선택 분포만 보여준다.
 */
@Getter
@Entity
@Table(name = "quiz")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz extends BaseTimeEntity {

    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> options = new ArrayList<>();

    /** 0-based 정답 번호. null = 투표 */
    private Integer answerIndex;

    @Column(nullable = false)
    private int timeLimitSec;

    public static Quiz create(String title, String question, List<String> options, Integer answerIndex, int timeLimitSec) {
        Quiz q = new Quiz();
        q.update(title, question, options, answerIndex, timeLimitSec);
        return q;
    }

    public void update(String title, String question, List<String> options, Integer answerIndex, int timeLimitSec) {
        List<String> cleaned = options == null ? List.of()
                : options.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
        if (cleaned.size() < MIN_OPTIONS || cleaned.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("보기는 " + MIN_OPTIONS + "~" + MAX_OPTIONS + "개여야 합니다.");
        }
        if (answerIndex != null && (answerIndex < 0 || answerIndex >= cleaned.size())) {
            throw new IllegalArgumentException("정답 번호가 보기 범위를 벗어났습니다.");
        }
        if (timeLimitSec < 5 || timeLimitSec > 600) {
            throw new IllegalArgumentException("제한 시간은 5~600초입니다.");
        }
        this.title = title;
        this.question = question;
        this.options = new ArrayList<>(cleaned);
        this.answerIndex = answerIndex;
        this.timeLimitSec = timeLimitSec;
    }

    public boolean isVote() {
        return answerIndex == null;
    }
}
