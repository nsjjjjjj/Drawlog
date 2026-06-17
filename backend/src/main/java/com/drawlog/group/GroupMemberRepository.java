package com.drawlog.group;

import com.drawlog.user.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    boolean existsByGroupIdAndUserIdAndUserStatus(Long groupId, Long userId, UserStatus status);
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);
    Optional<GroupMember> findByGroupIdAndUserIdAndUserStatus(Long groupId, Long userId, UserStatus status);
    @EntityGraph(attributePaths = {"group"})
    List<GroupMember> findByUserIdOrderByJoinedAtAsc(Long userId);
    @EntityGraph(attributePaths = {"group"})
    List<GroupMember> findByUserIdAndUserStatusOrderByJoinedAtAsc(Long userId, UserStatus status);
    @EntityGraph(attributePaths = {"user", "group"})
    List<GroupMember> findByGroupIdOrderByJoinedAtAsc(Long groupId);
    @EntityGraph(attributePaths = {"user", "group"})
    List<GroupMember> findByGroupIdAndUserStatusOrderByJoinedAtAsc(Long groupId, UserStatus status);
    @EntityGraph(attributePaths = {"user", "group"})
    List<GroupMember> findByUserStatusOrderByJoinedAtAsc(UserStatus status);
    long countByGroupId(Long groupId);
    long countByGroupIdAndUserStatus(Long groupId, UserStatus status);
    void deleteByGroupId(Long groupId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GroupMember member where member.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    @Query("""
            select member
            from GroupMember member
            join fetch member.user
            join fetch member.group
            where member.group.id in :groupIds
              and member.user.status = :status
            order by member.group.id asc, member.joinedAt asc
            """)
    List<GroupMember> findMembersForGroups(@Param("groupIds") List<Long> groupIds, @Param("status") UserStatus status);
}
