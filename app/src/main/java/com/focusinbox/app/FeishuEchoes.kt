package com.focusinbox.app

/** Complete-message/block matching only. Individual short lines are never harvested from a summary. */
object FeishuEchoes {
    fun plainText(message: String)=message.trim()
    private fun normalized(text: String)=text.replace("\r\n","\n").replace('\r','\n').trim()
    private fun senderPart(line: String): Pair<String,String>? {
        val index=line.indexOfFirst { it==':' || it=='：' }
        if(index !in 1..80) return null
        return line.substring(0,index).trim() to line.substring(index+1).trim()
    }
    /** Used only after a full echo was removed from another field of the same notification. */
    fun derivedPreview(text: String,summaries: Collection<String>): Boolean {
        val value=normalized(text)
        val bare=senderPart(value)?.second ?: value
        return summaries.any { summary ->
            val full=normalized(summary)
            listOf(value,bare).any { part -> (part.length>=24 && part==full.lineSequence().firstOrNull()?.trim()) ||
                ((part.endsWith("…") || part.endsWith("...")) && part.trimEnd('…','.').length>=24 && full.startsWith(part.trimEnd('…','.'))) }
        }
    }
    fun remove(text: String,summaries: Collection<String>): String {
        if(summaries.isEmpty() || text.isBlank()) return text
        val lines=normalized(text).lines()
        val known=summaries.filter { it.isNotBlank() }.flatMap { summary ->
            listOf(normalized(summary),normalized(summary).replace(Regex("[\\r\\n]+")," · "))
        }.distinct().map { it.lines().map(String::trim) }
        val kept=mutableListOf<String>(); var index=0; var changed=false
        while(index<lines.size) {
            val line=lines[index].trim()
            var length=0
            for(block in known) {
                if(index+block.size>lines.size) continue
                val firstPrefix=senderPart(line)
                val sender=if(firstPrefix?.second==block.first()) firstPrefix.first else null
                if(line!=block.first() && sender==null) continue
                val matches=block.indices.all { offset ->
                    val actual=lines[index+offset].trim()
                    actual==block[offset] || (sender!=null && senderPart(actual)?.let { it.first==sender && it.second==block[offset] }==true)
                }
                if(matches) length=maxOf(length,block.size)
            }
            if(length>0) { changed=true; index+=length } else { kept.add(lines[index]); index++ }
        }
        return if(changed) kept.joinToString("\n") else text
    }
}
