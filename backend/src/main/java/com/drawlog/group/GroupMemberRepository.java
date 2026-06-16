package com.drawlog.group;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    List<GroupMember> findByUserIdOrderByJoinedAtAsc(Long userId);
    List<GroupMember> findByGroupIdOrderByJoinedAtAsc(Long groupId);
    Optional<GroupMember> findFirstByUserIdOrderByJoinedAtAsc(Long userId);
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);
    long countByGroupId(Long groupId);
    void deleteByGroupId(Long groupId);
}
