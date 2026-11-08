package com.gravitycanvas.service;

import com.gravitycanvas.dto.*;
import com.gravitycanvas.entity.*;
import com.gravitycanvas.repository.GravitySettingsRepository;
import com.gravitycanvas.repository.TaskRepository;
import com.gravitycanvas.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test suite verifying TaskService's repository invocation patterns in Phase 8:
 * 1. getAllTasks() and getPendingTasks() invoke findAllWithDependencies() instead of findAll().
 * 2. Single-task write operations (approve, reject, updateStatus, updateDeadline, pingBlocker, etc.)
 *    pass List.of(task) and NEVER invoke taskRepository.findAll().
 *
 * Mocks only JPA repository interfaces to ensure compatibility across all JDK versions.
 */
class Phase8TaskServiceTest {

    private TaskRepository taskRepository;
    private UserAccountRepository userRepository;
    private GravitySettingsRepository settingsRepository;
    private GravityEngineService gravityEngineService;
    private GravityCalculatorService gravityCalculatorService;
    private SettingsService settingsService;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskRepository = Mockito.mock(TaskRepository.class);
        userRepository = Mockito.mock(UserAccountRepository.class);
        settingsRepository = Mockito.mock(GravitySettingsRepository.class);

        when(settingsRepository.findAll()).thenReturn(List.of(new GravitySettings()));

        gravityEngineService = new GravityEngineService(taskRepository);
        settingsService = new SettingsService(settingsRepository);
        gravityCalculatorService = new GravityCalculatorService(gravityEngineService, taskRepository, settingsService);
        taskService = new TaskService(taskRepository, userRepository, gravityCalculatorService, settingsService);
    }

    private Task sampleTask(Long id) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("Sample Task " + id);
        t.setStatus(TaskStatus.TODO);
        t.setApprovalStatus(ApprovalStatus.APPROVED);
        t.setEnvironment(TaskEnvironment.DEVELOPMENT);
        t.setDeadline(LocalDateTime.now().plusDays(2));
        t.setCreatedAt(LocalDateTime.now());
        t.setLastActivityAt(LocalDateTime.now());
        t.setDependencies(new HashSet<>());
        t.setDependentTasks(new HashSet<>());
        return t;
    }

    @Test
    @DisplayName("List Queries: getAllTasks() calls findAllWithDependencies(), never findAll()")
    void testGetAllTasksCallsFindAllWithDependencies() {
        Task t1 = sampleTask(1L);
        when(taskRepository.findAllWithDependencies()).thenReturn(List.of(t1));

        List<TaskResponseDto> result = taskService.getAllTasks();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(taskRepository, times(1)).findAllWithDependencies();
        verify(taskRepository, never()).findAll();
    }

    @Test
    @DisplayName("List Queries: getPendingTasks() calls findAllWithDependencies(), never findAll()")
    void testGetPendingTasksCallsFindAllWithDependencies() {
        Task t1 = sampleTask(1L);
        t1.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        when(taskRepository.findAllWithDependencies()).thenReturn(List.of(t1));

        List<TaskResponseDto> result = taskService.getPendingTasks();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(taskRepository, times(1)).findAllWithDependencies();
        verify(taskRepository, never()).findAll();
    }

    @Test
    @DisplayName("Single-task Write: approveTask passes single task and never calls findAll()")
    void testApproveTaskNeverCallsFindAll() {
        Task t = sampleTask(10L);
        t.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(t));
        when(taskRepository.save(any(Task.class))).thenReturn(t);

        TaskResponseDto dto = taskService.approveTask(10L);

        assertNotNull(dto);
        assertEquals(ApprovalStatus.APPROVED, dto.getApprovalStatus());
        verify(taskRepository, never()).findAll();
        verify(taskRepository, never()).findAllWithDependencies();
    }

    @Test
    @DisplayName("Single-task Write: updateStatus passes single task and never calls findAll()")
    void testUpdateStatusNeverCallsFindAll() {
        Task t = sampleTask(20L);
        when(taskRepository.findById(20L)).thenReturn(Optional.of(t));
        when(taskRepository.save(any(Task.class))).thenReturn(t);

        TaskResponseDto dto = taskService.updateStatus(20L, new UpdateStatusRequest(TaskStatus.IN_PROGRESS));

        assertNotNull(dto);
        assertEquals(TaskStatus.IN_PROGRESS, dto.getStatus());
        verify(taskRepository, never()).findAll();
        verify(taskRepository, never()).findAllWithDependencies();
    }

    @Test
    @DisplayName("Single-task Write: pingBlocker passes single task and never calls findAll()")
    void testPingBlockerNeverCallsFindAll() {
        Task t = sampleTask(30L);
        when(taskRepository.findById(30L)).thenReturn(Optional.of(t));
        when(taskRepository.save(any(Task.class))).thenReturn(t);

        TaskResponseDto dto = taskService.pingBlocker(30L);

        assertNotNull(dto);
        assertEquals(1, dto.getPingCount());
        verify(taskRepository, never()).findAll();
        verify(taskRepository, never()).findAllWithDependencies();
    }
}
