package com.hama.domain.goalai.service;

import com.hama.domain.goalai.entity.GoalProgressSummary;
import com.hama.domain.goalai.repository.GoalProgressSummaryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GoalProgressQueue {

    private final GoalProgressSummaryRepository summaries;
    private final Clock clock;

    public GoalProgressQueue(GoalProgressSummaryRepository summaries, @Qualifier("be3Clock") Clock clock) {
        this.summaries = summaries;
        this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void changed(GoalProgressChanged event) {
        if (event.goalId() == null) return;
        LocalDateTime now = LocalDateTime.now(clock);
        summaries.findForUpdate(event.goalId()).ifPresentOrElse(s -> s.markDirty(now),
                () -> summaries.save(GoalProgressSummary.create(event.goalId(), now)));
    }
}
