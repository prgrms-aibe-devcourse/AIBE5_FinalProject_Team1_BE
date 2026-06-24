package com.team1.codedock.domain.github.service;

import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GithubWebhookRegistrationRequiresNewTest {

    @Autowired
    private GithubRepositoryRepository githubRepositoryRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private RestClient.Builder restClientBuilder;

    @MockBean
    private GithubApiClient githubApiClient;

    private Long workspaceId;
    private Long repoId;
    private Long userId;

    @BeforeEach
    void setUp() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            User owner = userRepository.save(User.create("req-new-test@test.com", "hash", "Owner"));
            owner.updateOnGithubLogin("github-token", null);

            Workspace workspace = workspaceRepository.save(Workspace.create(owner, "Req New WS", "req-new-ws", null));
            workspaceMemberRepository.save(WorkspaceMember.create(workspace, owner, "owner"));

            GithubRepository repo = githubRepositoryRepository.save(GithubRepository.create(
                    workspace, "RQ001", "team", "project",
                    "team/project", "https://github.com/team/project", null, false, "main"));

            workspaceId = workspace.getId();
            repoId = repo.getId();
            userId = owner.getId();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            workspaceMemberRepository.deleteAll();
            githubRepositoryRepository.deleteAll();
            workspaceRepository.deleteAll();
            userRepository.deleteAll();
            return null;
        });
    }

    @Test
    @DisplayName("registerWebhook 메서드에 REQUIRES_NEW 트랜잭션이 설정되어 있다")
    void registerWebhook_메서드에_REQUIRES_NEW_트랜잭션이_설정되어있다() throws NoSuchMethodException {
        Method method = GithubWebhookRegistrationService.class.getMethod(
                "registerWebhook", Long.class, Long.class, Long.class);
        Transactional txAnnotation = method.getAnnotation(Transactional.class);

        assertThat(txAnnotation).isNotNull();
        assertThat(txAnnotation.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("REQUIRES_NEW 트랜잭션은 외부 트랜잭션이 롤백되어도 커밋된다")
    void registerWebhook_REQUIRES_NEW_외부_트랜잭션_롤백_후에도_커밋된다() {
        // 외부 트랜잭션 시작
        var outerTx = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // REQUIRES_NEW 내부 트랜잭션: webhook 정보 저장 후 커밋
        var innerTxDef = new DefaultTransactionDefinition();
        innerTxDef.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var innerTx = transactionManager.getTransaction(innerTxDef);
        try {
            GithubRepository repo = githubRepositoryRepository.findById(repoId).orElseThrow();
            repo.updateWebhook("hook-99999", "secret", "http://localhost/webhook", true);
            githubRepositoryRepository.save(repo);
            transactionManager.commit(innerTx);
        } catch (Exception e) {
            transactionManager.rollback(innerTx);
            throw new RuntimeException(e);
        }

        // 외부 트랜잭션 롤백
        transactionManager.rollback(outerTx);

        // REQUIRES_NEW로 커밋된 webhook 정보는 외부 롤백과 무관하게 DB에 존재
        GithubRepository result = githubRepositoryRepository.findById(repoId).orElseThrow();
        assertThat(result.getWebhookId()).isEqualTo("hook-99999");
        assertThat(result.isWebhookActive()).isTrue();
    }
}
