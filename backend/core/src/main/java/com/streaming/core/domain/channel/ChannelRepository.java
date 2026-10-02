package com.streaming.core.domain.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByCode(String code);

    boolean existsByCode(String code);

    List<Channel> findByStatusOrderByLiveStartedAtDesc(ChannelStatus status);
}
