package com.taskos.time.service;

import java.util.UUID;

public record ManualEntryMutation(UUID id, boolean overlapWarning) { }
