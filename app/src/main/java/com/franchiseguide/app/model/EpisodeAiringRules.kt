package com.franchiseguide.app.model

import java.time.LocalDate

/** Protect new user commands, not imported or already-confirmed remote history. */
object EpisodeAiringRules {
    fun requireNotFuture(date: LocalDate?, today: LocalDate = LocalDate.now()) {
        require(date == null || !date.isAfter(today)) {
            "Cet épisode est annoncé pour une date future. Aucun visionnage enregistré ni envoyé."
        }
    }
}
