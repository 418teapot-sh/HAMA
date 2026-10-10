package com.hama.domain.todo.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidTodoGoalLink.Validator.class)
public @interface ValidTodoGoalLink {
    String message() default "투두의 목표 연결이 올바르지 않습니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidTodoGoalLink, CreateTodoRequest> {
        @Override
        public boolean isValid(CreateTodoRequest request, ConstraintValidatorContext context) {
            if (request == null) return true;
            boolean valid = true;
            context.disableDefaultConstraintViolation();
            if ("TASK".equals(request.category())) {
                if (request.goalId() != null) {
                    error(context, "goalId", "일반 TASK에는 목표를 연결할 수 없습니다."); valid = false;
                }
                if (request.periodGoalId() != null) {
                    error(context, "periodGoalId", "일반 TASK에는 기간 목표를 연결할 수 없습니다."); valid = false;
                }
            } else if ("AI_GOAL_TASK".equals(request.category()) && request.goalId() == null) {
                error(context, "goalId", "목표 투두에는 goalId가 필요합니다."); valid = false;
            }
            return valid;
        }

        private void error(ConstraintValidatorContext context, String field, String message) {
            context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
        }
    }
}
