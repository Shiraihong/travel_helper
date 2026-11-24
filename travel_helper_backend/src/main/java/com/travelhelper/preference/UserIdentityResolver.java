package com.travelhelper.preference;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 解析当前用户身份。
 *
 * <p>优先级：
 * <ol>
 *   <li>{@link Principal}（接入 Spring Security / JWT 后就是认证主体的 sub）；</li>
 *   <li>请求参数 userId（仅当 {@code allow-user-id-param=true}，方便本地模拟多用户）；</li>
 *   <li>配置 {@code travel-helper.auth.dev-user-id}（本地开发兜底）。</li>
 * </ol>
 * 都没有时，需要用户身份的接口返回 401。
 */
@Component
public class UserIdentityResolver {

    private final String devUserId;
    private final boolean allowUserIdParam;

    public UserIdentityResolver(
            @Value("${travel-helper.auth.dev-user-id:}") String devUserId,
            @Value("${travel-helper.auth.allow-user-id-param:false}") boolean allowUserIdParam) {
        this.devUserId = devUserId;
        this.allowUserIdParam = allowUserIdParam;
    }

    /** 聊天等可选场景：解析不到就返回 null，不打断请求。 */
    public String resolveOptional(Principal principal, String userIdParam) {
        if (principal != null) {
            String name = principal.getName();
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        }
        if (allowUserIdParam && userIdParam != null && !userIdParam.isBlank()) {
            return userIdParam.trim();
        }
        if (devUserId != null && !devUserId.isBlank()) {
            return devUserId.trim();
        }
        return null;
    }

    /** 偏好接口等必须知道用户的场景：解析不到直接 401。 */
    public String require(Principal principal, String userIdParam) {
        String userId = resolveOptional(principal, userIdParam);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "缺少用户标识：请先登录，或配置 travel-helper.auth.dev-user-id");
        }
        return userId;
    }
}
