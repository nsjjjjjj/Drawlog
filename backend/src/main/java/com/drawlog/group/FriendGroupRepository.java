package com.drawlog.group;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendGroupRepository extends JpaRepository<FriendGroup, Long> {
    Optional<FriendGroup> findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);
}
