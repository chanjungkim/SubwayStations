package com.stazis.subwaystations.utils

import io.reactivex.Scheduler

interface SchedulerProvider {

    fun uiScheduler(): Scheduler
    fun ioScheduler(): Scheduler
}