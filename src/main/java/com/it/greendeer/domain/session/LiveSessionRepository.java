package com.it.greendeer.domain.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    List<LiveSession> findByStatusIn(Collection<SessionStatus> statuses);
}
