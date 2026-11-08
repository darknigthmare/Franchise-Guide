import com.franchiseguide.app.model.SupportReportRules

fun main() {
    val confidentialText="Bearer fictional-token /confidentialText/path alice@example.invalid note-personnelle"
    val report=SupportReportRules.build("0.38.0",4,50,3,listOf("error","warn",confidentialText),false,true)
    check(!report.toString().contains(confidentialText));check(report.getInt("errorCount")==1);check(report.getInt("warningCount")==1)
    check(report.getString("version")=="0.38.0");check(!report.getBoolean("accountsIncluded"));check(!report.getBoolean("rawMessagesIncluded"))
    check(runCatching { SupportReportRules.build(confidentialText,0,0,0,emptyList(),false,true) }.isFailure)
    check(runCatching { SupportReportRules.build("0.38.0",-1,0,0,emptyList(),false,true) }.isFailure)
    println("SupportReportRules: 8 assertions passed")
}
