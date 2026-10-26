package com.franchiseguide.app.model

import java.net.URI
import java.text.Normalizer
import java.util.Locale

data class BookEdition(val id:String,val title:String,val workVerified:Boolean,val pages:Int?,val publishDate:String,val publishers:List<String>,val languages:List<String>,val format:String,val isbns:List<String>,val invalidIsbnCount:Int,val coverUrl:String?,val work:String) {
    val sourceUrl:String get()="https://openlibrary.org$id"
}
data class BookEditionPage(val work:String,val offset:Int,val total:Int,val entries:List<BookEdition>,val nextOffset:Int?)
data class BookPagePlan(val mediaId:String,val work:String,val current:Int,val total:Int,val unit:String,val pages:Int,val createdAt:Long)

object BookEditionRules {
    const val PAGE_SIZE=30
    const val MAX_LOADED=300
    fun key(raw:String,type:String="work"):String {
        val prefix=if(type=="work")"works" else "books";val suffix=if(type=="work")"W" else "M"
        val id=Regex("^(?:/$prefix/)?(OL[1-9][0-9]{0,11}$suffix)$").matchEntire(raw.trim())?.groupValues?.get(1)?:error("Identifiant Open Library invalide.")
        return "/$prefix/$id"
    }
    fun text(v:Any?,label:String,max:Int=1000):String {if(v==null)return "";require(v is String && v.length<=max&&!Regex("[\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f\\u007f]").containsMatchIn(v)){"$label invalide."};return v.trim()}
    private fun strings(v:Any?,label:String):List<String> {if(v==null)return emptyList();require(v is List<*> && v.size<=100);return v.map{ text(it,label) }.filter{it.isNotBlank()}.distinct()}
    private fun integer(v:Any?,label:String,max:Int):Int {require(v is Number&&v.toDouble().isFinite()&&v.toDouble()%1.0==0.0&&v.toDouble() in 0.0..max.toDouble()){ "$label invalide." };return v.toInt()}
    fun isbn(raw:String):String? {
        val x=raw.replace(Regex("[\\s-]"),"").uppercase(Locale.ROOT)
        if(Regex("[0-9]{9}[0-9X]").matches(x))return x.takeIf { x.mapIndexed { i,c->(10-i)*(if(c=='X')10 else c.digitToInt()) }.sum()%11==0 }
        if(Regex("(?:978|979)[0-9]{10}").matches(x))return x.takeIf { x.mapIndexed{i,c->c.digitToInt()*if(i%2==0)1 else 3}.sum()%10==0 }
        return null
    }
    fun cover(v:Any?):String? {if(v==null)return null;require(v is List<*> && v.size<=100);val id=v.filterIsInstance<Number>().firstOrNull{it.toDouble()>0&&it.toDouble()%1.0==0.0&&it.toDouble()<=9007199254740991.0}?.toLong();return id?.let{"https://covers.openlibrary.org/b/id/$it-M.jpg"}}
    fun edition(raw:Map<String,Any?>,work:String):BookEdition {
        val id=key(text(raw["key"],"Édition"),"edition");val title=text(raw["title"],"Titre");require(title.isNotEmpty())
        val works=raw["works"]?:emptyList<Any?>();require(works is List<*> && works.size<=100)
        val refs=works.map{ require(it is Map<*,*>);key(text(it["key"],"Œuvre")) };require(refs.isEmpty()||work in refs){"Édition reçue pour une autre œuvre."}
        val languages=raw["languages"]?:emptyList<Any?>();require(languages is List<*> && languages.size<=100)
        val langs=languages.map { require(it is Map<*,*>);val k=text(it["key"],"Langue");require(Regex("/languages/[a-z]{3}").matches(k));k.substringAfterLast('/') }.distinct()
        val allIsbn=strings(raw["isbn_10"],"ISBN")+strings(raw["isbn_13"],"ISBN");val valid=allIsbn.mapNotNull(::isbn)
        return BookEdition(id,title,work in refs,raw["number_of_pages"]?.let{integer(it,"Pages",10000000)},text(raw["publish_date"],"Publication",200),strings(raw["publishers"],"Éditeurs"),langs,text(raw["physical_format"],"Format",300),valid.distinct(),allIsbn.size-valid.size,cover(raw["covers"]),work)
    }
    fun page(raw:Map<String,Any?>,workId:String,offset:Int=0,limit:Int=PAGE_SIZE):BookEditionPage {
        val work=key(workId);require(offset in 0..100000&&limit in 1..PAGE_SIZE)
        val total=integer(raw["size"],"Nombre d’éditions",1000000);val entries=raw["entries"];require(entries is List<*> && entries.size<=limit)
        require(offset+entries.size<=total && (offset>=total||entries.isNotEmpty())) {"Page d’éditions incomplète."}
        val links=raw["links"] as? Map<*,*>;links?.get("work")?.let{require(key(text(it,"Œuvre"))==work)}
        val items=entries.map { require(it is Map<*,*>);@Suppress("UNCHECKED_CAST") edition(it as Map<String,Any?>,work) };require(items.map{it.id}.distinct().size==items.size){"Édition en double."}
        // Construct next request ourselves; no provider-controlled URL is followed.
        val next=if(offset+items.size<total)offset+items.size else null
        links?.get("next")?.let { value ->
            val u=URI("https://openlibrary.org").resolve(text(value,"Pagination",2000));require(u.scheme=="https"&&u.host=="openlibrary.org"&&u.port==-1&&u.userInfo==null&&u.fragment==null&&u.path=="$work/editions.json")
            val parts=(u.rawQuery?:"").split('&').map{it.split('=',limit=2)};require(parts.all{it.size==2}&&parts.map{it[0]}.distinct().size==parts.size&&parts.all{it[0] in setOf("offset","limit")})
            val params=parts.associate{it[0] to it[1]};require(next!=null&&params["offset"]?.toIntOrNull()==next&&(params["limit"]==null||params["limit"]?.toIntOrNull()==limit)){"Pagination incohérente."}
        }
        return BookEditionPage(work,offset,total,items,next)
    }
    fun merge(old:BookEditionPage?,page:BookEditionPage):BookEditionPage {
        if(old==null)return page
        require(old.work==page.work&&old.nextOffset==page.offset&&old.total==page.total){"Liste changée : repars de la première page."}
        val all=old.entries+page.entries;require(all.size<=MAX_LOADED&&all.map{it.id}.distinct().size==all.size){"Page répétée ou limite atteinte."};return page.copy(offset=0,entries=all)
    }
    fun filter(entries:List<BookEdition>,query:String,language:String="",known:Boolean=false):List<BookEdition> {
        fun norm(v:String)=Normalizer.normalize(v,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT)
        val q=norm(query);return entries.filter{e->(language.isBlank()||language in e.languages)&&(!known||(e.pages?:0)>0)&&(q.isBlank()||norm((listOf(e.id,e.title,e.format,e.publishDate)+e.publishers+e.isbns).joinToString(" ")).contains(q))}
    }
    private fun valid(m:MediaItem) {require(m.kind==MediaKind.BOOK&&m.source=="openlibrary"&&m.added&&!m.isSuggestion);key(m.sourceId?:error("Œuvre absente."))}
    fun prepare(m:MediaItem,e:BookEdition,now:Long=System.currentTimeMillis()):BookPagePlan {
        valid(m);require(e.work==key(m.sourceId!!)&&e.workVerified&&e.pages!=null&&e.pages>0){"Pages et rattachement de l’édition requis."};require(m.progressUnit.trim().lowercase(Locale.ROOT)=="pages"){"Le suivi doit déjà être en pages."};require(m.progressCurrent<=e.pages){"L’avancement dépasse ce total. Aucune progression réduite."}
        return BookPagePlan(m.id,key(m.sourceId!!),m.progressCurrent,m.progressTotal,m.progressUnit,e.pages,now)
    }
    fun apply(m:MediaItem,p:BookPagePlan,now:Long=System.currentTimeMillis()):MediaItem {
        valid(m);require(now>=p.createdAt&&now-p.createdAt<=600000){"Confirmation expirée."};require(m.id==p.mediaId&&key(m.sourceId!!)==p.work&&m.progressCurrent==p.current&&m.progressTotal==p.total&&m.progressUnit==p.unit){"La fiche ou sa progression a changé."};require(p.pages in 1..10000000&&p.pages>=m.progressCurrent)
        return m.copy(progressTotal=p.pages)
    }
}
