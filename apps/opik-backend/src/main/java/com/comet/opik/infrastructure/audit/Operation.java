package com.comet.opik.infrastructure.audit;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 审计日志操作类型
 * 定义系统中所有可记录的操作类型
 */
@Getter
@RequiredArgsConstructor
public enum Operation {

    /**
     * 创建操作
     */
    CREATE("create", "创建"),

    /**
     * 读取/查看操作
     */
    READ("read", "查看"),

    /**
     * 更新操作
     */
    UPDATE("update", "更新"),

    /**
     * 删除操作
     */
    DELETE("delete", "删除"),

    /**
     * 执行操作
     */
    EXECUTE("execute", "执行"),

    /**
     * 登录操作
     */
    LOGIN("login", "登录"),

    /**
     * 登出操作
     */
    LOGOUT("logout", "登出"),

    /**
     * 其他操作
     */
    OTHER("other", "其他");

    @JsonValue
    private final String code;
    private final String description;

    /**
     * 根据code查找Operation
     */
    public static Operation fromCode(String code) {
        for (Operation op : values()) {
            if (op.code.equals(code)) {
                return op;
            }
        }
        return OTHER;
    }
}
