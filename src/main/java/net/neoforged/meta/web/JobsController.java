package net.neoforged.meta.web;

import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;

@Controller
public class JobsController {
    private final Collection<ScheduledTaskHolder> scheduledTaskHolders;

    public JobsController(Collection<ScheduledTaskHolder> scheduledTaskHolders) {
        this.scheduledTaskHolders = scheduledTaskHolders;
    }

    @GetMapping("/ui/jobs")
    public String index(Model model) {
        var jobs = new ArrayList<JobModel>();

        for (var scheduledTaskHolder : scheduledTaskHolders) {
            for (var task : scheduledTaskHolder.getScheduledTasks()) {
                var nextExecution = task.nextExecution();
                var lastOutcome = task.getTask().getLastExecutionOutcome();
                jobs.add(new JobModel(
                        task.toString(),
                        nextExecution,
                        lastOutcome.executionTime(),
                        lastOutcome.status(),
                        lastOutcome.throwable()
                ));
            }
        }

        model.addAttribute("jobs", jobs);

        return "jobs";
    }
}
