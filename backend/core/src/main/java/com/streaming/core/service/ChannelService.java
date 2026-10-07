package com.streaming.core.service;

import com.streaming.core.common.NotFoundException;
import com.streaming.core.domain.channel.Channel;
import com.streaming.core.domain.channel.ChannelRepository;
import com.streaming.core.domain.channel.ChannelStatus;
import com.streaming.core.domain.broadcast.BroadcastRepository;
import com.streaming.core.domain.quiz.QuizPushRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final BroadcastRepository broadcastRepository;
    private final QuizPushRepository quizPushRepository;

    public List<Channel> findAll() {
        return channelRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    public List<Channel> findLive() {
        return channelRepository.findByStatusOrderByLiveStartedAtDesc(ChannelStatus.LIVE);
    }

    public Channel get(Long id) {
        return channelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("채널이 없습니다. id=" + id));
    }

    public Channel getByCode(String code) {
        return channelRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("채널이 없습니다. code=" + code));
    }

    @Transactional
    public Channel create(String code, String name, String description) {
        if (channelRepository.existsByCode(code)) {
            throw new IllegalArgumentException("이미 사용 중인 채널 코드입니다: " + code);
        }
        return channelRepository.save(Channel.create(code, name, description));
    }

    @Transactional
    public Channel update(Long id, String name, String description) {
        Channel channel = get(id);
        channel.update(name, description);
        return channel;
    }

    @Transactional
    public Channel regenerateKey(Long id) {
        Channel channel = get(id);
        channel.regenerateStreamKey();
        return channel;
    }

    @Transactional
    public void delete(Long id) {
        if (broadcastRepository.existsByChannelId(id) || quizPushRepository.existsByChannelId(id)) {
            throw new IllegalArgumentException("방송/녹화 이력이 있는 채널은 삭제할 수 없습니다.");
        }
        channelRepository.delete(get(id));
    }

    @Transactional
    public void markLive(Channel channel) {
        channelRepository.findById(channel.getId()).ifPresent(Channel::goLive);
    }

    @Transactional
    public void markOffline(String code) {
        channelRepository.findByCode(code).ifPresent(Channel::goOffline);
    }
}
