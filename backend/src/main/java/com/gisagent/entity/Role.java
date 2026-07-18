package com.gisagent.entity;

/** 团队角色层级：OWNER > ADMIN > EDITOR > MEMBER > VIEWER */
public enum Role {
    OWNER(4),
    ADMIN(3),
    EDITOR(2),
    MEMBER(1),
    VIEWER(0);

    private final int level;
    Role(int level) { this.level = level; }

    public int getLevel() { return level; }
    public boolean isAtLeast(Role other) { return this.level >= other.level; }
}
