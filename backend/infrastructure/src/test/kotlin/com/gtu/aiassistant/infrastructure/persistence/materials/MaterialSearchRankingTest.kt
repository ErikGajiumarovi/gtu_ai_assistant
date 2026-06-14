package com.gtu.aiassistant.infrastructure.persistence.materials

import kotlin.test.Test
import kotlin.test.assertTrue

class MaterialSearchRankingTest {
    @Test
    fun `hybrid ranking prefers russian objective chunk over unrelated code chunk`() {
        val signals = buildMaterialQuerySignals("какая цель работы?")

        val objectiveScore = scoreMaterialCandidate(
            signals = signals,
            vectorScore = 0.08,
            title = "МойЗайм Дипломная Работа v3",
            text = "Целью работы является проектирование и реализация полнофункционального веб-приложения для управления онлайн-займами.",
            headingPath = "1.1. Цели и задачи"
        )
        val unrelatedScore = scoreMaterialCandidate(
            signals = signals,
            vectorScore = 0.11,
            title = "МойЗайм Дипломная Работа v3",
            text = "return request('/loans/pay', { method: \"POST\", body: JSON.stringify(payload) });",
            headingPath = null
        )

        assertTrue(objectiveScore > unrelatedScore)
        assertTrue(objectiveScore >= 0.2)
    }

    @Test
    fun `hybrid ranking prefers technology stack chunk over unrelated table of contents chunk`() {
        val signals = buildMaterialQuerySignals("какой технологический стек используется?")

        val stackScore = scoreMaterialCandidate(
            signals = signals,
            vectorScore = 0.09,
            title = "МойЗайм Дипломная Работа v3",
            text = "Технологический стек системы включает React, Spring Boot, PostgreSQL, Docker и REST API.",
            headingPath = "2.2. Обоснование технологического стека"
        )
        val contentsScore = scoreMaterialCandidate(
            signals = signals,
            vectorScore = 0.15,
            title = "МойЗайм Дипломная Работа v3",
            text = "5.2. Модели данных и репозитории 18",
            headingPath = null
        )

        assertTrue(stackScore > contentsScore)
        assertTrue(stackScore >= 0.2)
    }
}
