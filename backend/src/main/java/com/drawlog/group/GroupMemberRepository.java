package com.drawlog.group;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);
    @EntityGraph(attributePaths = {"group"})
    List<GroupMember> findByUserIdOrderByJoinedAtAsc(Long userId);
    @EntityGraph(attributePaths = {"user", "group"})
    List<GroupMember> findByGroupIdOrderByJoinedAtAsc(Long groupId);
    long countByGroupId(Long groupId);

    @Query("""
            select member
            from GroupMember member
            join fetch member.user
            join fetch member.group
            where member.group.id in :groupIds
            order by member.group.id asc, member.joinedAt asc
            """)
    List<GroupMember> findMembersForGroups(@Param("groupIds") List<Long> groupIds);
}
