package com.hama.domain.goal.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 목표 쪽에서 todo 테이블을 집계·일괄 변경합니다.
 *
 * <p>todo 는 BE3 소유 엔티티이고 아직 goal_id / period_goal_id 를 매핑하지 않아서(V5 마이그레이션 참고)
 * JPQL 대신 SQL 로 직접 조회합니다. Todo 엔티티가 이 컬럼을 매핑하면 JPQL 로 옮겨도 됩니다.
 */
@Repository
@RequiredArgsConstructor
public class GoalTodoRepository {

    private static final String ACTIVE_GOAL_TASK =
            "category = 'AI_GOAL_TASK' AND deleted_at IS NULL";

    private final NamedParameterJdbcTemplate jdbc;

    /** 목표별 집계. 투두가 없는 목표는 결과에 없으니 {@link GoalTodoCount#EMPTY} 로 대신하세요. */
    public Map<Long, GoalTodoCount> countByGoals(Collection<Long> goalIds) {
        Map<Long, GoalTodoCount> counts = new HashMap<>();
        if (goalIds.isEmpty()) {
            return counts;
        }
        jdbc.query("""
                        SELECT goal_id, COUNT(*) AS total,
                               SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed
                        FROM todo
                        WHERE goal_id IN (:goalIds) AND %s
                        GROUP BY goal_id
                        """.formatted(ACTIVE_GOAL_TASK),
                new MapSqlParameterSource("goalIds", goalIds),
                rs -> {
                    counts.put(rs.getLong("goal_id"),
                            new GoalTodoCount(rs.getLong("total"), rs.getLong("completed")));
                });
        return counts;
    }

    public GoalTodoCount countByGoal(Long goalId) {
        return countByGoals(List.of(goalId)).getOrDefault(goalId, GoalTodoCount.EMPTY);
    }

    /** 기간 목표별 집계 (트리 조회용). */
    public Map<Long, GoalTodoCount> countByPeriodGoals(Long goalId) {
        Map<Long, GoalTodoCount> counts = new HashMap<>();
        jdbc.query("""
                        SELECT period_goal_id, COUNT(*) AS total,
                               SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed
                        FROM todo
                        WHERE goal_id = :goalId AND period_goal_id IS NOT NULL AND %s
                        GROUP BY period_goal_id
                        """.formatted(ACTIVE_GOAL_TASK),
                new MapSqlParameterSource("goalId", goalId),
                rs -> {
                    counts.put(rs.getLong("period_goal_id"),
                            new GoalTodoCount(rs.getLong("total"), rs.getLong("completed")));
                });
        return counts;
    }

    /** 목표 삭제 시 연결된 투두(카테고리 무관)를 같은 트랜잭션에서 소프트 삭제합니다. */
    public int softDeleteByGoal(Long goalId, LocalDateTime now) {
        return jdbc.update("""
                        UPDATE todo SET deleted_at = :now, updated_at = :now
                        WHERE goal_id = :goalId AND deleted_at IS NULL
                        """,
                new MapSqlParameterSource()
                        .addValue("goalId", goalId)
                        .addValue("now", now));
    }
}
