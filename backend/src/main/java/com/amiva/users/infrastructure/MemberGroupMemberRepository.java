package com.amiva.users.infrastructure;

import java.util.List;
import java.util.UUID;

import com.amiva.users.domain.MemberGroupMember;
import com.amiva.users.domain.MemberGroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberGroupMemberRepository extends JpaRepository<MemberGroupMember, MemberGroupMemberId> {

    List<MemberGroupMember> findByGroupId(UUID groupId);

    long countByGroupId(UUID groupId);
}
