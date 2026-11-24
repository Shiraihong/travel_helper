package com.travelhelper.controller;

import java.security.Principal;

import com.travelhelper.preference.PreferencePatch;
import com.travelhelper.preference.UserIdentityResolver;
import com.travelhelper.preference.UserPreferenceService;
import com.travelhelper.preference.UserPreferenceView;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户偏好接口。
 *
 * <pre>
 * GET    /api/preferences                        读取（没有记录时返回空视图）
 * PATCH  /api/preferences?conversationId=...     合并更新
 * DELETE /api/preferences                        清空
 * </pre>
 *
 * 身份默认取 dev-user-id（本地开发）；接入认证后自动使用认证主体。
 */
@RestController
@RequestMapping("/api/preferences")
public class UserPreferenceController {

    private final UserPreferenceService service;
    private final UserIdentityResolver identityResolver;

    public UserPreferenceController(UserPreferenceService service,
                                    UserIdentityResolver identityResolver) {
        this.service = service;
        this.identityResolver = identityResolver;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public UserPreferenceView get(Principal principal,
                                  @RequestParam(required = false) String userId) {
        String resolved = identityResolver.require(principal, userId);
        return service.findView(resolved).orElseGet(() -> UserPreferenceView.empty(resolved));
    }

    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public UserPreferenceView patch(Principal principal,
                                    @RequestParam(required = false) String userId,
                                    @RequestParam(required = false) String conversationId,
                                    @RequestBody PreferencePatch patch) {
        String resolved = identityResolver.require(principal, userId);
        return service.merge(resolved, conversationId, patch);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal,
                       @RequestParam(required = false) String userId) {
        String resolved = identityResolver.require(principal, userId);
        service.delete(resolved);
    }
}
