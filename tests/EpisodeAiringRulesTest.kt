import com.franchiseguide.app.model.EpisodeAiringRules
import java.time.*

fun main() {
    val day = LocalDate.of(2026,11,7)
    EpisodeAiringRules.requireNotFuture(day,day)
    EpisodeAiringRules.requireNotFuture(day.minusDays(1),day)
    EpisodeAiringRules.requireNotFuture(null,day)
    check(runCatching { EpisodeAiringRules.requireNotFuture(day.plusDays(1),day) }.isFailure)
    val instant=Instant.parse("2026-11-07T00:30:00Z")
    for((zone,allowed) in listOf("Europe/Paris" to true,"UTC" to true,"America/Los_Angeles" to false,"Pacific/Auckland" to true)) {
        check(runCatching { EpisodeAiringRules.requireNotFuture(day,instant.atZone(ZoneId.of(zone)).toLocalDate()) }.isSuccess==allowed)
    }
    println("Episode airing guard: 8 assertions passed; no provider or Android-device test.")
}
