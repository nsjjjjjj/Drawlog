export function isActiveMember(member) {
  return Boolean(member) && member.status !== 'DELETED';
}

export function visibleMembers(members = []) {
  return members
    .filter(isActiveMember)
    .sort((a, b) => new Date(a.joinedAt || 0) - new Date(b.joinedAt || 0));
}

export function canManageMember(viewerIsOwner, viewerUserId, member) {
  return Boolean(
    viewerIsOwner
    && isActiveMember(member)
    && String(member.userId) !== String(viewerUserId),
  );
}
