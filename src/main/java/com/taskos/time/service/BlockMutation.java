package com.taskos.time.service;

import java.util.UUID;

public record BlockMutation(UUID id, boolean conflictWarning) { }
