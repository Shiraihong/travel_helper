package com.travelhelper.preference;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户偏好的读写入口：跨会话共享，按 user_id 合并更新。 */
@Service
public class UserPreferenceService {

    private final UserPreferenceRepository repository;
    private final PreferencePromptRenderer renderer;

    public UserPreferenceService(UserPreferenceRepository repository,
                                 PreferencePromptRenderer renderer) {
        this.repository = repository;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public Optional<UserPreferenceView> findView(String userId) {
        return repository.findByUserId(userId).map(UserPreferenceView::from);
    }

    /** 合并更新：没有就建，有就只改补丁里出现的字段。 */
    @Transactional
    public UserPreferenceView merge(String userId, String conversationId, PreferencePatch patch) {
        UserPreference preference = repository.findByUserId(userId)
                .orElseGet(() -> UserPreference.createFor(userId));
        preference.apply(patch);
        preference.touchConversation(conversationId);
        return UserPreferenceView.from(repository.save(preference));
    }

    @Transactional
    public void delete(String userId) {
        repository.deleteByUserId(userId);
    }

    /** 给聊天提示词用的数据块；没有偏好时返回「未提供」。 */
    @Transactional(readOnly = true)
    public String renderForPrompt(String userId) {
        return repository.findByUserId(userId)
                .map(renderer::render)
                .orElse("未提供");
    }
}
