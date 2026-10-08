package com.hama.domain.todo.repository;

import com.hama.domain.todo.entity.Todo;
import com.hama.domain.todo.entity.TodoCategory;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    @Query("""
            select t from Todo t
            where t.userId = :userId and t.todoDate = :date and t.deletedAt is null
              and (t.category = com.hama.domain.todo.entity.TodoCategory.TASK or exists
                  (select g.id from Goal g where g.id = t.goalId and g.userId = :userId and g.deletedAt is null))
              and (:category is null or t.category = :category)
            order by t.id
            """)
    List<Todo> findActiveByDate(@Param("userId") Long userId, @Param("date") LocalDate date,
            @Param("category") TodoCategory category);

    interface Link {
        Long getUserId();
        Long getGoalId();
    }

    List<Todo> findByGoalIdAndDeletedAtIsNull(Long goalId);

    @Query("select t.userId as userId, t.goalId as goalId from Todo t where t.id = :id and t.deletedAt is null")
    Optional<Link> findActiveLink(@Param("id") Long id);

    // 모든 상태 변경을 같은 행 잠금으로 직렬화해 완료 중복과 미룬 횟수 유실을 방지합니다.
    // 소유권은 조회 후 검사해야 타인 리소스 403 / 없는 리소스 404를 구분할 수 있습니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Todo t where t.id = :id and t.deletedAt is null")
    Optional<Todo> findActiveForUpdate(@Param("id") Long id);
}
