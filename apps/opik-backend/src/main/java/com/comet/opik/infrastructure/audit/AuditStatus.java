package com.comet.opik.infrastructure.audit;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 审计日志状态
 * 记录操作的执行结果
 */
@Getter
@RequiredArgsConstructor
public enum AuditStatus {

    /**
     * 操作成功
     */
    SUCCESS("success", "成功"),

    /**
     * 操作失败
     */
    FAILURE("failure", "失败"),

    /**
     * 部分成功
     */
    PARTIAL("partial", "部分成功");

    @JsonValue
    private final String code;
    private final String description;

    /**
     * 根据code查找AuditStatus
     */
    public static AuditStatus fromCode(String code) {
        for (AuditStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return FAILURE;
    }
}
