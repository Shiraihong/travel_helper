package com.travelhelper.preference;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    /** 一次带出两个子集合，避免在事务外访问时触发 LazyInitializationException。 */
    @EntityGraph(attributePaths = {"interests", "dietaryRestrictions"})
    Optional<UserPreference> findByUserId(String userId);

    void deleteByUserId(String userId);
}
