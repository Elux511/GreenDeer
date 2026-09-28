package com.it.greendeer.domain.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 游标分页：拉取 id > afterId 的消息（升序）。 */
    List<ChatMessage> findBySessionIdAndIdGreaterThanOrderByIdAsc(Long sessionId, Long afterId, Pageable pageable);

    /** 按 id 倒序取最近 N 条（组 LLM 上下文用），传入 PageRequest 限制条数。 */
    List<ChatMessage> findBySessionIdOrderByIdDesc(Long sessionId, Pageable pageable);

    /** 按 id 倒序取最近 N 条且排除指定类型（如 SYSTEM）。 */
    List<ChatMessage> findBySessionIdAndTypeNotOrderByIdDesc(Long sessionId, MessageType exclude, Pageable pageable);

    /** 某条弹幕之前的最近 N 条（回复它的上下文不应包含它自身）。 */
    List<ChatMessage> findBySessionIdAndIdLessThanOrderByIdDesc(Long sessionId, Long id, Pageable pageable);

    long countBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
