package com.back.backend.domain.github.entity;

import com.back.backend.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Entity
@Builder
@Table(
        name = "github_repositories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_github_repositories_connection_repo", columnNames = {"github_connection_id", "github_repo_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GithubRepository extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "github_connection_id", nullable = false)
    private GithubConnection githubConnection;

    @Column(name = "github_repo_id", nullable = false)
    private Long githubRepoId;

    @Column(name = "owner_login", nullable = false, length = 255)
    private String ownerLogin;

    @Column(name = "repo_name", nullable = false, length = 255)
    private String repoName;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "html_url", nullable = false, length = 1000)
    private String htmlUrl;

    @Convert(converter = RepositoryVisibilityConverter.class)
    @Column(name = "visibility", nullable = false, length = 20)
    private RepositoryVisibility visibility;

    @Column(name = "default_branch", length = 255)
    private String defaultBranch;

    @Column(name = "is_selected", nullable = false)
    private boolean selected;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    /** GitHub API의 pushed_at. 마지막으로 push된 시각. connection refresh 시 갱신된다. */
    @Column(name = "pushed_at")
    private Instant pushedAt;

    /**
     * 저장 시점에 결정된 소유자 유형.
     * "owner": connection sync 경로에서 ownerLogin == 사용자 githubLogin인 경우
     * "collaborator": org/타인 소유 repo, 기여 탭에서 추가한 경우
     */
    @Column(name = "owner_type", length = 20)
    private String ownerType;

    @Column(name = "repo_size_kb")
    private Integer repoSizeKb;

    @Column(name = "language", length = 100)
    private String language;

    /**
     * 시크릿 스캔 결과로 분석에서 제외된 파일 목록.
     * null이면 스캔 미실행 또는 발견 없음.
     * 구조: [{"filePath": "...", "ruleId": "..."}, ...]
     * 실제 시크릿 값은 저장하지 않는다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "secret_excluded_files", columnDefinition = "jsonb")
    private String secretExcludedFiles;

    /**
     * GitHub API에서 새로 받아온 값으로 repo 정보를 갱신한다.
     * visibility, defaultBranch, htmlUrl은 바뀔 수 있어 매 동기화마다 덮어쓴다.
     */
    public void sync(RepositoryVisibility visibility, String defaultBranch, String htmlUrl,
                     Instant pushedAt, String ownerType, String language, Instant syncedAt) {
        this.visibility = visibility;
        this.defaultBranch = defaultBranch;
        this.htmlUrl = htmlUrl;
        this.pushedAt = pushedAt;
        this.ownerType = ownerType;
        this.language = language;
        this.syncedAt = syncedAt;
    }

    /**
     * 사용자가 선택/해제한 상태를 저장한다.
     * is_selected = true 인 repo만 커밋 동기화 대상이 된다.
     */
    public void updateSelection(boolean selected) {
        this.selected = selected;
    }

    /**
     * 시크릿 스캔 결과로 제외된 파일 목록을 저장한다.
     * 실제 시크릿 값은 포함하지 않으며, 파일 경로와 룰 ID만 기록한다.
     *
     * @param secretExcludedFilesJson JSON 문자열 (null 허용 — 발견 없음)
     */
    public void updateSecretExcludedFiles(String secretExcludedFilesJson) {
        this.secretExcludedFiles = secretExcludedFilesJson;
    }
}
