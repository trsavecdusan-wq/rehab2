package com.rehab2.aac

import java.text.Normalizer
import java.util.Locale

object AacKeywordMatcher {
    private val RULES = listOf(
        Rule(listOf("caj", "tea"), listOf("tea", "thirsty")),
        Rule(listOf("voda", "vodo", "water"), listOf("water", "thirsty")),
        Rule(listOf("kava", "kavo", "coffee"), listOf("coffee", "thirsty")),
        Rule(listOf("fanta"), listOf("drink_fanta", "thirsty")),
        Rule(listOf("coca cola", "cocacola", "kokakola"), listOf("drink_coca_cola", "thirsty")),
        Rule(listOf("pepsi"), listOf("drink_pepsi", "thirsty")),
        Rule(listOf("sok", "juice"), listOf("juice", "thirsty")),
        Rule(listOf("mleko", "milk"), listOf("drink_milk", "thirsty")),
        Rule(listOf("pila", "pil", "piti", "zeja", "zejna", "zejen"), listOf("thirsty")),
        Rule(listOf("boli", "bolecina"), listOf("pain")),
        Rule(listOf("noga", "nogo", "noge"), listOf("leg")),
        Rule(listOf("roka", "roko", "roke"), listOf("arm")),
        Rule(listOf("glava", "glavo", "glave"), listOf("head")),
        Rule(listOf("trebuh", "trebuhom"), listOf("belly")),
        Rule(listOf("hrbet", "hrbtom"), listOf("back")),
        Rule(listOf("grlo", "grlom"), listOf("throat")),
        Rule(listOf("zana"), listOf("person_zana")),
        Rule(listOf("dusan"), listOf("person_dusan")),
        Rule(listOf("sergej"), listOf("person_sergej")),
        Rule(listOf("julija"), listOf("person_julija")),
        Rule(listOf("oksana"), listOf("person_oksana")),
        Rule(listOf("inna"), listOf("person_inna")),
        Rule(listOf("franc"), listOf("person_franc")),
        Rule(listOf("kje"), listOf("where_root")),
        Rule(listOf("kaj"), listOf("what_root")),
        Rule(listOf("kdaj"), listOf("when_root")),
        Rule(listOf("domov", "doma"), listOf("home")),
        Rule(listOf("wc", "stranisce", "toaleta"), listOf("wc")),
        Rule(listOf("pomoc", "pomagaj"), listOf("help")),
        Rule(listOf("pridi", "pride"), listOf("come_to_me")),
        Rule(listOf("pocakaj"), listOf("wait")),
        Rule(listOf("ponovi"), listOf("repeat"))
    )

    fun match(text: String, keywords: List<String>): List<String> {
        val normalizedText = normalize(text)
        val hasAllowedKeyword = keywords
            .map(::normalize)
            .filter { keyword -> keyword.isNotBlank() }
            .any { keyword -> containsTerm(normalizedText, keyword) }
        if (!hasAllowedKeyword) {
            return emptyList()
        }
        return matchWithRules(text, RULES)
    }

    fun matchToAacItems(text: String): List<String> {
        return matchWithRules(text, RULES)
    }

    private fun matchWithRules(text: String, rules: List<Rule>): List<String> {
        val normalizedText = normalize(text)
        if (normalizedText.isBlank()) {
            return emptyList()
        }

        return rules
            .filter { rule -> rule.keywords.any { keyword -> containsTerm(normalizedText, normalize(keyword)) } }
            .flatMap { rule -> rule.itemIds }
            .distinct()
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9 ]+".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private data class Rule(
        val keywords: List<String>,
        val itemIds: List<String>
    )

    private fun containsTerm(text: String, keyword: String): Boolean {
        if (keyword.isBlank()) {
            return false
        }
        return " $text ".contains(" $keyword ")
    }
}
