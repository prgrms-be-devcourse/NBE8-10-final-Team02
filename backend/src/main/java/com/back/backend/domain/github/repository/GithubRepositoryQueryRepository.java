package com.back.backend.domain.github.repository;

import com.back.backend.domain.github.entity.GithubRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class GithubRepositoryQueryRepository {

    private final EntityManager entityManager;

    public List<GithubRepository> findAllOwnedByIds(Long userId, Collection<Long> repositoryIds) {
        if (repositoryIds == null || repositoryIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery("""
                        select repository
                        from GithubRepository repository
                        join repository.githubConnection connection
                        where connection.user.id = :userId
                          and repository.id in :repositoryIds
                        """, GithubRepository.class)
                .setParameter("userId", userId)
                .setParameter("repositoryIds", repositoryIds)
                .getResultList();
    }
}
