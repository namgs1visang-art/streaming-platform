package com.streaming.admin.monitor;

import com.streaming.admin.quiz.QuizDto;
import com.streaming.core.chat.ChatEvent;
import com.streaming.core.chat.ChatEventPublisher;
import com.streaming.core.chat.MessageType;
import com.streaming.core.domain.broadcast.Broadcast;
import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.channel.ChannelStatus;
import com.streaming.core.domain.quiz.QuizPush;
import com.streaming.core.domain.schedule.Schedule;
import com.streaming.core.media.MediaUrls;
import com.streaming.core.media.OmeApiClient;
import com.streaming.core.media.OmeRecorder;
import com.streaming.core.service.BroadcastService;
import com.streaming.core.service.ChannelService;
import com.streaming.core.service.QuizService;
import com.streaming.core.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 실시간 방송 통합 모니터링/통제
 *  상태: 채널 LIVE 여부, 시청자 수(Redis), 송출 품질(OME REST API), 편성, 녹화, 진행 중 퀴즈, 얼리기
 *  통제: 방송 중지(송출 잠금 + OME 강제 종료), 송출 허용, 공지, 퀴즈 바로 출제
 *  (채팅 얼리기·메시지 관리·퀴즈 마감은 기존 채팅/퀴즈 API 를 그대로 사용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final ChannelService channelService;
    private final ScheduleService scheduleService;
    private final BroadcastService broadcastService;
    private final QuizService quizService;
    private final OmeApiClient ome;
    private final OmeRecorder recorder;
    private final MediaUrls mediaUrls;
    private final StringRedisTemplate redis;
    private final ChatEventPublisher publisher;

    @Value("${app.publish.early-minutes:10}")
    private int earlyMinutes;

    public List<MonitorDto.ChannelStatus> channels() {
        Map<String, OmeApiClient.StreamStat> streams = ome.streams();
        return channelService.findAll().stream()
                .map(c -> status(c, streams))
                .sorted(Comparator.comparing((MonitorDto.ChannelStatus s) -> !"LIVE".equals(s.status()))
                        .thenComparing(MonitorDto.ChannelStatus::name))
                .toList();
    }

    public MonitorDto.Detail detail(Long channelId) {
        MonitorDto.ChannelStatus st = status(channelService.get(channelId), ome.streams());
        List<QuizDto.Mapping> quizzes = st.schedule() == null ? List.of()
                : quizService.findScheduleQuizzes(st.schedule().id()).stream().map(QuizDto.Mapping::of).toList();
        return new MonitorDto.Detail(st, quizzes);
    }

    /**
     * 방송 중지: 진행 중 퀴즈 마감 → 송출 잠금 커밋 → 시청자 공지 → OME 송출 강제 종료.
     * 잠금을 먼저 커밋해야 OBS 가 바로 재접속해도 AdmissionWebhooks 에서 거부된다.
     * OME 가 closing 웹훅을 보내 채널 OFFLINE·방송 회차 종료가 되지만, OME 연결 실패에 대비해 여기서도 처리한다.
     */
    public MonitorDto.StopResult stop(Long channelId, String message) {
        quizService.findOpenByChannel(channelId).ifPresent(p -> quizService.closeEarly(p.getId()));
        Channel channel = channelService.lockPublish(channelId, true);
        String text = message == null || message.isBlank() ? "관리자에 의해 방송이 종료되었습니다." : message.strip();
        publisher.publish(ChatEvent.system(MessageType.SYSTEM, channel.getCode(), text, null));

        boolean kicked = ome.kick(channel.getCode());
        channelService.markOffline(channel.getCode());
        broadcastService.end(channel.getCode()).forEach(recorder::stop);
        log.warn("[monitor] 방송 중지 channel={}, kicked={}", channel.getCode(), kicked);
        return new MonitorDto.StopResult(kicked, status(channelService.get(channelId), Map.of()));
    }

    /** 송출 잠금 해제 — 이후 OBS 에서 다시 [방송 시작]하면 송출 가능 */
    public MonitorDto.ChannelStatus allowPublish(Long channelId) {
        Channel channel = channelService.lockPublish(channelId, false);
        return status(channel, ome.streams());
    }

    /** 공지 (채팅창 상단 시스템 메시지, 저장하지 않음) */
    public void notice(Long channelId, String content) {
        Channel channel = channelService.get(channelId);
        publisher.publish(ChatEvent.system(MessageType.SYSTEM, channel.getCode(), content.strip(), null));
    }

    public Long pushQuiz(Long channelId, Long quizId) {
        return quizService.pushToChannel(channelId, quizId).getId();
    }

    // ─────────────────────────────────────────────

    private MonitorDto.ChannelStatus status(Channel c, Map<String, OmeApiClient.StreamStat> streams) {
        boolean live = c.getStatus() == ChannelStatus.LIVE;

        Schedule schedule = scheduleService.findOnAir(c.getId(), earlyMinutes).orElse(null);
        MonitorDto.ScheduleInfo scheduleInfo = schedule == null ? null
                : new MonitorDto.ScheduleInfo(schedule.getId(), schedule.getTitle(), schedule.getStartAt(), schedule.getEndAt());

        Broadcast b = live ? broadcastService.findLive(c.getCode()).orElse(null) : null;
        MonitorDto.BroadcastInfo broadcastInfo = b == null ? null
                : new MonitorDto.BroadcastInfo(b.getId(), b.getStartedAt(), b.getRecordingStatus().name());

        OmeApiClient.StreamStat s = streams.get(c.getCode());
        MonitorDto.StreamInfo streamInfo = s == null ? null
                : new MonitorDto.StreamInfo(s.kbpsIn(), s.videoCodec(), s.width(), s.height(), s.framerate(),
                s.bframes(), s.audioCodec(), s.connections());

        QuizPush push = live ? quizService.findOpenByChannel(c.getId()).orElse(null) : null;
        MonitorDto.QuizInfo quizInfo = null;
        if (push != null) {
            QuizService.Stats stats = quizService.liveStats(push.getId(), push.getOptions().size());
            quizInfo = new MonitorDto.QuizInfo(push.getId(), push.getTitle(), push.getQuestion(),
                    push.isVote() ? "VOTE" : "QUIZ", push.getOptions(), push.getAnswerIndex(), push.getClosesAt(),
                    stats.counts(), stats.total());
        }

        return new MonitorDto.ChannelStatus(c.getId(), c.getCode(), c.getName(), c.getStatus().name(), c.getLiveStartedAt(),
                mediaUrls.llhls(c.getCode()), mediaUrls.webrtc(c.getCode()), live ? viewers(c.getCode()) : 0,
                c.isChatFrozen(), c.isStickerFrozen(), c.isPublishLocked(),
                scheduleInfo, broadcastInfo, streamInfo, quizInfo);
    }

    private long viewers(String code) {
        try {
            String v = redis.opsForValue().get("viewers:" + code);
            return v == null ? 0 : Math.max(0, Long.parseLong(v));
        } catch (Exception e) {
            return 0;
        }
    }
}
