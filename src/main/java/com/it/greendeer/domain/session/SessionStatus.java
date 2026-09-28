package com.it.greendeer.domain.session;

/**
 * 直播场次状态机：
 * CREATED --start--> LIVE --pause--> PAUSED --start--> LIVE
 *   ^                                        |
 *   +----------------stop/delete-------------------> ENDED
 */
public enum SessionStatus {
    CREATED,
    LIVE,
    PAUSED,
    ENDED
}
