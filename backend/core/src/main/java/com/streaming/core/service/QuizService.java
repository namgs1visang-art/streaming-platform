package com.streaming.core.service;

import com.streaming.core.chat.ChatEvent;
import com.streaming.core.chat.ChatEventPublisher;
import com.streaming.core.chat.MessageType;
import com.streaming.core.common.ConflictException;
import com.streaming.core.common.NotFoundException;
import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.channel.ChannelStatus;
import com.streaming.core.domain.quiz.*;
import com.streaming.core.domain.schedule.Schedule;
import com.streaming.core.domain.schedule.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 퀴즈/투표
 *
 *  문제 은행(Quiz) ─▶ 편성 퀴즈(ScheduleQuiz: 방송별 출제 목록)
 *    └▶ 출제 push() : QuizPush 생성 + QUIZ_START 발행 (채팅 채널로 시청자 전체에게)
 *    └▶ 응답 submitAnswer() : DB 저장(1인 1회) + Redis HINCRBY 실시간 집계
 *    └▶ flushLiveStats() (1초 주기) : 바뀐 퀴즈만 QUIZ_STATS 발행 → 시청자 화면 막대그래프 갱신
 *    └▶ 마감 close() : 시간 종료(closeExpired) 또는 관리자 조기 종료 → DB 기준 최종 집계로 QUIZ_RESULT 발행
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    private static final String COUNTS_KEY = "quiz:counts:";   // hash  {answerIndex → count}
    private static final String DIRTY_KEY = "quiz:dirty";      // set   {pushId}  집계가 바뀐 퀴즈

    private final QuizRepository quizRepository;
    private final ScheduleQuizRepository scheduleQuizRepository;
    private final QuizPushRepository pushRepository;
    private final QuizAnswerRepository answerRepository;
    private final ScheduleRepository scheduleRepository;
    private final ChatEventPublisher publisher;
    private final StringRedisTemplate redis;

    public record Stats(List<Long> counts, long total) {
    }

    // ─────────────────────────────────────────────
    // 문제 은행
    // ─────────────────────────────────────────────

    public Page<Quiz> search(String keyword, int page, int size) {
        return quizRepository.search(keyword == null ? "" : keyword.trim(),
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "id")));
    }

    public List<Quiz> findAll() {
        return quizRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    public Quiz get(Long id) {
        return quizRepository.findById(id).orElseThrow(() -> new NotFoundException("퀴즈가 없습니다. id=" + id));
    }

    @Transactional
    public Quiz create(String title, String question, List<String> options, Integer answerIndex, int timeLimitSec) {
        return quizRepository.save(Quiz.create(title, question, options, answerIndex, timeLimitSec));
    }

    @Transactional
    public Quiz update(Long id, String title, String question, List<String> options, Integer answerIndex, int timeLimitSec) {
        Quiz q = get(id);
        q.update(title, question, options, answerIndex, timeLimitSec);
        return q;
    }

    @Transactional
    public void delete(Long id) {
        Quiz q = get(id);
        if (pushRepository.existsByQuizId(id)) {
            throw new ConflictException("출제 이력이 있는 퀴즈는 삭제할 수 없습니다. (응답 통계 보존)");
        }
        scheduleQuizRepository.deleteByQuizId(id);
        quizRepository.delete(q);
    }

    // ─────────────────────────────────────────────
    // 편성 퀴즈
    // ─────────────────────────────────────────────

    public List<ScheduleQuiz> findScheduleQuizzes(Long scheduleId) {
        return scheduleQuizRepository.findBySchedule(scheduleId);
    }

    public long countScheduleQuizzes(Long scheduleId) {
        return scheduleQuizRepository.countByScheduleId(scheduleId);
    }

    @Transactional
    public int addToSchedule(Long scheduleId, List<Long> quizIds) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new NotFoundException("스케줄이 없습니다. id=" + scheduleId));
        int order = (int) scheduleQuizRepository.countByScheduleId(scheduleId);
        int added = 0;
        for (Long quizId : new LinkedHashSet<>(quizIds)) {
            if (scheduleQuizRepository.existsByScheduleIdAndQuizId(scheduleId, quizId)) continue;
            scheduleQuizRepository.save(ScheduleQuiz.of(schedule, get(quizId), order++));
            added++;
        }
        return added;
    }

    @Transactional
    public void removeFromSchedule(Long scheduleId, Long mappingId) {
        ScheduleQuiz sq = scheduleQuizRepository.findById(mappingId)
                .filter(m -> m.getSchedule().getId().equals(scheduleId))
                .orElseThrow(() -> new NotFoundException("편성 퀴즈가 없습니다. id=" + mappingId));
        scheduleQuizRepository.delete(sq);
    }

    /** mappingIds 순서가 곧 출제 순서 */
    @Transactional
    public void reorder(Long scheduleId, List<Long> mappingIds) {
        Map<Long, ScheduleQuiz> byId = new HashMap<>();
        scheduleQuizRepository.findBySchedule(scheduleId).forEach(m -> byId.put(m.getId(), m));
        for (int i = 0; i < mappingIds.size(); i++) {
            ScheduleQuiz m = byId.get(mappingIds.get(i));
            if (m == null) throw new IllegalArgumentException("편성 퀴즈 목록이 변경되었습니다. 새로고침 후 다시 시도하세요.");
            m.changeOrder(i);
        }
    }

    // ─────────────────────────────────────────────
    // 출제 / 마감
    // ─────────────────────────────────────────────

    public List<QuizPush> findPushes(Long scheduleId) {
        return pushRepository.findBySchedule(scheduleId);
    }

    public QuizPush getPush(Long pushId) {
        return pushRepository.findWithChannel(pushId).orElseThrow(() -> new NotFoundException("출제 이력이 없습니다. id=" + pushId));
    }

    /** 편성(방송)에서 퀴즈 출제 — 해당 채널이 LIVE 이고 진행 중인 퀴즈가 없어야 한다 */
    @Transactional
    public QuizPush push(Long scheduleId, Long quizId) {
        Schedule schedule = scheduleRepository.findWithChannel(scheduleId)
                .orElseThrow(() -> new NotFoundException("스케줄이 없습니다. id=" + scheduleId));
        Channel channel = schedule.getChannel();
        if (channel.getStatus() != ChannelStatus.LIVE) {
            throw new ConflictException("방송 중(LIVE)인 채널에서만 퀴즈를 출제할 수 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!pushRepository.findOpenByChannel(channel.getId(), now).isEmpty()) {
            throw new ConflictException("진행 중인 퀴즈가 있습니다. 마감 후 출제하세요.");
        }
        Quiz quiz = get(quizId);
        QuizPush push = pushRepository.save(QuizPush.of(quiz, channel, schedule, now));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pushId", push.getId());
        payload.put("title", push.getTitle());
        payload.put("question", push.getQuestion());
        payload.put("options", push.getOptions());
        payload.put("mode", push.isVote() ? "VOTE" : "QUIZ");
        payload.put("timeLimitSec", push.getTimeLimitSec());
        payload.put("closesAt", push.getClosesAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        publisher.publish(ChatEvent.system(MessageType.QUIZ_START, channel.getCode(), push.getQuestion(), payload));
        log.info("[quiz] 출제 push={}, channel={}, quiz={}", push.getId(), channel.getCode(), quizId);
        return push;
    }

    /** 관리자 조기 마감 */
    @Transactional
    public void closeEarly(Long pushId) {
        getPush(pushId);
        pushRepository.closeEarly(pushId, LocalDateTime.now());
        close(pushId);
    }

    /** 마감 시간이 지난 퀴즈 결과 발표 (스케줄러에서 1초마다) */
    @Transactional
    public void closeExpired() {
        for (Long id : pushRepository.findIdsToClose(LocalDateTime.now())) {
            close(id);
        }
    }

    private void close(Long pushId) {
        if (pushRepository.markClosed(pushId) == 0) return; // 다른 서버가 이미 마감
        QuizPush push = getPush(pushId);
        Stats stats = finalStats(push);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pushId", pushId);
        payload.put("counts", stats.counts());
        payload.put("total", stats.total());
        payload.put("answerIndex", push.getAnswerIndex());
        payload.put("mode", push.isVote() ? "VOTE" : "QUIZ");
        publisher.publish(ChatEvent.system(MessageType.QUIZ_RESULT, push.getChannel().getCode(), push.getQuestion(), payload));
        redis.expire(COUNTS_KEY + pushId, Duration.ofMinutes(10));
        log.info("[quiz] 마감 push={}, 참여 {}명", pushId, stats.total());
    }

    /** DB 기준 집계 (마감/이력/다시보기) */
    public Stats finalStats(QuizPush push) {
        long[] counts = new long[push.getOptions().size()];
        for (Object[] row : answerRepository.countByAnswer(push.getId())) {
            int idx = ((Number) row[0]).intValue();
            if (idx >= 0 && idx < counts.length) counts[idx] = ((Number) row[1]).longValue();
        }
        List<Long> list = Arrays.stream(counts).boxed().toList();
        return new Stats(list, list.stream().mapToLong(Long::longValue).sum());
    }

    // ─────────────────────────────────────────────
    // 시청자
    // ─────────────────────────────────────────────

    /** 시청자 응답 — 1인(clientId) 1회, 마감 후 불가 */
    @Transactional
    public Stats submitAnswer(Long pushId, String clientId, String nickname, int answerIndex) {
        if (clientId == null || clientId.isBlank()) throw new IllegalArgumentException("clientId 가 필요합니다.");
        QuizPush push = pushRepository.findById(pushId).orElseThrow(() -> new NotFoundException("퀴즈가 없습니다. id=" + pushId));
        if (!push.isOpen(LocalDateTime.now())) throw new ConflictException("응답 시간이 종료되었습니다.");
        if (answerIndex < 0 || answerIndex >= push.getOptions().size()) throw new IllegalArgumentException("잘못된 보기 번호입니다.");
        if (answerRepository.existsByPushIdAndClientId(pushId, clientId)) throw new ConflictException("이미 응답했습니다.");
        String nick = nickname == null || nickname.isBlank() ? "익명" : nickname.substring(0, Math.min(50, nickname.length()));
        try {
            answerRepository.saveAndFlush(QuizAnswer.of(push, clientId, nick, answerIndex));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("이미 응답했습니다.");
        }
        redis.opsForHash().increment(COUNTS_KEY + pushId, String.valueOf(answerIndex), 1);
        redis.expire(COUNTS_KEY + pushId, Duration.ofHours(1));
        redis.opsForSet().add(DIRTY_KEY, String.valueOf(pushId));
        return liveStats(pushId, push.getOptions().size());
    }

    /** Redis 실시간 집계 */
    public Stats liveStats(Long pushId, int optionCount) {
        Map<Object, Object> hash = redis.opsForHash().entries(COUNTS_KEY + pushId);
        List<Long> counts = new ArrayList<>();
        for (int i = 0; i < optionCount; i++) {
            Object v = hash.get(String.valueOf(i));
            counts.add(v == null ? 0L : Long.parseLong(v.toString()));
        }
        return new Stats(counts, counts.stream().mapToLong(Long::longValue).sum());
    }

    /** 1초마다: 응답이 들어온 퀴즈만 QUIZ_STATS 발행 (응답 1건마다 발행하면 시청자 수만큼 폭주) */
    public void flushLiveStats() {
        String id;
        int guard = 0;
        while (guard++ < 100 && (id = redis.opsForSet().pop(DIRTY_KEY)) != null) {
            Long pushId = Long.valueOf(id);
            pushRepository.findWithChannel(pushId).filter(p -> !p.isClosed()).ifPresent(p -> {
                Stats s = liveStats(pushId, p.getOptions().size());
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("pushId", pushId);
                payload.put("counts", s.counts());
                payload.put("total", s.total());
                publisher.publish(ChatEvent.system(MessageType.QUIZ_STATS, p.getChannel().getCode(), null, payload));
            });
        }
    }

    /** 시청자 입장/재접속 시 진행 중인 퀴즈 */
    public Optional<QuizPush> findOpen(String channelCode) {
        return pushRepository.findOpenByChannelCode(channelCode, LocalDateTime.now()).stream().findFirst();
    }

    public boolean hasAnswered(Long pushId, String clientId) {
        return clientId != null && answerRepository.existsByPushIdAndClientId(pushId, clientId);
    }
}
