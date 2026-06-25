package com.waigi.dock.di

import com.waigi.dock.database.DockDatabase
import com.waigi.dock.repository.HistoryRepository
import com.waigi.dock.ui.viewmodel.HistoryViewModel
import com.waigi.dock.ui.viewmodel.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // ── Database ──────────────────────────────────────────────────────────────
    single { DockDatabase.getInstance(get()) }
    single { get<DockDatabase>().historyDao() }

    // ── Repositories ──────────────────────────────────────────────────────────
    single { HistoryRepository(get()) }

    // ── ViewModels ────────────────────────────────────────────────────────────
    viewModel { HomeViewModel() }
    viewModel { HistoryViewModel(get()) }
}
