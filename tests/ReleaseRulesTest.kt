import com.franchiseguide.app.model.*
import java.io.ByteArrayInputStream
private var total=0
private fun verify(name:String, ok:Boolean){check(ok){name};total++;println("PASS $name")}
private fun rejects(name:String, block:()->Any?)=verify(name,runCatching(block).isFailure)
fun main(){
    for(n in 1..11)verify("Schema $n supported",ReleaseRules.schema(n)==n)
    verify("Missing schema is legacy V1",ReleaseRules.schema(null)==1)
    for(n in listOf(-1,0,12,5.5,"6",Double.NaN,Double.POSITIVE_INFINITY,Long.MAX_VALUE))rejects("Reject schema $n"){ReleaseRules.schema(n)}
    rejects("Four words cannot be posted"){ReleaseRules.reviewText("Un avis trop bref")}
    rejects("Five characters are not five words"){ReleaseRules.reviewText("abcde")}
    verify("Exactly five words",ReleaseRules.reviewText("Un avis comporte cinq mots").words==5)
    verify("199 words ordinary comment",!ReleaseRules.reviewText(List(199){"mot"}.joinToString(" ")).review)
    verify("200 words long review",ReleaseRules.reviewText(List(200){"mot"}.joinToString(" ")).review)
    rejects("Oversized review"){ReleaseRules.reviewText("un deux trois quatre "+"x".repeat(10_000))}
    verify("Paged show review path",ReleaseRules.reviewPath(MediaKind.SHOW,42,2)=="/shows/42/comments/likes?page=2&limit=20&extended=full")
    verify("Movie review path",ReleaseRules.reviewPath(MediaKind.MOVIE,43,1).startsWith("/movies/43/"))
    rejects("Games not sent to review API"){ReleaseRules.reviewPath(MediaKind.GAME,42,1)}
    rejects("Invalid review page"){ReleaseRules.reviewPath(MediaKind.SHOW,42,0)}
    rejects("Bounded review pages"){ReleaseRules.reviewPath(MediaKind.SHOW,42,501)}
    rejects("Invalid review ID"){ReleaseRules.reviewPath(MediaKind.SHOW,0,1)}
    verify("Own review accepted",ReleaseRules.ownsReview("alice","Alice"))
    verify("Other review not owned",!ReleaseRules.ownsReview("alice","bob"))
    verify("Guests never own reviews",!ReleaseRules.ownsReview(null,null))
    verify("Blank users never own reviews",!ReleaseRules.ownsReview("",""))
    verify("UTF8 import bounded",ReleaseRules.readImport(ByteArrayInputStream("étoile".toByteArray()),7)=="étoile")
    verify("BOM accepted",ReleaseRules.readImport(ByteArrayInputStream("\uFEFF[]".toByteArray()))=="[]")
    rejects("Overlimit rejected before decode"){ReleaseRules.readImport(ByteArrayInputStream(ByteArray(10)),9)}
    rejects("Invalid UTF8 rejected"){ReleaseRules.readImport(ByteArrayInputStream(byteArrayOf(0xc3.toByte(),0x28)))}
    verify("Empty title language follows general preference",ReleaseRules.contentLanguage("  ")==null)
    for(code in listOf("fr-FR","en-US","ja-JP","de"))verify("Title language $code",ReleaseRules.contentLanguage(code)==code)
    for(code in listOf("FR-fr","en-US-extra","../fr","<fr>","fra"))rejects("Reject title language $code"){ReleaseRules.contentLanguage(code)}
    println("ReleaseRules: $total assertions passed on JVM (no Android runtime).")
}
