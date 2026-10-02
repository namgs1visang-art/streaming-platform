package com.streaming.admin.schedule;

import com.streaming.core.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** 관리자 > LIVE > 스케줄 관리 */
@RestController
@RequestMapping("/api/admin/schedules")
@RequiredArgsConstructor
public class ScheduleAdminController {

    private final ScheduleService scheduleService;

    /** 예: /api/admin/schedules?from=2026-10-01&to=2026-10-07 */
    @GetMapping
    public List<ScheduleDto.Response> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleService.findInRange(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream().map(ScheduleDto.Response::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleDto.Response create(@Valid @RequestBody ScheduleDto.CreateRequest req) {
        return ScheduleDto.Response.of(scheduleService.create(
                req.channelId(), req.title(), req.startAt(), req.endAt(), req.recordEnabled()));
    }

    @PutMapping("/{id}")
    public ScheduleDto.Response update(@PathVariable Long id, @Valid @RequestBody ScheduleDto.UpdateRequest req) {
        return ScheduleDto.Response.of(scheduleService.update(
                id, req.title(), req.startAt(), req.endAt(), req.recordEnabled()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        scheduleService.delete(id);
    }
}
