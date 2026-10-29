package com.hama.domain.goal.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 목표 쪽에서 todo 테이블을 읽기 전용으로 집계합니다. 목표별 전체·완료 개수를 GROUP BY 한 번으로 끝내려고 SQL 로 둡니다.
 *
 * <p>todo 를 바꾸는 작업은 여기서 하지 마세요. SQL 로 바꾸면 영속성 컨텍스트의 Todo 엔티티와 어긋납니다.
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

    /**
     * 한 목표의 AI 투두를 내용(content)별로 셉니다. 플랜 선택이 할 일 내용을 그대로 투두 내용으로 쓰므로 할 일별 진행이 됩니다.
     * from·to 를 주면 그 기간(양끝 포함)의 투두만 셉니다. 둘 다 null 이면 전체입니다.
     */
    public Map<String, GoalTodoCount> countByContent(Long goalId, LocalDate from, LocalDate to) {
        Map<String, GoalTodoCount> counts = new HashMap<>();
        MapSqlParameterSource params = new MapSqlParameterSource("goalId", goalId);
        String period = "";
        if (from != null && to != null) {
            period = " AND todo_date BETWEEN :from AND :to";
            params.addValue("from", from).addValue("to", to);
        }
        jdbc.query("""
                        SELECT content, COUNT(*) AS total,
                               SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed
                        FROM todo
                        WHERE goal_id = :goalId AND %s%s
                        GROUP BY content
                        """.formatted(ACTIVE_GOAL_TASK, period),
                params,
                rs -> {
                    counts.put(rs.getString("content"),
                            new GoalTodoCount(rs.getLong("total"), rs.getLong("completed")));
                });
        return counts;
    }
}
